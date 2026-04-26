package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

/**
 * Standalone telemetry runtime mod orchestration built on the shared telemetry core engine.
 */
public final class TelemetryRuntimeService {

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final List<TelemetryProjectCollisionDetector.Collision> collisions;
    private final List<String> registrationWarnings;
    private final TelemetryRuntimeApi api;
    private final TelemetryCoreEngine engine;
    private final HytaleLogger logger;

    @Nonnull
    public static TelemetryRuntimeService create(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        TelemetryDataPaths dataPaths = TelemetryDataPaths.from(plugin);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.modsDirectory());
        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
                .loadAll(dataPaths.projectSettingsDirectory());
        List<TelemetryProjectRegistration> resolvedProjects = applyOverrides(discoveryResult.projects(), overrides);
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(resolvedProjects);
        List<String> registrationWarnings = buildRegistrationWarnings(collisions, discoveryResult.skippedRegistrationWarnings());
        return new TelemetryRuntimeService(
                settings,
                dataPaths,
                resolvedProjects,
                discoveryResult.loadedMods(),
                collisions,
                registrationWarnings,
                new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger),
                logger,
                HytaleServer.SCHEDULED_EXECUTOR
        );
    }

    TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                            @Nonnull TelemetryDataPaths dataPaths,
                            @Nonnull List<TelemetryProjectRegistration> projects,
                            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                            @Nonnull CrashReportClient client,
                            @Nullable HytaleLogger logger,
                            @Nullable ScheduledExecutorService executor) {
        this(
                settings,
                dataPaths,
                projects,
                loadedMods,
                TelemetryProjectCollisionDetector.detect(projects),
                buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
                client,
                logger,
                executor
        );
    }

    private TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                                    @Nonnull TelemetryDataPaths dataPaths,
                                    @Nonnull List<TelemetryProjectRegistration> projects,
                                    @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                    @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
                                    @Nonnull List<String> registrationWarnings,
                                    @Nonnull CrashReportClient client,
                                    @Nullable HytaleLogger logger,
                                    @Nullable ScheduledExecutorService executor) {
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.collisions = List.copyOf(collisions);
        this.registrationWarnings = List.copyOf(registrationWarnings);
        this.logger = logger;
        this.engine = new TelemetryCoreEngine(settings, dataPaths, projects, loadedMods, client, logger, executor);
        this.api = new TelemetryRuntimeApiImpl(this);
        logRegistrationWarnings();
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

    public int registeredProjectCount() {
        return engine.projects().size();
    }

    @Nonnull
    public TelemetryRuntimeApi api() {
        return api;
    }

    @Nonnull
    public List<TelemetryProjectRegistration> projects() {
        return engine.projects();
    }

    @Nonnull
    public List<TelemetryProjectCollisionDetector.Collision> collisions() {
        return collisions;
    }

    public boolean isProjectEnabled(@Nonnull String projectId) {
        return engine.isProjectEnabled(projectId);
    }

    public boolean triggerFlushAsync() {
        return engine.triggerFlushAsync();
    }

    public boolean triggerFlushAsync(@Nullable String projectId) {
        return engine.triggerFlushAsync(projectId);
    }

    public void recordBreadcrumb(@Nonnull String projectId,
                                 @Nonnull String category,
                                 @Nonnull String detail) {
        engine.recordBreadcrumb(projectId, category, detail);
    }

    public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        engine.captureSetupFailure(projectId, throwable);
    }

    public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        engine.captureStartFailure(projectId, throwable);
    }

    public void recordError(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable Throwable throwable,
                            @Nullable String detail) {
        engine.recordError(projectId, eventName, throwable, detail);
    }

    public void recordErrorWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable Throwable throwable,
                                       @Nullable TelemetryEventContext context) {
        engine.recordErrorWithContext(projectId, eventName, throwable, context);
    }

    public void recordLifecycle(@Nonnull String projectId,
                                @Nonnull String eventName,
                                int durationMs,
                                boolean success,
                                @Nullable String detail) {
        engine.recordLifecycle(projectId, eventName, durationMs, success, detail);
    }

    public void recordLifecycleWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           int durationMs,
                                           boolean success,
                                           @Nullable TelemetryEventContext context) {
        engine.recordLifecycleWithContext(projectId, eventName, durationMs, success, context);
    }

    public void recordPerformance(@Nonnull String projectId,
                                  @Nonnull String eventName,
                                  int durationMs,
                                  @Nullable Double metricValue,
                                  @Nullable String detail) {
        engine.recordPerformance(projectId, eventName, durationMs, metricValue, detail);
    }

    public void recordPerformanceWithContext(@Nonnull String projectId,
                                             @Nonnull String eventName,
                                             int durationMs,
                                             @Nullable Double metricValue,
                                             @Nullable TelemetryEventContext context) {
        engine.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, context);
    }

    public void recordUsage(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable String detail) {
        engine.recordUsage(projectId, eventName, detail);
    }

    public void recordUsageWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        engine.recordUsageWithContext(projectId, eventName, context);
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return engine.captureTestReport(projectId, detail);
    }

    public void captureExceptionalWorldRemoval(@Nullable World world,
                                               @Nullable RemoveWorldEvent.RemovalReason removalReason) {
        engine.captureExceptionalWorldRemoval(world, removalReason);
    }

    @Nonnull
    public TelemetryRuntimeDiagnostics diagnostics() {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(engine.projects().size());
        for (TelemetryProjectRegistration project : engine.projects()) {
            projectDiagnostics.add(buildProjectDiagnostics(project));
        }
        return new TelemetryRuntimeDiagnostics(
                engine.isEnabled(),
                engine.projects().size(),
                engine.loadedMods().size(),
                engine.pendingReports(null),
                engine.flushInProgress(),
                engine.lastFlushResult(),
                dataPaths.modsDirectory() == null ? null : dataPaths.modsDirectory().toString(),
                registrationWarnings,
                List.copyOf(projectDiagnostics)
        );
    }

    @Nullable
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findProject(projectId);
        return project == null ? null : buildProjectDiagnostics(project);
    }

    public int pendingReports(@Nullable String projectId) {
        return engine.pendingReports(projectId);
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        return engine.findProject(projectId);
    }

    @Nonnull
    FlushSummary flushPendingReportsNow(@Nonnull String reason) {
        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow(reason);
        return new FlushSummary(summary.attempted(), summary.uploaded(), summary.pendingAfter(), summary.lastFailure());
    }

    @Nonnull
    FlushSummary flushPendingReportsNow(@Nonnull String reason, @Nullable String projectIdFilter) {
        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow(reason, projectIdFilter);
        return new FlushSummary(summary.attempted(), summary.uploaded(), summary.pendingAfter(), summary.lastFailure());
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics buildProjectDiagnostics(
            @Nonnull TelemetryProjectRegistration project) {
        CrashReportClient.DeliveryTarget target = project.resolveDeliveryTarget(settings);
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                project.projectId(),
                project.displayName(),
                project.isEnabled(),
                project.hasOverride(),
                project.destinationMode(),
                target == null ? null : target.endpoint(),
                engine.pendingReports(project.projectId()),
                project.pluginIdentifier(),
                project.pluginVersion(),
                project.sourcePath() == null ? null : project.sourcePath().toString(),
                project.packagePrefixes(),
                project.runtimeMode()
        );
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
    private static List<String> buildRegistrationWarnings(
            @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
            @Nonnull List<String> skippedWarnings) {
        ArrayList<String> warnings = new ArrayList<>(skippedWarnings.size() + collisions.size());
        warnings.addAll(skippedWarnings);
        for (TelemetryProjectCollisionDetector.Collision collision : collisions) {
            warnings.add(collision.format());
        }
        return List.copyOf(warnings);
    }

    private void logRegistrationWarnings() {
        if (logger == null || registrationWarnings.isEmpty()) {
            return;
        }
        for (String warning : registrationWarnings) {
            Level level = warning.contains("runtimeMode=embedded") ? Level.INFO : Level.WARNING;
            logger.at(level).log("Telemetry registration note: " + warning);
        }
    }

    /**
     * One flush pass summary.
     */
    record FlushSummary(int attempted, int uploaded, int pendingAfter, @Nullable String lastFailure) {
    }
}
