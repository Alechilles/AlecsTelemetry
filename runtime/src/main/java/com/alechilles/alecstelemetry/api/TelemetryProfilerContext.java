package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable context captured with a profiler snapshot. */
public record TelemetryProfilerContext(long observedAtMillis,
                                       @Nullable String hytaleVersion,
                                       @Nullable Integer playerCount,
                                       @Nullable Double tps,
                                       @Nullable Double mspt,
                                       @Nonnull Map<String, Integer> breadcrumbCategoryCounts) {

    public TelemetryProfilerContext(long observedAtMillis,
                                    @Nullable Integer playerCount,
                                    @Nullable Double tps,
                                    @Nullable Double mspt,
                                    @Nonnull Map<String, Integer> breadcrumbCategoryCounts) {
        this(observedAtMillis, null, playerCount, tps, mspt, breadcrumbCategoryCounts);
    }

    public TelemetryProfilerContext {
        requireNonNegative(observedAtMillis, "observedAtMillis");
        hytaleVersion = optionalText(hytaleVersion);
        requireFiniteNonNegative(tps, "tps");
        requireFiniteNonNegative(mspt, "mspt");
        breadcrumbCategoryCounts = immutableCounts(breadcrumbCategoryCounts);
    }

    @Nonnull
    public static TelemetryProfilerContext unavailable() {
        return new TelemetryProfilerContext(0L, null, null, null, null, Map.of());
    }

    @Nonnull
    private static Map<String, Integer> immutableCounts(@Nullable Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                key = "<unknown>";
            } else {
                key = key.trim();
            }
            normalized.put(key, entry.getValue() == null ? 0 : entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    @Nullable
    private static String optionalText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requireNonNegative(long value, @Nonnull String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private static void requireFiniteNonNegative(@Nullable Double value, @Nonnull String fieldName) {
        if (value != null && (!Double.isFinite(value) || value < 0.0d)) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }
}
