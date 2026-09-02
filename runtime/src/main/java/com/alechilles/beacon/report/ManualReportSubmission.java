package com.alechilles.beacon.report;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw player-entered report submission before descriptor validation.
 */
public record ManualReportSubmission(@Nonnull ManualReportKind kind,
                                     @Nonnull String title,
                                     @Nonnull String description,
                                     @Nullable String contact,
                                     @Nonnull Map<String, Object> formValues,
                                     boolean includeCurrentServerLog,
                                     boolean includePreviousServerLog,
                                     boolean includeLoadedModList,
                                     boolean includeDiagnostics,
                                     boolean allowResolutionUpdates) {

    public ManualReportSubmission {
        kind = ManualReportKind.normalize(kind);
        title = normalizeText(title);
        description = normalizeText(description);
        contact = normalizeNullable(contact);
        formValues = normalizeMap(formValues);
    }

    @Nonnull
    private static String normalizeText(@Nullable String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "" : normalized;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static Map<String, Object> normalizeMap(@Nullable Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().trim(), entry.getValue());
        }
        return Map.copyOf(normalized);
    }
}
