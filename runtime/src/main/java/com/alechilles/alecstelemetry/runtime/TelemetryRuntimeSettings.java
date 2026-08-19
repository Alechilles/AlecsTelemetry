package com.alechilles.alecstelemetry.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Global runtime settings for the telemetry mod.
 */
public record TelemetryRuntimeSettings(@Nonnull Path filePath,
                                       boolean enabled,
                                       int flushIntervalSeconds,
                                       int connectTimeoutMs,
                                       int readTimeoutMs,
                                       int maxPendingReportsPerProject,
                                       int maxPendingEventsPerProject,
                                       int maxUploadsPerFlush,
                                       int maxBreadcrumbsPerProject,
                                       @Nonnull ManualReportSettings manualReports,
                                       @Nonnull SparkProfilerSettings sparkProfiler,
                                       @Nonnull String hostedIngestEndpoint,
                                       @Nonnull String hostedEventIngestEndpoint) {

    public static final String DEFAULT_HOSTED_INGEST_ENDPOINT =
            "https://telemetry.alecsmods.com/ingest/crash";
    public static final String DEFAULT_HOSTED_EVENT_INGEST_ENDPOINT =
            "https://telemetry.alecsmods.com/ingest/event";
    public static final String DEFAULT_HOSTED_REPORT_INGEST_ENDPOINT =
            "https://telemetry.alecsmods.com/ingest/report";

    private static final int CURRENT_VERSION = 1;
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 180;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 3000;
    private static final int DEFAULT_MAX_PENDING_REPORTS_PER_PROJECT = 200;
    private static final int DEFAULT_MAX_PENDING_EVENTS_PER_PROJECT = 500;
    private static final int DEFAULT_MAX_UPLOADS_PER_FLUSH = 10;
    private static final int DEFAULT_MAX_BREADCRUMBS_PER_PROJECT = 30;
    private static final boolean DEFAULT_MANUAL_REPORTS_ENABLED = true;
    private static final boolean DEFAULT_MANUAL_REVIEW_REQUIRED = false;
    private static final boolean DEFAULT_ALLOW_REPORT_CONTACT = true;
    private static final boolean DEFAULT_ALLOW_RESOLUTION_UPDATES = true;
    private static final boolean DEFAULT_ALLOW_CURRENT_SERVER_LOG = true;
    private static final boolean DEFAULT_ALLOW_PREVIOUS_SERVER_LOG = true;
    private static final boolean DEFAULT_ALLOW_LOADED_MOD_LIST = true;
    private static final boolean DEFAULT_ALLOW_DIAGNOSTICS = true;
    private static final int DEFAULT_MAX_LOG_ATTACHMENT_BYTES = 262144;
    private static final int DEFAULT_MAX_PENDING_MANUAL_REPORTS_PER_PROJECT = 200;
    private static final boolean DEFAULT_SPARK_PROFILER_ENABLED = false;
    private static final int DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS = 90;
    private static final int DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES = 5;
    private static final int DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS = 10;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /**
     * Keeps the pre-canary constructor available for existing embedded callers.
     */
    public TelemetryRuntimeSettings(@Nonnull Path filePath,
                                    boolean enabled,
                                    int flushIntervalSeconds,
                                    int connectTimeoutMs,
                                    int readTimeoutMs,
                                    int maxPendingReportsPerProject,
                                    int maxPendingEventsPerProject,
                                    int maxUploadsPerFlush,
                                    int maxBreadcrumbsPerProject,
                                    @Nonnull ManualReportSettings manualReports,
                                    @Nonnull String hostedIngestEndpoint,
                                    @Nonnull String hostedEventIngestEndpoint) {
        this(
                filePath,
                enabled,
                flushIntervalSeconds,
                connectTimeoutMs,
                readTimeoutMs,
                maxPendingReportsPerProject,
                maxPendingEventsPerProject,
                maxUploadsPerFlush,
                maxBreadcrumbsPerProject,
                manualReports,
                new SparkProfilerSettings(
                        DEFAULT_SPARK_PROFILER_ENABLED,
                        DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS,
                        DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS,
                        DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS,
                        DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES,
                        DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS
                ),
                hostedIngestEndpoint,
                hostedEventIngestEndpoint
        );
    }

    /**
     * Keeps the pre-recurring-monitor constructor available for existing embedded callers.
     */
    public TelemetryRuntimeSettings(@Nonnull Path filePath,
                                    boolean enabled,
                                    int flushIntervalSeconds,
                                    int connectTimeoutMs,
                                    int readTimeoutMs,
                                    int maxPendingReportsPerProject,
                                    int maxPendingEventsPerProject,
                                    int maxUploadsPerFlush,
                                    int maxBreadcrumbsPerProject,
                                    @Nonnull ManualReportSettings manualReports,
                                    @Nonnull SparkProfilerCanarySettings sparkProfilerCanary,
                                    @Nonnull String hostedIngestEndpoint,
                                    @Nonnull String hostedEventIngestEndpoint) {
        this(
                filePath,
                enabled,
                flushIntervalSeconds,
                connectTimeoutMs,
                readTimeoutMs,
                maxPendingReportsPerProject,
                maxPendingEventsPerProject,
                maxUploadsPerFlush,
                maxBreadcrumbsPerProject,
                manualReports,
                new SparkProfilerSettings(
                        sparkProfilerCanary.enabled(),
                        sparkProfilerCanary.initialDelaySeconds(),
                        DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS,
                        sparkProfilerCanary.timeoutSeconds(),
                        sparkProfilerCanary.maxSummaryEntries(),
                        DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS
                ),
                hostedIngestEndpoint,
                hostedEventIngestEndpoint
        );
    }

    @Nonnull
    public static TelemetryRuntimeSettings load(@Nonnull Path filePath, @Nullable HytaleLogger logger) {
        ensureTemplateExists(filePath, logger);
        SettingsDocument parsed = readSettings(filePath, logger);
        return new TelemetryRuntimeSettings(
                filePath,
                parsed.enabled == null ? DEFAULT_ENABLED : parsed.enabled,
                clamp(parsed.flushIntervalSeconds, DEFAULT_FLUSH_INTERVAL_SECONDS, 10, 3600),
                clamp(parsed.connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS, 100, 30000),
                clamp(parsed.readTimeoutMs, DEFAULT_READ_TIMEOUT_MS, 100, 30000),
                clamp(parsed.maxPendingReportsPerProject, DEFAULT_MAX_PENDING_REPORTS_PER_PROJECT, 1, 5000),
                clamp(parsed.maxPendingEventsPerProject, DEFAULT_MAX_PENDING_EVENTS_PER_PROJECT, 1, 10000),
                clamp(parsed.maxUploadsPerFlush, DEFAULT_MAX_UPLOADS_PER_FLUSH, 1, 500),
                clamp(parsed.maxBreadcrumbsPerProject, DEFAULT_MAX_BREADCRUMBS_PER_PROJECT, 1, 200),
                normalizeManualReportSettings(parsed.manualReports),
                parsed.sparkProfilerPresent
                        ? normalizeSparkProfilerSettings(parsed.sparkProfiler)
                        : normalizeSparkProfilerCanarySettings(parsed.sparkProfilerCanary),
                parsed.hostedIngestEndpoint == null || parsed.hostedIngestEndpoint.isBlank()
                        ? DEFAULT_HOSTED_INGEST_ENDPOINT
                        : parsed.hostedIngestEndpoint.trim(),
                parsed.hostedEventIngestEndpoint == null || parsed.hostedEventIngestEndpoint.isBlank()
                        ? DEFAULT_HOSTED_EVENT_INGEST_ENDPOINT
                        : parsed.hostedEventIngestEndpoint.trim()
        );
    }

    private static void ensureTemplateExists(@Nonnull Path filePath, @Nullable HytaleLogger logger) {
        if (Files.isRegularFile(filePath)) {
            return;
        }
        writeSettings(filePath, new SettingsDocument(), logger);
    }

    @Nonnull
    private static SettingsDocument readSettings(@Nonnull Path filePath, @Nullable HytaleLogger logger) {
        try {
            String raw = Files.readString(filePath, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new SettingsDocument();
            }
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            SettingsDocument parsed = GSON.fromJson(json, SettingsDocument.class);
            if (parsed == null) {
                return new SettingsDocument();
            }
            parsed.sparkProfilerPresent = json.has("sparkProfiler");
            return parsed;
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Unable to read telemetry runtime settings file: " + filePath
                );
            }
            return new SettingsDocument();
        }
    }

    private static void writeSettings(@Nonnull Path filePath,
                                      @Nonnull SettingsDocument document,
                                      @Nullable HytaleLogger logger) {
        SettingsDocument safe = new SettingsDocument();
        safe.version = CURRENT_VERSION;
        safe.enabled = DEFAULT_ENABLED;
        safe.flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
        safe.connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        safe.readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        safe.maxPendingReportsPerProject = DEFAULT_MAX_PENDING_REPORTS_PER_PROJECT;
        safe.maxPendingEventsPerProject = DEFAULT_MAX_PENDING_EVENTS_PER_PROJECT;
        safe.maxUploadsPerFlush = DEFAULT_MAX_UPLOADS_PER_FLUSH;
        safe.maxBreadcrumbsPerProject = DEFAULT_MAX_BREADCRUMBS_PER_PROJECT;
        safe.manualReports = ManualReportSettingsDocument.defaults();
        safe.sparkProfiler = SparkProfilerSettingsDocument.defaults();
        safe.hostedIngestEndpoint = DEFAULT_HOSTED_INGEST_ENDPOINT;
        safe.hostedEventIngestEndpoint = DEFAULT_HOSTED_EVENT_INGEST_ENDPOINT;
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String serialized = GSON.toJson(safe) + System.lineSeparator();
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(tmp, serialized, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Unable to save telemetry runtime settings file: " + filePath
                );
            }
        }
    }

    private static int clamp(@Nullable Integer value, int fallback, int min, int max) {
        int safe = value == null ? fallback : value;
        return Math.max(min, Math.min(max, safe));
    }

    @Nonnull
    private static ManualReportSettings normalizeManualReportSettings(@Nullable ManualReportSettingsDocument document) {
        ManualReportSettingsDocument safe = document == null ? new ManualReportSettingsDocument() : document;
        String endpoint = safe.hostedReportIngestEndpoint == null || safe.hostedReportIngestEndpoint.isBlank()
                ? DEFAULT_HOSTED_REPORT_INGEST_ENDPOINT
                : safe.hostedReportIngestEndpoint.trim();
        return new ManualReportSettings(
                safe.enabled == null ? DEFAULT_MANUAL_REPORTS_ENABLED : safe.enabled,
                safe.manualReviewRequired == null ? DEFAULT_MANUAL_REVIEW_REQUIRED : safe.manualReviewRequired,
                safe.allowContact == null ? DEFAULT_ALLOW_REPORT_CONTACT : safe.allowContact,
                safe.allowResolutionUpdates == null ? DEFAULT_ALLOW_RESOLUTION_UPDATES : safe.allowResolutionUpdates,
                safe.allowCurrentServerLog == null ? DEFAULT_ALLOW_CURRENT_SERVER_LOG : safe.allowCurrentServerLog,
                safe.allowPreviousServerLog == null ? DEFAULT_ALLOW_PREVIOUS_SERVER_LOG : safe.allowPreviousServerLog,
                safe.allowLoadedModList == null ? DEFAULT_ALLOW_LOADED_MOD_LIST : safe.allowLoadedModList,
                safe.allowDiagnostics == null ? DEFAULT_ALLOW_DIAGNOSTICS : safe.allowDiagnostics,
                clamp(safe.maxLogAttachmentBytes, DEFAULT_MAX_LOG_ATTACHMENT_BYTES, 0, 1048576),
                clamp(safe.maxPendingManualReportsPerProject, DEFAULT_MAX_PENDING_MANUAL_REPORTS_PER_PROJECT, 1, 5000),
                endpoint
        );
    }

    @Nonnull
    private static SparkProfilerSettings normalizeSparkProfilerSettings(
            @Nullable SparkProfilerSettingsDocument document) {
        SparkProfilerSettingsDocument safe = document == null
                ? new SparkProfilerSettingsDocument()
                : document;
        return new SparkProfilerSettings(
                safe.enabled == null ? DEFAULT_SPARK_PROFILER_ENABLED : safe.enabled,
                clamp(safe.initialDelaySeconds, DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS, 60, 3600),
                clamp(safe.intervalSeconds, DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS, 30, 3600),
                clamp(safe.timeoutSeconds, DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS, 1, 60),
                clamp(safe.maxSummaryEntries, DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES, 1, 10),
                clamp(safe.maxHistorySnapshots, DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS, 1, 10)
        );
    }

    private static SparkProfilerSettings normalizeSparkProfilerCanarySettings(
            @Nullable SparkProfilerCanarySettingsDocument document) {
        SparkProfilerCanarySettingsDocument safe = document == null
                ? new SparkProfilerCanarySettingsDocument()
                : document;
        return new SparkProfilerSettings(
                safe.enabled == null ? DEFAULT_SPARK_PROFILER_ENABLED : safe.enabled,
                clamp(safe.initialDelaySeconds, DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS, 60, 3600),
                DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS,
                clamp(safe.timeoutSeconds, DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS, 1, 60),
                clamp(safe.maxSummaryEntries, DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES, 1, 10),
                DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS
        );
    }

    /**
     * Temporary compatibility view for callers that still use the canary name.
     */
    @Nonnull
    public SparkProfilerCanarySettings sparkProfilerCanary() {
        return new SparkProfilerCanarySettings(
                sparkProfiler.enabled(),
                sparkProfiler.initialDelaySeconds(),
                sparkProfiler.timeoutSeconds(),
                sparkProfiler.maxSummaryEntries()
        );
    }

    public record ManualReportSettings(boolean enabled,
                                       boolean manualReviewRequired,
                                       boolean allowContact,
                                       boolean allowResolutionUpdates,
                                       boolean allowCurrentServerLog,
                                       boolean allowPreviousServerLog,
                                       boolean allowLoadedModList,
                                       boolean allowDiagnostics,
                                       int maxLogAttachmentBytes,
                                       int maxPendingManualReportsPerProject,
                                       @Nonnull String hostedReportIngestEndpoint) {
    }

    public record SparkProfilerSettings(boolean enabled,
                                        int initialDelaySeconds,
                                        int intervalSeconds,
                                        int timeoutSeconds,
                                        int maxSummaryEntries,
                                        int maxHistorySnapshots) {
    }

    public record SparkProfilerCanarySettings(boolean enabled,
                                              int initialDelaySeconds,
                                              int timeoutSeconds,
                                              int maxSummaryEntries) {
    }

    private static final class SettingsDocument {
        private Integer version;
        private transient boolean sparkProfilerPresent;
        private Boolean enabled;
        private Integer flushIntervalSeconds = DEFAULT_FLUSH_INTERVAL_SECONDS;
        private Integer connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        private Integer readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        private Integer maxPendingReportsPerProject = DEFAULT_MAX_PENDING_REPORTS_PER_PROJECT;
        private Integer maxPendingEventsPerProject = DEFAULT_MAX_PENDING_EVENTS_PER_PROJECT;
        private Integer maxUploadsPerFlush = DEFAULT_MAX_UPLOADS_PER_FLUSH;
        private Integer maxBreadcrumbsPerProject = DEFAULT_MAX_BREADCRUMBS_PER_PROJECT;
        private ManualReportSettingsDocument manualReports = ManualReportSettingsDocument.defaults();
        private SparkProfilerSettingsDocument sparkProfiler;
        private SparkProfilerCanarySettingsDocument sparkProfilerCanary;
        private String hostedIngestEndpoint = DEFAULT_HOSTED_INGEST_ENDPOINT;
        private String hostedEventIngestEndpoint = DEFAULT_HOSTED_EVENT_INGEST_ENDPOINT;
    }

    private static final class ManualReportSettingsDocument {
        private Boolean enabled = DEFAULT_MANUAL_REPORTS_ENABLED;
        private Boolean manualReviewRequired = DEFAULT_MANUAL_REVIEW_REQUIRED;
        private Boolean allowContact = DEFAULT_ALLOW_REPORT_CONTACT;
        private Boolean allowResolutionUpdates = DEFAULT_ALLOW_RESOLUTION_UPDATES;
        private Boolean allowCurrentServerLog = DEFAULT_ALLOW_CURRENT_SERVER_LOG;
        private Boolean allowPreviousServerLog = DEFAULT_ALLOW_PREVIOUS_SERVER_LOG;
        private Boolean allowLoadedModList = DEFAULT_ALLOW_LOADED_MOD_LIST;
        private Boolean allowDiagnostics = DEFAULT_ALLOW_DIAGNOSTICS;
        private Integer maxLogAttachmentBytes = DEFAULT_MAX_LOG_ATTACHMENT_BYTES;
        private Integer maxPendingManualReportsPerProject = DEFAULT_MAX_PENDING_MANUAL_REPORTS_PER_PROJECT;
        private String hostedReportIngestEndpoint = DEFAULT_HOSTED_REPORT_INGEST_ENDPOINT;

        @Nonnull
        private static ManualReportSettingsDocument defaults() {
            return new ManualReportSettingsDocument();
        }
    }

    private static final class SparkProfilerSettingsDocument {
        private Boolean enabled = DEFAULT_SPARK_PROFILER_ENABLED;
        private Integer initialDelaySeconds = DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS;
        private Integer intervalSeconds = DEFAULT_SPARK_PROFILER_INTERVAL_SECONDS;
        private Integer timeoutSeconds = DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS;
        private Integer maxSummaryEntries = DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES;
        private Integer maxHistorySnapshots = DEFAULT_SPARK_PROFILER_MAX_HISTORY_SNAPSHOTS;

        @Nonnull
        private static SparkProfilerSettingsDocument defaults() {
            return new SparkProfilerSettingsDocument();
        }
    }

    private static final class SparkProfilerCanarySettingsDocument {
        private Boolean enabled = DEFAULT_SPARK_PROFILER_ENABLED;
        private Integer initialDelaySeconds = DEFAULT_SPARK_PROFILER_INITIAL_DELAY_SECONDS;
        private Integer timeoutSeconds = DEFAULT_SPARK_PROFILER_TIMEOUT_SECONDS;
        private Integer maxSummaryEntries = DEFAULT_SPARK_PROFILER_MAX_SUMMARY_ENTRIES;

        @Nonnull
        private static SparkProfilerCanarySettingsDocument defaults() {
            return new SparkProfilerCanarySettingsDocument();
        }
    }
}
