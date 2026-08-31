package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Non-throwing result from a diagnostic-bundle submission attempt.
 */
public record TelemetryDiagnosticBundleResult(@Nonnull Status status,
                                              @Nullable String detail) {

    public TelemetryDiagnosticBundleResult {
        status = status == null ? Status.FAILED : status;
        detail = detail == null || detail.isBlank() ? null : detail.trim();
    }

    public boolean accepted() {
        return status == Status.QUEUED;
    }

    @Nonnull
    public static TelemetryDiagnosticBundleResult unsupported() {
        return new TelemetryDiagnosticBundleResult(Status.UNSUPPORTED, "diagnostic_bundles_unsupported");
    }

    public enum Status {
        QUEUED,
        DISABLED,
        REJECTED,
        FAILED,
        UNSUPPORTED
    }
}
