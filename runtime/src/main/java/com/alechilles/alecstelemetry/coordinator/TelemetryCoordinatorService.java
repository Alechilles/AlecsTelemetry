package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportKind;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntSupplier;

/**
 * Active runtime coordinator that owns telemetry capture, queueing, and upload for all projects.
 */
public final class TelemetryCoordinatorService {

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryCoreEngine engine;
    private final TelemetryCoordinatorStatsHeartbeat statsHeartbeat;

    public TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                       @Nonnull TelemetryDataPaths dataPaths,
                                       @Nonnull List<TelemetryProjectRegistration> projects,
                                       @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                       @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                       @Nonnull CrashReportClient client,
                                       @Nullable HytaleLogger logger,
                                       @Nullable ScheduledExecutorService executor) {
        this(settings, dataPaths, projects, manualReportProjects, loadedMods, client, logger, executor, null);
    }

    public TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                       @Nonnull TelemetryDataPaths dataPaths,
                                       @Nonnull List<TelemetryProjectRegistration> projects,
                                       @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                       @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                       @Nonnull CrashReportClient client,
                                       @Nullable HytaleLogger logger,
                                       @Nullable ScheduledExecutorService executor,
                                       @Nullable IntSupplier onlinePlayers) {
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                projects,
                manualReportProjects,
                loadedMods,
                client,
                logger,
                executor
        );
        this.statsHeartbeat = onlinePlayers == null
                ? null
                : new TelemetryCoordinatorStatsHeartbeat(engine, onlinePlayers, executor, logger);
    }

    @Nonnull
    public static TelemetryCoordinatorService fromDiscovery(@Nonnull TelemetryRuntimeSettings settings,
                                                            @Nonnull TelemetryDataPaths dataPaths,
                                                            @Nonnull TelemetryRuntimeDiscoveryResult discovery,
                                                            @Nonnull CrashReportClient client,
                                                            @Nullable HytaleLogger logger,
                                                            @Nullable ScheduledExecutorService executor,
                                                            @Nullable IntSupplier onlinePlayers) {
        return new TelemetryCoordinatorService(
                settings,
                dataPaths,
                discovery.projects(),
                manualReportRuntimeProjects(discovery.projects(), discovery.consentProjects()),
                discovery.loadedMods(),
                client,
                logger,
                executor,
                onlinePlayers
        );
    }

    @Nonnull
    public static TelemetryCoordinatorService discover(@Nonnull TelemetryDataPaths dataPaths,
                                                       @Nonnull CrashReportClient client,
                                                       @Nullable HytaleLogger logger,
                                                       @Nullable ScheduledExecutorService executor) {
        return discover(dataPaths, client, logger, executor, null);
    }

    @Nonnull
    public static TelemetryCoordinatorService discover(@Nonnull TelemetryDataPaths dataPaths,
                                                       @Nonnull CrashReportClient client,
                                                       @Nullable HytaleLogger logger,
                                                       @Nullable ScheduledExecutorService executor,
                                                       @Nullable IntSupplier onlinePlayers) {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscovery(logger).discoverActive(dataPaths);
        return fromDiscovery(
                settings,
                dataPaths,
                discovery,
                client,
                logger,
                executor,
                onlinePlayers
        );
    }

    public void start() {
        engine.start();
        if (statsHeartbeat != null) {
            statsHeartbeat.start();
        }
    }

    public void shutdown() {
        if (statsHeartbeat != null) {
            statsHeartbeat.shutdown();
        }
        engine.shutdown();
    }

    public boolean isEnabled() {
        return engine.isEnabled();
    }

    public boolean isProjectEnabled(@Nonnull String projectId) {
        return engine.isProjectEnabled(projectId);
    }

    public boolean isCrashEnabled(@Nonnull String projectId) {
        return engine.isCrashEnabled(projectId);
    }

    public boolean isErrorEventsEnabled(@Nonnull String projectId) {
        return engine.isErrorEventsEnabled(projectId);
    }

    public boolean isLifecycleEventsEnabled(@Nonnull String projectId) {
        return engine.isLifecycleEventsEnabled(projectId);
    }

    public boolean isPerformanceEnabled(@Nonnull String projectId) {
        return engine.isPerformanceEnabled(projectId);
    }

    public boolean isUsageEnabled(@Nonnull String projectId) {
        return engine.isUsageEnabled(projectId);
    }

    public boolean isStatsEnabled(@Nonnull String projectId) {
        return engine.isStatsEnabled(projectId);
    }

    public boolean isBreadcrumbsEnabled(@Nonnull String projectId) {
        return engine.isBreadcrumbsEnabled(projectId);
    }

    public int registeredProjectCount() {
        return engine.projects().size();
    }

    @Nonnull
    public TelemetryRuntimeSettings settings() {
        return settings;
    }

    @Nonnull
    public TelemetryDataPaths dataPaths() {
        return dataPaths;
    }

    @Nonnull
    public List<TelemetryProjectRegistration> projects() {
        return engine.projects();
    }

    @Nonnull
    public List<TelemetryProjectRegistration> manualReportProjects() {
        return engine.manualReportProjects();
    }

    @Nonnull
    public List<CrashReportEnvelope.LoadedModMetadata> loadedMods() {
        return engine.loadedMods();
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        return engine.findProject(projectId);
    }

    public boolean recordBreadcrumb(@Nonnull String projectId,
                                    @Nonnull String category,
                                    @Nonnull String detail) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordBreadcrumb(projectId, category, detail);
        return true;
    }

    public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null && engine.findManualReportProject(projectId) == null) {
            return false;
        }
        engine.setProjectEnabled(projectId, enabled);
        return true;
    }

    public boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setCrashEnabled(projectId, enabled);
        return true;
    }

    public boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setErrorEventsEnabled(projectId, enabled);
        return true;
    }

    public boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setLifecycleEventsEnabled(projectId, enabled);
        return true;
    }

    public boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setPerformanceEnabled(projectId, enabled);
        return true;
    }

    public boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setUsageEnabled(projectId, enabled);
        return true;
    }

    public boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setStatsEnabled(projectId, enabled);
        return true;
    }

    public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setBreadcrumbsEnabled(projectId, enabled);
        return true;
    }

    public boolean captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.captureSetupFailure(projectId, throwable);
        return true;
    }

    public boolean captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.captureStartFailure(projectId, throwable);
        return true;
    }

    public boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable,
                                                  @Nullable String worldName,
                                                  @Nullable String removalReason,
                                                  @Nullable String possibleFailureCause) {
        return engine.captureExceptionalWorldRemoval(throwable, worldName, removalReason, possibleFailureCause);
    }

    public boolean recordError(@Nonnull String projectId,
                               @Nonnull String eventName,
                               @Nullable Throwable throwable,
                               @Nonnull Map<String, Object> details) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordErrorWithContext(projectId, eventName, throwable, contextFrom(details, TelemetryEventContext.error()));
        return true;
    }

    public boolean recordLifecycle(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   int durationMs,
                                   boolean success,
                                   @Nonnull Map<String, Object> details) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordLifecycleWithContext(projectId, eventName, durationMs, success, contextFrom(details, TelemetryEventContext.lifecycle()));
        return true;
    }

    public boolean recordPerformance(@Nonnull String projectId,
                                     @Nonnull String eventName,
                                     int durationMs,
                                     @Nullable Double metricValue,
                                     @Nonnull Map<String, Object> details) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, contextFrom(details, TelemetryEventContext.performance()));
        return true;
    }

    public boolean recordUsage(@Nonnull String projectId,
                               @Nonnull String eventName,
                               @Nonnull Map<String, Object> details) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordUsageWithContext(projectId, eventName, contextFrom(details, TelemetryEventContext.usage()));
        return true;
    }

    public boolean recordStats(@Nonnull String projectId,
                               @Nonnull String eventName,
                               @Nonnull Map<String, Object> details) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordStatsWithContext(projectId, eventName, contextFrom(details, TelemetryEventContext.stats()));
        return true;
    }

    public boolean requestFlush(@Nullable String projectId) {
        return engine.triggerFlushAsync(projectId);
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return engine.captureTestReport(projectId, detail);
    }

    @Nonnull
    public Map<String, Object> submitManualReport(@Nonnull String projectId,
                                                  @Nonnull Map<String, Object> submission,
                                                  @Nonnull Map<String, Object> playerContext) {
        ManualReportEnvelope.CreateResult result = engine.submitManualReport(
                projectId,
                submissionFrom(submission),
                playerContextFrom(playerContext)
        );
        return manualReportResultToMap(result);
    }

    public int pendingReports(@Nullable String projectId) {
        return engine.pendingReports(projectId);
    }

    void emitStatsHeartbeatNow() {
        if (statsHeartbeat != null) {
            statsHeartbeat.emitHeartbeatNow();
        }
    }

    public boolean flushInProgress() {
        return engine.flushInProgress();
    }

    @Nonnull
    public String lastFlushResult() {
        return engine.lastFlushResult();
    }

    @Nonnull
    public TelemetryCoreEngine.FlushSummary flushPendingReportsNow(@Nonnull String reason) {
        return engine.flushPendingReportsNow(reason);
    }

    @Nonnull
    public TelemetryCoreEngine.FlushSummary flushPendingReportsNow(@Nonnull String reason, @Nullable String projectId) {
        return engine.flushPendingReportsNow(reason, projectId);
    }

    @Nonnull
    private static TelemetryEventContext contextFrom(@Nonnull Map<String, Object> details,
                                                    @Nonnull TelemetryEventContext.Builder builder) {
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            Object value = entry.getValue();
            switch (key) {
                case "detail" -> builder.detail(value == null ? null : value.toString());
                case "severity" -> builder.severity(value == null ? null : value.toString());
                case "fingerprint" -> builder.fingerprint(value == null ? null : value.toString());
                case "subsystem" -> builder.subsystem(value == null ? null : value.toString());
                case "phase" -> builder.phase(value == null ? null : value.toString());
                case "operation" -> builder.operation(value == null ? null : value.toString());
                case "target" -> builder.target(value == null ? null : value.toString());
                case "featureKey" -> builder.featureKey(value == null ? null : value.toString());
                case "entryPoint" -> builder.entryPoint(value == null ? null : value.toString());
                case "runtimeSide" -> builder.runtimeSide(value == null ? null : value.toString());
                case "entityType" -> builder.entityType(value == null ? null : value.toString());
                case "itemId" -> builder.itemId(value == null ? null : value.toString());
                case "blockId" -> builder.blockId(value == null ? null : value.toString());
                case "biomeId" -> builder.biomeId(value == null ? null : value.toString());
                case "commandName" -> builder.commandName(value == null ? null : value.toString());
                case "worldName" -> builder.worldName(value == null ? null : value.toString());
                default -> builder.detail(key, value);
            }
        }
        return builder.build();
    }

    @Nonnull
    private static ManualReportSubmission submissionFrom(@Nonnull Map<String, Object> values) {
        return new ManualReportSubmission(
                manualReportKind(values.get("kind")),
                stringValue(values.get("title")),
                stringValue(values.get("description")),
                nullableStringValue(values.get("contact")),
                mapValue(values.get("formValues")),
                booleanValue(values.get("includeCurrentServerLog")),
                booleanValue(values.get("includePreviousServerLog")),
                booleanValue(values.get("includeLoadedModList")),
                booleanValue(values.get("includeDiagnostics")),
                booleanValue(values.get("allowResolutionUpdates"))
        );
    }

    @Nonnull
    private static PlayerReportRuntimeContext playerContextFrom(@Nonnull Map<String, Object> values) {
        return new PlayerReportRuntimeContext(
                booleanValue(values.get("singleplayer")),
                intValue(values.get("onlinePlayerCount")),
                nullableStringValue(values.get("worldName")),
                loadedModsFrom(values.get("loadedMods"))
        );
    }

    @Nonnull
    private static Map<String, Object> manualReportResultToMap(@Nonnull ManualReportEnvelope.CreateResult result) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", result.accepted());
        values.put("validationErrors", List.copyOf(result.validationErrors()));
        if (result.envelope() != null) {
            values.put("envelopeJson", result.envelope().toJson());
        }
        if (result.followUpToken() != null) {
            values.put("followUpToken", result.followUpToken());
        }
        return Map.copyOf(values);
    }

    @Nonnull
    private static ManualReportKind manualReportKind(@Nullable Object value) {
        if (value == null) {
            return ManualReportKind.ISSUE;
        }
        String normalized = value.toString().trim();
        for (ManualReportKind kind : ManualReportKind.values()) {
            if (kind.name().equalsIgnoreCase(normalized) || kind.key().equalsIgnoreCase(normalized)) {
                return kind;
            }
        }
        return ManualReportKind.ISSUE;
    }

    @Nonnull
    private static String stringValue(@Nullable Object value) {
        String normalized = nullableStringValue(value);
        return normalized == null ? "" : normalized;
    }

    @Nullable
    private static String nullableStringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean booleanValue(@Nullable Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static int intValue(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Nonnull
    private static Map<String, Object> mapValue(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().toString().trim();
            if (!key.isBlank()) {
                values.put(key, entry.getValue());
            }
        }
        return Map.copyOf(values);
    }

    @Nonnull
    private static List<CrashReportEnvelope.LoadedModMetadata> loadedModsFrom(@Nullable Object value) {
        if (!(value instanceof List<?> rawMods) || rawMods.isEmpty()) {
            return List.of();
        }
        ArrayList<CrashReportEnvelope.LoadedModMetadata> loadedMods = new ArrayList<>();
        for (Object rawMod : rawMods) {
            if (!(rawMod instanceof Map<?, ?> mod)) {
                continue;
            }
            loadedMods.add(new CrashReportEnvelope.LoadedModMetadata(
                    stringValue(mod.get("identifier")),
                    stringValue(mod.get("version"))
            ));
        }
        return List.copyOf(loadedMods);
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> manualReportRuntimeProjects(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull List<TelemetryProjectRegistration> consentProjects) {
        LinkedHashMap<String, TelemetryProjectRegistration> reportProjects = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : projects) {
            reportProjects.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : consentProjects) {
            if (project.descriptor().reports().enabled()) {
                reportProjects.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
            }
        }
        return List.copyOf(reportProjects.values());
    }
}
