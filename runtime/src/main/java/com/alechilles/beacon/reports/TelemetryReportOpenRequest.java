package com.alechilles.beacon.reports;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Request from a consumer mod to open the player report UI for one telemetry project.
 */
public record TelemetryReportOpenRequest(@Nonnull String reportKind,
                                         @Nullable String initialTitle,
                                         @Nullable String initialDescription) {

    public TelemetryReportOpenRequest {
        reportKind = reportKind == null || reportKind.isBlank() ? "issue" : reportKind.trim();
        initialTitle = normalizeNullable(initialTitle);
        initialDescription = normalizeNullable(initialDescription);
    }

    @Nonnull
    public String normalizedKind() {
        return "suggestion".equalsIgnoreCase(reportKind) ? "suggestion" : "issue";
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
