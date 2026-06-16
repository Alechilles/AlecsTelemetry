package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
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
    private final TelemetryProjectOverrideStore overrideStore;
    private final TelemetryConsentStateStore consentStateStore;
    private List<TelemetryProjectRegistration> consentProjects;

    @Nonnull
    public static TelemetryRuntimeService create(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        TelemetryDataPaths dataPaths = TelemetryDataPaths.from(plugin);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
                .loadAll(dataPaths.projectSettingsDirectory());
        Map<String, TelemetryProjectOverride> consentOverrides = loadConsentOverrides(
                discoveryResult.consentProjects(),
                overrides,
                dataPaths,
                logger
        );
        List<TelemetryProjectRegistration> resolvedProjects = applyOverrides(discoveryResult.projects(), overrides);
        List<TelemetryProjectRegistration> resolvedConsentProjects = applyOverrides(discoveryResult.consentProjects(), consentOverrides);
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(resolvedProjects);
        List<String> registrationWarnings = buildRegistrationWarnings(collisions, discoveryResult.skippedRegistrationWarnings());
        return new TelemetryRuntimeService(
                settings,
                dataPaths,
                resolvedProjects,
                resolvedConsentProjects,
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
                            @Nonnull List<TelemetryProjectRegistration> consentProjects,
                            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                            @Nonnull CrashReportClient client,
                            @Nullable HytaleLogger logger,
                            @Nullable ScheduledExecutorService executor) {
        this(
                settings,
                dataPaths,
                projects,
                consentProjects,
                loadedMods,
                TelemetryProjectCollisionDetector.detect(projects),
                buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
                client,
                logger,
                executor
        );
    }

    TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                            @Nonnull TelemetryDataPaths dataPaths,
                            @Nonnull List<TelemetryProjectRegistration> projects,
                            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                            @Nonnull CrashReportClient client,
                            @Nullable HytaleLogger logger,
                            @Nullable ScheduledExecutorService executor) {
        this(settings, dataPaths, projects, projects, loadedMods, client, logger, executor);
    }

    private TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                                    @Nonnull TelemetryDataPaths dataPaths,
                                    @Nonnull List<TelemetryProjectRegistration> projects,
                                    @Nonnull List<TelemetryProjectRegistration> consentProjects,
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
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.engine = new TelemetryCoreEngine(settings, dataPaths, projects, loadedMods, client, logger, executor);
        this.api = new TelemetryRuntimeApiImpl(this);
        this.consentProjects = List.copyOf(consentProjects);
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

    @Nonnull
    public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
        return consentStateStore.unreviewedProjects(dataPaths.consentStateFile(), consentProjects);
    }

    public boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot) {
        boolean appliedAll = true;
        for (TelemetryProjectRegistration project : consentProjects) {
            appliedAll &= applyConsent(project.projectId(), snapshot);
        }
        return appliedAll;
    }

    public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        if (project == null) {
            return false;
        }
        java.nio.file.Path overrideFile = dataPaths.projectOverrideFile(project.projectId());
        boolean saved = overrideStore.saveProjectEnabled(overrideFile, snapshot.projectEnabled())
                && overrideStore.saveCrashEnabled(overrideFile, snapshot.crashEnabled())
                && overrideStore.saveErrorEventsEnabled(overrideFile, snapshot.errorEnabled())
                && overrideStore.saveLifecycleEventsEnabled(overrideFile, snapshot.lifecycleEnabled())
                && overrideStore.savePerformanceEnabled(overrideFile, snapshot.performanceEnabled())
                && overrideStore.saveUsageEnabled(overrideFile, snapshot.usageEnabled())
                && overrideStore.saveBreadcrumbsEnabled(overrideFile, snapshot.breadcrumbsEnabled());
        java.nio.file.Path embeddedOverrideFile = embeddedProjectOverrideFile(project);
        if (embeddedOverrideFile != null) {
            saved = saved
                    && overrideStore.saveProjectEnabled(embeddedOverrideFile, snapshot.projectEnabled())
                    && overrideStore.saveCrashEnabled(embeddedOverrideFile, snapshot.crashEnabled())
                    && overrideStore.saveErrorEventsEnabled(embeddedOverrideFile, snapshot.errorEnabled())
                    && overrideStore.saveLifecycleEventsEnabled(embeddedOverrideFile, snapshot.lifecycleEnabled())
                    && overrideStore.savePerformanceEnabled(embeddedOverrideFile, snapshot.performanceEnabled())
                    && overrideStore.saveUsageEnabled(embeddedOverrideFile, snapshot.usageEnabled())
                    && overrideStore.saveBreadcrumbsEnabled(embeddedOverrideFile, snapshot.breadcrumbsEnabled());
        }
        if (!saved) {
            return false;
        }
        TelemetryProjectOverride override = overrideStore.load(overrideFile);
        if (override != null) {
            replaceConsentProject(project.withOverride(override));
        }
        if (findProject(project.projectId()) != null) {
            engine.setProjectEnabled(project.projectId(), snapshot.projectEnabled());
            engine.setCrashEnabled(project.projectId(), snapshot.crashEnabled());
            engine.setErrorEventsEnabled(project.projectId(), snapshot.errorEnabled());
            engine.setLifecycleEventsEnabled(project.projectId(), snapshot.lifecycleEnabled());
            engine.setPerformanceEnabled(project.projectId(), snapshot.performanceEnabled());
            engine.setUsageEnabled(project.projectId(), snapshot.usageEnabled());
            engine.setBreadcrumbsEnabled(project.projectId(), snapshot.breadcrumbsEnabled());
        }
        return true;
    }

    public boolean markConsentReviewed(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project != null && consentStateStore.markReviewed(dataPaths.consentStateFile(), project);
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
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            projectDiagnostics.add(buildProjectDiagnostics(project));
        }
        return new TelemetryRuntimeDiagnostics(
                engine.isEnabled(),
                consentProjects.size(),
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
        TelemetryProjectRegistration project = findConsentProject(projectId);
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
        boolean registeredForStandaloneRuntime = findProject(project.projectId()) != null;
        int pendingReports = registeredForStandaloneRuntime ? engine.pendingReports(project.projectId()) : 0;
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                project.projectId(),
                project.displayName(),
                project.isEnabled(),
                project.hasOverride(),
                project.destinationMode(),
                target == null ? null : target.endpoint(),
                pendingReports,
                project.pluginIdentifier(),
                project.pluginVersion(),
                project.sourcePath() == null ? null : project.sourcePath().toString(),
                project.descriptor().ui().iconTexturePath(),
                project.packagePrefixes(),
                project.runtimeMode(),
                registeredForStandaloneRuntime ? engine.isCrashEnabled(project.projectId()) : project.isCrashTelemetryEnabled(),
                registeredForStandaloneRuntime ? engine.isErrorEventsEnabled(project.projectId()) : project.events().errors().enabled(),
                registeredForStandaloneRuntime ? engine.isLifecycleEventsEnabled(project.projectId()) : project.events().lifecycle().enabled(),
                registeredForStandaloneRuntime ? engine.isPerformanceEnabled(project.projectId()) : project.performance().enabled(),
                registeredForStandaloneRuntime ? engine.isUsageEnabled(project.projectId()) : project.usage().enabled(),
                registeredForStandaloneRuntime ? engine.isBreadcrumbsEnabled(project.projectId()) : project.events().breadcrumbs().enabled()
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
    private static Map<String, TelemetryProjectOverride> loadConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> centralOverrides,
            @Nonnull TelemetryDataPaths dataPaths,
            @Nullable HytaleLogger logger) {
        LinkedHashMap<String, TelemetryProjectOverride> resolved = new LinkedHashMap<>(centralOverrides);
        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(logger);
        for (TelemetryProjectRegistration project : projects) {
            String projectIdKey = project.projectId().toLowerCase(Locale.ROOT);
            if (resolved.containsKey(projectIdKey)) {
                continue;
            }
            java.nio.file.Path embeddedOverrideFile = embeddedProjectOverrideFile(dataPaths, project);
            if (embeddedOverrideFile == null) {
                continue;
            }
            TelemetryProjectOverride override = store.load(embeddedOverrideFile);
            if (override != null) {
                resolved.put(projectIdKey, override);
            }
        }
        return Map.copyOf(resolved);
    }

    @Nullable
    private TelemetryProjectRegistration findConsentProject(@Nonnull String projectId) {
        String normalized = projectId.trim();
        for (TelemetryProjectRegistration project : consentProjects) {
            if (project.projectId().equalsIgnoreCase(normalized)) {
                return project;
            }
        }
        return null;
    }

    private void replaceConsentProject(@Nonnull TelemetryProjectRegistration replacement) {
        ArrayList<TelemetryProjectRegistration> updated = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            updated.add(project.projectId().equalsIgnoreCase(replacement.projectId()) ? replacement : project);
        }
        consentProjects = List.copyOf(updated);
    }

    @Nullable
    private java.nio.file.Path embeddedProjectOverrideFile(@Nonnull TelemetryProjectRegistration project) {
        return embeddedProjectOverrideFile(dataPaths, project);
    }

    @Nullable
    private static java.nio.file.Path embeddedProjectOverrideFile(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectRegistration project) {
        if (!project.isEmbeddedMode() || dataPaths.modsDirectory() == null) {
            return null;
        }
        return dataPaths.modsDirectory()
                .resolve(sanitizePluginDataDirectory(project.pluginIdentifier()))
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve(project.projectId() + ".json");
    }

    @Nonnull
    private static String sanitizePluginDataDirectory(@Nonnull String pluginIdentifier) {
        return pluginIdentifier.trim().replace(':', '_');
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
            Level level = warning.contains("embedded telemetry project") ? Level.INFO : Level.WARNING;
            logger.at(level).log("Telemetry registration note: " + warning);
        }
    }

    /**
     * One flush pass summary.
     */
    record FlushSummary(int attempted, int uploaded, int pendingAfter, @Nullable String lastFailure) {
    }
}
