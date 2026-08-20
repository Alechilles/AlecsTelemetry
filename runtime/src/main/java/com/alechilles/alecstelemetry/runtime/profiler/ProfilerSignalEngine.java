package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure bounded evaluator for project profiler signals. */
public final class ProfilerSignalEngine {
    public static final String RULE_SET = "hot-path-v1";
    static final int MAX_WINDOWS = 5;
    static final int MAX_SIGNALS = 5;
    static final double MIN_MILLISECONDS = 20.0d;
    static final double MIN_SHARE_PERCENT = 2.0d;
    static final double PROVISIONAL_MILLISECONDS = 100.0d;
    static final double PROVISIONAL_SHARE_PERCENT = 20.0d;

    private static final Comparator<ProfilerProjectSignal> SIGNAL_ORDER = Comparator
            .comparingDouble(ProfilerProjectSignal::medianSharePercent)
            .reversed()
            .thenComparing(Comparator.comparingDouble(ProfilerProjectSignal::totalSampledMilliseconds)
                    .reversed())
            .thenComparing(signal -> signal.representative().fingerprint());

    @Nonnull
    public Evaluation evaluate(
            @Nonnull List<TelemetryProfilerSnapshot> previousWindows,
            @Nonnull List<TelemetryProfilerPathEvidence> currentPaths) {
        Objects.requireNonNull(previousWindows, "previousWindows");
        Objects.requireNonNull(currentPaths, "currentPaths");

        List<List<TelemetryProfilerPathEvidence>> windows = new ArrayList<>(MAX_WINDOWS);
        int start = Math.max(0, previousWindows.size() - (MAX_WINDOWS - 1));
        for (int index = start; index < previousWindows.size(); index++) {
            TelemetryProfilerSnapshot snapshot = Objects.requireNonNull(
                    previousWindows.get(index), "previousWindows contains null");
            windows.add(snapshot.paths());
        }
        windows.add(List.copyOf(currentPaths));

        Map<String, Candidate> candidates = candidates(windows);
        List<TelemetryProfilerPathEvidence> qualifiedCurrentPaths = currentPaths.stream()
                .map(path -> withQualification(path, qualification(path, candidates.get(path.fingerprint()))))
                .toList();
        return new Evaluation(qualifiedCurrentPaths, signals(candidates));
    }

    @Nonnull
    public List<ProfilerProjectSignal> signals(
            @Nonnull List<TelemetryProfilerSnapshot> windows) {
        Objects.requireNonNull(windows, "windows");
        int start = Math.max(0, windows.size() - MAX_WINDOWS);
        List<List<TelemetryProfilerPathEvidence>> boundedWindows = new ArrayList<>(MAX_WINDOWS);
        for (int index = start; index < windows.size(); index++) {
            TelemetryProfilerSnapshot snapshot = Objects.requireNonNull(
                    windows.get(index), "windows contains null");
            boundedWindows.add(snapshot.paths());
        }
        return signals(candidates(boundedWindows));
    }

    @Nonnull
    private static Map<String, Candidate> candidates(
            @Nonnull List<List<TelemetryProfilerPathEvidence>> windows) {
        Map<String, Candidate> candidates = new HashMap<>();
        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            List<TelemetryProfilerPathEvidence> paths = windows.get(windowIndex);
            if (paths == null || paths.isEmpty()) {
                continue;
            }
            for (TelemetryProfilerPathEvidence path : paths) {
                if (path == null || !qualifies(path)) {
                    continue;
                }
                candidates.computeIfAbsent(path.fingerprint(), ignored -> new Candidate())
                        .add(windowIndex, path);
            }
        }
        return candidates;
    }

    @Nonnull
    private static List<ProfilerProjectSignal> signals(@Nonnull Map<String, Candidate> candidates) {
        List<ProfilerProjectSignal> signals = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates.values()) {
            if (candidate.appearances.isEmpty()) {
                continue;
            }
            List<TelemetryProfilerPathEvidence> appearances = candidate.appearances.stream()
                    .map(Appearance::path)
                    .toList();
            double[] shares = appearances.stream()
                    .mapToDouble(TelemetryProfilerPathEvidence::selectedWorldThreadSharePercent)
                    .sorted()
                    .toArray();
            double median = shares.length % 2 == 0
                    ? (shares[shares.length / 2 - 1] + shares[shares.length / 2]) / 2.0d
                    : shares[shares.length / 2];
            double totalSampledMilliseconds = appearances.stream()
                    .mapToDouble(TelemetryProfilerPathEvidence::sampledMilliseconds)
                    .sum();
            int qualifyingWindows = Math.min(MAX_WINDOWS, appearances.size());
            TelemetryProfilerQualification qualification = qualification(candidate, qualifyingWindows);
            signals.add(new ProfilerProjectSignal(
                    candidate.appearances.getLast().path(),
                    qualification,
                    severity(median),
                    qualifyingWindows,
                    median,
                    totalSampledMilliseconds
            ));
        }
        return signals.stream()
                .sorted(SIGNAL_ORDER)
                .limit(MAX_SIGNALS)
                .toList();
    }

    @Nonnull
    private static TelemetryProfilerQualification qualification(
            @Nonnull TelemetryProfilerPathEvidence path,
            Candidate candidate) {
        if (!qualifies(path) || candidate == null) {
            return TelemetryProfilerQualification.OBSERVED;
        }
        return qualification(candidate, Math.min(MAX_WINDOWS, candidate.appearances.size()));
    }

    @Nonnull
    private static TelemetryProfilerQualification qualification(
            @Nonnull Candidate candidate,
            int qualifyingWindows) {
        if (qualifyingWindows >= 3) {
            return TelemetryProfilerQualification.REPEATED;
        }
        if (candidate.appearances.stream().anyMatch(Appearance::isProvisional)) {
            return TelemetryProfilerQualification.PROVISIONAL;
        }
        return TelemetryProfilerQualification.OBSERVED;
    }

    private static boolean qualifies(@Nonnull TelemetryProfilerPathEvidence path) {
        return path.sampledMilliseconds() >= MIN_MILLISECONDS
                && path.selectedWorldThreadSharePercent() >= MIN_SHARE_PERCENT;
    }

    @Nonnull
    private static ProfilerSignalSeverity severity(double medianSharePercent) {
        if (medianSharePercent >= PROVISIONAL_SHARE_PERCENT) {
            return ProfilerSignalSeverity.SEVERE;
        }
        if (medianSharePercent >= 10.0d) {
            return ProfilerSignalSeverity.HIGH;
        }
        if (medianSharePercent >= 5.0d) {
            return ProfilerSignalSeverity.MODERATE;
        }
        return ProfilerSignalSeverity.NOTICE;
    }

    @Nonnull
    private static TelemetryProfilerPathEvidence withQualification(
            @Nonnull TelemetryProfilerPathEvidence path,
            @Nonnull TelemetryProfilerQualification qualification) {
        return new TelemetryProfilerPathEvidence(
                path.fingerprint(),
                path.attribution(),
                path.ownedClassName(),
                path.ownedMethodName(),
                path.ownedMethodDescriptor(),
                path.firstExternalClassName(),
                path.firstExternalMethodName(),
                path.firstExternalMethodDescriptor(),
                path.sampledMilliseconds(),
                path.selectedWorldThreadSharePercent(),
                qualification,
                path.representativePath()
        );
    }

    private static final class Candidate {
        private final List<Appearance> appearances = new ArrayList<>();

        private void add(int windowIndex, @Nonnull TelemetryProfilerPathEvidence path) {
            if (!appearances.isEmpty() && appearances.getLast().windowIndex() == windowIndex) {
                appearances.set(appearances.size() - 1, new Appearance(windowIndex, path));
                return;
            }
            appearances.add(new Appearance(windowIndex, path));
        }
    }

    private record Appearance(int windowIndex, @Nonnull TelemetryProfilerPathEvidence path) {
        private boolean isProvisional() {
            return path.sampledMilliseconds() >= PROVISIONAL_MILLISECONDS
                    && path.selectedWorldThreadSharePercent() >= PROVISIONAL_SHARE_PERCENT;
        }
    }

    public record Evaluation(
            @Nonnull List<TelemetryProfilerPathEvidence> currentPaths,
            @Nonnull List<ProfilerProjectSignal> signals) {
        public Evaluation {
            currentPaths = List.copyOf(Objects.requireNonNull(currentPaths, "currentPaths"));
            signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        }
    }
}
