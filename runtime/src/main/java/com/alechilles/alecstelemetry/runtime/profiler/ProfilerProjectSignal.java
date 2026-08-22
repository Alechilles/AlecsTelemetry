package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/** Immutable command-facing rolling signal for one project path. */
public record ProfilerProjectSignal(
        @Nonnull TelemetryProfilerPathEvidence representative,
        @Nonnull TelemetryProfilerQualification qualification,
        @Nonnull ProfilerSignalSeverity severity,
        int qualifyingWindows,
        double medianSharePercent,
        double totalSampledMilliseconds,
        @Nonnull Map<String, Integer> recentCorrelationCounts) {

    public ProfilerProjectSignal(@Nonnull TelemetryProfilerPathEvidence representative,
                                 @Nonnull TelemetryProfilerQualification qualification,
                                 @Nonnull ProfilerSignalSeverity severity,
                                 int qualifyingWindows,
                                 double medianSharePercent,
                                 double totalSampledMilliseconds) {
        this(
                representative,
                qualification,
                severity,
                qualifyingWindows,
                medianSharePercent,
                totalSampledMilliseconds,
                Map.of()
        );
    }

    public ProfilerProjectSignal {
        representative = Objects.requireNonNull(representative, "representative");
        qualification = Objects.requireNonNull(qualification, "qualification");
        severity = Objects.requireNonNull(severity, "severity");
        recentCorrelationCounts = Map.copyOf(Objects.requireNonNull(
                recentCorrelationCounts, "recentCorrelationCounts"));
        if (qualifyingWindows < 1 || qualifyingWindows > 5) {
            throw new IllegalArgumentException("qualifyingWindows must be between 1 and 5");
        }
        requireFiniteNonNegative(medianSharePercent, "medianSharePercent");
        requireFiniteNonNegative(totalSampledMilliseconds, "totalSampledMilliseconds");
    }

    private static void requireFiniteNonNegative(double value, @Nonnull String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }
}
