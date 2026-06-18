package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentRuntime;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

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
 * Embedded telemetry runtime for one owning consumer mod.
 */
public final class EmbeddedTelemetryService implements EmbeddedTelemetryHandle, TelemetryConsentRuntime {

    private final TelemetryProjectRegistration project;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryProjectOverrideStore overrideStore;
    private final TelemetryRuntimeSettings settings;
    private final TelemetryCoreEngine engine;
    private final EmbeddedTelemetryStatsHeartbeat statsHeartbeat;
    private final EmbeddedCoordinatorBridge coordinatorBridge;
    private final HytaleLogger logger;
    private final String disabledReason;
    private final TelemetryConsentStateStore consentStateStore;
    private List<TelemetryProjectRegistration> consentProjects;

    EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                             @Nonnull TelemetryDataPaths dataPaths,
                             @Nonnull TelemetryProjectRegistration project,
                             @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                             @Nonnull CrashReportClient client,
                             @Nullable HytaleLogger logger,
                             @Nullable ScheduledExecutorService executor) {
        this(settings, dataPaths, project, loadedMods, client, logger, executor, new EmbeddedTelemetryPlayerCounter());
    }

    EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                             @Nonnull TelemetryDataPaths dataPaths,
                             @Nonnull TelemetryProjectRegistration project,
                             @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                             @Nonnull CrashReportClient client,
                             @Nullable HytaleLogger logger,
                             @Nullable ScheduledExecutorService executor,
                             @Nonnull EmbeddedTelemetryPlayerCounter playerCounter) {
        this(settings, dataPaths, project, loadedMods, client, logger, executor, playerCounter, null, null);
    }

    EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                             @Nonnull TelemetryDataPaths dataPaths,
                             @Nonnull TelemetryProjectRegistration project,
                             @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                             @Nonnull CrashReportClient client,
                             @Nullable HytaleLogger logger,
                             @Nullable ScheduledExecutorService executor,
                             @Nonnull EmbeddedTelemetryPlayerCounter playerCounter,
                             @Nullable TelemetryRuntimeCandidate candidate,
                             @Nullable TelemetryCoordinatorService coordinatorService) {
        this.project = project;
        this.dataPaths = dataPaths;
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.settings = settings;
        this.engine = coordinatorService == null
                ? new TelemetryCoreEngine(settings, dataPaths, List.of(project), loadedMods, client, logger, executor)
                : null;
        this.statsHeartbeat = engine == null
                ? null
                : new EmbeddedTelemetryStatsHeartbeat(project.projectId(), engine, playerCounter, executor, logger);
        this.coordinatorBridge = candidate == null || coordinatorService == null
                ? null
                : new EmbeddedCoordinatorBridge(candidate, coordinatorService);
        this.logger = logger;
        this.disabledReason = null;
        this.consentProjects = coordinatorService == null ? List.of(project) : consentProjects(coordinatorService);
    }

    private EmbeddedTelemetryService(@Nonnull String projectId,
                                     @Nonnull String displayName,
                                     @Nullable HytaleLogger logger,
                                     @Nonnull String disabledReason) {
        this.project = nullRegistration(projectId, displayName);
        this.dataPaths = null;
        this.overrideStore = null;
        this.settings = null;
        this.engine = null;
        this.statsHeartbeat = null;
        this.coordinatorBridge = null;
        this.logger = logger;
        this.disabledReason = disabledReason;
        this.consentStateStore = null;
        this.consentProjects = List.of(this.project);
        if (logger != null) {
            logger.at(Level.INFO).log("Embedded telemetry is disabled: " + disabledReason);
        }
    }

    @Nonnull
    public static EmbeddedTelemetryService disabled(@Nonnull String projectId,
                                                    @Nonnull String displayName,
                                                    @Nullable HytaleLogger logger,
                                                    @Nonnull String disabledReason) {
        return new EmbeddedTelemetryService(projectId, displayName, logger, disabledReason);
    }

    @Nonnull
    @Override
    public String projectId() {
        return project.projectId();
    }

    @Nonnull
    @Override
    public String displayName() {
        return project.displayName();
    }

    @Override
    public boolean isEnabled() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.isProjectEnabled(project.projectId());
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.isProjectEnabled(project.projectId());
        }
        return engine != null && engine.isProjectEnabled(project.projectId());
    }

    @Nullable
    @Override
    public String disabledReason() {
        return disabledReason;
    }

    boolean isDisabled() {
        return disabledReason != null;
    }

    @Nullable
    TelemetryProjectRegistration project() {
        return project;
    }

    @Nullable
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
    }

    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null
                && coordinatorBridge != null
                && active.providerId().equals(coordinatorBridge.providerId());
    }

    @Nonnull
    public List<TelemetryProjectRegistration> commandProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return projectsFromSummaries(activeProjects(active));
        }
        return List.copyOf(consentProjects);
    }

    @Nullable
    public TelemetryProjectRegistration commandProject(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            TelemetryProjectRegistration project = projectFromSummary(active.findProjectSummary(projectId));
            if (project != null) {
                return project;
            }
            return projectFromSummary(active.consentProjectDiagnostics(projectId));
        }
        return findConsentProject(projectId);
    }

    public boolean commandProjectEnabled(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.isProjectEnabled(projectId);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.isProjectEnabled(projectId);
        }
        return project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && isEnabled();
    }

    public int commandPendingReports(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            if (projectId == null) {
                Map<String, Object> activeDiagnostics = active.consentDiagnostics();
                return activeDiagnostics.isEmpty()
                        ? 0
                        : TelemetryConsentBridgePayload.diagnosticsFromSummary(activeDiagnostics).totalPendingReports();
            }
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(projectId);
            return activeDiagnostics.isEmpty()
                    ? 0
                    : TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(activeDiagnostics).pendingReports();
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.pendingReports(projectId);
        }
        if (engine != null) {
            return engine.pendingReports(projectId);
        }
        return 0;
    }

    @Nonnull
    public String commandLastFlushResult() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentDiagnostics();
            if (activeDiagnostics.isEmpty()) {
                return "<unavailable>";
            }
            return TelemetryConsentBridgePayload.diagnosticsFromSummary(activeDiagnostics).lastFlushResult();
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.lastFlushResult();
        }
        if (engine != null) {
            return engine.lastFlushResult();
        }
        return disabledReason == null ? "<unavailable>" : disabledReason;
    }

    public boolean commandFlush(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(projectId);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.requestFlush(projectId);
        }
        return projectId == null ? requestFlush() : project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && requestFlush();
    }

    public boolean commandCaptureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.captureTestReport(projectId, detail);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.captureTestReport(projectId, detail);
        }
        return project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && captureTestReport(detail);
    }

    @Nonnull
    @Override
    public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return List.of();
        }
        TelemetryDataPaths consentPaths = consentDataPaths();
        if (consentStateStore == null || consentPaths == null) {
            return List.of();
        }
        return consentStateStore.unreviewedProjects(consentPaths.consentStateFile(), consentProjects);
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

    @Override
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
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = buildProjectDiagnostics(project);
            TelemetryConsentSnapshot current = diagnostics.consentSnapshot();
            appliedAll &= applyLocalConsent(project.projectId(), current.withCategory(category, enabled));
        }
        return appliedAll;
    }

    @Override
    public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsent(projectId, TelemetryConsentBridgePayload.snapshotSummary(snapshot));
        }
        return applyLocalConsent(projectId, snapshot);
    }

    private boolean applyLocalConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        TelemetryDataPaths consentPaths = consentDataPaths();
        if (project == null || consentPaths == null || overrideStore == null) {
            return false;
        }
        TelemetryConsentSnapshot normalized = clampToSupported(snapshot, supportedSnapshot(project));
        Path overrideFile = consentPaths.projectOverrideFile(project.projectId());
        boolean saved = saveConsent(overrideFile, normalized);
        Path embeddedOverrideFile = embeddedProjectOverrideFile(consentPaths, project);
        if (embeddedOverrideFile != null && !embeddedOverrideFile.equals(overrideFile)) {
            saved = saved && saveConsent(embeddedOverrideFile, normalized);
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

    private boolean saveConsent(@Nonnull Path overrideFile, @Nonnull TelemetryConsentSnapshot snapshot) {
        return overrideStore.saveProjectEnabled(overrideFile, snapshot.projectEnabled())
                && overrideStore.saveCrashEnabled(overrideFile, snapshot.crashEnabled())
                && overrideStore.saveErrorEventsEnabled(overrideFile, snapshot.errorEnabled())
                && overrideStore.saveLifecycleEventsEnabled(overrideFile, snapshot.lifecycleEnabled())
                && overrideStore.savePerformanceEnabled(overrideFile, snapshot.performanceEnabled())
                && overrideStore.saveUsageEnabled(overrideFile, snapshot.usageEnabled())
                && overrideStore.saveStatsEnabled(overrideFile, snapshot.statsEnabled())
                && overrideStore.saveBreadcrumbsEnabled(overrideFile, snapshot.breadcrumbsEnabled());
    }

    private boolean applyRuntimeConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsent(projectId, TelemetryConsentBridgePayload.snapshotSummary(snapshot));
        }
        if (coordinatorBridge != null) {
            boolean runtimeProject = coordinatorBridge.service.findProject(projectId) != null;
            boolean applied = coordinatorBridge.service.setProjectEnabled(projectId, snapshot.projectEnabled());
            if (!runtimeProject && !applied) {
                return findConsentProject(projectId) != null;
            }
            if (!runtimeProject) {
                return true;
            }
            applied &= coordinatorBridge.service.setCrashEnabled(projectId, snapshot.crashEnabled());
            applied &= coordinatorBridge.service.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            applied &= coordinatorBridge.service.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            applied &= coordinatorBridge.service.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            applied &= coordinatorBridge.service.setUsageEnabled(projectId, snapshot.usageEnabled());
            applied &= coordinatorBridge.service.setStatsEnabled(projectId, snapshot.statsEnabled());
            applied &= coordinatorBridge.service.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
            return applied;
        }
        if (engine != null && this.project != null && this.project.projectId().equalsIgnoreCase(projectId)) {
            engine.setProjectEnabled(projectId, snapshot.projectEnabled());
            engine.setCrashEnabled(projectId, snapshot.crashEnabled());
            engine.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            engine.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            engine.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            engine.setUsageEnabled(projectId, snapshot.usageEnabled());
            engine.setStatsEnabled(projectId, snapshot.statsEnabled());
            engine.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
            return true;
        }
        return false;
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> consentProjects(@Nonnull TelemetryCoordinatorService coordinatorService) {
        LinkedHashMap<String, TelemetryProjectRegistration> projects = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : coordinatorService.projects()) {
            projects.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : coordinatorService.manualReportProjects()) {
            projects.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        return List.copyOf(projects.values());
    }

    @Override
    public boolean markConsentReviewed(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.markConsentReviewed(projectId);
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        TelemetryDataPaths consentPaths = consentDataPaths();
        return project != null
                && consentStateStore != null
                && consentPaths != null
                && consentStateStore.markReviewed(consentPaths.consentStateFile(), project);
    }

    @Override
    public boolean setProjectEnabled(boolean enabled) {
        if (dataPaths == null || overrideStore == null) {
            return false;
        }
        boolean saved = overrideStore.saveProjectEnabled(dataPaths.projectOverrideFile(project.projectId()), enabled);
        return saved && applyProjectEnabled(enabled);
    }

    @Override
    public boolean setBreadcrumbsEnabled(boolean enabled) {
        if (dataPaths == null || overrideStore == null) {
            return false;
        }
        boolean saved = overrideStore.saveBreadcrumbsEnabled(dataPaths.projectOverrideFile(project.projectId()), enabled);
        return saved && applyBreadcrumbsEnabled(enabled);
    }

    private boolean applyProjectEnabled(boolean enabled) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.setProjectEnabled(project.projectId(), enabled);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.setProjectEnabled(project.projectId(), enabled);
        }
        if (engine != null) {
            engine.setProjectEnabled(project.projectId(), enabled);
            return true;
        }
        return false;
    }

    private boolean applyBreadcrumbsEnabled(boolean enabled) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.setBreadcrumbsEnabled(project.projectId(), enabled);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.setBreadcrumbsEnabled(project.projectId(), enabled);
        }
        if (engine != null) {
            engine.setBreadcrumbsEnabled(project.projectId(), enabled);
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        if (coordinatorBridge != null) {
            TelemetryCoordinatorRegistry.register(coordinatorBridge);
        } else if (engine != null) {
            engine.start();
            if (statsHeartbeat != null) {
                statsHeartbeat.start();
            }
        }
    }

    @Override
    public void shutdown() {
        if (coordinatorBridge != null) {
            TelemetryCoordinatorRegistry.unregister(coordinatorBridge.providerId());
        } else if (engine != null) {
            if (statsHeartbeat != null) {
                statsHeartbeat.shutdown();
            }
            engine.shutdown();
        }
    }

    @Override
    public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordBreadcrumb(project.projectId(), category, detail);
        } else if (engine != null) {
            engine.recordBreadcrumb(project.projectId(), category, detail);
        }
    }

    public void clearBreadcrumbs() {
        if (engine != null) {
            engine.clearBreadcrumbs(project.projectId());
        }
    }

    @Override
    public void captureSetupFailure(@Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureSetupFailure(project.projectId(), throwable);
        } else if (engine != null) {
            engine.captureSetupFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void captureStartFailure(@Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureStartFailure(project.projectId(), throwable);
        } else if (engine != null) {
            engine.captureStartFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(project.projectId(), eventName, throwable, detailsFrom(detail));
        } else if (engine != null) {
            engine.recordError(project.projectId(), eventName, throwable, detail);
        }
    }

    @Override
    public void recordErrorWithContext(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(project.projectId(), eventName, throwable, detailsFrom(context));
        } else if (engine != null) {
            engine.recordErrorWithContext(project.projectId(), eventName, throwable, context);
        }
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(project.projectId(), eventName, durationMs, success, detailsFrom(detail));
        } else if (engine != null) {
            engine.recordLifecycle(project.projectId(), eventName, durationMs, success, detail);
        }
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(project.projectId(), eventName, durationMs, success, detailsFrom(context));
        } else if (engine != null) {
            engine.recordLifecycleWithContext(project.projectId(), eventName, durationMs, success, context);
        }
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detailsFrom(detail));
        } else if (engine != null) {
            engine.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detail);
        }
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detailsFrom(context));
        } else if (engine != null) {
            engine.recordPerformanceWithContext(project.projectId(), eventName, durationMs, metricValue, context);
        }
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(project.projectId(), eventName, detailsFrom(detail));
        } else if (engine != null) {
            engine.recordUsage(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(project.projectId(), eventName, detailsFrom(context));
        } else if (engine != null) {
            engine.recordUsageWithContext(project.projectId(), eventName, context);
        }
    }

    @Override
    public void recordStats(@Nonnull String eventName, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(project.projectId(), eventName, detailsFrom(detail));
        } else if (engine != null) {
            engine.recordStats(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(project.projectId(), eventName, detailsFrom(context));
        } else if (engine != null) {
            engine.recordStatsWithContext(project.projectId(), eventName, context);
        }
    }

    void emitStatsHeartbeatNow() {
        if (statsHeartbeat != null) {
            statsHeartbeat.emitHeartbeatNow();
        }
    }

    @Override
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
        } else if (engine != null) {
            engine.captureExceptionalWorldRemoval(world, removalReason);
        }
    }

    @Override
    public boolean requestFlush() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(project.projectId());
        }
        return engine != null && engine.triggerFlushAsync(project.projectId());
    }

    @Override
    public boolean captureTestReport(@Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.captureTestReport(project.projectId(), detail);
        }
        return engine != null && engine.captureTestReport(project.projectId(), detail);
    }

    @Nonnull
    public EmbeddedTelemetryDiagnostics diagnostics() {
        return new EmbeddedTelemetryDiagnostics(
                isEnabled(),
                resolveEndpoint(),
                pendingReports(),
                coordinatorBridge != null ? coordinatorBridge.service.flushInProgress() : engine != null && engine.flushInProgress(),
                coordinatorBridge != null ? coordinatorBridge.service.lastFlushResult() : engine == null ? disabledReason : engine.lastFlushResult()
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
        return localConsentDiagnostics();
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics localConsentDiagnostics() {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            projectDiagnostics.add(buildProjectDiagnostics(project));
        }
        TelemetryDataPaths consentPaths = consentDataPaths();
        return new TelemetryRuntimeDiagnostics(
                !isDisabled() && (coordinatorBridge == null ? engine != null && engine.isEnabled() : coordinatorBridge.service.isEnabled()),
                consentProjects.size(),
                loadedModCount(),
                commandPendingReports(null),
                coordinatorBridge != null ? coordinatorBridge.service.flushInProgress() : engine != null && engine.flushInProgress(),
                commandLastFlushResult(),
                consentPaths == null || consentPaths.modsDirectory() == null ? null : consentPaths.modsDirectory().toString(),
                List.of(),
                List.copyOf(projectDiagnostics)
        );
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
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project == null ? null : buildProjectDiagnostics(project);
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics buildProjectDiagnostics(
            @Nonnull TelemetryProjectRegistration project) {
        TelemetryRuntimeSettings effectiveSettings = consentSettings();
        CrashReportClient.DeliveryTarget target = effectiveSettings == null ? null : project.resolveDeliveryTarget(effectiveSettings);
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                project.projectId(),
                project.displayName(),
                commandProjectEnabled(project.projectId()),
                project.hasOverride(),
                project.destinationMode(),
                target == null ? null : target.endpoint(),
                commandPendingReports(project.projectId()),
                project.pluginIdentifier(),
                project.pluginVersion(),
                project.sourcePath() == null ? null : project.sourcePath().toString(),
                project.descriptor().ui().iconTexturePath(),
                project.packagePrefixes(),
                project.runtimeMode(),
                commandCrashEnabled(project),
                commandErrorEnabled(project),
                commandLifecycleEnabled(project),
                commandPerformanceEnabled(project),
                commandUsageEnabled(project),
                commandStatsEnabled(project),
                commandBreadcrumbsEnabled(project),
                supportsCrash(project),
                project.descriptor().events().errors().enabled(),
                project.descriptor().events().lifecycle().enabled(),
                project.descriptor().performance().enabled(),
                project.descriptor().usage().enabled(),
                project.descriptor().stats().enabled(),
                project.descriptor().events().breadcrumbs().enabled()
        );
    }

    private int loadedModCount() {
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.loadedMods().size();
        }
        return project == null ? 0 : 1;
    }

    @Nullable
    private TelemetryRuntimeSettings consentSettings() {
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.settings();
        }
        return settings;
    }

    @Nullable
    private TelemetryDataPaths consentDataPaths() {
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.dataPaths();
        }
        return dataPaths;
    }

    int pendingReports() {
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.pendingReports(project.projectId());
        }
        return engine == null ? 0 : engine.pendingReports(project.projectId());
    }

    @Nonnull
    TelemetryCoreEngine.FlushSummary flushPendingReportsNow(@Nonnull String reason) {
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.flushPendingReportsNow(reason, project.projectId());
        }
        return engine == null
                ? new TelemetryCoreEngine.FlushSummary(0, 0, 0, disabledReason)
                : engine.flushPendingReportsNow(reason, project.projectId());
    }

    @Nonnull
    private String resolveEndpoint() {
        if (settings == null) {
            return "<disabled>";
        }
        CrashReportClient.DeliveryTarget target = project.resolveEventDeliveryTarget(settings);
        if (target == null || target.endpoint() == null || target.endpoint().isBlank()) {
            target = project.resolveDeliveryTarget(settings);
        }
        return target == null || target.endpoint() == null || target.endpoint().isBlank()
                ? "<disabled>"
                : target.endpoint();
    }

    private boolean commandCrashEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isCrashEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isCrashEnabled(project.projectId())
                : project.isCrashTelemetryEnabled();
    }

    private boolean commandErrorEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isErrorEventsEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isErrorEventsEnabled(project.projectId())
                : project.events().errors().enabled();
    }

    private boolean commandLifecycleEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isLifecycleEventsEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isLifecycleEventsEnabled(project.projectId())
                : project.events().lifecycle().enabled();
    }

    private boolean commandPerformanceEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isPerformanceEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isPerformanceEnabled(project.projectId())
                : project.performance().enabled();
    }

    private boolean commandUsageEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isUsageEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isUsageEnabled(project.projectId())
                : project.usage().enabled();
    }

    private boolean commandStatsEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isStatsEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isStatsEnabled(project.projectId())
                : project.stats().enabled();
    }

    private boolean commandBreadcrumbsEnabled(@Nonnull TelemetryProjectRegistration project) {
        if (coordinatorBridge != null && coordinatorBridge.service.findProject(project.projectId()) != null) {
            return coordinatorBridge.service.isBreadcrumbsEnabled(project.projectId());
        }
        return this.project != null && engine != null && this.project.projectId().equalsIgnoreCase(project.projectId())
                ? engine.isBreadcrumbsEnabled(project.projectId())
                : project.events().breadcrumbs().enabled();
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

    @Nullable
    private static Path embeddedProjectOverrideFile(@Nonnull TelemetryDataPaths dataPaths,
                                                    @Nonnull TelemetryProjectRegistration project) {
        if (!project.isEmbeddedMode() || dataPaths.modsDirectory() == null) {
            return null;
        }
        return dataPaths.modsDirectory()
                .resolve(project.pluginIdentifier().trim().replace(':', '_'))
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve(project.projectId() + ".json");
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

    private final class EmbeddedCoordinatorBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final TelemetryCoordinatorService service;
        private final AtomicBoolean active = new AtomicBoolean(false);

        private EmbeddedCoordinatorBridge(@Nonnull TelemetryRuntimeCandidate candidate,
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
            return TelemetryConsentBridgePayload.diagnosticsSummary(EmbeddedTelemetryService.this.localConsentDiagnostics());
        }

        @Nonnull
        @Override
        public Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
            TelemetryProjectRegistration project = findConsentProject(projectId);
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = project == null
                    ? null
                    : buildProjectDiagnostics(project);
            return diagnostics == null ? Map.of() : TelemetryConsentBridgePayload.projectDiagnosticsSummary(diagnostics);
        }

        @Override
        public boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
            return EmbeddedTelemetryService.this.applyLocalConsentToAll(TelemetryConsentBridgePayload.snapshotFromSummary(snapshot));
        }

        @Override
        public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
            return EmbeddedTelemetryService.this.applyLocalConsentCategoryToAll(category, enabled);
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
            return EmbeddedTelemetryService.this.applyLocalConsent(
                    projectId,
                    TelemetryConsentBridgePayload.snapshotFromSummary(snapshot)
            );
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            TelemetryProjectRegistration project = findConsentProject(projectId);
            TelemetryDataPaths consentPaths = consentDataPaths();
            return project != null
                    && consentStateStore != null
                    && consentPaths != null
                    && consentStateStore.markReviewed(consentPaths.consentStateFile(), project);
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
        List<Map<String, Object>> summaries = active.projectSummaries();
        if (!summaries.isEmpty()) {
            return summaries;
        }
        Object rawProjects = active.consentDiagnostics().get("projects");
        if (!(rawProjects instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        ArrayList<Map<String, Object>> projects = new ArrayList<>(rawList.size());
        for (Object rawProject : rawList) {
            if (rawProject instanceof Map<?, ?> project) {
                projects.add(stringObjectMap(project));
            }
        }
        return List.copyOf(projects);
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

    @Nonnull
    private static TelemetryProjectRegistration nullRegistration(@Nonnull String projectId,
                                                                 @Nonnull String displayName) {
        return new TelemetryProjectRegistration(
                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor(
                        1,
                        projectId,
                        displayName,
                        com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.RUNTIME_MODE_EMBEDDED,
                        List.of(),
                        List.of(),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.CaptureOptions(true, true, true, true),
                        com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.EventOptions.defaults(),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.PerformanceOptions(false, 1.0d, 100, java.util.Map.of()),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.UsageOptions(false, java.util.List.of(), java.util.Map.of()),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.StatsOptions(false, java.util.List.of(), java.util.Map.of()),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.UiOptions(null),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportOptions(
                                false,
                                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportForm(false, java.util.List.of()),
                                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportForm(false, java.util.List.of()),
                                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportAttachmentOptions(false, false, 262144),
                                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportContactOptions(false, 160),
                                new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.ManualReportResolutionOptions(false)
                        ),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.Defaults(false, "hosted"),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.HostedDestination(null, null, null, java.util.Map.of()),
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.CustomEndpoint(null, null, java.util.Map.of())
                ),
                "unknown",
                "unknown",
                null
        );
    }
}
