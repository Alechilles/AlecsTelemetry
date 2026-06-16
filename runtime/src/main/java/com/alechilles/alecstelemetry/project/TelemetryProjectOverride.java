package com.alechilles.alecstelemetry.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime override loaded from Alec's Telemetry settings for one project.
 */
public record TelemetryProjectOverride(@Nullable Boolean enabled,
                                        @Nullable CaptureOverride capture,
                                        @Nullable String destinationMode,
                                        @Nullable EventsOverride events,
                                        @Nullable PerformanceOverride performance,
                                        @Nullable UsageOverride usage,
                                        @Nullable StatsOverride stats,
                                        @Nonnull TelemetryProjectDescriptor.HostedDestination hosted,
                                        @Nonnull TelemetryProjectDescriptor.CustomEndpoint customEndpoint) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Nonnull
    public static TelemetryProjectOverride fromJson(@Nonnull String rawJson) {
        Document parsed = GSON.fromJson(rawJson, Document.class);
        Document safe = parsed == null ? new Document() : parsed;
        return new TelemetryProjectOverride(
                safe.enabled,
                safe.capture == null
                        ? null
                        : new CaptureOverride(
                        safe.capture.uncaughtExceptions,
                        safe.capture.setupFailures,
                        safe.capture.startFailures,
                        safe.capture.exceptionalWorldRemovals
                ),
                normalizeMode(safe.destinationMode),
                safe.events == null
                        ? null
                        : new EventsOverride(
                        safe.events.errors == null ? null : new EventTypeOverride(safe.events.errors.enabled),
                        safe.events.lifecycle == null ? null : new EventTypeOverride(safe.events.lifecycle.enabled),
                        safe.events.breadcrumbs == null ? null : new BreadcrumbsOverride(safe.events.breadcrumbs.enabled)
                ),
                safe.performance == null
                        ? null
                        : new PerformanceOverride(
                        safe.performance.enabled,
                        safe.performance.sampleRate == null ? null : Math.max(0.0d, Math.min(1.0d, safe.performance.sampleRate)),
                        safe.performance.thresholdMs == null ? null : Math.max(1, Math.min(60000, safe.performance.thresholdMs))
                ),
                safe.usage == null
                        ? null
                        : new UsageOverride(
                        safe.usage.enabled,
                        normalizeNonBlankList(safe.usage.allowedEvents, 120)
                ),
                safe.stats == null
                        ? null
                        : new StatsOverride(
                        safe.stats.enabled,
                        normalizeNonBlankList(safe.stats.allowedEvents, 120)
                ),
                new TelemetryProjectDescriptor.HostedDestination(
                        normalizeNullable(safe.hosted == null ? null : safe.hosted.endpoint),
                        normalizeNullable(safe.hosted == null ? null : safe.hosted.eventEndpoint),
                        normalizeNullable(safe.hosted == null ? null : safe.hosted.projectKey),
                        normalizeHeaders(safe.hosted == null ? null : safe.hosted.headers)
                ),
                new TelemetryProjectDescriptor.CustomEndpoint(
                        normalizeNullable(safe.customEndpoint == null ? null : safe.customEndpoint.url),
                        normalizeNullable(safe.customEndpoint == null ? null : safe.customEndpoint.eventUrl),
                        normalizeHeaders(safe.customEndpoint == null ? null : safe.customEndpoint.headers)
                )
        );
    }

    public boolean hasAnyValue() {
        return enabled != null
                || capture != null && capture.hasAnyValue()
                || destinationMode != null
                || events != null && events.hasAnyValue()
                || performance != null
                || usage != null
                || stats != null
                || hosted.endpoint() != null
                || hosted.eventEndpoint() != null
                || hosted.projectKey() != null
                || !hosted.headers().isEmpty()
                || customEndpoint.url() != null
                || customEndpoint.eventUrl() != null
                || !customEndpoint.headers().isEmpty();
    }

    @Nullable
    private static String normalizeMode(@Nullable String rawMode) {
        String normalized = normalizeNullable(rawMode);
        if (normalized == null) {
            return null;
        }
        return "custom".equalsIgnoreCase(normalized) ? "custom" : "hosted";
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static Map<String, String> normalizeHeaders(@Nullable Map<String, String> rawHeaders) {
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawHeaders.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = normalizeNullable(entry.getKey());
            String value = normalizeNullable(entry.getValue());
            if (key != null && value != null) {
                normalized.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
            }
        }
        LinkedHashMap<String, String> withOriginalKeys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawHeaders.entrySet()) {
            String key = normalizeNullable(entry.getKey());
            String value = normalizeNullable(entry.getValue());
            if (key != null && value != null) {
                withOriginalKeys.put(key, value);
            }
        }
        return Map.copyOf(withOriginalKeys);
    }

    @Nonnull
    private static java.util.List<String> normalizeNonBlankList(@Nullable java.util.List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return java.util.List.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            String safe = normalizeNullable(value);
            if (safe != null) {
                String trimmed = safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
                normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return java.util.List.copyOf(new java.util.ArrayList<>(normalized.values()));
    }

    private static final class Document {
        private Boolean enabled;
        private CaptureDocument capture;
        private String destinationMode;
        private EventsDocument events;
        private PerformanceDocument performance;
        private UsageDocument usage;
        private StatsDocument stats;
        private HostedDocument hosted;
        private CustomEndpointDocument customEndpoint;
    }

    private static final class CaptureDocument {
        private Boolean uncaughtExceptions;
        private Boolean setupFailures;
        private Boolean startFailures;
        private Boolean exceptionalWorldRemovals;
    }

    private static final class EventsDocument {
        private EventTypeDocument errors;
        private EventTypeDocument lifecycle;
        private BreadcrumbsDocument breadcrumbs;
    }

    private static final class EventTypeDocument {
        private Boolean enabled;
    }

    private static final class BreadcrumbsDocument {
        private Boolean enabled;
    }

    private static final class PerformanceDocument {
        private Boolean enabled;
        private Double sampleRate;
        private Integer thresholdMs;
    }

    private static final class UsageDocument {
        private Boolean enabled;
        private java.util.List<String> allowedEvents;
    }

    private static final class StatsDocument {
        private Boolean enabled;
        private java.util.List<String> allowedEvents;
    }

    public record PerformanceOverride(@Nullable Boolean enabled,
                                      @Nullable Double sampleRate,
                                      @Nullable Integer thresholdMs) {
    }

    public record UsageOverride(@Nullable Boolean enabled,
                                @Nonnull java.util.List<String> allowedEvents) {
    }

    public record StatsOverride(@Nullable Boolean enabled,
                                @Nonnull java.util.List<String> allowedEvents) {
    }

    public record CaptureOverride(@Nullable Boolean uncaughtExceptions,
                                  @Nullable Boolean setupFailures,
                                  @Nullable Boolean startFailures,
                                  @Nullable Boolean exceptionalWorldRemovals) {
        public boolean hasAnyValue() {
            return uncaughtExceptions != null
                    || setupFailures != null
                    || startFailures != null
                    || exceptionalWorldRemovals != null;
        }
    }

    public record EventsOverride(@Nullable EventTypeOverride errors,
                                 @Nullable EventTypeOverride lifecycle,
                                 @Nullable BreadcrumbsOverride breadcrumbs) {
        public boolean hasAnyValue() {
            return errors != null && errors.enabled() != null
                    || lifecycle != null && lifecycle.enabled() != null
                    || breadcrumbs != null && breadcrumbs.enabled() != null;
        }
    }

    public record EventTypeOverride(@Nullable Boolean enabled) {
    }

    public record BreadcrumbsOverride(@Nullable Boolean enabled) {
    }

    private static final class HostedDocument {
        private String endpoint;
        private String eventEndpoint;
        private String projectKey;
        private Map<String, String> headers;
    }

    private static final class CustomEndpointDocument {
        private String url;
        private String eventUrl;
        private Map<String, String> headers;
    }
}
