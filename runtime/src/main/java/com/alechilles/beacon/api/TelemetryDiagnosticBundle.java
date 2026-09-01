package com.alechilles.beacon.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-owned diagnostic metadata and opaque attachments before runtime attribution.
 */
public record TelemetryDiagnosticBundle(@Nonnull String diagnosticId,
                                        @Nonnull String capturedAtUtc,
                                        @Nonnull String source,
                                        @Nonnull String diagnosticKind,
                                        @Nonnull String title,
                                        @Nonnull String summary,
                                        @Nonnull String severity,
                                        @Nonnull TelemetryDiagnosticDisposition disposition,
                                        @Nonnull Map<String, Object> attributes,
                                        @Nonnull List<TelemetryDiagnosticAttachment> attachments) {

    public TelemetryDiagnosticBundle {
        diagnosticId = normalize(diagnosticId);
        capturedAtUtc = normalize(capturedAtUtc);
        source = normalize(source);
        diagnosticKind = normalize(diagnosticKind);
        title = normalize(title);
        summary = normalize(summary);
        severity = normalize(severity).toLowerCase(java.util.Locale.ROOT);
        disposition = disposition == null
                ? TelemetryDiagnosticDisposition.informational()
                : disposition;
        attributes = immutableMap(attributes);
        attachments = List.copyOf(attachments == null ? List.of() : attachments);
    }

    @Nonnull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nonnull
    private static Map<String, Object> immutableMap(@Nullable Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry != null && entry.getKey() != null && !entry.getKey().isBlank()
                    && entry.getValue() != null) {
                copy.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }
}
