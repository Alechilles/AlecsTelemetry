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
                                       @Nullable String destinationMode,
                                       @Nonnull TelemetryProjectDescriptor.HostedDestination hosted,
                                       @Nonnull TelemetryProjectDescriptor.CustomEndpoint customEndpoint) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Nonnull
    public static TelemetryProjectOverride fromJson(@Nonnull String rawJson) {
        Document parsed = GSON.fromJson(rawJson, Document.class);
        Document safe = parsed == null ? new Document() : parsed;
        return new TelemetryProjectOverride(
                safe.enabled,
                normalizeMode(safe.destinationMode),
                new TelemetryProjectDescriptor.HostedDestination(
                        normalizeNullable(safe.hosted == null ? null : safe.hosted.endpoint),
                        normalizeNullable(safe.hosted == null ? null : safe.hosted.projectKey),
                        normalizeHeaders(safe.hosted == null ? null : safe.hosted.headers)
                ),
                new TelemetryProjectDescriptor.CustomEndpoint(
                        normalizeNullable(safe.customEndpoint == null ? null : safe.customEndpoint.url),
                        normalizeHeaders(safe.customEndpoint == null ? null : safe.customEndpoint.headers)
                )
        );
    }

    public boolean hasAnyValue() {
        return enabled != null
                || destinationMode != null
                || hosted.endpoint() != null
                || hosted.projectKey() != null
                || !hosted.headers().isEmpty()
                || customEndpoint.url() != null
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

    private static final class Document {
        private Boolean enabled;
        private String destinationMode;
        private HostedDocument hosted;
        private CustomEndpointDocument customEndpoint;
    }

    private static final class HostedDocument {
        private String endpoint;
        private String projectKey;
        private Map<String, String> headers;
    }

    private static final class CustomEndpointDocument {
        private String url;
        private Map<String, String> headers;
    }
}
