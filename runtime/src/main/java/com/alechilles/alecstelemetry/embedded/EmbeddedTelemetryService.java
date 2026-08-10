package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCapabilities;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryProjectContributionBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryProjectContributionRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.reports.TelemetryReportCoordinator;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentRuntime;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryBreadcrumbBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryManualReportBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHostHandle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
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
    private final TelemetryRuntimeHostHandle hostHandle;
    private final HytaleLogger logger;
    private final String disabledReason;
    private final TelemetryConsentStateStore consentStateStore;
    private List<TelemetryProjectRegistration> consentProjects;
    private boolean contributed;
    private String contributionToken;
    private ContributionBridge contributionBridge;
    private final ArrayDeque<BufferedOperation> bufferedOperations = new ArrayDeque<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final int MAX_BUFFERED_CONTRIBUTION_OPERATIONS = 64;

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
        this.hostHandle = null;
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
        this.hostHandle = null;
        this.logger = logger;
        this.disabledReason = disabledReason;
        this.consentStateStore = null;
        this.consentProjects = List.of(this.project);
        if (logger != null) {
            logger.at(Level.INFO).log("Embedded telemetry is disabled: " + disabledReason);
        }
    }

    private EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                                     @Nonnull TelemetryDataPaths dataPaths,
                                     @Nonnull TelemetryProjectRegistration project,
                                     @Nonnull TelemetryRuntimeHostHandle hostHandle,
                                     @Nullable HytaleLogger logger) {
        this.project = project;
        this.dataPaths = dataPaths;
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.settings = settings;
        this.engine = null;
        this.statsHeartbeat = null;
        this.coordinatorBridge = null;
        this.hostHandle = hostHandle;
        this.logger = logger;
        this.disabledReason = null;
        this.consentProjects = List.of(project);
    }

    @Nonnull
    public static EmbeddedTelemetryService disabled(@Nonnull String projectId,
                                                    @Nonnull String displayName,
                                                    @Nullable HytaleLogger logger,
                                                    @Nonnull String disabledReason) {
        return new EmbeddedTelemetryService(projectId, displayName, logger, disabledReason);
    }

    @Nonnull
    static EmbeddedTelemetryService fromHost(@Nonnull TelemetryRuntimeSettings settings,
                                             @Nonnull TelemetryDataPaths dataPaths,
                                             @Nonnull TelemetryProjectRegistration project,
                                             @Nonnull TelemetryRuntimeHostHandle hostHandle,
                                             @Nullable HytaleLogger logger) {
        return new EmbeddedTelemetryService(settings, dataPaths, project, hostHandle, logger);
    }

    @Nonnull
    static EmbeddedTelemetryService fromContribution(@Nonnull TelemetryRuntimeSettings settings,
                                                     @Nonnull TelemetryDataPaths dataPaths,
                                                     @Nonnull TelemetryProjectRegistration project,
                                                     @Nonnull TelemetryRuntimeHostHandle hostHandle,
                                                     @Nonnull String hostPluginIdentifier,
                                                     @Nonnull String hostPluginVersion,
                                                     @Nonnull String descriptorHash,
                                                     @Nullable HytaleLogger logger) {
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                project,
                hostHandle,
                logger
        );
        service.configureContribution(hostPluginIdentifier, hostPluginVersion, descriptorHash);
        return service;
    }

    private void configureContribution(@Nonnull String hostPluginIdentifier,
                                       @Nonnull String hostPluginVersion,
                                       @Nonnull String descriptorHash) {
        contributed = true;
        contributionBridge = new ContributionBridge(
                hostPluginIdentifier,
                hostPluginVersion,
                descriptorHash
        );
        contributionToken = TelemetryProjectContributionRegistry.register(contributionBridge);
        reconcileActiveCoordinator();
    }

    @Nullable
    private TelemetryProjectHandle hostProjectHandle() {
        return hostProjectHandle(project.projectId());
    }

    @Nullable
    private TelemetryProjectHandle hostProjectHandle(@Nonnull String projectId) {
        return hostHandle == null ? null : hostHandle.api().findProject(projectId);
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
        if (contributed && !started.get()) {
            return project.isEnabled();
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(project.projectId());
            if (!activeDiagnostics.isEmpty()) {
                return TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(activeDiagnostics).enabled();
            }
            return active.isProjectEnabled(project.projectId());
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            return hostProject.isEnabled();
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
        if (hostHandle != null) {
            return hostHandle.ownsActiveCoordinator();
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null
                && coordinatorBridge != null
                && active.providerId().equals(coordinatorBridge.providerId());
    }

    @Nonnull
    TelemetryCommandRuntime commandRuntime() {
        return new EmbeddedCommandRuntime();
    }

    public int registeredProjectCount() {
        return projects().size();
    }

    @Nonnull
    public List<TelemetryProjectRegistration> projects() {
        return commandProjects();
    }

    @Nonnull
    public List<TelemetryProjectRegistration> commandProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return projectsFromSummaries(activeProjects(active));
        }
        return currentConsentProjects();
    }

    @Nullable
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        return commandProject(projectId);
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
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(projectId);
            if (!activeDiagnostics.isEmpty()) {
                return TelemetryConsentBridgePayload.projectDiagnosticsFromSummary(activeDiagnostics).enabled();
            }
            return active.isProjectEnabled(projectId);
        }
        if (active != null) {
            TelemetryProjectRegistration consentProject = findConsentProject(projectId);
            boolean runtimeProject = coordinatorBridge != null
                    && coordinatorBridge.service.findProject(projectId) != null;
            if (consentProject != null && !runtimeProject) {
                return consentProject.isEnabled();
            }
            return active.isProjectEnabled(projectId);
        }
        TelemetryProjectHandle hostProject = hostProjectHandle(projectId);
        if (hostProject != null) {
            return hostProject.isEnabled();
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.isProjectEnabled(projectId);
        }
        return project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && isEnabled();
    }

    public int pendingReports(@Nullable String projectId) {
        return commandPendingReports(projectId);
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
    public String lastFlushResult() {
        return commandLastFlushResult();
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

    public boolean requestFlush(@Nullable String projectId) {
        return commandFlush(projectId);
    }

    @Nonnull
    public TelemetryServerVerificationResult requestServerVerification() {
        return requestServerVerification(null);
    }

    @Nonnull
    public TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
        return commandServerVerification(claimToken);
    }

    @Nonnull
    public TelemetryServerVerificationResult commandServerVerification() {
        return commandServerVerification(null);
    }

    @Nonnull
    public TelemetryServerVerificationResult commandServerVerification(@Nullable String claimToken) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            TelemetryServerVerificationResult activeResult = active.requestServerVerification(claimToken);
            if (activeResult.status() != TelemetryServerVerificationResult.Status.UNAVAILABLE) {
                return activeResult;
            }
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.requestServerVerification(claimToken);
        }
        if (engine == null || project == null) {
            return TelemetryServerVerificationResult.unavailable(disabledReason == null ? "runtime_unavailable" : disabledReason);
        }
        if (claimToken != null && !engine.saveServerClaimToken(claimToken)) {
            return new TelemetryServerVerificationResult(
                    TelemetryServerVerificationResult.Status.MISSING_CLAIM_TOKEN,
                    0,
                    new TelemetryCoreEngine.FlushSummary(0, 0, 0, "invalid_claim_token")
            );
        }
        if (engine.serverClaimToken() == null) {
            return new TelemetryServerVerificationResult(
                    TelemetryServerVerificationResult.Status.MISSING_CLAIM_TOKEN,
                    0,
                    new TelemetryCoreEngine.FlushSummary(0, 0, 0, "missing_claim_token")
            );
        }
        if (statsHeartbeat == null
                || !engine.isStatsEnabled(project.projectId())
                || !project.stats().allows(EmbeddedTelemetryStatsHeartbeat.EVENT_HEARTBEAT)) {
            return new TelemetryServerVerificationResult(
                    TelemetryServerVerificationResult.Status.NO_STATS_PROJECTS,
                    0,
                    new TelemetryCoreEngine.FlushSummary(0, 0, 0, "no_stats_projects")
            );
        }
        statsHeartbeat.emitHeartbeatNow();
        TelemetryCoreEngine.FlushSummary flushSummary = engine.flushPendingReportsNow("server-verification", project.projectId());
        return new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                1,
                flushSummary
        );
    }

    public boolean commandFlush(@Nullable String projectId) {
        if (contributed) {
            if (projectId != null && !project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            return Boolean.TRUE.equals(dispatchContribution("flush", Map.of("reason", "command"), null).get("accepted"));
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(projectId);
        }
        if (hostHandle != null) {
            if (projectId == null) {
                return hostHandle.api().requestFlush();
            }
            TelemetryProjectHandle hostProject = hostProjectHandle(projectId);
            return hostProject != null && hostProject.requestFlush();
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.requestFlush(projectId);
        }
        return projectId == null ? requestFlush() : project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && requestFlush();
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return commandCaptureTestReport(projectId, detail);
    }

    public boolean commandCaptureTestReport(@Nonnull String projectId, @Nullable String detail) {
        if (contributed) {
            return project.projectId().equalsIgnoreCase(projectId.trim())
                    && dispatchContribution("test_report", detail == null ? Map.of() : Map.of("detail", detail), null)
                    .getOrDefault("accepted", false) == Boolean.TRUE;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.captureTestReport(projectId, detail);
        }
        if (hostHandle != null) {
            return hostHandle.captureTestReport(projectId, detail);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.captureTestReport(projectId, detail);
        }
        return project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && captureTestReport(detail);
    }

@Nullable
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
        return consentProjectDiagnostics(projectId);
    }

    public boolean openConsentPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef playerRef,
                                   boolean firstRun) {
        return new TelemetryConsentCoordinator(this, logger).openConsentPage(playerEntityRef, store, playerRef, firstRun);
    }

    public boolean openReportPage(@Nonnull String projectId,
                                  @Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull TelemetryReportOpenRequest request) {
        return new TelemetryReportCoordinator(commandRuntime(), logger).openReportPage(projectId, playerEntityRef, store, playerRef, request);
    }

    @Nonnull
    public List<TelemetryProjectRegistration> manualReportProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return manualReportProjectsFromSummaries(active.manualReportProjectSummaries());
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.manualReportProjects().stream()
                    .filter(project -> project.descriptor().reports().enabled())
                    .toList();
        }
        if (engine != null) {
            return engine.manualReportProjects().stream()
                    .filter(project -> project.descriptor().reports().enabled())
                    .toList();
        }
        return project != null && project.descriptor().reports().enabled() ? List.of(project) : List.of();
    }

    @Nullable
    public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return projectFromSummary(active.findManualReportProjectSummary(projectId));
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.findManualReportProject(projectId);
        }
        if (engine != null) {
            return engine.findManualReportProject(projectId);
        }
        return project != null
                && project.projectId().equalsIgnoreCase(projectId.trim())
                && project.descriptor().reports().enabled()
                ? project
                : null;
    }

    @Nonnull
    public TelemetryRuntimeSettings.ManualReportSettings manualReportSettings() {
        return settings.manualReports();
    }

    @Nonnull
    public PlayerReportRuntimeContext currentPlayerReportRuntimeContext() {
        if (coordinatorBridge != null) {
            return new PlayerReportRuntimeContext(false, 0, null, coordinatorBridge.service.loadedMods());
        }
        return PlayerReportRuntimeContext.UNKNOWN;
    }

    @Nonnull
    public ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                                @Nonnull ManualReportSubmission submission,
                                                                @Nullable PlayerReportRuntimeContext playerContext) {
        if (contributed) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return new ManualReportEnvelope.CreateResult(null, List.of("contribution_project_mismatch"));
            }
            return manualReportResultFromMap(dispatchContribution(
                    "manual_report",
                    Map.of(
                            "submission", submissionToMap(submission),
                            "playerContext", playerContextToMap(playerContext)
                    ),
                    null
            ));
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return manualReportResultFromMap(active.submitManualReport(
                    projectId,
                    submissionToMap(submission),
                    playerContextToMap(playerContext)
            ));
        }
        if (coordinatorBridge != null) {
            return manualReportResultFromMap(coordinatorBridge.service.submitManualReport(
                    projectId,
                    submissionToMap(submission),
                    playerContextToMap(playerContext)
            ));
        }
        if (engine != null) {
            return engine.submitManualReport(projectId, submission, playerContext);
        }
        return new ManualReportEnvelope.CreateResult(null, List.of("embedded_telemetry_disabled"));
    }

    @Nonnull
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.manualReportReceiptStatus(reportId);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.manualReportReceiptStatus(reportId);
        }
        return engine == null ? "unknown" : engine.manualReportReceiptStatus(reportId);
    }

    @Nonnull
    public List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return TelemetryManualReportBridgePayload.envelopesFromSummaries(active.manualReportsForReview(maxReportsPerProject));
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.manualReportsForReview(maxReportsPerProject);
        }
        return engine == null ? List.of() : engine.manualReportsForReview(maxReportsPerProject);
    }

    public boolean approveManualReport(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.approveManualReport(reportId);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.approveManualReport(reportId);
        }
        return engine != null && engine.approveManualReport(reportId);
    }

    public boolean rejectManualReport(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.rejectManualReport(reportId);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.rejectManualReport(reportId);
        }
        return engine != null && engine.rejectManualReport(reportId);
    }

    @Nonnull
    public List<String> submittedManualReportAuditLines(int maxLines) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.submittedManualReportAuditLines(maxLines);
        }
        if (coordinatorBridge != null) {
            return coordinatorBridge.service.submittedManualReportAuditLines(maxLines);
        }
        return engine == null ? List.of() : engine.submittedManualReportAuditLines(maxLines);
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
        return consentStateStore.unreviewedProjects(consentPaths.consentStateFile(), currentConsentProjects());
    }

    @Nonnull
    @Override
    public List<String> addedConsentCategories(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return List.of();
        }
        TelemetryDataPaths consentPaths = consentDataPaths();
        TelemetryProjectRegistration consentProject = findConsentProject(projectId);
        return consentStateStore == null || consentPaths == null || consentProject == null
                ? List.of()
                : consentStateStore.addedSupportedCategories(consentPaths.consentStateFile(), consentProject);
    }

    @Override
    public boolean isConsentNoticeShown(@Nonnull String viewerKey,
                                        @Nonnull List<TelemetryProjectRegistration> projects) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return false;
        }
        TelemetryDataPaths consentPaths = consentDataPaths();
        return consentStateStore != null
                && consentPaths != null
                && consentStateStore.isNoticeShown(consentPaths.consentStateFile(), viewerKey, projects);
    }

    @Override
    public boolean markConsentNoticeShown(@Nonnull String viewerKey,
                                          @Nonnull List<TelemetryProjectRegistration> projects) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return false;
        }
        TelemetryDataPaths consentPaths = consentDataPaths();
        return consentStateStore != null
                && consentPaths != null
                && consentStateStore.markNoticeShown(consentPaths.consentStateFile(), viewerKey, projects);
    }

    @Override
    public void recordConsentPromptShown(@Nonnull String viewerKey,
                                         @Nonnull List<TelemetryProjectRegistration> projects) {
        if (hostHandle != null) {
            hostHandle.recordConsentPromptShown(viewerKey, projects);
        }
    }

    @Override
    public void recordConsentUiOpened(@Nonnull String viewerKey,
                                      @Nonnull List<TelemetryProjectRegistration> projects) {
        if (hostHandle != null) {
            hostHandle.recordConsentUiOpened(viewerKey, projects);
        }
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
        for (TelemetryProjectRegistration project : currentConsentProjects()) {
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
        for (TelemetryProjectRegistration project : currentConsentProjects()) {
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
        TelemetryConsentSnapshot supported = supportedSnapshot(project);
        TelemetryConsentSnapshot normalized = clampToSupported(snapshot, supported);
        Path overrideFile = consentPaths.projectOverrideFile(project.projectId());
        boolean saved = saveConsent(overrideFile, normalized, supported);
        Path embeddedOverrideFile = embeddedProjectOverrideFile(consentPaths, project);
        if (embeddedOverrideFile != null && !embeddedOverrideFile.equals(overrideFile)) {
            saved = saved && saveConsent(embeddedOverrideFile, normalized, supported);
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

    private boolean saveConsent(@Nonnull Path overrideFile,
                                @Nonnull TelemetryConsentSnapshot snapshot,
                                @Nonnull TelemetryConsentSnapshot supported) {
        return overrideStore.saveConsentSnapshot(overrideFile, snapshot, supported);
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
        if (hostHandle != null) {
            TelemetryProjectHandle runtimeProject = hostProjectHandle(projectId);
            boolean applied = hostHandle.applyConsent(projectId, snapshot);
            if (runtimeProject == null && !applied) {
                return findConsentProject(projectId) != null;
            }
            return runtimeProject == null || applied;
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
        if (hostHandle != null && hostHandle.markConsentReviewed(projectId)) {
            return true;
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
        if (hostHandle != null) {
            return hostHandle.setProjectEnabled(project.projectId(), enabled);
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
        if (hostHandle != null) {
            return hostHandle.setBreadcrumbsEnabled(project.projectId(), enabled);
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
        if (shutdown.get()) {
            return;
        }
        if (contributed) {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            if (hostHandle != null) {
                hostHandle.start();
            }
            drainContributionBuffer();
            return;
        }
        if (hostHandle != null) {
            hostHandle.start();
            return;
        }
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
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        if (contributed) {
            String token = contributionToken;
            contributionToken = null;
            bufferedOperations.clear();
            if (token != null) {
                TelemetryProjectContributionRegistry.unregister(token);
                reconcileActiveCoordinator();
            }
            if (hostHandle != null) {
                hostHandle.shutdown();
            }
            return;
        }
        if (hostHandle != null) {
            hostHandle.shutdown();
            return;
        }
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
        recordBreadcrumb(TelemetryBreadcrumbContext.of(category, detail));
    }

    @Override
    public void recordBreadcrumb(@Nonnull TelemetryBreadcrumbContext context) {
        TelemetryBreadcrumbContext normalized = context.normalize();
        if (contributed) {
            dispatchContribution(
                    "breadcrumb",
                    Map.of(
                            "category", normalized.category(),
                            "detail", normalized.detail(),
                            "context", TelemetryBreadcrumbBridgePayload.summary(normalized)
                    ),
                    null
            );
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordBreadcrumb(
                    project.projectId(),
                    normalized.category(),
                    normalized.detail(),
                    TelemetryBreadcrumbBridgePayload.summary(normalized)
            );
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordBreadcrumb(normalized);
        } else if (engine != null) {
            engine.recordBreadcrumb(project.projectId(), normalized);
        }
    }

    public void clearBreadcrumbs() {
        if (engine != null) {
            engine.clearBreadcrumbs(project.projectId());
        }
    }

    @Override
    public void captureSetupFailure(@Nullable Throwable throwable) {
        if (contributed) {
            dispatchContribution("capture_setup", Map.of(), throwable);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureSetupFailure(project.projectId(), throwable);
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.captureSetupFailure(throwable);
        } else if (engine != null) {
            engine.captureSetupFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void captureStartFailure(@Nullable Throwable throwable) {
        if (contributed) {
            dispatchContribution("capture_start", Map.of(), throwable);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureStartFailure(project.projectId(), throwable);
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.captureStartFailure(throwable);
        } else if (engine != null) {
            engine.captureStartFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        if (contributed) {
            dispatchContribution("error", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(detail)
            ), throwable);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(project.projectId(), eventName, throwable, detailsFrom(detail));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordError(eventName, throwable, detail);
        } else if (engine != null) {
            engine.recordError(project.projectId(), eventName, throwable, detail);
        }
    }

    @Override
    public void recordErrorWithContext(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        if (contributed) {
            dispatchContribution("error", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(context)
            ), throwable);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(project.projectId(), eventName, throwable, detailsFrom(context));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordErrorWithContext(eventName, throwable, context);
        } else if (engine != null) {
            engine.recordErrorWithContext(project.projectId(), eventName, throwable, context);
        }
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        if (contributed) {
            dispatchContribution("lifecycle", Map.of(
                    "eventName", eventName,
                    "durationMs", durationMs,
                    "success", success,
                    "details", detailsFrom(detail)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(project.projectId(), eventName, durationMs, success, detailsFrom(detail));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordLifecycle(eventName, durationMs, success, detail);
        } else if (engine != null) {
            engine.recordLifecycle(project.projectId(), eventName, durationMs, success, detail);
        }
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        if (contributed) {
            dispatchContribution("lifecycle", Map.of(
                    "eventName", eventName,
                    "durationMs", durationMs,
                    "success", success,
                    "details", detailsFrom(context)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(project.projectId(), eventName, durationMs, success, detailsFrom(context));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordLifecycleWithContext(eventName, durationMs, success, context);
        } else if (engine != null) {
            engine.recordLifecycleWithContext(project.projectId(), eventName, durationMs, success, context);
        }
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        if (contributed) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventName", eventName);
            payload.put("durationMs", durationMs);
            if (metricValue != null) {
                payload.put("metricValue", metricValue);
            }
            payload.put("details", detailsFrom(detail));
            dispatchContribution("performance", payload, null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detailsFrom(detail));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordPerformance(eventName, durationMs, metricValue, detail);
        } else if (engine != null) {
            engine.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detail);
        }
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        if (contributed) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventName", eventName);
            payload.put("durationMs", durationMs);
            if (metricValue != null) {
                payload.put("metricValue", metricValue);
            }
            payload.put("details", detailsFrom(context));
            dispatchContribution("performance", payload, null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detailsFrom(context));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordPerformanceWithContext(eventName, durationMs, metricValue, context);
        } else if (engine != null) {
            engine.recordPerformanceWithContext(project.projectId(), eventName, durationMs, metricValue, context);
        }
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        if (contributed) {
            dispatchContribution("usage", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(detail)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(project.projectId(), eventName, detailsFrom(detail));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordUsage(eventName, detail);
        } else if (engine != null) {
            engine.recordUsage(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        if (contributed) {
            dispatchContribution("usage", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(context)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(project.projectId(), eventName, detailsFrom(context));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordUsageWithContext(eventName, context);
        } else if (engine != null) {
            engine.recordUsageWithContext(project.projectId(), eventName, context);
        }
    }

    @Override
    public void recordStats(@Nonnull String eventName, @Nullable String detail) {
        if (contributed) {
            dispatchContribution("stats", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(detail)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(project.projectId(), eventName, detailsFrom(detail));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordStats(eventName, detail);
        } else if (engine != null) {
            engine.recordStats(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        if (contributed) {
            dispatchContribution("stats", Map.of(
                    "eventName", eventName,
                    "details", detailsFrom(context)
            ), null);
            return;
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(project.projectId(), eventName, detailsFrom(context));
            return;
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            hostProject.recordStatsWithContext(eventName, context);
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
        if (contributed) {
            dispatchContribution("capture_world_removal", Map.of(
                    "worldName", world.getName(),
                    "removalReason", removalReason.name(),
                    "possibleFailureCause", world.getPossibleFailureCause() == null
                            ? ""
                            : world.getPossibleFailureCause().toString()
            ), world.getFailureException());
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

    public boolean requestFlush() {
        if (contributed) {
            return Boolean.TRUE.equals(dispatchContribution("flush", Map.of("reason", "manual"), null).get("accepted"));
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.requestFlush(project.projectId());
        }
        TelemetryProjectHandle hostProject = hostProjectHandle();
        if (hostProject != null) {
            return hostProject.requestFlush();
        }
        return engine != null && engine.triggerFlushAsync(project.projectId());
    }

    public boolean captureTestReport(@Nullable String detail) {
        if (contributed) {
            return Boolean.TRUE.equals(dispatchContribution(
                    "test_report",
                    detail == null ? Map.of() : Map.of("detail", detail),
                    null
            ).get("accepted"));
        }
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return active.captureTestReport(project.projectId(), detail);
        }
        if (hostHandle != null) {
            return hostHandle.captureTestReport(project.projectId(), detail);
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
        List<TelemetryProjectRegistration> currentProjects = currentConsentProjects();
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(currentProjects.size());
        for (TelemetryProjectRegistration project : currentProjects) {
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
                project.descriptor().events().errors().supported(),
                project.descriptor().events().lifecycle().supported(),
                project.descriptor().performance().supported(),
                project.descriptor().usage().supported(),
                project.descriptor().stats().supported(),
                project.descriptor().events().breadcrumbs().supported()
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
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            return commandPendingReports(project.projectId());
        }
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
        TelemetryProjectRegistration consentProject = null;
        for (TelemetryProjectRegistration project : consentProjects) {
            if (project.projectId().equalsIgnoreCase(normalized)) {
                consentProject = project;
                break;
            }
        }
        if (consentProject != null && consentProject.hasOverride()) {
            return consentProject;
        }
        if (coordinatorBridge != null) {
            TelemetryProjectRegistration runtimeProject = coordinatorBridge.service.findProject(normalized);
            if (runtimeProject != null) {
                return runtimeProject;
            }
        }
        return consentProject;
    }

    @Nonnull
    private List<TelemetryProjectRegistration> currentConsentProjects() {
        if (coordinatorBridge == null) {
            return consentProjects;
        }
        LinkedHashMap<String, TelemetryProjectRegistration> projects = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : consentProjects) {
            projects.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : coordinatorBridge.service.projects()) {
            String key = project.projectId().toLowerCase(Locale.ROOT);
            TelemetryProjectRegistration existing = projects.get(key);
            if (existing == null || !existing.hasOverride() || project.hasOverride()) {
                projects.put(key, project);
            }
        }
        for (TelemetryProjectRegistration project : coordinatorBridge.service.manualReportProjects()) {
            projects.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        return List.copyOf(projects.values());
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
        return TelemetryConsentCapabilities.supportedSnapshot(project);
    }

    private static boolean supportsCrash(@Nonnull TelemetryProjectRegistration project) {
        return project.descriptor().capture().supportsAnySource();
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
        if (dataPaths.modsDirectory() == null) {
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
    private static List<TelemetryProjectRegistration> manualReportProjectsFromSummaries(@Nonnull List<Map<String, Object>> summaries) {
        ArrayList<TelemetryProjectRegistration> projects = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            TelemetryProjectRegistration project = projectFromSummary(summary);
            if (project != null && project.descriptor().reports().enabled()) {
                projects.add(project);
            }
        }
        return List.copyOf(projects);
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
                .map(EmbeddedTelemetryService::loadedModToMap)
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
        if (!Boolean.TRUE.equals(values.get("accepted")) && errors.isEmpty()) {
            String reason = stringValue(values.get("reason"));
            if (reason != null) {
                errors = List.of(reason);
            }
        }
        ManualReportEnvelope envelope = envelopeFrom(values.get("envelopeJson"));
        String followUpToken = stringValue(values.get("followUpToken"));
        if (Boolean.TRUE.equals(values.get("accepted")) && envelope == null && errors.isEmpty()) {
            errors = List.of("coordinator_manual_report_missing_envelope");
        }
        return new ManualReportEnvelope.CreateResult(envelope, errors, followUpToken);
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
    private Map<String, Object> dispatchContribution(@Nonnull String operation,
                                                     @Nonnull Map<String, Object> payload,
                                                     @Nullable Throwable throwable) {
        String token = contributionToken;
        if (!contributed || token == null || shutdown.get()) {
            return Map.of("accepted", false, "reason", "contribution_unavailable");
        }
        Map<String, Object> normalized = Map.copyOf(payload);
        if (!started.get()) {
            if (!isBufferableBeforeStart(operation)) {
                return Map.of("accepted", false, "reason", "not_started");
            }
            synchronized (bufferedOperations) {
                if (bufferedOperations.size() >= MAX_BUFFERED_CONTRIBUTION_OPERATIONS) {
                    bufferedOperations.removeFirst();
                }
                bufferedOperations.addLast(new BufferedOperation(operation, normalized, throwable));
            }
            return Map.of("accepted", true, "buffered", true);
        }
        return TelemetryProjectContributionRegistry.dispatch(token, operation, normalized, throwable);
    }

    private static boolean isBufferableBeforeStart(@Nonnull String operation) {
        return switch (operation.trim().toLowerCase(Locale.ROOT)) {
            case "breadcrumb", "lifecycle", "capture_setup" -> true;
            default -> false;
        };
    }

    private void drainContributionBuffer() {
        String token = contributionToken;
        if (!contributed || token == null) {
            return;
        }
        while (true) {
            BufferedOperation operation;
            synchronized (bufferedOperations) {
                operation = bufferedOperations.pollFirst();
            }
            if (operation == null) {
                return;
            }
            TelemetryProjectContributionRegistry.dispatch(
                    token,
                    operation.operation(),
                    operation.payload(),
                    operation.throwable()
            );
        }
    }

    private void reconcileActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active == null || active.coordinatorProtocolVersion() < TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION) {
            return;
        }
        active.reconcileProjectContributions(
                TelemetryProjectContributionRegistry.revision(),
                TelemetryProjectContributionRegistry.activeContributions()
        );
    }

    @Nonnull
    private static String sha256(@Nonnull String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(Character.forDigit((part >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(part & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record BufferedOperation(@Nonnull String operation,
                                     @Nonnull Map<String, Object> payload,
                                     @Nullable Throwable throwable) {
    }

    private final class ContributionBridge implements TelemetryProjectContributionBridge {
        private final Map<String, Object> candidate;

        private ContributionBridge(@Nonnull String hostPluginIdentifier,
                                    @Nonnull String hostPluginVersion,
                                    @Nonnull String descriptorHash) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("abiVersion", TelemetryProjectContributionRegistry.CONTRIBUTION_ABI_VERSION);
            values.put("projectId", project.projectId());
            values.put("logicalPluginIdentifier", project.pluginIdentifier());
            values.put("logicalPluginVersion", project.pluginVersion());
            values.put("origin", hostPluginIdentifier.trim().equals(project.pluginIdentifier().trim())
                    ? TelemetryProjectContributionRegistry.STANDALONE_ORIGIN
                    : TelemetryProjectContributionRegistry.EMBEDDED_ORIGIN);
            values.put("hostPluginIdentifier", hostPluginIdentifier);
            values.put("hostPluginVersion", hostPluginVersion);
            values.put("sourcePath", project.sourcePath() == null
                    ? dataPaths.runtimeRoot().toString()
                    : project.sourcePath().toString());
            values.put("descriptorJson", project.descriptor().toJson());
            values.put("descriptorHash", descriptorHash == null || descriptorHash.isBlank()
                    ? sha256(project.descriptor().toJson())
                    : descriptorHash);
            candidate = Map.copyOf(values);
        }

        @Override
        public int contributionAbiVersion() {
            return TelemetryProjectContributionRegistry.CONTRIBUTION_ABI_VERSION;
        }

        @Nonnull
        @Override
        public Map<String, Object> candidate() {
            return candidate;
        }

        @Nonnull
        @Override
        public Map<String, Object> dispatch(@Nonnull String token,
                                             @Nonnull String operation,
                                             @Nonnull Map<String, Object> payload,
                                             @Nullable Throwable throwable) {
            TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
            if (active == null) {
                return Map.of("accepted", false, "reason", "coordinator_unavailable");
            }
            return active.dispatchProjectContribution(token, operation, payload, throwable);
        }
    }

    private final class EmbeddedCommandRuntime implements TelemetryCommandRuntime {
        @Override
        public boolean ownsActiveCoordinator() {
            return EmbeddedTelemetryService.this.ownsActiveCoordinator();
        }

        @Nullable
        @Override
        public String activeCoordinatorProviderId() {
            return EmbeddedTelemetryService.this.activeCoordinatorProviderId();
        }

        @Override
        public int registeredProjectCount() {
            return EmbeddedTelemetryService.this.registeredProjectCount();
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> projects() {
            return EmbeddedTelemetryService.this.projects();
        }

        @Nullable
        @Override
        public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
            return EmbeddedTelemetryService.this.findProject(projectId);
        }

        @Nonnull
        @Override
        public TelemetryRuntimeDiagnostics diagnostics() {
            return EmbeddedTelemetryService.this.consentDiagnostics();
        }

        @Nullable
        @Override
        public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
            return EmbeddedTelemetryService.this.projectDiagnostics(projectId);
        }

        @Override
        public int pendingReports(@Nullable String projectId) {
            return EmbeddedTelemetryService.this.pendingReports(projectId);
        }

        @Nonnull
        @Override
        public String lastFlushResult() {
            return EmbeddedTelemetryService.this.lastFlushResult();
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            return EmbeddedTelemetryService.this.requestFlush(projectId);
        }

        @Nonnull
        @Override
        public TelemetryServerVerificationResult requestServerVerification() {
            return EmbeddedTelemetryService.this.requestServerVerification();
        }

        @Nonnull
        @Override
        public TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
            return EmbeddedTelemetryService.this.requestServerVerification(claimToken);
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return EmbeddedTelemetryService.this.captureTestReport(projectId, detail);
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
            return EmbeddedTelemetryService.this.unreviewedConsentProjects();
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
            return EmbeddedTelemetryService.this.applyConsent(projectId, snapshot);
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            return EmbeddedTelemetryService.this.markConsentReviewed(projectId);
        }

        @Override
        public boolean openConsentPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull PlayerRef playerRef,
                                       boolean firstRun) {
            return EmbeddedTelemetryService.this.openConsentPage(playerEntityRef, store, playerRef, firstRun);
        }

        @Override
        public boolean openReportPage(@Nonnull String projectId,
                                      @Nonnull Ref<EntityStore> playerEntityRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull TelemetryReportOpenRequest request) {
            return EmbeddedTelemetryService.this.openReportPage(projectId, playerEntityRef, store, playerRef, request);
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> manualReportProjects() {
            return EmbeddedTelemetryService.this.manualReportProjects();
        }

        @Nullable
        @Override
        public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
            return EmbeddedTelemetryService.this.findManualReportProject(projectId);
        }

        @Nonnull
        @Override
        public TelemetryRuntimeSettings.ManualReportSettings manualReportSettings() {
            return EmbeddedTelemetryService.this.manualReportSettings();
        }

        @Nonnull
        @Override
        public PlayerReportRuntimeContext currentPlayerReportRuntimeContext() {
            return EmbeddedTelemetryService.this.currentPlayerReportRuntimeContext();
        }

        @Nonnull
        @Override
        public ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                                    @Nonnull ManualReportSubmission submission,
                                                                    @Nullable PlayerReportRuntimeContext playerContext) {
            return EmbeddedTelemetryService.this.submitManualReport(projectId, submission, playerContext);
        }

        @Nonnull
        @Override
        public String manualReportReceiptStatus(@Nonnull String reportId) {
            return EmbeddedTelemetryService.this.manualReportReceiptStatus(reportId);
        }

        @Nonnull
        @Override
        public List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject) {
            return EmbeddedTelemetryService.this.manualReportsForReview(maxReportsPerProject);
        }

        @Override
        public boolean approveManualReport(@Nonnull String reportId) {
            return EmbeddedTelemetryService.this.approveManualReport(reportId);
        }

        @Override
        public boolean rejectManualReport(@Nonnull String reportId) {
            return EmbeddedTelemetryService.this.rejectManualReport(reportId);
        }

        @Nonnull
        @Override
        public List<String> submittedManualReportAuditLines(int maxLines) {
            return EmbeddedTelemetryService.this.submittedManualReportAuditLines(maxLines);
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
        public boolean recordBreadcrumb(@Nonnull String projectId,
                                        @Nonnull String category,
                                        @Nonnull String detail,
                                        @Nonnull Map<String, Object> context) {
            return service.recordBreadcrumb(projectId, category, detail, context);
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

        @Nonnull
        @Override
        public TelemetryServerVerificationResult requestServerVerification() {
            return service.requestServerVerification();
        }

        @Nonnull
        @Override
        public TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
            return service.requestServerVerification(claimToken);
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
        @Nonnull
        @Override
        public List<Map<String, Object>> manualReportProjectSummaries() {
            ArrayList<Map<String, Object>> projects = new ArrayList<>();
            for (TelemetryProjectRegistration project : service.manualReportProjects()) {
                if (project.descriptor().reports().enabled()) {
                    projects.add(projectSummary(project));
                }
            }
            return List.copyOf(projects);
        }

        @Nonnull
        @Override
        public Map<String, Object> findManualReportProjectSummary(@Nonnull String projectId) {
            TelemetryProjectRegistration project = service.findManualReportProject(projectId);
            return project == null ? Map.of() : projectSummary(project);
        }

        @Nonnull
        @Override
        public String manualReportReceiptStatus(@Nonnull String reportId) {
            return service.manualReportReceiptStatus(reportId);
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> manualReportsForReview(int maxReportsPerProject) {
            return TelemetryManualReportBridgePayload.envelopeSummaries(service.manualReportsForReview(maxReportsPerProject));
        }

        @Override
        public boolean approveManualReport(@Nonnull String reportId) {
            return service.approveManualReport(reportId);
        }

        @Override
        public boolean rejectManualReport(@Nonnull String reportId) {
            return service.rejectManualReport(reportId);
        }

        @Nonnull
        @Override
        public List<String> submittedManualReportAuditLines(int maxLines) {
            return service.submittedManualReportAuditLines(maxLines);
        }
    }

    @Nonnull
    private static TelemetryProjectDescriptor descriptorFromSummary(@Nonnull Map<String, Object> summary,
                                                                    @Nonnull String projectId,
                                                                    @Nonnull String displayName,
                                                                    @Nonnull String runtimeMode) {
        String descriptorJson = stringValue(summary.get("descriptorJson"));
        if (descriptorJson != null) {
            try {
                return TelemetryProjectDescriptor.fromJson(descriptorJson, null);
            } catch (RuntimeException ignored) {
            }
        }
        String reports = Boolean.TRUE.equals(summary.get("manualReportsEnabled"))
                ? ",\"reports\":{\"enabled\":true}"
                : "";
        return TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + escapeJson(projectId)
                        + "\",\"displayName\":\"" + escapeJson(displayName)
                        + "\",\"runtimeMode\":\"" + escapeJson(runtimeMode) + "\"" + reports + "}",
                null
        );
    }
    @Nonnull
    private static Map<String, Object> projectSummary(@Nonnull TelemetryProjectRegistration project) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", project.projectId());
        summary.put("displayName", project.displayName());
        summary.put("runtimeMode", project.runtimeMode());
        summary.put("pluginIdentifier", project.pluginIdentifier());
        summary.put("pluginVersion", project.pluginVersion());
        summary.put("descriptorJson", project.descriptor().toJson());
        summary.put("manualReportsEnabled", project.descriptor().reports().enabled());
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
        TelemetryProjectDescriptor descriptor = descriptorFromSummary(summary, projectId, displayName, runtimeMode);
        return new TelemetryProjectRegistration(
                descriptor,
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
                        new com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.CaptureOptions(false, false, false, false),
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
