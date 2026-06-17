package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Active runtime coordinator that owns telemetry capture, queueing, and upload for all projects.
 */
public final class TelemetryCoordinatorService {

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryCoreEngine engine;

    public TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                       @Nonnull TelemetryDataPaths dataPaths,
                                       @Nonnull List<TelemetryProjectRegistration> projects,
                                       @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                       @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                       @Nonnull CrashReportClient client,
                                       @Nullable HytaleLogger logger,
                                       @Nullable ScheduledExecutorService executor) {
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
    }

    @Nonnull
    public static TelemetryCoordinatorService discover(@Nonnull TelemetryDataPaths dataPaths,
                                                       @Nonnull CrashReportClient client,
                                                       @Nullable HytaleLogger logger,
                                                       @Nullable ScheduledExecutorService executor) {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult discovery = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
                .loadAll(dataPaths.projectSettingsDirectory());
        List<TelemetryProjectRegistration> projects = applyOverrides(discovery.projects(), overrides);
        List<TelemetryProjectRegistration> consentProjects = applyOverrides(discovery.consentProjects(), overrides);
        return new TelemetryCoordinatorService(
                settings,
                dataPaths,
                projects,
                manualReportRuntimeProjects(projects, consentProjects),
                discovery.loadedMods(),
                client,
                logger,
                executor
        );
    }

    public void start() {
        engine.start();
    }

    public void shutdown() {
        engine.shutdown();
    }

    public boolean isEnabled() {
        return engine.isEnabled();
    }

    public boolean isProjectEnabled(@Nonnull String projectId) {
        return engine.isProjectEnabled(projectId);
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

    public int pendingReports(@Nullable String projectId) {
        return engine.pendingReports(projectId);
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
    private static List<TelemetryProjectRegistration> applyOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides) {
        ArrayList<TelemetryProjectRegistration> resolved = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            resolved.add(project.withOverride(overrides.get(project.projectId().toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(resolved);
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
