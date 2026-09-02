package com.alechilles.beacon.runtime;

import com.alechilles.beacon.api.TelemetryBreadcrumbContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts structured breadcrumbs to classloader-safe coordinator payloads. */
public final class TelemetryBreadcrumbBridgePayload {

    private TelemetryBreadcrumbBridgePayload() {
    }

    @Nonnull
    public static Map<String, Object> summary(@Nonnull TelemetryBreadcrumbContext context) {
        TelemetryBreadcrumbContext normalized = context.normalize();
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        put(summary, "correlationId", normalized.correlationId());
        put(summary, "incidentId", normalized.incidentId());
        put(summary, "phase", normalized.phase());
        put(summary, "operation", normalized.operation());
        put(summary, "scopeType", normalized.scopeType());
        put(summary, "failureClass", normalized.failureClass());
        put(summary, "disposition", normalized.disposition());
        if (!normalized.attributes().isEmpty()) {
            summary.put("attributes", normalized.attributes());
        }
        return Map.copyOf(summary);
    }

    @Nonnull
    public static TelemetryBreadcrumbContext fromSummary(@Nonnull String category,
                                                         @Nonnull String detail,
                                                         @Nullable Map<String, Object> summary) {
        Map<String, Object> safe = summary == null ? Map.of() : summary;
        TelemetryBreadcrumbContext.Builder builder = TelemetryBreadcrumbContext.builder(category, detail)
                .correlationId(stringValue(safe.get("correlationId")))
                .incidentId(stringValue(safe.get("incidentId")))
                .phase(stringValue(safe.get("phase")))
                .operation(stringValue(safe.get("operation")))
                .scopeType(stringValue(safe.get("scopeType")))
                .failureClass(stringValue(safe.get("failureClass")))
                .disposition(stringValue(safe.get("disposition")));
        Object attributes = safe.get("attributes");
        if (attributes instanceof Map<?, ?> rawAttributes) {
            for (Map.Entry<?, ?> entry : rawAttributes.entrySet()) {
                if (entry.getKey() != null) {
                    builder.attribute(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        return builder.build();
    }

    private static void put(@Nonnull Map<String, Object> target,
                            @Nonnull String key,
                            @Nullable String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        return value instanceof CharSequence text ? text.toString() : null;
    }
}
