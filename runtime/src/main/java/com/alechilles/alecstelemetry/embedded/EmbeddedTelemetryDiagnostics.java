package com.alechilles.alecstelemetry.embedded;

import javax.annotation.Nonnull;

/**
 * Snapshot status for one embedded telemetry runtime.
 */
public record EmbeddedTelemetryDiagnostics(boolean enabled,
                                           @Nonnull String endpoint,
                                           int pendingReports,
                                           boolean flushInProgress,
                                           @Nonnull String lastFlushResult) {
}
