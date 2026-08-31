package com.alechilles.alecstelemetry.diagnostic;

import com.alechilles.alecstelemetry.api.TelemetryDiagnosticAttachment;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundleResult;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticDisposition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts diagnostic contracts to JDK-only values for cross-classloader coordinator calls.
 */
public final class TelemetryDiagnosticBundleBridge {

    private TelemetryDiagnosticBundleBridge() {
    }

    @Nonnull
    public static Map<String, Object> bundleToMap(@Nonnull TelemetryDiagnosticBundle bundle) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("diagnosticId", bundle.diagnosticId());
        value.put("capturedAtUtc", bundle.capturedAtUtc());
        value.put("source", bundle.source());
        value.put("diagnosticKind", bundle.diagnosticKind());
        value.put("title", bundle.title());
        value.put("summary", bundle.summary());
        value.put("severity", bundle.severity());
        value.put("disposition", dispositionToMap(bundle.disposition()));
        value.put("attributes", bundle.attributes());
        value.put("attachments", bundle.attachments().stream()
                .map(TelemetryDiagnosticBundleBridge::attachmentToMap)
                .toList());
        return Map.copyOf(value);
    }

    @Nonnull
    public static TelemetryDiagnosticBundle bundleFromMap(@Nullable Map<String, Object> value) {
        Map<String, Object> safe = value == null ? Map.of() : value;
        ArrayList<TelemetryDiagnosticAttachment> attachments = new ArrayList<>();
        Object rawAttachments = safe.get("attachments");
        if (rawAttachments instanceof List<?> list) {
            for (Object rawAttachment : list) {
                if (rawAttachment instanceof Map<?, ?> map) {
                    attachments.add(attachmentFromMap(map));
                }
            }
        }
        return new TelemetryDiagnosticBundle(
                string(safe.get("diagnosticId")),
                string(safe.get("capturedAtUtc")),
                string(safe.get("source")),
                string(safe.get("diagnosticKind")),
                string(safe.get("title")),
                string(safe.get("summary")),
                string(safe.get("severity")),
                dispositionFromMap(map(safe.get("disposition"))),
                map(safe.get("attributes")),
                List.copyOf(attachments)
        );
    }

    @Nonnull
    public static Map<String, Object> resultToMap(@Nonnull TelemetryDiagnosticBundleResult result) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", result.status().name());
        value.put("accepted", result.accepted());
        if (result.detail() != null) value.put("detail", result.detail());
        return Map.copyOf(value);
    }

    @Nonnull
    public static TelemetryDiagnosticBundleResult resultFromMap(@Nullable Map<String, Object> value) {
        if (value == null || value.isEmpty()) return TelemetryDiagnosticBundleResult.unsupported();
        TelemetryDiagnosticBundleResult.Status status;
        try {
            status = TelemetryDiagnosticBundleResult.Status.valueOf(
                    string(value.get("status")).toUpperCase(java.util.Locale.ROOT)
            );
        } catch (RuntimeException failure) {
            status = Boolean.TRUE.equals(value.get("accepted"))
                    ? TelemetryDiagnosticBundleResult.Status.QUEUED
                    : TelemetryDiagnosticBundleResult.Status.FAILED;
        }
        return new TelemetryDiagnosticBundleResult(status, nullableString(value.get("detail")));
    }

    @Nonnull
    private static Map<String, Object> dispositionToMap(
            @Nonnull TelemetryDiagnosticDisposition disposition
    ) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("mode", disposition.mode());
        if (disposition.fingerprint() != null) value.put("fingerprint", disposition.fingerprint());
        return Map.copyOf(value);
    }

    @Nonnull
    private static Map<String, Object> attachmentToMap(
            @Nonnull TelemetryDiagnosticAttachment attachment
    ) {
        return Map.of(
                "attachmentId", attachment.attachmentId(),
                "kind", attachment.kind(),
                "fileName", attachment.fileName(),
                "contentType", attachment.contentType(),
                "contentEncoding", attachment.contentEncoding(),
                "byteCount", attachment.byteCount(),
                "sha256", attachment.sha256(),
                "content", attachment.content()
        );
    }

    @Nonnull
    private static TelemetryDiagnosticDisposition dispositionFromMap(
            @Nonnull Map<String, Object> value
    ) {
        return new TelemetryDiagnosticDisposition(
                string(value.get("mode")),
                nullableString(value.get("fingerprint"))
        );
    }

    @Nonnull
    private static TelemetryDiagnosticAttachment attachmentFromMap(@Nonnull Map<?, ?> value) {
        return new TelemetryDiagnosticAttachment(
                string(value.get("attachmentId")),
                string(value.get("kind")),
                string(value.get("fileName")),
                string(value.get("contentType")),
                string(value.get("contentEncoding")),
                integer(value.get("byteCount")),
                string(value.get("sha256")),
                string(value.get("content"))
        );
    }

    @Nonnull
    private static Map<String, Object> map(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }

    private static int integer(@Nullable Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Nonnull
    private static String string(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    @Nullable
    private static String nullableString(@Nullable Object value) {
        String normalized = string(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
