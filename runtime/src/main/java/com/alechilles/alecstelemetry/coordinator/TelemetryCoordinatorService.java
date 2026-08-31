package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.diagnostic.TelemetryDiagnosticBundleBridge;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportKind;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryBreadcrumbBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerIntervalSnapshot;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Active runtime coordinator that owns telemetry capture, queueing, and upload for all projects.
 */
public final class TelemetryCoordinatorService {

    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryCoreEngine engine;
    private final TelemetryCoordinatorStatsHeartbeat statsHeartbeat;
    private List<TelemetryProjectRegistration> baseProjects;
    private List<TelemetryProjectRegistration> baseManualReportProjects;
    private Set<String> rejectedContributionTokens = Set.of();
    private volatile long contributionRevision = -1L;
    private final TelemetryConsentStateStore consentStateStore;
    private final TelemetryProjectOverrideStore overrideStore;

    public TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                       @Nonnull TelemetryDataPaths dataPaths,
                                       @Nonnull List<TelemetryProjectRegistration> projects,
                                       @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                       @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                       @Nonnull CrashReportClient client,
                                       @Nullable HytaleLogger logger,
                                       @Nullable ScheduledExecutorService executor) {
        this(settings, dataPaths, projects, manualReportProjects, loadedMods, client, logger, executor, (IntSupplier) null);
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
        this.baseProjects = List.copyOf(projects);
        this.baseManualReportProjects = List.copyOf(manualReportProjects);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.statsHeartbeat = onlinePlayers == null
                ? null
                : new TelemetryCoordinatorStatsHeartbeat(engine, onlinePlayers, executor, logger);
    }

    private TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                        @Nonnull TelemetryDataPaths dataPaths,
                                        @Nonnull List<TelemetryProjectRegistration> projects,
                                        @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                                        @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                        @Nonnull CrashReportClient client,
                                        @Nullable HytaleLogger logger,
                                        @Nullable ScheduledExecutorService executor,
                                        @Nullable Supplier<TelemetryPlayerIntervalSnapshot> playerIntervalSnapshot) {
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
        this.baseProjects = List.copyOf(projects);
        this.baseManualReportProjects = List.copyOf(manualReportProjects);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.statsHeartbeat = playerIntervalSnapshot == null
                ? null
                : new TelemetryCoordinatorStatsHeartbeat(engine, playerIntervalSnapshot, executor, logger);
    }

    @Nonnull
    public static TelemetryCoordinatorService fromDiscoveryWithPlayerIntervalSnapshot(@Nonnull TelemetryRuntimeSettings settings,
                                                                                      @Nonnull TelemetryDataPaths dataPaths,
                                                                                      @Nonnull TelemetryRuntimeDiscoveryResult discovery,
                                                                                      @Nonnull CrashReportClient client,
                                                                                      @Nullable HytaleLogger logger,
                                                                                      @Nullable ScheduledExecutorService executor,
                                                                                      @Nullable Supplier<TelemetryPlayerIntervalSnapshot> playerIntervalSnapshot) {
        return new TelemetryCoordinatorService(
                settings,
                dataPaths,
                discovery.projects(),
                manualReportRuntimeProjects(discovery.projects(), discovery.consentProjects()),
                discovery.loadedMods(),
                client,
                logger,
                executor,
                playerIntervalSnapshot
        );
    }

    @Nonnull
    public static TelemetryCoordinatorService fromDiscovery(@Nonnull TelemetryRuntimeSettings settings,
                                                            @Nonnull TelemetryDataPaths dataPaths,
                                                            @Nonnull TelemetryRuntimeDiscoveryResult discovery,
                                                            @Nonnull CrashReportClient client,
                                                            @Nullable HytaleLogger logger,
                                                            @Nullable ScheduledExecutorService executor,
                                                            @Nullable IntSupplier onlinePlayers) {
        return fromDiscoveryWithPlayerIntervalSnapshot(
                settings,
                dataPaths,
                discovery,
                client,
                logger,
                executor,
                playerIntervalSnapshotFrom(onlinePlayers)
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

    @Nullable
    private static Supplier<TelemetryPlayerIntervalSnapshot> playerIntervalSnapshotFrom(@Nullable IntSupplier onlinePlayers) {
        return onlinePlayers == null
                ? null
                : () -> TelemetryPlayerIntervalSnapshot.single(onlinePlayers.getAsInt());
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

    /**
     * Reconciles a complete immutable contribution snapshot before exposing it through the
     * provider bridge. Invalid descriptor proposals leave the prior engine catalog untouched.
     */
    public synchronized boolean reconcileProjectContributions(long revision,
                                                               @Nonnull List<Map<String, Object>> contributions) {
        if (revision < contributionRevision) {
            return true;
        }
        LinkedHashMap<String, TelemetryProjectRegistration> byProjectId = new LinkedHashMap<>();
        for (TelemetryProjectRegistration baseProject : baseProjects) {
            byProjectId.put(baseProject.projectId().toLowerCase(Locale.ROOT), baseProject);
        }
        HashSet<String> rejectedTokens = new HashSet<>();
        for (Map<String, Object> contribution : contributions == null ? List.<Map<String, Object>>of() : contributions) {
            String token = stringValue(contribution == null ? null : contribution.get("token"));
            String proposedProjectId = stringValue(contribution == null ? null : contribution.get("projectId"));
            TelemetryProjectRegistration passiveBase = byProjectId.get(proposedProjectId.toLowerCase(Locale.ROOT));
            TelemetryProjectRegistration registration = registrationFromContribution(contribution, passiveBase);
            if (registration == null) {
                return false;
            }
            String normalizedProjectId = registration.projectId().toLowerCase(Locale.ROOT);
            TelemetryProjectRegistration base = byProjectId.get(normalizedProjectId);
            if ("custom".equalsIgnoreCase(registration.destinationMode())
                    || (base != null && !canUpgradePassiveBase(base, registration))) {
                if (!token.isBlank()) {
                    rejectedTokens.add(token);
                }
                continue;
            }
            if (base != null && base.isPassiveDescriptor()) {
                List<String> additions = consentStateStore.addedSupportedCategories(
                        dataPaths.consentStateFile(),
                        registration
                );
                Path overrideFile = dataPaths.projectOverrideFile(registration.projectId());
                if (!additions.isEmpty() && !overrideStore.disableCategories(overrideFile, additions)) {
                    if (!token.isBlank()) {
                        rejectedTokens.add(token);
                    }
                    continue;
                }
                TelemetryProjectOverride override = overrideStore.load(overrideFile);
                if (!additions.isEmpty() && override == null) {
                    if (!token.isBlank()) {
                        rejectedTokens.add(token);
                    }
                    continue;
                }
                if (override != null) {
                    registration = registration.withOverride(override);
                }
            }
            byProjectId.put(normalizedProjectId, registration);
        }
        ArrayList<TelemetryProjectRegistration> runtimeProjects = new ArrayList<>(byProjectId.values());
        boolean reconciled = engine.reconcileProjects(runtimeProjects, mergeManualProjects(runtimeProjects));
        if (reconciled) {
            rejectedContributionTokens = Set.copyOf(rejectedTokens);
            contributionRevision = revision;
        }
        return reconciled;
    }

    /** Adds a conventional host descriptor discovered after a contribution-only lease started. */
    public synchronized boolean ensureBaseProject(@Nonnull TelemetryProjectRegistration registration) {
        String normalizedProjectId = registration.projectId().toLowerCase(Locale.ROOT);
        TelemetryProjectRegistration current = engine.projects().stream()
                .filter(project -> project.projectId().equalsIgnoreCase(registration.projectId()))
                .findFirst()
                .orElse(null);
        if (current != null) {
            TelemetryProjectRegistration establishedBase = baseProjects.stream()
                    .filter(project -> project.projectId().equalsIgnoreCase(registration.projectId()))
                    .findFirst()
                    .orElse(null);
            return establishedBase != null && establishedBase.equals(registration);
        }
        List<TelemetryProjectRegistration> previousBaseProjects = baseProjects;
        List<TelemetryProjectRegistration> previousBaseManualProjects = baseManualReportProjects;
        LinkedHashMap<String, TelemetryProjectRegistration> updatedBase = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : baseProjects) {
            updatedBase.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        updatedBase.put(normalizedProjectId, registration);
        baseProjects = List.copyOf(updatedBase.values());

        LinkedHashMap<String, TelemetryProjectRegistration> updatedManual = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : baseManualReportProjects) {
            updatedManual.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        updatedManual.putIfAbsent(normalizedProjectId, registration);
        baseManualReportProjects = List.copyOf(updatedManual.values());
        boolean reconciled = reconcileProjectContributions(
                TelemetryProjectContributionRegistry.revision(),
                TelemetryProjectContributionRegistry.activeContributions()
        );
        if (!reconciled) {
            baseProjects = previousBaseProjects;
            baseManualReportProjects = previousBaseManualProjects;
        }
        return reconciled;
    }

    @Nonnull
    public Map<String, Object> dispatchProjectContribution(@Nonnull String token,
                                                           @Nonnull String operation,
                                                           @Nonnull Map<String, Object> payload,
                                                           @Nullable Throwable throwable) {
        TelemetryProjectRegistration project = contributionProjectForToken(token);
        if (project == null) {
            return Map.of("accepted", false, "reason", "contribution_not_found");
        }
        try {
            return dispatchContribution(project, operation, payload, throwable);
        } catch (Throwable failure) {
            return Map.of("accepted", false, "reason", "contribution_dispatch_failed");
        }
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

    public boolean isDiagnosticsEnabled(@Nonnull String projectId) {
        return engine.isDiagnosticsEnabled(projectId);
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
        return recordBreadcrumb(projectId, TelemetryBreadcrumbContext.of(category, detail));
    }

    public boolean recordBreadcrumb(@Nonnull String projectId,
                                    @Nonnull String category,
                                    @Nonnull String detail,
                                    @Nonnull Map<String, Object> context) {
        return recordBreadcrumb(
                projectId,
                TelemetryBreadcrumbBridgePayload.fromSummary(category, detail, context)
        );
    }

    public boolean recordBreadcrumb(@Nonnull String projectId,
                                    @Nonnull TelemetryBreadcrumbContext context) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.recordBreadcrumb(projectId, context);
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

    public boolean setDiagnosticsEnabled(@Nonnull String projectId, boolean enabled) {
        if (engine.findProject(projectId) == null) {
            return false;
        }
        engine.setDiagnosticsEnabled(projectId, enabled);
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

    public boolean captureExceptionalWorldRemovalForProject(@Nonnull String projectId,
                                                            @Nullable Throwable throwable,
                                                            @Nullable String worldName,
                                                            @Nullable String removalReason,
                                                            @Nullable String possibleFailureCause) {
        return engine.captureExceptionalWorldRemovalForProject(
                projectId,
                throwable,
                worldName,
                removalReason,
                possibleFailureCause
        );
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

    @Nonnull
    public TelemetryServerVerificationResult requestServerVerification() {
        return requestServerVerification(null);
    }

    @Nonnull
    public TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
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
        int statsProjects = statsHeartbeatProjectCount();
        if (statsHeartbeat == null || statsProjects == 0) {
            return new TelemetryServerVerificationResult(
                    TelemetryServerVerificationResult.Status.NO_STATS_PROJECTS,
                    0,
                    new TelemetryCoreEngine.FlushSummary(0, 0, 0, "no_stats_projects")
            );
        }
        statsHeartbeat.emitHeartbeatNow();
        TelemetryCoreEngine.FlushSummary flushSummary = engine.flushPendingReportsNow("server-verification");
        return new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                statsProjects,
                flushSummary
        );
    }

    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return engine.captureTestReport(projectId, detail);
    }

    @Nonnull
    public Map<String, Object> submitDiagnosticBundle(@Nonnull String projectId,
                                                      @Nonnull Map<String, Object> bundle) {
        return TelemetryDiagnosticBundleBridge.resultToMap(engine.submitDiagnosticBundle(
                projectId,
                TelemetryDiagnosticBundleBridge.bundleFromMap(bundle)
        ));
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

    @Nullable
    public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
        return engine.findManualReportProject(projectId);
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
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        return engine.manualReportReceiptStatus(reportId);
    }

    void emitStatsHeartbeatNow() {
        if (statsHeartbeat != null) {
            statsHeartbeat.emitHeartbeatNow();
        }
    }

    private int statsHeartbeatProjectCount() {
        int count = 0;
        for (TelemetryProjectRegistration project : engine.projects()) {
            if (engine.isStatsEnabled(project.projectId()) && project.stats().allows(TelemetryCoordinatorStatsHeartbeat.EVENT_HEARTBEAT)) {
                count++;
            }
        }
        return count;
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

    @Nullable
    private TelemetryProjectRegistration contributionProjectForToken(@Nonnull String token) {
        if (rejectedContributionTokens.contains(token)) {
            return null;
        }
        for (Map<String, Object> contribution : TelemetryProjectContributionRegistry.activeContributions()) {
            if (token.equals(stringValue(contribution.get("token")))) {
                return engine.findProject(stringValue(contribution.get("projectId")));
            }
        }
        return null;
    }

    @Nullable
    private static TelemetryProjectRegistration registrationFromContribution(
            @Nullable Map<String, Object> contribution,
            @Nullable TelemetryProjectRegistration passiveBase) {
        if (contribution == null) {
            return null;
        }
        String projectId = stringValue(contribution.get("projectId"));
        String logicalIdentifier = stringValue(contribution.get("logicalPluginIdentifier"));
        String logicalVersion = stringValue(contribution.get("logicalPluginVersion"));
        String descriptorJson = stringValue(contribution.get("descriptorJson"));
        if (projectId == null || logicalIdentifier == null || logicalVersion == null || descriptorJson == null) {
            return null;
        }
        try {
            TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(descriptorJson, null);
            if (!descriptor.projectId().equalsIgnoreCase(projectId)
                    || descriptor.ownerPluginIdentifiers().stream().noneMatch(owner -> owner.equalsIgnoreCase(logicalIdentifier))) {
                return null;
            }
            if (descriptor.projectVersion() != null
                    && !descriptor.projectVersion().isBlank()
                    && !descriptor.projectVersion().equals(logicalVersion)) {
                return null;
            }
            String sourcePath = stringValue(contribution.get("sourcePath"));
            String hostPluginIdentifier = stringValue(contribution.get("hostPluginIdentifier"));
            String hostPluginVersion = stringValue(contribution.get("hostPluginVersion"));
            String descriptorHash = stringValue(contribution.get("descriptorHash"));
            String canonicalHash = descriptor.canonicalHash();
            if (!canonicalHash.equalsIgnoreCase(descriptorHash)) {
                boolean compatibleSchemaHash = descriptorHash.matches("(?i)[0-9a-f]{64}")
                        && passiveBase != null
                        && passiveBase.isPassiveDescriptor()
                        && canonicalHash.equalsIgnoreCase(passiveBase.declaredDescriptorHash());
                if (!compatibleSchemaHash) {
                    return null;
                }
                descriptorHash = canonicalHash;
            }
            return TelemetryProjectRegistration.contribution(
                    descriptor,
                    logicalIdentifier,
                    logicalVersion,
                    sourcePath.isBlank() ? null : Path.of(sourcePath),
                    hostPluginIdentifier,
                    hostPluginVersion,
                    descriptorHash
            );
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean canUpgradePassiveBase(@Nonnull TelemetryProjectRegistration base,
                                                 @Nonnull TelemetryProjectRegistration contribution) {
        String baseHash = base.declaredDescriptorHash();
        String contributionHash = contribution.declaredDescriptorHash();
        return base.isPassiveDescriptor()
                && base.pluginVersion().equals(contribution.pluginVersion())
                && base.ownerPluginIdentifiers().stream()
                .anyMatch(owner -> owner.equalsIgnoreCase(contribution.pluginIdentifier()))
                && baseHash != null
                && contributionHash != null
                && baseHash.equalsIgnoreCase(contributionHash);
    }

    @Nonnull
    private List<TelemetryProjectRegistration> mergeManualProjects(
            @Nonnull List<TelemetryProjectRegistration> runtimeProjects) {
        LinkedHashMap<String, TelemetryProjectRegistration> merged = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : runtimeProjects) {
            merged.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : baseManualReportProjects) {
            merged.putIfAbsent(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        return List.copyOf(merged.values());
    }

    @Nonnull
    private Map<String, Object> dispatchContribution(@Nonnull TelemetryProjectRegistration project,
                                                     @Nonnull String operation,
                                                     @Nonnull Map<String, Object> payload,
                                                     @Nullable Throwable throwable) {
        String normalized = operation.trim().toLowerCase(Locale.ROOT);
        boolean accepted;
        switch (normalized) {
            case "breadcrumb" -> accepted = recordBreadcrumb(
                    project.projectId(),
                    stringValue(payload.get("category"), "telemetry"),
                    stringValue(payload.get("detail"), ""),
                    mapValue(payload.get("context"))
            );
            case "error" -> accepted = recordError(
                    project.projectId(),
                    stringValue(payload.get("eventName"), "error"),
                    throwable,
                    mapValue(payload.get("details"))
            );
            case "lifecycle" -> accepted = recordLifecycle(
                    project.projectId(),
                    stringValue(payload.get("eventName"), "lifecycle"),
                    intValue(payload.get("durationMs")),
                    booleanValue(payload.get("success")),
                    mapValue(payload.get("details"))
            );
            case "performance" -> accepted = recordPerformance(
                    project.projectId(),
                    stringValue(payload.get("eventName"), "performance"),
                    intValue(payload.get("durationMs")),
                    nullableDoubleValue(payload.get("metricValue")),
                    mapValue(payload.get("details"))
            );
            case "usage" -> accepted = recordUsage(
                    project.projectId(),
                    stringValue(payload.get("eventName"), "usage"),
                    mapValue(payload.get("details"))
            );
            case "stats" -> accepted = recordStats(
                    project.projectId(),
                    stringValue(payload.get("eventName"), "stats"),
                    mapValue(payload.get("details"))
            );
            case "capture_setup" -> accepted = captureSetupFailure(project.projectId(), throwable);
            case "capture_start" -> accepted = captureStartFailure(project.projectId(), throwable);
            case "capture_world_removal" -> accepted = captureExceptionalWorldRemovalForProject(
                    project.projectId(),
                    throwable,
                    nullableStringValue(payload.get("worldName")),
                    nullableStringValue(payload.get("removalReason")),
                    nullableStringValue(payload.get("possibleFailureCause"))
            );
            case "test_report" -> accepted = captureTestReport(
                    project.projectId(),
                    nullableStringValue(payload.get("detail"))
            );
            case "flush" -> {
                TelemetryCoreEngine.FlushSummary summary = flushPendingReportsNow(
                        stringValue(payload.get("reason"), "manual"),
                        project.projectId()
                );
                LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                result.put("accepted", true);
                result.put("attempted", summary.attempted());
                result.put("uploaded", summary.uploaded());
                result.put("pending", summary.pendingAfter());
                if (summary.lastFailure() != null) {
                    result.put("lastFailure", summary.lastFailure());
                }
                return Map.copyOf(result);
            }
            case "manual_report" -> {
                ManualReportEnvelope.CreateResult result = engine.submitManualReport(
                        project.projectId(),
                        submissionFrom(mapValue(payload.get("submission"))),
                        playerContextFrom(mapValue(payload.get("playerContext")))
                );
                return manualReportResultToMap(result);
            }
            case "diagnostic_bundle" -> {
                return submitDiagnosticBundle(
                        project.projectId(),
                        mapValue(payload.get("bundle"))
                );
            }
            default -> {
                return Map.of("accepted", false, "reason", "unknown_contribution_operation");
            }
        }
        return Map.of("accepted", accepted);
    }

    @Nonnull
    private static String stringValue(@Nullable Object value, @Nonnull String fallback) {
        String normalized = stringValue(value);
        return normalized == null ? fallback : normalized;
    }

    @Nullable
    private static Double nullableDoubleValue(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
