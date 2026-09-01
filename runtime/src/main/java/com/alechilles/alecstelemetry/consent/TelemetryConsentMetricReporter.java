package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Emits privacy-preserving aggregate consent choice metrics to the hosted platform.
 */
public final class TelemetryConsentMetricReporter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String CONSENT_METRIC_PATH = "/ingest/consent-metric";
    private static final String[] CATEGORIES = {
            "crash",
            "error",
            "diagnostics",
            "lifecycle",
            "performance",
            "usage",
            "stats",
            "breadcrumbs"
    };

    private final TelemetryRuntimeSettings settings;
    private final CrashReportClient client;
    private final String runtimeVersion;
    private final HytaleLogger logger;

    public TelemetryConsentMetricReporter(@Nonnull TelemetryRuntimeSettings settings,
                                          @Nonnull CrashReportClient client,
                                          @Nonnull String runtimeVersion,
                                          @Nullable HytaleLogger logger) {
        this.settings = settings;
        this.client = client;
        this.runtimeVersion = runtimeVersion == null || runtimeVersion.isBlank() ? "unknown" : runtimeVersion.trim();
        this.logger = logger;
    }

    public void recordFirstReview(@Nonnull TelemetryProjectRegistration project,
                                  @Nonnull TelemetryConsentSnapshot snapshot,
                                  @Nonnull TelemetryConsentSnapshot supported) {
        for (String category : CATEGORIES) {
            if (!supported.categoryEnabled(category)) {
                continue;
            }
            boolean enabled = effective(snapshot, category);
            upload(project, category, true, enabled, enabled, "first_run_notice", true);
        }
    }

    public void recordConsentChange(@Nonnull TelemetryProjectRegistration project,
                                    @Nonnull TelemetryConsentSnapshot previous,
                                    @Nonnull TelemetryConsentSnapshot current,
                                    @Nonnull TelemetryConsentSnapshot supported) {
        for (String category : CATEGORIES) {
            if (!supported.categoryEnabled(category)) {
                continue;
            }
            boolean previousEnabled = effective(previous, category);
            boolean enabled = effective(current, category);
            if (previousEnabled == enabled) {
                continue;
            }
            upload(project, category, true, enabled, previousEnabled, "consent_ui", false);
        }
    }

    public void recordPromptShown(@Nonnull TelemetryProjectRegistration project) {
        uploadFunnel(project, "prompt_shown", "chat_first_run");
    }

    public void recordUiOpened(@Nonnull TelemetryProjectRegistration project) {
        uploadFunnel(project, "ui_opened", "command_after_prompt");
    }

    public void recordFirstReviewCompleted(@Nonnull TelemetryProjectRegistration project) {
        uploadFunnel(project, "first_review_completed", "consent_ui");
    }

    private void upload(@Nonnull TelemetryProjectRegistration project,
                        @Nonnull String category,
                        boolean supported,
                        boolean enabled,
                        boolean previousEnabled,
                        @Nonnull String changeSource,
                        boolean firstReview) {
        CrashReportClient.DeliveryTarget target = resolveConsentMetricTarget(project);
        if (target == null) {
            return;
        }
        ConsentMetricEnvelope envelope = new ConsentMetricEnvelope(
                1,
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                runtimeVersion,
                project.projectId(),
                project.displayName(),
                project.pluginVersion(),
                category,
                supported,
                enabled,
                previousEnabled,
                changeSource,
                firstReview
        );
        CrashReportClient.UploadResult result = client.upload(target, GSON.toJson(envelope) + System.lineSeparator());
        if (!result.success() && logger != null) {
            logger.at(Level.FINE).log("Telemetry consent metric upload failed: " + result.detail());
        }
    }

    private void uploadFunnel(@Nonnull TelemetryProjectRegistration project,
                              @Nonnull String funnelEvent,
                              @Nonnull String source) {
        CrashReportClient.DeliveryTarget target = resolveConsentMetricTarget(project);
        if (target == null) {
            return;
        }
        ConsentFunnelEnvelope envelope = new ConsentFunnelEnvelope(
                1,
                "consent_funnel",
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                runtimeVersion,
                project.projectId(),
                project.displayName(),
                project.pluginVersion(),
                funnelEvent,
                source
        );
        CrashReportClient.UploadResult result = client.upload(target, GSON.toJson(envelope) + System.lineSeparator());
        if (!result.success() && logger != null) {
            logger.at(Level.FINE).log("Telemetry consent funnel metric upload failed: " + result.detail());
        }
    }

    @Nullable
    private CrashReportClient.DeliveryTarget resolveConsentMetricTarget(@Nonnull TelemetryProjectRegistration project) {
        if ("custom".equalsIgnoreCase(project.destinationMode())) {
            return null;
        }
        CrashReportClient.DeliveryTarget eventTarget = project.resolveEventDeliveryTarget(settings);
        if (eventTarget == null) {
            return null;
        }
        String endpoint = consentMetricEndpoint(eventTarget.endpoint());
        if (endpoint == null) {
            return null;
        }
        return new CrashReportClient.DeliveryTarget(endpoint, metricHeaders(eventTarget.headers()));
    }

    @Nullable
    private static String consentMetricEndpoint(@Nullable String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String normalized = endpoint.trim();
        if (normalized.endsWith(CONSENT_METRIC_PATH)) {
            return normalized;
        }
        if (normalized.endsWith("/ingest/event")) {
            return normalized.substring(0, normalized.length() - "/ingest/event".length()) + CONSENT_METRIC_PATH;
        }
        if (normalized.endsWith("/ingest/crash")) {
            return normalized.substring(0, normalized.length() - "/ingest/crash".length()) + CONSENT_METRIC_PATH;
        }
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1) + CONSENT_METRIC_PATH;
        }
        return normalized + CONSENT_METRIC_PATH;
    }

    @Nonnull
    private static Map<String, String> metricHeaders(@Nonnull Map<String, String> source) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (TelemetryProjectDescriptor.PROJECT_KEY_HEADER.equalsIgnoreCase(entry.getKey().trim())) {
                continue;
            }
            headers.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(headers);
    }

    private static boolean effective(@Nonnull TelemetryConsentSnapshot snapshot, @Nonnull String category) {
        return snapshot.projectEnabled() && snapshot.categoryEnabled(category.toLowerCase(Locale.ROOT));
    }

    private record ConsentMetricEnvelope(int schemaVersion,
                                         @Nonnull String eventId,
                                         @Nonnull String capturedAtUtc,
                                         @Nonnull String runtimeVersion,
                                         @Nonnull String projectId,
                                         @Nonnull String projectDisplayName,
                                         @Nonnull String projectVersion,
                                         @Nonnull String category,
                                         boolean supported,
                                         boolean enabled,
                                         boolean previousEnabled,
                                         @Nonnull String changeSource,
                                         boolean firstReview) {
    }

    private record ConsentFunnelEnvelope(int schemaVersion,
                                         @Nonnull String metricKind,
                                         @Nonnull String eventId,
                                         @Nonnull String capturedAtUtc,
                                         @Nonnull String runtimeVersion,
                                         @Nonnull String projectId,
                                         @Nonnull String projectDisplayName,
                                         @Nonnull String projectVersion,
                                         @Nonnull String funnelEvent,
                                         @Nonnull String source) {
    }
}
