package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.reports.TelemetryReportCoordinator;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryLoadedModSnapshotProvider;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryStatsHeartbeatService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone telemetry runtime mod orchestration built on the shared telemetry core engine.
 */
public final class TelemetryRuntimeService implements TelemetryConsentRuntime, TelemetryRuntimeOperations {

    private static final String FALLBACK_RUNTIME_VERSION = "0.1.3";

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final List<TelemetryProjectCollisionDetector.Collision> collisions;
    private final List<String> registrationWarnings;
    private final TelemetryRuntimeApi api;
    private final TelemetryCoreEngine engine;
    private final TelemetryRuntimeCandidate candidate;
    private final TelemetryCoordinatorService coordinatorService;
    private final StandaloneCoordinatorBridge coordinatorBridge;
    private final HytaleLogger logger;
    private final TelemetryProjectOverrideStore overrideStore;
    private final TelemetryConsentStateStore consentStateStore;
    private final List<TelemetryProjectRegistration> projects;
    private final TelemetryLoadedModSnapshotProvider loadedModSnapshotProvider;
    private volatile TelemetryStatsHeartbeatService statsHeartbeatService;
    private List<TelemetryProjectRegistration> consentProjects;

    @Nonnull
    public static TelemetryRuntimeService create(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        TelemetryDataPaths dataPaths = TelemetryDataPaths.from(plugin);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult descriptorSnapshot = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        TelemetryLoadedModSnapshotProvider loadedModSnapshotProvider = TelemetryLoadedModSnapshotProvider.hytalePluginManager(
                descriptorSnapshot.loadedMods(),
                logger
        );
        TelemetryRuntimeDiscoveryResult discoveryResult = new TelemetryRuntimeDiscovery(logger)
                .discoverActive(dataPaths, descriptorSnapshot, loadedModSnapshotProvider);
        return new TelemetryRuntimeService(
                settings,
                dataPaths,
                discoveryResult.projects(),
                discoveryResult.consentProjects(),
                discoveryResult.loadedMods(),
                loadedModSnapshotProvider,
                discoveryResult.collisions(),
                discoveryResult.registrationWarnings(),
                new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger),
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                standaloneCandidate(plugin, dataPaths)
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
                TelemetryLoadedModSnapshotProvider.fixed(loadedMods),
                TelemetryProjectCollisionDetector.detect(projects),
                TelemetryRuntimeDiscovery.buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
                client,
                logger,
                executor,
                standaloneCandidate(dataPaths)
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

    TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                            @Nonnull TelemetryDataPaths dataPaths,
                            @Nonnull List<TelemetryProjectRegistration> projects,
                            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                            @Nonnull TelemetryLoadedModSnapshotProvider loadedModSnapshotProvider,
                            @Nonnull CrashReportClient client,
                            @Nullable HytaleLogger logger,
                            @Nullable ScheduledExecutorService executor) {
        this(
                settings,
                dataPaths,
                projects,
                projects,
                loadedMods,
                loadedModSnapshotProvider,
                TelemetryProjectCollisionDetector.detect(projects),
                TelemetryRuntimeDiscovery.buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
                client,
                logger,
                executor,
                standaloneCandidate(dataPaths)
        );
    }

    private TelemetryRuntimeService(@Nonnull TelemetryRuntimeSettings settings,
                                    @Nonnull TelemetryDataPaths dataPaths,
                                    @Nonnull List<TelemetryProjectRegistration> projects,
                                    @Nonnull List<TelemetryProjectRegistration> consentProjects,
                                    @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                    @Nonnull TelemetryLoadedModSnapshotProvider loadedModSnapshotProvider,
                                    @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
                                    @Nonnull List<String> registrationWarnings,
                                    @Nonnull CrashReportClient client,
                                    @Nullable HytaleLogger logger,
                                    @Nullable ScheduledExecutorService executor,
                                    @Nonnull TelemetryRuntimeCandidate candidate) {
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.collisions = List.copyOf(collisions);
        this.registrationWarnings = List.copyOf(registrationWarnings);
        this.logger = logger;
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.projects = List.copyOf(projects);
        this.loadedModSnapshotProvider = loadedModSnapshotProvider;
        this.candidate = candidate;
        this.engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                projects,
                manualReportRuntimeProjects(projects, consentProjects),
                loadedMods,
                client,
                logger,
                executor
        );
        this.coordinatorService = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                projects,
                manualReportRuntimeProjects(projects, consentProjects),
                loadedMods,
                client,
                logger,
                executor
        );
        this.coordinatorBridge = new StandaloneCoordinatorBridge(candidate, coordinatorService);
        this.api = new TelemetryRuntimeApiImpl(this);
        this.consentProjects = List.copyOf(consentProjects);
    }

    public void start() {
        TelemetryCoordinatorRegistry.register(coordinatorBridge);
    }

    public void shutdown() {
        TelemetryCoordinatorRegistry.unregister(candidate.providerId());
        stopStatsHeartbeat();
    }

    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && active.providerId().equals(candidate.providerId());
    }

    public void attachStatsHeartbeat(@Nullable TelemetryStatsHeartbeatService statsHeartbeatService) {
        this.statsHeartbeatService = statsHeartbeatService;
    }

    private void startStatsHeartbeat() {
        TelemetryStatsHeartbeatService heartbeat = statsHeartbeatService;
        if (heartbeat != null) {
            heartbeat.start();
        }
    }

    private void stopStatsHeartbeat() {
        TelemetryStatsHeartbeatService heartbeat = statsHeartbeatService;
        if (heartbeat != null) {
            heartbeat.shutdown();
        }
    }

    @Nullable
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
    }

    @Override
    public boolean isEnabled() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.isEnabled();
        }
        if (coordinatorBridge != null && active != null) {
            return coordinatorService.isEnabled();
        }
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
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return projectsFromSummaries(activeProjects(active));
        }
        if (coordinatorBridge != null && active != null) {
            return mergedProjects(coordinatorService.projects(), consentProjects);
        }
        return engine.projects();
    }

    @Nonnull
    public List<TelemetryProjectCollisionDetector.Collision> collisions() {
        return collisions;
    }

    public boolean isProjectEnabled(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return activeProjectEnabled(active, projectId);
        }
        return engine.isProjectEnabled(projectId);
    }

    @Nonnull
    public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return List.of();
        }
        return consentStateStore.unreviewedProjects(dataPaths.consentStateFile(), consentProjects);
    }

    @Override
    public boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsentToAll(TelemetryConsentBridgePayload.snapshotSummary(snapshot));
        }
        return applyLocalConsentToAll(snapshot);
    }

    private boolean applyLocalConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot) {
        boolean appliedAll = true;
        for (TelemetryProjectRegistration project : consentProjects) {
            appliedAll &= applyLocalConsent(project.projectId(), clampToSupported(snapshot, supportedSnapshot(project)));
        }
        return appliedAll;
    }

    public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsentCategoryToAll(category, enabled);
        }
        return applyLocalConsentCategoryToAll(category, enabled);
    }

    private boolean applyLocalConsentCategoryToAll(@Nonnull String category, boolean enabled) {
        boolean appliedAll = true;
        for (TelemetryProjectRegistration project : consentProjects) {
            TelemetryConsentSnapshot supported = supportedSnapshot(project);
            if (!supported.categoryEnabled(category)) {
                continue;
            }
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = projectDiagnostics(project.projectId());
            if (diagnostics == null) {
                appliedAll = false;
                continue;
            }
            TelemetryConsentSnapshot current = diagnostics.consentSnapshot();
            appliedAll &= applyLocalConsent(project.projectId(), current.withCategory(category, enabled));
        }
        return appliedAll;
    }

    public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsent(projectId, TelemetryConsentBridgePayload.snapshotSummary(snapshot));
        }
        return applyLocalConsent(projectId, snapshot);
    }

    private boolean applyLocalConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        if (project == null) {
            return false;
        }
        TelemetryConsentSnapshot normalized = clampToSupported(snapshot, supportedSnapshot(project));
        java.nio.file.Path overrideFile = dataPaths.projectOverrideFile(project.projectId());
        boolean saved = overrideStore.saveProjectEnabled(overrideFile, normalized.projectEnabled())
                && overrideStore.saveCrashEnabled(overrideFile, normalized.crashEnabled())
                && overrideStore.saveErrorEventsEnabled(overrideFile, normalized.errorEnabled())
                && overrideStore.saveLifecycleEventsEnabled(overrideFile, normalized.lifecycleEnabled())
                && overrideStore.savePerformanceEnabled(overrideFile, normalized.performanceEnabled())
                && overrideStore.saveUsageEnabled(overrideFile, normalized.usageEnabled())
                && overrideStore.saveStatsEnabled(overrideFile, normalized.statsEnabled())
                && overrideStore.saveBreadcrumbsEnabled(overrideFile, normalized.breadcrumbsEnabled());
        java.nio.file.Path embeddedOverrideFile = embeddedProjectOverrideFile(project);
        if (embeddedOverrideFile != null) {
            saved = saved
                    && overrideStore.saveProjectEnabled(embeddedOverrideFile, normalized.projectEnabled())
                    && overrideStore.saveCrashEnabled(embeddedOverrideFile, normalized.crashEnabled())
                    && overrideStore.saveErrorEventsEnabled(embeddedOverrideFile, normalized.errorEnabled())
                    && overrideStore.saveLifecycleEventsEnabled(embeddedOverrideFile, normalized.lifecycleEnabled())
                    && overrideStore.savePerformanceEnabled(embeddedOverrideFile, normalized.performanceEnabled())
                    && overrideStore.saveUsageEnabled(embeddedOverrideFile, normalized.usageEnabled())
                    && overrideStore.saveStatsEnabled(embeddedOverrideFile, normalized.statsEnabled())
                    && overrideStore.saveBreadcrumbsEnabled(embeddedOverrideFile, normalized.breadcrumbsEnabled());
        }
        if (!saved) {
            return false;
        }
        TelemetryProjectOverride override = overrideStore.load(overrideFile);
        if (override != null) {
            replaceConsentProject(project.withOverride(override));
        }
        return applyRuntimeConsent(project.projectId(), normalized);
    }

    private boolean applyRuntimeConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsent(projectId, TelemetryConsentBridgePayload.snapshotSummary(snapshot));
        }
        if (coordinatorBridge != null) {
            boolean applied = applyCoordinatorRuntimeConsent(coordinatorService, projectId, snapshot);
            if (applied) {
                applyEngineRuntimeConsent(projectId, snapshot);
            }
            return applied;
        }
        return applyEngineRuntimeConsent(projectId, snapshot);
    }

    private boolean applyCoordinatorRuntimeConsent(@Nonnull TelemetryCoordinatorService service,
                                                   @Nonnull String projectId,
                                                   @Nonnull TelemetryConsentSnapshot snapshot) {
        boolean registeredProject = service.findProject(projectId) != null;
        boolean manualReportProject = findManualReportProject(projectId) != null;
        if (!registeredProject && !manualReportProject) {
            return findConsentProject(projectId) != null;
        }
        boolean applied = service.setProjectEnabled(projectId, snapshot.projectEnabled());
        if (registeredProject) {
            applied &= service.setCrashEnabled(projectId, snapshot.crashEnabled());
            applied &= service.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            applied &= service.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            applied &= service.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            applied &= service.setUsageEnabled(projectId, snapshot.usageEnabled());
            applied &= service.setStatsEnabled(projectId, snapshot.statsEnabled());
            applied &= service.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
        }
        return applied;
    }

    private boolean applyEngineRuntimeConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        boolean registeredProject = findProject(projectId) != null;
        boolean manualReportProject = findManualReportProject(projectId) != null;
        if (!registeredProject && !manualReportProject) {
            return findConsentProject(projectId) != null;
        }
        engine.setProjectEnabled(projectId, snapshot.projectEnabled());
        if (registeredProject) {
            engine.setCrashEnabled(projectId, snapshot.crashEnabled());
            engine.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            engine.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            engine.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            engine.setUsageEnabled(projectId, snapshot.usageEnabled());
            engine.setStatsEnabled(projectId, snapshot.statsEnabled());
            engine.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
        }
        return true;
    }

    public boolean markConsentReviewed(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.markConsentReviewed(projectId);
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project != null && consentStateStore.markReviewed(dataPaths.consentStateFile(), project);
    }

    public boolean triggerFlushAsync() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(null);
        }
        return engine.triggerFlushAsync();
    }

    public boolean triggerFlushAsync(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(projectId);
        }
        return engine.triggerFlushAsync(projectId);
    }

    @Override
    public boolean requestFlush(@Nullable String projectId) {
        return triggerFlushAsync(projectId);
    }

    public void recordBreadcrumb(@Nonnull String projectId,
                                 @Nonnull String category,
                                 @Nonnull String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordBreadcrumb(projectId, category, detail);
            return;
        }
        engine.recordBreadcrumb(projectId, category, detail);
    }

    public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureSetupFailure(projectId, throwable);
            return;
        }
        engine.captureSetupFailure(projectId, throwable);
    }

    public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureStartFailure(projectId, throwable);
            return;
        }
        engine.captureStartFailure(projectId, throwable);
    }

    public void recordError(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable Throwable throwable,
                            @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(projectId, eventName, throwable, detailsFrom(detail));
            return;
        }
        engine.recordError(projectId, eventName, throwable, detail);
    }

    public void recordErrorWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable Throwable throwable,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(projectId, eventName, throwable, detailsFrom(context));
            return;
        }
        engine.recordErrorWithContext(projectId, eventName, throwable, context);
    }

    public void recordLifecycle(@Nonnull String projectId,
                                @Nonnull String eventName,
                                int durationMs,
                                boolean success,
                                @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(projectId, eventName, durationMs, success, detailsFrom(detail));
            return;
        }
        engine.recordLifecycle(projectId, eventName, durationMs, success, detail);
    }

    public void recordLifecycleWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           int durationMs,
                                           boolean success,
                                           @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(projectId, eventName, durationMs, success, detailsFrom(context));
            return;
        }
        engine.recordLifecycleWithContext(projectId, eventName, durationMs, success, context);
    }

    public void recordPerformance(@Nonnull String projectId,
                                  @Nonnull String eventName,
                                  int durationMs,
                                  @Nullable Double metricValue,
                                  @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(projectId, eventName, durationMs, metricValue, detailsFrom(detail));
            return;
        }
        engine.recordPerformance(projectId, eventName, durationMs, metricValue, detail);
    }

    public void recordPerformanceWithContext(@Nonnull String projectId,
                                             @Nonnull String eventName,
                                             int durationMs,
                                             @Nullable Double metricValue,
                                             @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(projectId, eventName, durationMs, metricValue, detailsFrom(context));
            return;
        }
        engine.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, context);
    }

    public void recordUsage(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(projectId, eventName, detailsFrom(detail));
            return;
        }
        engine.recordUsage(projectId, eventName, detail);
    }

    public void recordUsageWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(projectId, eventName, detailsFrom(context));
            return;
        }
        engine.recordUsageWithContext(projectId, eventName, context);
    }

    public void recordStats(@Nonnull String projectId,
                            @Nonnull String eventName,
                            @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(projectId, eventName, detailsFrom(detail));
            return;
        }
        engine.recordStats(projectId, eventName, detail);
    }

    public void recordStatsWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(projectId, eventName, detailsFrom(context));
            return;
        }
        engine.recordStatsWithContext(projectId, eventName, context);
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.captureTestReport(projectId, detail);
        }
        return engine.captureTestReport(projectId, detail);
    }

    public void captureExceptionalWorldRemoval(@Nullable World world,
                                               @Nullable RemoveWorldEvent.RemovalReason removalReason) {
        if (world == null || removalReason != RemoveWorldEvent.RemovalReason.EXCEPTIONAL || world.getFailureException() == null) {
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureExceptionalWorldRemoval(
                    world.getFailureException(),
                    world.getName(),
                    removalReason.name(),
                    world.getPossibleFailureCause() == null ? null : world.getPossibleFailureCause().toString()
            );
            return;
        }
        engine.captureExceptionalWorldRemoval(world, removalReason);
    }

    @Nonnull
    public ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                                @Nonnull ManualReportSubmission submission,
                                                                @Nullable PlayerReportRuntimeContext playerContext) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return manualReportResultFromMap(active.submitManualReport(
                    projectId,
                    submissionToMap(submission),
                    playerContextToMap(playerContext)
            ));
        }
        return engine.submitManualReport(projectId, submission, playerContext);
    }

    @Nonnull
    public PlayerReportRuntimeContext currentPlayerReportRuntimeContext() {
        return new PlayerReportRuntimeContext(
                false,
                0,
                null,
                loadedModSnapshotProvider.snapshotLoadedMods()
        );
    }

    @Nonnull
    public TelemetryRuntimeSettings.ManualReportSettings manualReportSettings() {
        return settings.manualReports();
    }

    @Override
    public boolean openReportPage(@Nonnull String projectId,
                                  @Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull TelemetryReportOpenRequest request) {
        return new TelemetryReportCoordinator(this, logger)
                .openReportPage(projectId, playerEntityRef, store, playerRef, request);
    }

    @Nonnull
    public List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject) {
        return engine.manualReportsForReview(maxReportsPerProject);
    }

    public boolean approveManualReport(@Nonnull String reportId) {
        return engine.approveManualReport(reportId);
    }

    public boolean rejectManualReport(@Nonnull String reportId) {
        return engine.rejectManualReport(reportId);
    }

    @Nonnull
    public List<String> submittedManualReportAuditLines(int maxLines) {
        return engine.submittedManualReportAuditLines(maxLines);
    }

    @Nonnull
    public TelemetryRuntimeDiagnostics diagnostics() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentDiagnostics();
            return activeDiagnostics.isEmpty()
                    ? unavailableConsentDiagnostics(active)
                    : TelemetryConsentBridgePayload.diagnosticsFromSummary(activeDiagnostics);
        }
        if (coordinatorBridge != null && active != null) {
            return coordinatorDiagnostics();
        }
        return engineDiagnostics();
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics engineDiagnostics() {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            projectDiagnostics.add(buildEngineProjectDiagnostics(project));
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

    @Nonnull
    private TelemetryRuntimeDiagnostics coordinatorDiagnostics() {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            projectDiagnostics.add(buildCoordinatorProjectDiagnostics(project));
        }
        return new TelemetryRuntimeDiagnostics(
                coordinatorService.isEnabled(),
                consentProjects.size(),
                coordinatorService.loadedMods().size(),
                coordinatorService.pendingReports(null),
                coordinatorService.flushInProgress(),
                coordinatorService.lastFlushResult(),
                dataPaths.modsDirectory() == null ? null : dataPaths.modsDirectory().toString(),
                registrationWarnings,
                List.copyOf(projectDiagnostics)
        );
    }

    @Nonnull
    @Override
    public TelemetryRuntimeDiagnostics consentDiagnostics() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentDiagnostics();
            return activeDiagnostics.isEmpty()
                    ? unavailableConsentDiagnostics(active)
                    : TelemetryConsentBridgePayload.diagnosticsFromSummary(activeDiagnostics);
        }
        return diagnostics();
    }

    @Nullable
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(projectId);
            return activeDiagnostics.isEmpty()
                    ? null
                    : TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(activeDiagnostics);
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        if (project == null) {
            return null;
        }
        return coordinatorBridge != null && active != null
                ? buildCoordinatorProjectDiagnostics(project)
                : buildEngineProjectDiagnostics(project);
    }

    @Nullable
    @Override
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics consentProjectDiagnostics(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(projectId);
            return activeDiagnostics.isEmpty()
                    ? null
                    : TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(activeDiagnostics);
        }
        return projectDiagnostics(projectId);
    }

    public int pendingReports(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            if (projectId == null) {
                return diagnostics().totalPendingReports();
            }
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = projectDiagnostics(projectId);
            return diagnostics == null ? 0 : diagnostics.pendingReports();
        }
        if (coordinatorBridge != null && active != null) {
            return coordinatorService.pendingReports(projectId);
        }
        return engine.pendingReports(projectId);
    }

    @Nonnull
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        return engine.manualReportReceiptStatus(reportId);
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            TelemetryProjectRegistration project = projectFromSummary(active.findProjectSummary(projectId));
            return project == null ? projectFromSummary(active.consentProjectDiagnostics(projectId)) : project;
        }
        if (coordinatorBridge != null && active != null) {
            TelemetryProjectRegistration project = coordinatorService.findProject(projectId);
            return project == null ? findConsentProject(projectId) : project;
        }
        return engine.findProject(projectId);
    }

    @Nullable
    public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
        return engine.findManualReportProject(projectId);
    }

    @Nonnull
    public List<TelemetryProjectRegistration> manualReportProjects() {
        ArrayList<TelemetryProjectRegistration> reportProjects = new ArrayList<>();
        for (TelemetryProjectRegistration project : engine.manualReportProjects()) {
            if (project.descriptor().reports().enabled()) {
                reportProjects.add(project);
            }
        }
        return List.copyOf(reportProjects);
    }

    @Nonnull
    FlushSummary flushPendingReportsNow(@Nonnull String reason) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            int attempted = pendingReports(null);
            boolean scheduled = active.requestFlush(null);
            return new FlushSummary(scheduled ? attempted : 0, 0, pendingReports(null), scheduled ? null : "active coordinator rejected flush");
        }
        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow(reason);
        return new FlushSummary(summary.attempted(), summary.uploaded(), summary.pendingAfter(), summary.lastFailure());
    }

    @Nonnull
    FlushSummary flushPendingReportsNow(@Nonnull String reason, @Nullable String projectIdFilter) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            int attempted = pendingReports(projectIdFilter);
            boolean scheduled = active.requestFlush(projectIdFilter);
            return new FlushSummary(scheduled ? attempted : 0, 0, pendingReports(projectIdFilter), scheduled ? null : "active coordinator rejected flush");
        }
        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow(reason, projectIdFilter);
        return new FlushSummary(summary.attempted(), summary.uploaded(), summary.pendingAfter(), summary.lastFailure());
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics buildEngineProjectDiagnostics(
            @Nonnull TelemetryProjectRegistration project) {
        CrashReportClient.DeliveryTarget target = project.resolveDeliveryTarget(settings);
        boolean registeredForStandaloneRuntime = engine.findProject(project.projectId()) != null;
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
                registeredForStandaloneRuntime ? engine.isStatsEnabled(project.projectId()) : project.stats().enabled(),
                registeredForStandaloneRuntime ? engine.isBreadcrumbsEnabled(project.projectId()) : project.events().breadcrumbs().enabled(),
                supportsCrash(project),
                project.descriptor().events().errors().enabled(),
                project.descriptor().events().lifecycle().enabled(),
                project.descriptor().performance().enabled(),
                project.descriptor().usage().enabled(),
                project.descriptor().stats().enabled(),
                project.descriptor().events().breadcrumbs().enabled()
        );
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics buildCoordinatorProjectDiagnostics(
            @Nonnull TelemetryProjectRegistration project) {
        CrashReportClient.DeliveryTarget target = project.resolveDeliveryTarget(settings);
        boolean registeredForStandaloneRuntime = coordinatorService.findProject(project.projectId()) != null;
        int pendingReports = registeredForStandaloneRuntime ? coordinatorService.pendingReports(project.projectId()) : 0;
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
                registeredForStandaloneRuntime ? coordinatorService.isCrashEnabled(project.projectId()) : project.isCrashTelemetryEnabled(),
                registeredForStandaloneRuntime ? coordinatorService.isErrorEventsEnabled(project.projectId()) : project.events().errors().enabled(),
                registeredForStandaloneRuntime ? coordinatorService.isLifecycleEventsEnabled(project.projectId()) : project.events().lifecycle().enabled(),
                registeredForStandaloneRuntime ? coordinatorService.isPerformanceEnabled(project.projectId()) : project.performance().enabled(),
                registeredForStandaloneRuntime ? coordinatorService.isUsageEnabled(project.projectId()) : project.usage().enabled(),
                registeredForStandaloneRuntime ? coordinatorService.isStatsEnabled(project.projectId()) : project.stats().enabled(),
                registeredForStandaloneRuntime ? coordinatorService.isBreadcrumbsEnabled(project.projectId()) : project.events().breadcrumbs().enabled(),
                supportsCrash(project),
                project.descriptor().events().errors().enabled(),
                project.descriptor().events().lifecycle().enabled(),
                project.descriptor().performance().enabled(),
                project.descriptor().usage().enabled(),
                project.descriptor().stats().enabled(),
                project.descriptor().events().breadcrumbs().enabled()
        );
    }

    @Nonnull
    private static TelemetryConsentSnapshot supportedSnapshot(@Nonnull TelemetryProjectRegistration project) {
        return new TelemetryConsentSnapshot(
                true,
                supportsCrash(project),
                project.descriptor().events().errors().enabled(),
                project.descriptor().events().lifecycle().enabled(),
                project.descriptor().performance().enabled(),
                project.descriptor().usage().enabled(),
                project.descriptor().stats().enabled(),
                project.descriptor().events().breadcrumbs().enabled()
        );
    }

    private static boolean supportsCrash(@Nonnull TelemetryProjectRegistration project) {
        TelemetryProjectDescriptor.CaptureOptions capture = project.descriptor().capture();
        return capture.uncaughtExceptions()
                || capture.setupFailures()
                || capture.startFailures()
                || capture.exceptionalWorldRemovals();
    }

    @Nonnull
    private static TelemetryConsentSnapshot clampToSupported(@Nonnull TelemetryConsentSnapshot snapshot,
                                                             @Nonnull TelemetryConsentSnapshot supported) {
        return new TelemetryConsentSnapshot(
                snapshot.projectEnabled(),
                supported.crashEnabled() && snapshot.crashEnabled(),
                supported.errorEnabled() && snapshot.errorEnabled(),
                supported.lifecycleEnabled() && snapshot.lifecycleEnabled(),
                supported.performanceEnabled() && snapshot.performanceEnabled(),
                supported.usageEnabled() && snapshot.usageEnabled(),
                supported.statsEnabled() && snapshot.statsEnabled(),
                supported.breadcrumbsEnabled() && snapshot.breadcrumbsEnabled()
        );
    }

    @Nonnull
    static List<TelemetryProjectRegistration> filterRegistrationsToLoadedMods(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        return TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(projects, loadedMods);
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

    private boolean usesLocalCoordinator(@Nullable TelemetryCoordinatorBridge active) {
        return active == null || (coordinatorBridge != null && coordinatorBridge.providerId().equals(active.providerId()));
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> mergedProjects(
            @Nonnull List<TelemetryProjectRegistration> runtimeProjects,
            @Nonnull List<TelemetryProjectRegistration> consentProjects) {
        LinkedHashMap<String, TelemetryProjectRegistration> projects = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : runtimeProjects) {
            projects.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : consentProjects) {
            projects.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        return List.copyOf(projects.values());
    }

    @Nonnull
    private static TelemetryRuntimeDiagnostics unavailableConsentDiagnostics(@Nonnull TelemetryCoordinatorBridge active) {
        return new TelemetryRuntimeDiagnostics(
                false,
                0,
                0,
                0,
                false,
                "<unavailable>",
                null,
                List.of("Active telemetry coordinator " + active.providerId() + " does not expose consent diagnostics."),
                List.of()
        );
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

    @Nullable
    private static TelemetryProjectRegistration projectFromSummary(@Nonnull Map<String, Object> summary) {
        String projectId = stringValue(summary.get("projectId"));
        if (projectId == null) {
            return null;
        }
        String displayName = firstNonBlank(stringValue(summary.get("displayName")), projectId);
        String runtimeMode = firstNonBlank(
                stringValue(summary.get("runtimeMode")),
                TelemetryProjectDescriptor.RUNTIME_MODE_DEPENDENCY
        );
        String pluginIdentifier = firstNonBlank(stringValue(summary.get("pluginIdentifier")), "unknown:unknown");
        String pluginVersion = firstNonBlank(stringValue(summary.get("pluginVersion")), "unknown");
        return new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        "{\"projectId\":\"" + escapeJson(projectId)
                                + "\",\"displayName\":\"" + escapeJson(displayName)
                                + "\",\"runtimeMode\":\"" + escapeJson(runtimeMode) + "\"}",
                        null
                ),
                pluginIdentifier,
                pluginVersion,
                pathValue(summary.get("sourcePath"))
        );
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> projectsFromSummaries(@Nonnull List<Map<String, Object>> summaries) {
        if (summaries.isEmpty()) {
            return List.of();
        }
        ArrayList<TelemetryProjectRegistration> projects = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            TelemetryProjectRegistration project = projectFromSummary(summary);
            if (project != null) {
                projects.add(project);
            }
        }
        return List.copyOf(projects);
    }

    @Nonnull
    private static List<Map<String, Object>> activeProjects(@Nonnull TelemetryCoordinatorBridge active) {
        LinkedHashMap<String, Map<String, Object>> projects = new LinkedHashMap<>();
        for (Map<String, Object> summary : active.projectSummaries()) {
            putProjectSummary(projects, summary);
        }
        Object rawProjects = active.consentDiagnostics().get("projects");
        if (rawProjects instanceof List<?> rawList) {
            for (Object rawProject : rawList) {
                if (rawProject instanceof Map<?, ?> project) {
                    putProjectSummary(projects, stringObjectMap(project));
                }
            }
        }
        return List.copyOf(projects.values());
    }

    private static void putProjectSummary(@Nonnull LinkedHashMap<String, Map<String, Object>> projects,
                                          @Nonnull Map<String, Object> summary) {
        String projectId = stringValue(summary.get("projectId"));
        if (projectId != null) {
            projects.putIfAbsent(projectId.toLowerCase(Locale.ROOT), summary);
        }
    }

    private static boolean activeProjectEnabled(@Nonnull TelemetryCoordinatorBridge active,
                                                @Nonnull String projectId) {
        if (!active.findProjectSummary(projectId).isEmpty()) {
            return active.isProjectEnabled(projectId);
        }
        Map<String, Object> diagnostics = active.consentProjectDiagnostics(projectId);
        return diagnostics.isEmpty()
                ? active.isProjectEnabled(projectId)
                : TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(diagnostics).enabled();
    }

    @Nonnull
    private static Map<String, Object> projectSummary(@Nonnull TelemetryProjectRegistration project) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", project.projectId());
        summary.put("displayName", project.displayName());
        summary.put("runtimeMode", project.runtimeMode());
        summary.put("pluginIdentifier", project.pluginIdentifier());
        summary.put("pluginVersion", project.pluginVersion());
        if (project.sourcePath() != null) {
            summary.put("sourcePath", project.sourcePath().toString());
        }
        return Map.copyOf(summary);
    }

    @Nonnull
    private static String firstNonBlank(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Nonnull
    private static Map<String, Object> stringObjectMap(@Nonnull Map<?, ?> source) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            values.put(entry.getKey().toString(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    @Nullable
    private static Path pathValue(@Nullable Object value) {
        String normalized = stringValue(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Path.of(normalized);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String escapeJson(@Nonnull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * One flush pass summary.
     */
    record FlushSummary(int attempted, int uploaded, int pendingAfter, @Nullable String lastFailure) {
    }

    @Nonnull
    private static TelemetryRuntimeCandidate standaloneCandidate(@Nonnull TelemetryDataPaths dataPaths) {
        return new TelemetryRuntimeCandidate(
                "standalone:Alechilles:Alec's Telemetry",
                TelemetryRuntimeOrigin.STANDALONE,
                resolveRuntimeVersion(),
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Alechilles:Alec's Telemetry",
                resolveRuntimeVersion(),
                dataPaths.runtimeRoot(),
                dataPaths.runtimeRoot()
        );
    }

    @Nonnull
    private static TelemetryRuntimeCandidate standaloneCandidate(@Nonnull JavaPlugin plugin,
                                                                @Nonnull TelemetryDataPaths dataPaths) {
        String pluginIdentifier = plugin.getIdentifier() == null ? "Alechilles:Alec's Telemetry" : plugin.getIdentifier().toString();
        String pluginVersion = plugin.getManifest() == null || plugin.getManifest().getVersion() == null
                ? resolveRuntimeVersion()
                : plugin.getManifest().getVersion().toString();
        Path sourcePath;
        try {
            sourcePath = plugin.getFile() == null ? dataPaths.runtimeRoot() : plugin.getFile().toAbsolutePath().normalize();
        } catch (Exception ignored) {
            sourcePath = dataPaths.runtimeRoot();
        }
        return new TelemetryRuntimeCandidate(
                "standalone:" + pluginIdentifier,
                TelemetryRuntimeOrigin.STANDALONE,
                resolveRuntimeVersion(),
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                pluginIdentifier,
                pluginVersion,
                sourcePath,
                dataPaths.runtimeRoot()
        );
    }

    @Nonnull
    private static String resolveRuntimeVersion() {
        Package runtimePackage = TelemetryRuntimeService.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_RUNTIME_VERSION
                : implementationVersion.trim();
    }

    @Nonnull
    private static Map<String, Object> detailsFrom(@Nullable String detail) {
        return detail == null || detail.isBlank() ? Map.of() : Map.of("detail", detail);
    }

    @Nonnull
    private static Map<String, Object> detailsFrom(@Nullable TelemetryEventContext context) {
        if (context == null) {
            return Map.of();
        }
        TelemetryEventContext normalized = context.normalize();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(normalized.details());
        putIfPresent(details, "detail", normalized.detail());
        putIfPresent(details, "severity", normalized.severity());
        putIfPresent(details, "fingerprint", normalized.fingerprint());
        putIfPresent(details, "subsystem", normalized.subsystem());
        putIfPresent(details, "phase", normalized.phase());
        putIfPresent(details, "operation", normalized.operation());
        putIfPresent(details, "target", normalized.target());
        putIfPresent(details, "featureKey", normalized.featureKey());
        putIfPresent(details, "entryPoint", normalized.entryPoint());
        putIfPresent(details, "runtimeSide", normalized.runtimeSide());
        putIfPresent(details, "entityType", normalized.entityType());
        putIfPresent(details, "itemId", normalized.itemId());
        putIfPresent(details, "blockId", normalized.blockId());
        putIfPresent(details, "biomeId", normalized.biomeId());
        putIfPresent(details, "commandName", normalized.commandName());
        putIfPresent(details, "worldName", normalized.worldName());
        return Map.copyOf(details);
    }

    private static void putIfPresent(@Nonnull Map<String, Object> details,
                                     @Nonnull String key,
                                     @Nullable String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value);
        }
    }

    @Nonnull
    private static Map<String, Object> submissionToMap(@Nonnull ManualReportSubmission submission) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("kind", submission.kind().key());
        values.put("title", submission.title());
        values.put("description", submission.description());
        putIfPresent(values, "contact", submission.contact());
        values.put("formValues", submission.formValues());
        values.put("includeCurrentServerLog", submission.includeCurrentServerLog());
        values.put("includePreviousServerLog", submission.includePreviousServerLog());
        values.put("includeLoadedModList", submission.includeLoadedModList());
        values.put("includeDiagnostics", submission.includeDiagnostics());
        values.put("allowResolutionUpdates", submission.allowResolutionUpdates());
        return Map.copyOf(values);
    }

    @Nonnull
    private static Map<String, Object> playerContextToMap(@Nullable PlayerReportRuntimeContext playerContext) {
        PlayerReportRuntimeContext safeContext = playerContext == null ? PlayerReportRuntimeContext.UNKNOWN : playerContext;
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("singleplayer", safeContext.singleplayer());
        values.put("onlinePlayerCount", safeContext.onlinePlayerCount());
        putIfPresent(values, "worldName", safeContext.worldName());
        values.put("loadedMods", safeContext.loadedMods().stream()
                .map(TelemetryRuntimeService::loadedModToMap)
                .toList());
        return Map.copyOf(values);
    }

    @Nonnull
    private static Map<String, Object> loadedModToMap(@Nonnull CrashReportEnvelope.LoadedModMetadata loadedMod) {
        return Map.of(
                "identifier", loadedMod.identifier(),
                "version", loadedMod.version()
        );
    }

    @Nonnull
    private static ManualReportEnvelope.CreateResult manualReportResultFromMap(@Nonnull Map<String, Object> values) {
        List<String> errors = stringList(values.get("validationErrors"));
        ManualReportEnvelope envelope = envelopeFrom(values.get("envelopeJson"));
        String followUpToken = stringValue(values.get("followUpToken"));
        if (Boolean.TRUE.equals(values.get("accepted")) && envelope == null && errors.isEmpty()) {
            errors = List.of("coordinator_manual_report_missing_envelope");
        }
        return new ManualReportEnvelope.CreateResult(envelope, errors, followUpToken);
    }

    @Nullable
    private static ManualReportEnvelope envelopeFrom(@Nullable Object value) {
        String rawJson = stringValue(value);
        if (rawJson == null) {
            return null;
        }
        try {
            return ManualReportEnvelope.fromJson(rawJson);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isBlank() ? null : normalized;
    }

    @Nonnull
    private static List<String> stringList(@Nullable Object value) {
        if (!(value instanceof List<?> rawValues) || rawValues.isEmpty()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (Object rawValue : rawValues) {
            String normalized = stringValue(rawValue);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private final class StandaloneCoordinatorBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final TelemetryCoordinatorService service;
        private final AtomicBoolean active = new AtomicBoolean(false);

        private StandaloneCoordinatorBridge(@Nonnull TelemetryRuntimeCandidate candidate,
                                            @Nonnull TelemetryCoordinatorService service) {
            this.candidate = candidate;
            this.service = service;
        }

        @Override
        public String providerId() {
            return candidate.providerId();
        }

        @Override
        public String origin() {
            return candidate.origin().name();
        }

        @Override
        public String runtimeVersion() {
            return candidate.runtimeVersion();
        }

        @Override
        public int coordinatorProtocolVersion() {
            return candidate.coordinatorProtocolVersion();
        }

        @Override
        public String providerPluginIdentifier() {
            return candidate.providerPluginIdentifier();
        }

        @Override
        public String providerPluginVersion() {
            return candidate.providerPluginVersion();
        }

        @Override
        public String sourcePath() {
            return candidate.sourcePath().toString();
        }

        @Override
        public String sharedDataRoot() {
            return candidate.sharedDataRoot().toString();
        }

        @Override
        public void activate() {
            active.set(true);
        }

        @Override
        public void deactivate() {
            active.set(false);
            stopStatsHeartbeat();
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void start() {
            service.start();
            startStatsHeartbeat();
        }

        @Override
        public void shutdown() {
            stopStatsHeartbeat();
            service.shutdown();
        }

        @Override
        public boolean isEnabled() {
            return service.isEnabled();
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> projectSummaries() {
            ArrayList<Map<String, Object>> projects = new ArrayList<>(service.projects().size());
            for (TelemetryProjectRegistration project : service.projects()) {
                projects.add(projectSummary(project));
            }
            return List.copyOf(projects);
        }

        @Nonnull
        @Override
        public Map<String, Object> findProjectSummary(@Nonnull String projectId) {
            TelemetryProjectRegistration project = service.findProject(projectId);
            return project == null ? Map.of() : projectSummary(project);
        }

        @Override
        public boolean isProjectEnabled(@Nonnull String projectId) {
            return service.isProjectEnabled(projectId);
        }

        @Override
        public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setProjectEnabled(projectId, enabled);
        }

        @Override
        public boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setCrashEnabled(projectId, enabled);
        }

        @Override
        public boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setErrorEventsEnabled(projectId, enabled);
        }

        @Override
        public boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setLifecycleEventsEnabled(projectId, enabled);
        }

        @Override
        public boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setPerformanceEnabled(projectId, enabled);
        }

        @Override
        public boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setUsageEnabled(projectId, enabled);
        }

        @Override
        public boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setStatsEnabled(projectId, enabled);
        }

        @Override
        public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
            return service.setBreadcrumbsEnabled(projectId, enabled);
        }

        @Nonnull
        @Override
        public Map<String, Object> consentDiagnostics() {
            return TelemetryConsentBridgePayload.diagnosticsSummary(TelemetryRuntimeService.this.coordinatorDiagnostics());
        }

        @Nonnull
        @Override
        public Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
            TelemetryProjectRegistration project = findConsentProject(projectId);
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = project == null
                    ? null
                    : buildCoordinatorProjectDiagnostics(project);
            return diagnostics == null ? Map.of() : TelemetryConsentBridgePayload.projectDiagnosticsSummary(diagnostics);
        }

        @Override
        public boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
            return TelemetryRuntimeService.this.applyLocalConsentToAll(TelemetryConsentBridgePayload.snapshotFromSummary(snapshot));
        }

        @Override
        public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
            return TelemetryRuntimeService.this.applyLocalConsentCategoryToAll(category, enabled);
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
            return TelemetryRuntimeService.this.applyLocalConsent(
                    projectId,
                    TelemetryConsentBridgePayload.snapshotFromSummary(snapshot)
            );
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            TelemetryProjectRegistration project = findConsentProject(projectId);
            return project != null && consentStateStore.markReviewed(dataPaths.consentStateFile(), project);
        }

        @Override
        public boolean recordBreadcrumb(@Nonnull String projectId,
                                        @Nonnull String category,
                                        @Nonnull String detail) {
            return service.recordBreadcrumb(projectId, category, detail);
        }

        @Override
        public boolean captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            return service.captureSetupFailure(projectId, throwable);
        }

        @Override
        public boolean captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            return service.captureStartFailure(projectId, throwable);
        }

        @Override
        public boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable,
                                                      @Nullable String worldName,
                                                      @Nullable String removalReason,
                                                      @Nullable String possibleFailureCause) {
            return service.captureExceptionalWorldRemoval(throwable, worldName, removalReason, possibleFailureCause);
        }

        @Override
        public boolean recordError(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nullable Throwable throwable,
                                   @Nonnull Map<String, Object> details) {
            return service.recordError(projectId, eventName, throwable, details);
        }

        @Override
        public boolean recordLifecycle(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       int durationMs,
                                       boolean success,
                                       @Nonnull Map<String, Object> details) {
            return service.recordLifecycle(projectId, eventName, durationMs, success, details);
        }

        @Override
        public boolean recordPerformance(@Nonnull String projectId,
                                         @Nonnull String eventName,
                                         int durationMs,
                                         @Nullable Double metricValue,
                                         @Nonnull Map<String, Object> details) {
            return service.recordPerformance(projectId, eventName, durationMs, metricValue, details);
        }

        @Override
        public boolean recordUsage(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            return service.recordUsage(projectId, eventName, details);
        }

        @Override
        public boolean recordStats(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            return service.recordStats(projectId, eventName, details);
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            return service.requestFlush(projectId);
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return service.captureTestReport(projectId, detail);
        }

        @Override
        public Map<String, Object> submitManualReport(@Nonnull String projectId,
                                                      @Nonnull Map<String, Object> submission,
                                                      @Nonnull Map<String, Object> playerContext) {
            return service.submitManualReport(projectId, submission, playerContext);
        }
    }
}
