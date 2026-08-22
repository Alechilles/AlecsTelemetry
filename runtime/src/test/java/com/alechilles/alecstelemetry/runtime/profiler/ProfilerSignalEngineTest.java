package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerSignalEngineTest {

    private final ProfilerSignalEngine engine = new ProfilerSignalEngine();

    // Regression: a path must not become repeated after only two qualifying windows.
    @Test
    void keepsCurrentPathObservedBeforeThreeQualifyingWindows() {
        TelemetryProfilerPathEvidence current = path("path-a", 40.0d, 4.0d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(
                windows(List.of(current), List.of()),
                List.of(current)
        );

        assertEquals(TelemetryProfilerQualification.OBSERVED,
                evaluation.currentPaths().getFirst().qualification());
    }

    // Regression: the rolling evaluator must mark the third qualifying appearance as repeated.
    @Test
    void marksCurrentPathRepeatedOnThirdQualifyingWindow() {
        TelemetryProfilerPathEvidence current = path("path-a", 40.0d, 4.0d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(
                windows(List.of(current), List.of(), List.of(current), List.of()),
                List.of(current)
        );

        assertEquals(TelemetryProfilerQualification.REPEATED,
                evaluation.currentPaths().getFirst().qualification());
    }

    // Regression: stable absolute time must identify a repeated path even on a busy server.
    @Test
    void marksLowSharePathRepeatedAfterThreeAbsoluteTimeAppearances() {
        TelemetryProfilerPathEvidence current = path("path-a", 40.0d, 0.4d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(
                windows(List.of(current), List.of(), List.of(current), List.of()),
                List.of(current)
        );

        assertEquals(TelemetryProfilerQualification.REPEATED,
                evaluation.currentPaths().getFirst().qualification());
        assertEquals(1, evaluation.signals().size());
        assertEquals(3, evaluation.signals().getFirst().qualifyingWindows());
    }

    // Regression: a single large current observation must publish an immediate provisional qualification.
    @Test
    void marksLargeCurrentPathProvisionalImmediately() {
        TelemetryProfilerPathEvidence current = path("path-a", 100.0d, 20.0d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(List.of(), List.of(current));

        assertEquals(TelemetryProfilerQualification.PROVISIONAL,
                evaluation.currentPaths().getFirst().qualification());
        assertEquals(ProfilerSignalSeverity.SEVERE,
                evaluation.signals().getFirst().severity());
    }

    // Regression: a current path below either qualification threshold must remain observed.
    @Test
    void keepsNonQualifyingCurrentPathObserved() {
        TelemetryProfilerPathEvidence current = path("path-a", 19.99d, 20.0d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(List.of(), List.of(current));

        assertEquals(TelemetryProfilerQualification.OBSERVED,
                evaluation.currentPaths().getFirst().qualification());
        assertTrue(evaluation.signals().isEmpty());
    }

    // Regression: an old candidate must expire only after five newer actionable window markers.
    @Test
    void expiresCandidateAfterFiveNewActionableWindows() {
        TelemetryProfilerPathEvidence old = path("path-a", 40.0d, 4.0d);

        List<TelemetryProfilerSnapshot> history = windows(
                List.of(old),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertTrue(engine.signals(history).isEmpty());
    }

    // Regression: prior windows beyond the five-window ring must not create a false repeated signal.
    @Test
    void evaluatesOnlyTheLatestFourPriorWindows() {
        TelemetryProfilerPathEvidence current = path("path-a", 40.0d, 4.0d);

        ProfilerSignalEngine.Evaluation evaluation = engine.evaluate(
                windows(List.of(current), List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(current)
        );

        assertEquals(TelemetryProfilerQualification.OBSERVED,
                evaluation.currentPaths().getFirst().qualification());
    }

    // Regression: signal intensity must include repeatable absolute-time appearances and use the newest evidence.
    @Test
    void usesNewestRepeatableRepresentativeAndAbsoluteTimeIntensity() {
        TelemetryProfilerPathEvidence first = path("path-a", "old.Owner", 30.0d, 3.0d);
        TelemetryProfilerPathEvidence newestQualifying = path(
                "path-a", "new.Owner", 100.0d, 20.0d);
        TelemetryProfilerPathEvidence newestAbsoluteTime = path(
                "path-a", "absolute.Owner", 1000.0d, 1.0d);

        ProfilerProjectSignal signal = engine.signals(windows(
                List.of(first),
                List.of(),
                List.of(newestQualifying),
                List.of(newestAbsoluteTime)
        )).getFirst();

        assertEquals("absolute.Owner", signal.representative().ownedClassName());
        assertEquals(3, signal.qualifyingWindows());
        assertEquals(3.0d, signal.medianSharePercent(), 0.0001d);
        assertEquals(1130.0d, signal.totalSampledMilliseconds(), 0.0001d);
        assertEquals(TelemetryProfilerQualification.REPEATED, signal.qualification());
    }

    // Regression: median calculation must average the two middle values for even qualifying counts.
    @Test
    void calculatesEvenQualifyingMediansFromTheTwoMiddleValues() {
        TelemetryProfilerPathEvidence twoWindow = path("two", 20.0d, 2.0d);
        TelemetryProfilerPathEvidence fourWindow = path("four", 20.0d, 2.0d);

        List<TelemetryProfilerSnapshot> history = windows(
                List.of(twoWindow, fourWindow),
                List.of(path("two", 80.0d, 8.0d), path("four", 40.0d, 4.0d)),
                List.of(fourWindowWith(60.0d, 6.0d)),
                List.of(fourWindowWith(80.0d, 8.0d))
        );

        List<ProfilerProjectSignal> signals = engine.signals(history);
        ProfilerProjectSignal twoSignal = signals.stream()
                .filter(signal -> signal.representative().fingerprint().equals("two"))
                .findFirst()
                .orElseThrow();
        ProfilerProjectSignal fourSignal = signals.stream()
                .filter(signal -> signal.representative().fingerprint().equals("four"))
                .findFirst()
                .orElseThrow();

        assertEquals(5.0d, twoSignal.medianSharePercent(), 0.0001d);
        assertEquals(5.0d, fourSignal.medianSharePercent(), 0.0001d);
        assertEquals(2, twoSignal.qualifyingWindows());
        assertEquals(4, fourSignal.qualifyingWindows());
    }

    // Regression: ranking must be bounded and deterministic across median, total time, then fingerprint.
    @Test
    void ranksAtMostFiveSignalsByMedianThenTotalThenFingerprint() {
        List<TelemetryProfilerSnapshot> history = windows(
                List.of(
                        path("a", 100.0d, 20.0d),
                        path("b", 20.0d, 2.0d),
                        path("c", 20.0d, 2.0d),
                        path("d", 50.0d, 10.0d),
                        path("e", 50.0d, 5.0d),
                        path("f", 20.0d, 2.0d)
                ),
                List.of(
                        path("b", 40.0d, 4.0d),
                        path("c", 80.0d, 8.0d),
                        path("e", 50.0d, 5.0d)
                ),
                List.of(path("b", 60.0d, 6.0d)),
                List.of(path("b", 80.0d, 8.0d)),
                List.of(path("d", 50.0d, 10.0d))
        );

        List<ProfilerProjectSignal> signals = engine.signals(history);

        assertEquals(5, signals.size());
        assertEquals(List.of("a", "d", "b", "c", "e"), signals.stream()
                .map(signal -> signal.representative().fingerprint())
                .toList());
        assertEquals(5.0d, signals.get(2).medianSharePercent(), 0.0001d);
        assertEquals(200.0d, signals.get(2).totalSampledMilliseconds(), 0.0001d);
        assertEquals(100.0d, signals.get(3).totalSampledMilliseconds(), 0.0001d);
        assertEquals(100.0d, signals.get(4).totalSampledMilliseconds(), 0.0001d);
        assertEquals(ProfilerSignalSeverity.SEVERE, signals.getFirst().severity());
        assertFalse(signals.stream().anyMatch(signal -> signal.representative().fingerprint().equals("f")));
    }

    // Regression: exact share thresholds must remain in the intended severity bands.
    @Test
    void assignsSeverityAtExactShareBoundaries() {
        List<ProfilerProjectSignal> signals = engine.signals(windows(
                List.of(
                        path("notice", 20.0d, 2.0d),
                        path("moderate", 50.0d, 5.0d),
                        path("high", 100.0d, 10.0d),
                        path("severe", 100.0d, 20.0d)
                )
        ));

        assertEquals(ProfilerSignalSeverity.NOTICE, signalFor(signals, "notice").severity());
        assertEquals(ProfilerSignalSeverity.MODERATE, signalFor(signals, "moderate").severity());
        assertEquals(ProfilerSignalSeverity.HIGH, signalFor(signals, "high").severity());
        assertEquals(ProfilerSignalSeverity.SEVERE, signalFor(signals, "severe").severity());
    }

    // Regression: rolling signals must expose recent approved correlation counts without double-counting windows.
    @Test
    void usesLatestAppearanceCorrelationCountsForSignal() {
        TelemetryProfilerPathEvidence path = path("path-a", 40.0d, 4.0d);
        TelemetryProfilerSnapshot first = snapshot(
                1,
                List.of(path),
                new TelemetryProfilerContext(1L, null, null, null, Map.of("companion.needs.slow-task", 1))
        );
        TelemetryProfilerSnapshot latest = snapshot(
                2,
                List.of(path),
                new TelemetryProfilerContext(2L, null, null, null, Map.of("companion.needs.slow-task", 2))
        );

        ProfilerProjectSignal signal = engine.signals(List.of(first, latest)).getFirst();

        assertEquals(Map.of("companion.needs.slow-task", 2), signal.recentCorrelationCounts());
    }

    // Regression: qualification must replace only the qualification component on current path copies.
    @Test
    void preservesCurrentPathEvidenceWhenQualificationChanges() {
        TelemetryProfilerPathEvidence current = path("path-a", "Owner", 100.0d, 20.0d);

        TelemetryProfilerPathEvidence qualified = engine.evaluate(List.of(), List.of(current))
                .currentPaths()
                .getFirst();

        assertNotSame(current, qualified);
        assertEquals(current.fingerprint(), qualified.fingerprint());
        assertEquals(current.attribution(), qualified.attribution());
        assertEquals(current.ownedClassName(), qualified.ownedClassName());
        assertEquals(current.ownedMethodName(), qualified.ownedMethodName());
        assertEquals(current.ownedMethodDescriptor(), qualified.ownedMethodDescriptor());
        assertEquals(current.firstExternalClassName(), qualified.firstExternalClassName());
        assertEquals(current.firstExternalMethodName(), qualified.firstExternalMethodName());
        assertEquals(current.firstExternalMethodDescriptor(), qualified.firstExternalMethodDescriptor());
        assertEquals(current.sampledMilliseconds(), qualified.sampledMilliseconds(), 0.0001d);
        assertEquals(current.selectedWorldThreadSharePercent(),
                qualified.selectedWorldThreadSharePercent(), 0.0001d);
        assertEquals(current.representativePath(), qualified.representativePath());
        assertEquals(TelemetryProfilerQualification.PROVISIONAL, qualified.qualification());
    }

    private static ProfilerProjectSignal signalFor(List<ProfilerProjectSignal> signals, String fingerprint) {
        return signals.stream()
                .filter(signal -> signal.representative().fingerprint().equals(fingerprint))
                .findFirst()
                .orElseThrow();
    }

    @SafeVarargs
    private static List<TelemetryProfilerSnapshot> windows(
            List<TelemetryProfilerPathEvidence>... pathWindows) {
        List<TelemetryProfilerSnapshot> snapshots = new ArrayList<>(pathWindows.length);
        for (int index = 0; index < pathWindows.length; index++) {
            snapshots.add(snapshot(index + 1, pathWindows[index]));
        }
        return List.copyOf(snapshots);
    }

    private static TelemetryProfilerSnapshot snapshot(
            int windowKey,
            List<TelemetryProfilerPathEvidence> paths) {
        return snapshot(windowKey, paths, TelemetryProfilerContext.unavailable());
    }

    private static TelemetryProfilerSnapshot snapshot(
            int windowKey,
            List<TelemetryProfilerPathEvidence> paths,
            TelemetryProfilerContext context) {
        return new TelemetryProfilerSnapshot(
                "mod-a",
                "1.0.0",
                "1.10.172-beta7",
                windowKey,
                windowKey,
                1000.0d,
                ProfilerSignalEngine.RULE_SET,
                paths,
                context
        );
    }

    private static TelemetryProfilerPathEvidence path(
            String fingerprint,
            double sampledMilliseconds,
            double sharePercent) {
        return path(fingerprint, "Owner-" + fingerprint, sampledMilliseconds, sharePercent);
    }

    private static TelemetryProfilerPathEvidence path(
            String fingerprint,
            String owner,
            double sampledMilliseconds,
            double sharePercent) {
        return new TelemetryProfilerPathEvidence(
                fingerprint,
                TelemetryProfilerAttribution.SELF,
                owner,
                "tick",
                "()V",
                null,
                null,
                null,
                sampledMilliseconds,
                sharePercent,
                TelemetryProfilerQualification.OBSERVED,
                List.of(owner + "#tick()V")
        );
    }

    private static TelemetryProfilerPathEvidence fourWindowWith(
            double sampledMilliseconds,
            double sharePercent) {
        return path("four", sampledMilliseconds, sharePercent);
    }
}
