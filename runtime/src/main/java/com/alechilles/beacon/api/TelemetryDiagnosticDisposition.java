package com.alechilles.beacon.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Requested portal treatment for a diagnostic bundle.
 */
public record TelemetryDiagnosticDisposition(@Nonnull String mode,
                                             @Nullable String fingerprint) {

    public static final String INFORMATIONAL = "informational";
    public static final String CREATE_OR_JOIN_ISSUE = "create_or_join_issue";

    public TelemetryDiagnosticDisposition {
        mode = normalize(mode, INFORMATIONAL).toLowerCase(Locale.ROOT);
        fingerprint = normalizeNullable(fingerprint);
    }

    @Nonnull
    public static TelemetryDiagnosticDisposition informational() {
        return new TelemetryDiagnosticDisposition(INFORMATIONAL, null);
    }

    @Nonnull
    public static TelemetryDiagnosticDisposition createOrJoinIssue(@Nonnull String fingerprint) {
        return new TelemetryDiagnosticDisposition(CREATE_OR_JOIN_ISSUE, fingerprint);
    }

    @Nonnull
    private static String normalize(@Nullable String value, @Nonnull String fallback) {
        String normalized = normalizeNullable(value);
        return normalized == null ? fallback : normalized;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
