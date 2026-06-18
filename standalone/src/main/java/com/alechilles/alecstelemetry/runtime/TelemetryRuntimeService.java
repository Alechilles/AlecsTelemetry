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
import java.util.logging.Level;

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
    private List<TelemetryProjectRegistration> consentProjects;

    @Nonnull
    public static TelemetryRuntimeService create(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        TelemetryDataPaths dataPaths = TelemetryDataPaths.from(plugin);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        TelemetryLoadedModSnapshotProvider loadedModSnapshotProvider = TelemetryLoadedModSnapshotProvider.hytalePluginManager(
                discoveryResult.loadedMods(),
                logger
        );
        List<CrashReportEnvelope.LoadedModMetadata> activeLoadedMods = loadedModSnapshotProvider.snapshotLoadedMods();
        List<TelemetryProjectRegistration> activeProjects = filterRegistrationsToLoadedMods(
                discoveryResult.projects(),
                activeLoadedMods
        );
        List<TelemetryProjectRegistration> activeConsentProjects = filterRegistrationsToLoadedMods(
                discoveryResult.consentProjects(),
                activeLoadedMods
        );
        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
                .loadAll(dataPaths.projectSettingsDirectory());
        Map<String, TelemetryProjectOverride> consentOverrides = loadConsentOverrides(
                activeConsentProjects,
                overrides,
                dataPaths,
                logger
        );
        List<TelemetryProjectRegistration> resolvedProjects = applyOverrides(activeProjects, overrides);
        List<TelemetryProjectRegistration> resolvedConsentProjects = applyOverrides(activeConsentProjects, consentOverrides);
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(resolvedProjects);
        List<String> registrationWarnings = buildRegistrationWarnings(collisions, discoveryResult.skippedRegistrationWarnings());
        return new TelemetryRuntimeService(
                settings,
                dataPaths,
                resolvedProjects,
                resolvedConsentProjects,
                activeLoadedMods,
                loadedModSnapshotProvider,
                collisions,
                registrationWarnings,
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
                buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
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
                buildRegistrationWarnings(TelemetryProjectCollisionDetector.detect(projects), List.of()),
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
        logRegistrationWarnings();
    }

    public void start() {
        TelemetryCoordinatorRegistry.register(coordinatorBridge);
    }

    public void shutdown() {
        TelemetryCoordinatorRegistry.unregister(candidate.providerId());
    }

    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && active.providerId().equals(candidate.providerId());
    }

    @Nullable
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
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
            appliedAll &= applyConsent(project.projectId(), clampToSupported(snapshot, supportedSnapshot(project)));
        }
        return appliedAll;
    }

    public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
        boolean appliedAll = true;
        for (TelemetryProjectRegistration project : consentProjects) {
            TelemetryConsentSnapshot supported = supportedSnapshot(project);
            if (!supported.categoryEnabled(category)) {
                continue;
            }
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = buildProjectDiagnostics(project);
            TelemetryConsentSnapshot current = diagnostics.consentSnapshot();
            appliedAll &= applyConsent(project.projectId(), current.withCategory(category, enabled));
        }
        return appliedAll;
    }

    public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
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
        applyRuntimeConsent(project.projectId(), normalized);
        return true;
    }

    private void applyRuntimeConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.setProjectEnabled(projectId, snapshot.projectEnabled());
            active.setCrashEnabled(projectId, snapshot.crashEnabled());
            active.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            active.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            active.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            active.setUsageEnabled(projectId, snapshot.usageEnabled());
            active.setStatsEnabled(projectId, snapshot.statsEnabled());
            active.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
            return;
        }
        if (findProject(projectId) != null || findManualReportProject(projectId) != null) {
            engine.setProjectEnabled(projectId, snapshot.projectEnabled());
        }
        if (findProject(projectId) != null) {
            engine.setCrashEnabled(projectId, snapshot.crashEnabled());
            engine.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            engine.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            engine.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            engine.setUsageEnabled(projectId, snapshot.usageEnabled());
            engine.setStatsEnabled(projectId, snapshot.statsEnabled());
            engine.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
        }
    }

    public boolean markConsentReviewed(@Nonnull String projectId) {
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

    @Nonnull
    @Override
    public TelemetryRuntimeDiagnostics consentDiagnostics() {
        return diagnostics();
    }

    @Nullable
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project == null ? null : buildProjectDiagnostics(project);
    }

    @Nullable
    @Override
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics consentProjectDiagnostics(@Nonnull String projectId) {
        return projectDiagnostics(projectId);
    }

    public int pendingReports(@Nullable String projectId) {
        return engine.pendingReports(projectId);
    }

    @Nonnull
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        return engine.manualReportReceiptStatus(reportId);
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
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
    static List<TelemetryProjectRegistration> filterRegistrationsToLoadedMods(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        if (projects.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> loadedIdentifiers = new LinkedHashMap<>();
        for (CrashReportEnvelope.LoadedModMetadata loadedMod : loadedMods) {
            if (loadedMod == null) {
                continue;
            }
            addIdentifierVariants(loadedIdentifiers, loadedMod.identifier());
        }
        if (loadedIdentifiers.isEmpty()) {
            return List.of();
        }

        ArrayList<TelemetryProjectRegistration> filtered = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            if (matchesLoadedMod(project, loadedIdentifiers)) {
                filtered.add(project);
            }
        }
        return List.copyOf(filtered);
    }

    private static boolean matchesLoadedMod(@Nonnull TelemetryProjectRegistration project,
                                            @Nonnull Map<String, Boolean> loadedIdentifiers) {
        if (containsIdentifierVariant(loadedIdentifiers, project.pluginIdentifier())) {
            return true;
        }
        if (containsIdentifierVariant(loadedIdentifiers, project.displayName())) {
            return true;
        }
        for (String ownerPluginIdentifier : project.ownerPluginIdentifiers()) {
            if (containsIdentifierVariant(loadedIdentifiers, ownerPluginIdentifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifierVariant(@Nonnull Map<String, Boolean> loadedIdentifiers,
                                                     @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        LinkedHashMap<String, Boolean> variants = new LinkedHashMap<>();
        addIdentifierVariants(variants, identifier);
        for (String variant : variants.keySet()) {
            if (loadedIdentifiers.containsKey(variant)) {
                return true;
            }
        }
        return false;
    }

    private static void addIdentifierVariants(@Nonnull Map<String, Boolean> identifiers,
                                              @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String normalized = identifier.trim();
        identifiers.put(normalized.toLowerCase(Locale.ROOT), true);
        int separator = normalized.indexOf(':');
        if (separator >= 0 && separator + 1 < normalized.length()) {
            identifiers.put(normalized.substring(separator + 1).trim().toLowerCase(Locale.ROOT), true);
        }
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

    private static final class StandaloneCoordinatorBridge implements TelemetryCoordinatorBridge {
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
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void start() {
            service.start();
        }

        @Override
        public void shutdown() {
            service.shutdown();
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
