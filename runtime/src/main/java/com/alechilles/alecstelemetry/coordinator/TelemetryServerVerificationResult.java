package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;

import javax.annotation.Nonnull;

/**
 * Result of a server profile claim verification request.
 */
public record TelemetryServerVerificationResult(@Nonnull Status status,
                                                int queuedHeartbeats,
                                                @Nonnull TelemetryCoreEngine.FlushSummary flushSummary) {

    public enum Status {
        QUEUED,
        MISSING_CLAIM_TOKEN,
        NO_STATS_PROJECTS,
        UNAVAILABLE
    }

    @Nonnull
    public static TelemetryServerVerificationResult unavailable(@Nonnull String reason) {
        return new TelemetryServerVerificationResult(
                Status.UNAVAILABLE,
                0,
                new TelemetryCoreEngine.FlushSummary(0, 0, 0, reason)
        );
    }
}
