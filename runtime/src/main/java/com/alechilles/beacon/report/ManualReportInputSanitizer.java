package com.alechilles.beacon.report;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Normalizes player-entered manual report values before they reach durable storage.
 */
public final class ManualReportInputSanitizer {

    private static final Pattern TELEMETRY_SELECTOR = Pattern.compile("^#TelemetryReport[A-Za-z0-9_.-]+$");

    private ManualReportInputSanitizer() {
    }

    @Nullable
    public static String sanitizeText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (isTelemetrySelector(trimmed)) {
            return null;
        }
        return trimmed;
    }

    @Nonnull
    public static String sanitizeText(@Nullable String value, @Nonnull String fallback) {
        String sanitized = sanitizeText(value);
        return sanitized == null ? fallback : sanitized;
    }

    public static boolean isTelemetrySelector(@Nonnull String value) {
        return TELEMETRY_SELECTOR.matcher(value).matches() || value.contains(" #TelemetryReport");
    }
}
