package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import java.util.List;

/** Immutable project-scoped profiler snapshot. */
public record TelemetryProfilerSnapshot(@Nonnull String projectId,
                                        @Nonnull String projectVersion,
                                        @Nonnull String sparkVersion,
                                        int windowKey,
                                        long observedAtMillis,
                                        double selectedWorldThreadSelfMilliseconds,
                                        @Nonnull String qualificationRuleSet,
                                        @Nonnull List<TelemetryProfilerPathEvidence> paths,
                                        @Nonnull TelemetryProfilerContext context) {

    public TelemetryProfilerSnapshot {
        projectId = requiredText(projectId);
        projectVersion = requiredText(projectVersion);
        sparkVersion = requiredText(sparkVersion);
        if (observedAtMillis < 0L) {
            throw new IllegalArgumentException("observedAtMillis must be non-negative");
        }
        if (!Double.isFinite(selectedWorldThreadSelfMilliseconds)
                || selectedWorldThreadSelfMilliseconds < 0.0d) {
            throw new IllegalArgumentException(
                    "selectedWorldThreadSelfMilliseconds must be finite and non-negative");
        }
        qualificationRuleSet = requiredText(qualificationRuleSet);
        paths = paths == null ? List.of() : List.copyOf(paths);
        context = context == null ? TelemetryProfilerContext.unavailable() : context;
    }

    @Nonnull
    private static String requiredText(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value.trim();
    }
}
