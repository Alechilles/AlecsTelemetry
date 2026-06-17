package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized runtime registration for one discovered telemetry-enabled project.
 */
public record TelemetryProjectRegistration(@Nonnull TelemetryProjectDescriptor descriptor,
                                           @Nonnull String pluginIdentifier,
                                           @Nonnull String pluginVersion,
                                           @Nullable Path sourcePath,
                                           @Nullable TelemetryProjectOverride override) {

    public TelemetryProjectRegistration(@Nonnull TelemetryProjectDescriptor descriptor,
                                        @Nonnull String pluginIdentifier,
                                        @Nonnull String pluginVersion,
                                        @Nullable Path sourcePath) {
        this(descriptor, pluginIdentifier, pluginVersion, sourcePath, null);
    }

    @Nonnull
    public String projectId() {
        return descriptor.projectId();
    }

    @Nonnull
    public String displayName() {
        return descriptor.displayName();
    }

    @Nonnull
    public List<String> ownerPluginIdentifiers() {
        return descriptor.ownerPluginIdentifiers();
    }

    @Nonnull
    public List<String> packagePrefixes() {
        return descriptor.packagePrefixes();
    }

    public boolean isEnabled() {
        return override != null && override.enabled() != null ? override.enabled() : descriptor.defaults().enabled();
    }

    @Nonnull
    public String runtimeMode() {
        return descriptor.runtimeMode();
    }

    public boolean isEmbeddedMode() {
        return descriptor.isEmbeddedMode();
    }

    public boolean isDependencyMode() {
        return descriptor.isDependencyMode();
    }

    public boolean hasOverride() {
        return override != null && override.hasAnyValue();
    }

    @Nonnull
    public String destinationMode() {
        if (override != null && override.destinationMode() != null && !override.destinationMode().isBlank()) {
            return override.destinationMode();
        }
        return descriptor.defaults().destinationMode();
    }

    public boolean capturesSource(@Nonnull String source) {
        return capture().capturesSource(source);
    }

    public boolean isCrashTelemetryEnabled() {
        TelemetryProjectDescriptor.CaptureOptions capture = capture();
        return isEnabled()
                && (capture.uncaughtExceptions()
                || capture.setupFailures()
                || capture.startFailures()
                || capture.exceptionalWorldRemovals());
    }

    @Nonnull
    public TelemetryConsentSnapshot consentSnapshot() {
        return new TelemetryConsentSnapshot(
                isEnabled(),
                isCrashTelemetryEnabled(),
                events().errors().enabled(),
                events().lifecycle().enabled(),
                performance().enabled(),
                usage().enabled(),
                stats().enabled(),
                events().breadcrumbs().enabled()
        );
    }

    @Nonnull
    public TelemetryProjectDescriptor.CaptureOptions capture() {
        if (override == null || override.capture() == null) {
            return descriptor.capture();
        }
        TelemetryProjectDescriptor.CaptureOptions defaults = descriptor.capture();
        TelemetryProjectOverride.CaptureOverride captureOverride = override.capture();
        return new TelemetryProjectDescriptor.CaptureOptions(
                captureOverride.uncaughtExceptions() == null ? defaults.uncaughtExceptions() : captureOverride.uncaughtExceptions(),
                captureOverride.setupFailures() == null ? defaults.setupFailures() : captureOverride.setupFailures(),
                captureOverride.startFailures() == null ? defaults.startFailures() : captureOverride.startFailures(),
                captureOverride.exceptionalWorldRemovals() == null ? defaults.exceptionalWorldRemovals() : captureOverride.exceptionalWorldRemovals()
        );
    }

    @Nonnull
    public TelemetryProjectDescriptor.EventOptions events() {
        if (override == null || override.events() == null) {
            return descriptor.events();
        }
        TelemetryProjectDescriptor.EventOptions defaults = descriptor.events();
        TelemetryProjectOverride.EventsOverride eventsOverride = override.events();
        TelemetryProjectOverride.EventTypeOverride errorsOverride = eventsOverride.errors();
        TelemetryProjectOverride.EventTypeOverride lifecycleOverride = eventsOverride.lifecycle();
        TelemetryProjectOverride.BreadcrumbsOverride breadcrumbsOverride = eventsOverride.breadcrumbs();
        return new TelemetryProjectDescriptor.EventOptions(
                new TelemetryProjectDescriptor.EventTypeOptions(
                        errorsOverride == null || errorsOverride.enabled() == null
                                ? defaults.errors().enabled()
                                : errorsOverride.enabled(),
                        defaults.errors().details()
                ),
                new TelemetryProjectDescriptor.EventTypeOptions(
                        lifecycleOverride == null || lifecycleOverride.enabled() == null
                                ? defaults.lifecycle().enabled()
                                : lifecycleOverride.enabled(),
                        defaults.lifecycle().details()
                ),
                new TelemetryProjectDescriptor.BreadcrumbOptions(
                        breadcrumbsOverride == null || breadcrumbsOverride.enabled() == null
                                ? defaults.breadcrumbs().enabled()
                                : breadcrumbsOverride.enabled(),
                        defaults.breadcrumbs().automatic()
                )
        );
    }

    @Nonnull
    public TelemetryProjectDescriptor.PerformanceOptions performance() {
        if (override == null || override.performance() == null) {
            return descriptor.performance();
        }
        TelemetryProjectDescriptor.PerformanceOptions defaults = descriptor.performance();
        TelemetryProjectOverride.PerformanceOverride performanceOverride = override.performance();
        return new TelemetryProjectDescriptor.PerformanceOptions(
                performanceOverride.enabled() == null ? defaults.enabled() : performanceOverride.enabled(),
                performanceOverride.sampleRate() == null ? defaults.sampleRate() : performanceOverride.sampleRate(),
                performanceOverride.thresholdMs() == null ? defaults.thresholdMs() : performanceOverride.thresholdMs(),
                defaults.details()
        );
    }

    @Nonnull
    public TelemetryProjectDescriptor.UsageOptions usage() {
        if (override == null || override.usage() == null) {
            return descriptor.usage();
        }
        TelemetryProjectDescriptor.UsageOptions defaults = descriptor.usage();
        TelemetryProjectOverride.UsageOverride usageOverride = override.usage();
        return new TelemetryProjectDescriptor.UsageOptions(
                usageOverride.enabled() == null ? defaults.enabled() : usageOverride.enabled(),
                usageOverride.allowedEvents().isEmpty() ? defaults.allowedEvents() : usageOverride.allowedEvents(),
                defaults.details()
        );
    }

    @Nonnull
    public TelemetryProjectDescriptor.StatsOptions stats() {
        if (override == null || override.stats() == null) {
            return descriptor.stats();
        }
        TelemetryProjectDescriptor.StatsOptions defaults = descriptor.stats();
        TelemetryProjectOverride.StatsOverride statsOverride = override.stats();
        return new TelemetryProjectDescriptor.StatsOptions(
                statsOverride.enabled() == null ? defaults.enabled() : statsOverride.enabled(),
                statsOverride.allowedEvents().isEmpty() ? defaults.allowedEvents() : statsOverride.allowedEvents(),
                defaults.details()
        );
    }

    @Nullable
    public CrashReportClient.DeliveryTarget resolveDeliveryTarget(@Nonnull TelemetryRuntimeSettings settings) {
        if ("custom".equalsIgnoreCase(destinationMode())) {
            String url = firstNonBlank(
                    override == null ? null : override.customEndpoint().url(),
                    descriptor.customEndpoint().url()
            );
            if (url == null) {
                return null;
            }
            return new CrashReportClient.DeliveryTarget(
                    url,
                    mergeHeaders(
                            descriptor.customEndpoint().headers(),
                            override == null ? Map.of() : override.customEndpoint().headers()
                    )
            );
        }

        String endpoint = firstNonBlank(
                override == null ? null : override.hosted().endpoint(),
                descriptor.hosted().endpoint(),
                settings.hostedIngestEndpoint()
        );
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(mergeHeaders(
                descriptor.hosted().headers(),
                override == null ? Map.of() : override.hosted().headers()
        ));
        String projectKey = firstNonBlank(
                override == null ? null : override.hosted().projectKey(),
                descriptor.hosted().projectKey()
        );
        if (projectKey != null) {
            headers.put(TelemetryProjectDescriptor.PROJECT_KEY_HEADER, projectKey);
        }
        return endpoint == null ? null : new CrashReportClient.DeliveryTarget(endpoint, headers);
    }

    @Nullable
    public CrashReportClient.DeliveryTarget resolveEventDeliveryTarget(@Nonnull TelemetryRuntimeSettings settings) {
        if ("custom".equalsIgnoreCase(destinationMode())) {
            String url = firstNonBlank(
                    override == null ? null : override.customEndpoint().eventUrl(),
                    descriptor.customEndpoint().eventUrl(),
                    override == null ? null : override.customEndpoint().url(),
                    descriptor.customEndpoint().url()
            );
            if (url == null) {
                return null;
            }
            return new CrashReportClient.DeliveryTarget(
                    url,
                    mergeHeaders(
                            descriptor.customEndpoint().headers(),
                            override == null ? Map.of() : override.customEndpoint().headers()
                    )
            );
        }

        String endpoint = firstNonBlank(
                override == null ? null : override.hosted().eventEndpoint(),
                descriptor.hosted().eventEndpoint(),
                override == null ? null : override.hosted().endpoint(),
                descriptor.hosted().endpoint(),
                settings.hostedEventIngestEndpoint()
        );
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(mergeHeaders(
                descriptor.hosted().headers(),
                override == null ? Map.of() : override.hosted().headers()
        ));
        String projectKey = firstNonBlank(
                override == null ? null : override.hosted().projectKey(),
                descriptor.hosted().projectKey()
        );
        if (projectKey != null) {
            headers.put(TelemetryProjectDescriptor.PROJECT_KEY_HEADER, projectKey);
        }
        return endpoint == null ? null : new CrashReportClient.DeliveryTarget(endpoint, headers);
    }

    @Nullable
    public CrashReportClient.DeliveryTarget resolveReportDeliveryTarget(@Nonnull TelemetryRuntimeSettings settings) {
        if ("custom".equalsIgnoreCase(destinationMode())) {
            String url = firstNonBlank(
                    override == null ? null : override.customEndpoint().eventUrl(),
                    descriptor.customEndpoint().eventUrl(),
                    override == null ? null : override.customEndpoint().url(),
                    descriptor.customEndpoint().url()
            );
            if (url == null) {
                return null;
            }
            return new CrashReportClient.DeliveryTarget(
                    url,
                    mergeHeaders(
                            descriptor.customEndpoint().headers(),
                            override == null ? Map.of() : override.customEndpoint().headers()
                    )
            );
        }

        String configuredReportEndpoint = settings.manualReports().hostedReportIngestEndpoint();
        boolean explicitReportEndpoint = !TelemetryRuntimeSettings.DEFAULT_HOSTED_REPORT_INGEST_ENDPOINT.equals(configuredReportEndpoint);
        String endpoint = firstNonBlank(
                explicitReportEndpoint ? configuredReportEndpoint : null,
                reportEndpointFromHostedEndpoint(override == null ? null : override.hosted().eventEndpoint()),
                reportEndpointFromHostedEndpoint(descriptor.hosted().eventEndpoint()),
                reportEndpointFromHostedEndpoint(override == null ? null : override.hosted().endpoint()),
                reportEndpointFromHostedEndpoint(descriptor.hosted().endpoint()),
                override == null ? null : override.hosted().endpoint(),
                descriptor.hosted().endpoint(),
                override == null ? null : override.hosted().eventEndpoint(),
                descriptor.hosted().eventEndpoint(),
                configuredReportEndpoint
        );
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(mergeHeaders(
                descriptor.hosted().headers(),
                override == null ? Map.of() : override.hosted().headers()
        ));
        String projectKey = firstNonBlank(
                override == null ? null : override.hosted().projectKey(),
                descriptor.hosted().projectKey()
        );
        if (projectKey != null) {
            headers.put(TelemetryProjectDescriptor.PROJECT_KEY_HEADER, projectKey);
        }
        return endpoint == null ? null : new CrashReportClient.DeliveryTarget(endpoint, headers);
    }

    @Nonnull
    public TelemetryProjectRegistration withOverride(@Nullable TelemetryProjectOverride override) {
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, pluginVersion, sourcePath, override);
    }

    @Nonnull
    private static Map<String, String> mergeHeaders(@Nonnull Map<String, String> base,
                                                    @Nonnull Map<String, String> overrides) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return Map.copyOf(merged);
    }

    @Nullable
    private static String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    @Nullable
    private static String reportEndpointFromHostedEndpoint(@Nullable String endpoint) {
        String normalized = firstNonBlank(endpoint);
        if (normalized == null) {
            return null;
        }
        if (normalized.endsWith("/ingest/report")) {
            return normalized;
        }
        if (normalized.endsWith("/ingest/event")) {
            return normalized.substring(0, normalized.length() - "/ingest/event".length()) + "/ingest/report";
        }
        if (normalized.endsWith("/ingest/crash")) {
            return normalized.substring(0, normalized.length() - "/ingest/crash".length()) + "/ingest/report";
        }
        return null;
    }
}
