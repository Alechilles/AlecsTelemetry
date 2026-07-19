package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded structured context for one consumer breadcrumb.
 *
 * <p>The context deliberately accepts only scalar attributes. Breadcrumbs are
 * ambient diagnostic context, so their payload must stay smaller and safer
 * than a full telemetry event.</p>
 */
public record TelemetryBreadcrumbContext(@Nonnull String category,
                                         @Nonnull String detail,
                                         @Nullable String correlationId,
                                         @Nullable String incidentId,
                                         @Nullable String phase,
                                         @Nullable String operation,
                                         @Nullable String scopeType,
                                         @Nullable String failureClass,
                                         @Nullable String disposition,
                                         @Nonnull Map<String, Object> attributes) {

    private static final int MAX_ATTRIBUTES = 12;

    @Nonnull
    public static Builder builder(@Nonnull String category, @Nonnull String detail) {
        return new Builder(category, detail);
    }

    @Nonnull
    public static TelemetryBreadcrumbContext of(@Nonnull String category, @Nonnull String detail) {
        return builder(category, detail).build();
    }

    @Nonnull
    public TelemetryBreadcrumbContext normalize() {
        return new TelemetryBreadcrumbContext(
                normalizeRequired(category, "general", 80),
                normalizeRequired(detail, "<empty>", 400),
                normalizeNullable(correlationId, 120),
                normalizeNullable(incidentId, 120),
                normalizeNullable(phase, 120),
                normalizeNullable(operation, 120),
                normalizeNullable(scopeType, 80),
                normalizeNullable(failureClass, 80),
                normalizeNullable(disposition, 80),
                normalizeAttributes(attributes)
        );
    }

    @Nonnull
    private static String normalizeRequired(@Nullable String value,
                                            @Nonnull String fallback,
                                            int maxLength) {
        String normalized = normalizeNullable(value, maxLength);
        return normalized == null ? fallback : normalized;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    @Nonnull
    private static Map<String, Object> normalizeAttributes(@Nullable Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (normalized.size() >= MAX_ATTRIBUTES) {
                break;
            }
            String key = normalizeNullable(entry.getKey(), 80);
            Object value = normalizeAttribute(entry.getValue());
            if (key != null && value != null) {
                normalized.put(key, value);
            }
        }
        return Map.copyOf(normalized);
    }

    @Nullable
    private static Object normalizeAttribute(@Nullable Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return normalizeNullable(text.toString(), 200);
        }
        return null;
    }

    /** Builds an immutable, normalized breadcrumb context. */
    public static final class Builder {
        private final String category;
        private final String detail;
        private final LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        private String correlationId;
        private String incidentId;
        private String phase;
        private String operation;
        private String scopeType;
        private String failureClass;
        private String disposition;

        private Builder(@Nonnull String category, @Nonnull String detail) {
            this.category = category;
            this.detail = detail;
        }

        @Nonnull
        public Builder correlationId(@Nullable String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Nonnull
        public Builder incidentId(@Nullable String incidentId) {
            this.incidentId = incidentId;
            return this;
        }

        @Nonnull
        public Builder phase(@Nullable String phase) {
            this.phase = phase;
            return this;
        }

        @Nonnull
        public Builder operation(@Nullable String operation) {
            this.operation = operation;
            return this;
        }

        @Nonnull
        public Builder scopeType(@Nullable String scopeType) {
            this.scopeType = scopeType;
            return this;
        }

        @Nonnull
        public Builder failureClass(@Nullable String failureClass) {
            this.failureClass = failureClass;
            return this;
        }

        @Nonnull
        public Builder disposition(@Nullable String disposition) {
            this.disposition = disposition;
            return this;
        }

        @Nonnull
        public Builder attribute(@Nonnull String key, @Nullable Object value) {
            attributes.put(key, value);
            return this;
        }

        @Nonnull
        public TelemetryBreadcrumbContext build() {
            return new TelemetryBreadcrumbContext(
                    category,
                    detail,
                    correlationId,
                    incidentId,
                    phase,
                    operation,
                    scopeType,
                    failureClass,
                    disposition,
                    new LinkedHashMap<>(attributes)
            ).normalize();
        }
    }
}
