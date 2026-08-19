package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCapabilities;
import com.alechilles.alecstelemetry.consent.TelemetryConsentMetricReporter;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentStateStore;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.reports.TelemetryReportCoordinator;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentRuntime;
import com.alechilles.alecstelemetry.runtime.TelemetryBreadcrumbBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryDataMigrator;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryManualReportBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryLoadedModSnapshotProvider;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerDiagnostics;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerMonitor;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryRuntimeProviderHandle implements TelemetryRuntimeHostHandle, TelemetryRuntimeOperations, TelemetryConsentRuntime, TelemetryCommandRuntime {
    private static final PluginIdentifier SPARK_PLUGIN_IDENTIFIER = new PluginIdentifier("spark", "spark");

    private final TelemetryRuntimeBootstrapRequest request;
    private final TelemetryRuntimeSettings settings;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryCoordinatorService coordinator;
    private final ProviderBridge bridge;
    private final TelemetryRuntimePluginEvents pluginEvents;
    private final TelemetryRuntimeCommandRegistrar commandRegistrar;
    private final TelemetryConsentCoordinator consentCoordinator;
    private final TelemetryConsentMetricReporter consentMetricReporter;
    private final TelemetryRuntimeApi api;
    private final TelemetryProjectOverrideStore overrideStore;
    private final TelemetryConsentStateStore consentStateStore;
    private final HytaleLogger logger;
    private final SparkProfilerMonitor sparkProfilerMonitor;
    private final List<String> registrationWarnings;
    private List<TelemetryProjectRegistration> consentProjects;

    @Nonnull
    static TelemetryRuntimeProviderHandle create(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        HytaleLogger logger = request.plugin().getLogger();
        TelemetryDataPaths dataPaths = request.embedded()
                ? TelemetryDataPaths.forSharedCoordinator(request.plugin())
                : TelemetryDataPaths.from(request.plugin());
        TelemetryRuntimeSettings settings = loadMigratedSettings(dataPaths, logger);
        List<TelemetryProjectRegistration> providerRegistrations = providerRegistrations(request, dataPaths, logger);
        List<CrashReportEnvelope.LoadedModMetadata> fallbackLoadedMods = providerRegistrations.stream()
                .map(registration -> new CrashReportEnvelope.LoadedModMetadata(
                        registration.pluginIdentifier(),
                        registration.pluginVersion()
                ))
                .toList();
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscovery(logger).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.hytalePluginManager(fallbackLoadedMods, logger)
        );
        for (TelemetryProjectRegistration providerRegistration : providerRegistrations) {
            discovery = discovery.withProviderRegistration(providerRegistration);
        }
        TelemetryPlayerCounter playerCounter = new TelemetryPlayerCounter();
        CrashReportClient client = new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger);
        TelemetryCoordinatorService coordinator = TelemetryCoordinatorService.fromDiscoveryWithPlayerIntervalSnapshot(
                settings,
                dataPaths,
                discovery,
                client,
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                playerCounter::snapshotAndResetInterval
        );
        return new TelemetryRuntimeProviderHandle(
                request,
                candidate(request, dataPaths),
                settings,
                dataPaths,
                coordinator,
                discovery.consentProjects(),
                discovery.registrationWarnings(),
                playerCounter,
                logger,
                new TelemetryRuntimeCommandRegistrar(),
                client
        );
    }

    @Nonnull
    static TelemetryRuntimeSettings loadMigratedSettings(@Nonnull TelemetryDataPaths dataPaths,
                                                         @Nullable HytaleLogger logger) {
        TelemetryDataMigrator.migrate(dataPaths, logger);
        return TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
    }

    @Nullable
    static TelemetryProjectRegistration providerRegistration(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                                             @Nonnull TelemetryDataPaths dataPaths,
                                                             @Nullable HytaleLogger logger) {
        List<TelemetryProjectRegistration> registrations = providerRegistrations(request, dataPaths, logger);
        return registrations.isEmpty() ? null : registrations.getFirst();
    }

    @Nonnull
    static List<TelemetryProjectRegistration> providerRegistrations(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                                                    @Nonnull TelemetryDataPaths dataPaths,
                                                                    @Nullable HytaleLogger logger) {
        ArrayList<TelemetryProjectRegistration> registrations = new ArrayList<>();
        if (request.standalone()) {
            TelemetryProjectOverride override = new TelemetryProjectOverrideStore(logger)
                    .load(dataPaths.projectOverrideFile(TelemetrySelfProjectRegistration.PROJECT_ID));
            registrations.add(TelemetrySelfProjectRegistration.create(
                    request.providerPluginIdentifier(),
                    request.providerPluginVersion(),
                    request.sourcePath(),
                    override
            ));
            return List.copyOf(registrations);
        }
        TelemetryProjectRegistration embeddedProvider = embeddedProviderRegistration(request, dataPaths, logger);
        if (embeddedProvider != null) {
            registrations.add(embeddedProvider);
        }
        TelemetryProjectOverride override = new TelemetryProjectOverrideStore(logger)
                .load(dataPaths.projectOverrideFile(TelemetrySelfProjectRegistration.PROJECT_ID));
        registrations.add(TelemetrySelfProjectRegistration.create(
                "Alechilles:Alec's Telemetry!",
                request.runtimeVersion(),
                request.sourcePath(),
                override
        ));
        return List.copyOf(registrations);
    }

    @Nullable
    private static TelemetryProjectRegistration embeddedProviderRegistration(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                                                             @Nonnull TelemetryDataPaths dataPaths,
                                                                             @Nullable HytaleLogger logger) {
        TelemetryProjectDescriptor descriptor = request.embeddedDescriptor();
        if (!request.embedded() || descriptor == null) {
            return null;
        }
        TelemetryProjectOverride override = embeddedProviderOverride(request, dataPaths, logger, descriptor.projectId());
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                request.providerPluginIdentifier(),
                request.providerPluginVersion(),
                request.sourcePath()
        );
        return override == null ? registration : registration.withOverride(override);
    }

    @Nullable
    private static TelemetryProjectOverride embeddedProviderOverride(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                                                     @Nonnull TelemetryDataPaths dataPaths,
                                                                     @Nullable HytaleLogger logger,
                                                                     @Nonnull String projectId) {
        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(logger);
        TelemetryProjectOverride sharedOverride = store.load(dataPaths.projectOverrideFile(projectId));
        if (sharedOverride != null) {
            return sharedOverride;
        }
        Path legacyOverrideFile = dataPaths.legacyEmbeddedProjectOverrideFile(
                request.providerPluginIdentifier(),
                projectId
        );
        return legacyOverrideFile == null ? null : store.load(legacyOverrideFile);
    }

    TelemetryRuntimeProviderHandle(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                   @Nonnull TelemetryRuntimeCandidate candidate,
                                   @Nonnull TelemetryRuntimeSettings settings,
                                   @Nonnull TelemetryDataPaths dataPaths,
                                   @Nonnull TelemetryCoordinatorService coordinator,
                                   @Nonnull List<TelemetryProjectRegistration> consentProjects,
                                   @Nonnull List<String> registrationWarnings,
                                   @Nonnull TelemetryPlayerCounter playerCounter,
                                   @Nullable HytaleLogger logger,
                                   @Nonnull TelemetryRuntimeCommandRegistrar commandRegistrar,
                                   @Nonnull CrashReportClient consentMetricClient) {
        this(
                request,
                candidate,
                settings,
                dataPaths,
                coordinator,
                consentProjects,
                registrationWarnings,
                playerCounter,
                logger,
                commandRegistrar,
                consentMetricClient,
                null
        );
    }

    TelemetryRuntimeProviderHandle(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                   @Nonnull TelemetryRuntimeCandidate candidate,
                                   @Nonnull TelemetryRuntimeSettings settings,
                                   @Nonnull TelemetryDataPaths dataPaths,
                                   @Nonnull TelemetryCoordinatorService coordinator,
                                   @Nonnull List<TelemetryProjectRegistration> consentProjects,
                                   @Nonnull List<String> registrationWarnings,
                                   @Nonnull TelemetryPlayerCounter playerCounter,
                                   @Nullable HytaleLogger logger,
                                   @Nonnull TelemetryRuntimeCommandRegistrar commandRegistrar,
                                   @Nonnull CrashReportClient consentMetricClient,
                                   @Nullable SparkProfilerMonitor monitor) {
        this.request = request;
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.coordinator = coordinator;
        this.bridge = new ProviderBridge(candidate, coordinator);
        this.commandRegistrar = commandRegistrar;
        this.consentCoordinator = new TelemetryConsentCoordinator(this, logger);
        this.consentMetricReporter = new TelemetryConsentMetricReporter(settings, consentMetricClient, candidate.runtimeVersion(), logger);
        this.api = new TelemetryRuntimeApiImpl(this);
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.consentStateStore = new TelemetryConsentStateStore(logger);
        this.logger = logger;
        this.sparkProfilerMonitor = monitor == null
                ? new SparkProfilerMonitor(
                        settings.sparkProfiler(),
                        () -> PluginManager.get().getPlugin(SPARK_PLUGIN_IDENTIFIER),
                        bridge::isActive,
                        logger
                )
                : monitor;
        this.consentProjects = List.copyOf(consentProjects);
        this.registrationWarnings = List.copyOf(registrationWarnings);
        this.pluginEvents = new TelemetryRuntimePluginEvents(this, playerCounter, logger);
        commandRegistrar.initialize(request.plugin(), this, logger);
    }

    @Override
    public void start() {
        pluginEvents.register(request.plugin());
        TelemetryCoordinatorRegistry.register(bridge);
    }

    @Override
    public void shutdown() {
        TelemetryCoordinatorRegistry.unregister(bridge.providerId());
        commandRegistrar.unregister();
        TelemetryRuntimeLocator.clearIfCurrent(api);
        pluginEvents.unregister();
        sparkProfilerMonitor.close();
    }

    @Override
    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && bridge.providerId().equals(active.providerId());
    }

    void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (ownsActiveCoordinator()) {
            commandRegistrar.onPlayerReady(event);
            refreshDiscoveredProjects();
            consentCoordinator.onPlayerReady(event);
        }
    }

    void onBoot() {
        if (ownsActiveCoordinator()) {
            refreshDiscoveredProjects();
        }
    }

    private void refreshDiscoveredProjects() {
        try {
            TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscovery(logger).discoverActive(
                    dataPaths,
                    TelemetryLoadedModSnapshotProvider.hytalePluginManager(coordinator.loadedMods(), logger)
            );
            refreshDiscoveredProjects(discovery);
        } catch (RuntimeException ex) {
            if (logger != null) {
                logger.at(java.util.logging.Level.WARNING).withCause(ex).log(
                        "Telemetry skipped late passive-project discovery after player ready."
                );
            }
        }
    }

    void refreshDiscoveredProjects(@Nonnull TelemetryRuntimeDiscoveryResult discovery) {
        boolean reconciled = false;
        for (TelemetryProjectRegistration project : discovery.projects()) {
            reconciled |= coordinator.ensureBaseProject(project);
        }
        if (reconciled) {
            consentProjects = mergedProjects(coordinator.projects(), discovery.consentProjects());
        }
    }

    void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        if (!ownsActiveCoordinator()
                || event.getRemovalReason() != RemoveWorldEvent.RemovalReason.EXCEPTIONAL
                || event.getWorld() == null
                || event.getWorld().getFailureException() == null) {
            return;
        }
        coordinator.captureExceptionalWorldRemoval(
                event.getWorld().getFailureException(),
                event.getWorld().getName(),
                event.getRemovalReason().name(),
                event.getWorld().getPossibleFailureCause() == null
                        ? null
                        : event.getWorld().getPossibleFailureCause().toString()
        );
    }

    @Nullable
    @Override
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
    }

    @Override
    public int registeredProjectCount() {
        return projects().size();
    }

    @Override
    public boolean ensureEmbeddedProject(@Nonnull TelemetryProjectRegistration project) {
        boolean reconciled = coordinator.ensureBaseProject(project);
        if (reconciled) {
            consentProjects = mergedProjects(coordinator.projects(), coordinator.manualReportProjects());
        }
        return reconciled;
    }

    @Nonnull
    @Override
    public TelemetryRuntimeApi api() {
        return api;
    }

    @Override
    public boolean isEnabled() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.isEnabled() : active.isEnabled();
    }

    @Nonnull
    @Override
    public List<TelemetryProjectRegistration> projects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (usesLocalCoordinator(active)) {
            return mergedProjects(coordinator.projects(), consentProjects);
        }
        return projectsFromSummaries(activeProjects(active));
    }

    @Nullable
    @Override
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (usesLocalCoordinator(active)) {
            TelemetryProjectRegistration project = coordinator.findProject(projectId);
            return project == null ? findConsentProject(projectId) : project;
        }
        TelemetryProjectRegistration project = projectFromSummary(active.findProjectSummary(projectId));
        return project == null ? projectFromSummary(active.consentProjectDiagnostics(projectId)) : project;
    }

    @Override
    public boolean isProjectEnabled(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.isProjectEnabled(projectId) : activeProjectEnabled(active, projectId);
    }

    @Override
    public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.setProjectEnabled(projectId, enabled) : active.setProjectEnabled(projectId, enabled);
    }

    @Override
    public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.setBreadcrumbsEnabled(projectId, enabled) : active.setBreadcrumbsEnabled(projectId, enabled);
    }
    @Nonnull
    @Override
    public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return List.of();
        }
        return consentStateStore.unreviewedProjects(dataPaths.consentStateFile(), currentConsentProjects());
    }

    @Nonnull
    @Override
    public List<String> addedConsentCategories(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return List.of();
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project == null
                ? List.of()
                : consentStateStore.addedSupportedCategories(dataPaths.consentStateFile(), project);
    }

    @Override
    public boolean isConsentNoticeShown(@Nonnull String viewerKey,
                                        @Nonnull List<TelemetryProjectRegistration> projects) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return usesLocalCoordinator(active)
                && consentStateStore.isNoticeShown(dataPaths.consentStateFile(), viewerKey, projects);
    }

    @Override
    public boolean markConsentNoticeShown(@Nonnull String viewerKey,
                                          @Nonnull List<TelemetryProjectRegistration> projects) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return usesLocalCoordinator(active)
                && consentStateStore.markNoticeShown(dataPaths.consentStateFile(), viewerKey, projects);
    }

    @Override
    public void recordConsentPromptShown(@Nonnull String viewerKey,
                                         @Nonnull List<TelemetryProjectRegistration> projects) {
        recordConsentFunnelEvents(viewerKey, "prompt_shown", projects);
    }

    @Override
    public void recordConsentUiOpened(@Nonnull String viewerKey,
                                      @Nonnull List<TelemetryProjectRegistration> projects) {
        recordConsentFunnelEvents(viewerKey, "ui_opened", projects);
    }

    private void recordConsentFunnelEvents(@Nonnull String viewerKey,
                                           @Nonnull String eventType,
                                           @Nonnull List<TelemetryProjectRegistration> projects) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return;
        }
        for (TelemetryProjectRegistration project : projects) {
            if (!consentStateStore.markFunnelEventRecorded(dataPaths.consentStateFile(), viewerKey, eventType, project)) {
                continue;
            }
            if ("prompt_shown".equals(eventType)) {
                consentMetricReporter.recordPromptShown(project);
            } else if ("ui_opened".equals(eventType)) {
                consentMetricReporter.recordUiOpened(project);
            }
        }
    }

    @Override
    public boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsentToAll(snapshotSummary(snapshot));
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
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = buildLocalProjectDiagnostics(project);
            TelemetryConsentSnapshot current = diagnostics.consentSnapshot();
            appliedAll &= applyLocalConsent(project.projectId(), current.withCategory(category, enabled));
        }
        return appliedAll;
    }

    @Override
    public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.applyConsent(projectId, snapshotSummary(snapshot));
        }
        return applyLocalConsent(projectId, snapshot);
    }

    private boolean applyLocalConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryProjectRegistration project = findConsentProject(projectId);
        if (project == null) {
            return false;
        }
        boolean reviewed = consentStateStore.isReviewed(dataPaths.consentStateFile(), project);
        TelemetryConsentSnapshot previous = buildLocalProjectDiagnostics(project).consentSnapshot();
        TelemetryConsentSnapshot supported = supportedSnapshot(project);
        TelemetryConsentSnapshot normalized = clampToSupported(snapshot, supported);
        Path overrideFile = dataPaths.projectOverrideFile(project.projectId());
        boolean saved = saveConsentOverride(overrideFile, normalized, supported);
        if (!saved) {
            return false;
        }
        TelemetryProjectOverride override = overrideStore.load(overrideFile);
        if (override != null) {
            replaceConsentProject(project.withOverride(override));
        }
        boolean applied = applyRuntimeConsent(project.projectId(), normalized);
        if (applied && reviewed) {
            consentMetricReporter.recordConsentChange(project, previous, normalized, supported);
        }
        return applied;
    }

    @Override
    public boolean markConsentReviewed(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return active.markConsentReviewed(projectId);
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        if (project == null) {
            return false;
        }
        boolean alreadyReviewed = consentStateStore.isReviewed(dataPaths.consentStateFile(), project);
        boolean marked = consentStateStore.markReviewed(dataPaths.consentStateFile(), project);
        if (marked && !alreadyReviewed) {
            TelemetryConsentSnapshot snapshot = buildLocalProjectDiagnostics(project).consentSnapshot();
            consentMetricReporter.recordFirstReview(project, snapshot, supportedSnapshot(project));
            consentMetricReporter.recordFirstReviewCompleted(project);
        }
        return marked;
    }

    @Nonnull
    @Override
    public TelemetryRuntimeDiagnostics consentDiagnostics() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentDiagnostics();
            return activeDiagnostics.isEmpty() ? unavailableConsentDiagnostics(active) : diagnosticsFromSummary(activeDiagnostics);
        }
        return localConsentDiagnostics();
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics localConsentDiagnostics() {
        List<TelemetryProjectRegistration> currentProjects = currentConsentProjects();
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projectDiagnostics = new ArrayList<>(currentProjects.size());
        for (TelemetryProjectRegistration project : currentProjects) {
            projectDiagnostics.add(buildLocalProjectDiagnostics(project));
        }
        return new TelemetryRuntimeDiagnostics(
                coordinator.isEnabled(),
                currentProjects.size(),
                coordinator.loadedMods().size(),
                coordinator.pendingReports(null),
                coordinator.flushInProgress(),
                coordinator.lastFlushResult(),
                dataPaths.modsDirectory() == null ? null : dataPaths.modsDirectory().toString(),
                registrationWarnings,
                List.copyOf(projectDiagnostics)
        );
    }

    @Nullable
    @Override
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics consentProjectDiagnostics(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            Map<String, Object> activeDiagnostics = active.consentProjectDiagnostics(projectId);
            return activeDiagnostics.isEmpty() ? null : projectDiagnosticsFromSummary(activeDiagnostics);
        }
        TelemetryProjectRegistration project = findConsentProject(projectId);
        return project == null ? null : buildLocalProjectDiagnostics(project);
    }

    @Override
    public boolean requestFlush(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.requestFlush(projectId) : active.requestFlush(projectId);
    }

    @Nonnull
    @Override
    public TelemetryServerVerificationResult requestServerVerification() {
        return requestServerVerification(null);
    }

    @Nonnull
    @Override
    public TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            TelemetryServerVerificationResult activeResult = active.requestServerVerification(claimToken);
            if (activeResult.status() != TelemetryServerVerificationResult.Status.UNAVAILABLE) {
                return activeResult;
            }
        }
        return coordinator.requestServerVerification(claimToken);
    }

    @Override
    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.captureTestReport(projectId, detail) : active.captureTestReport(projectId, detail);
    }

    @Nonnull
    @Override
    public TelemetryRuntimeDiagnostics diagnostics() {
        return consentDiagnostics();
    }

    @Nonnull
    @Override
    public SparkProfilerDiagnostics sparkProfilerDiagnostics() {
        return sparkProfilerMonitor.diagnostics();
    }

    @Nonnull
    @Override
    public List<SparkProfileSnapshot> sparkProfilerHistory() {
        return sparkProfilerMonitor.history();
    }

    @Nullable
    @Override
    public TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId) {
        return consentProjectDiagnostics(projectId);
    }

    @Override
    public int pendingReports(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            if (projectId == null) {
                return diagnostics().totalPendingReports();
            }
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = projectDiagnostics(projectId);
            return diagnostics == null ? 0 : diagnostics.pendingReports();
        }
        return coordinator.pendingReports(projectId);
    }

    @Nonnull
    @Override
    public String lastFlushResult() {
        return diagnostics().lastFlushResult();
    }

    @Override
    public boolean openConsentPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef playerRef,
                                   boolean firstRun) {
        return consentCoordinator.openConsentPage(playerEntityRef, store, playerRef, firstRun);
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
    @Override
    public List<TelemetryProjectRegistration> manualReportProjects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return manualReportProjectsFromSummaries(active.manualReportProjectSummaries());
        }
        ArrayList<TelemetryProjectRegistration> projects = new ArrayList<>();
        for (TelemetryProjectRegistration project : coordinator.manualReportProjects()) {
            if (project.descriptor().reports().enabled()) {
                projects.add(project);
            }
        }
        return List.copyOf(projects);
    }

    @Nullable
    @Override
    public TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return projectFromSummary(active.findManualReportProjectSummary(projectId));
        }
        return coordinator.findManualReportProject(projectId);
    }

    @Nonnull
    @Override
    public TelemetryRuntimeSettings.ManualReportSettings manualReportSettings() {
        return settings.manualReports();
    }

    @Nonnull
    @Override
    public PlayerReportRuntimeContext currentPlayerReportRuntimeContext() {
        return new PlayerReportRuntimeContext(false, 0, null, coordinator.loadedMods());
    }

    @Nonnull
    @Override
    public ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                                @Nonnull ManualReportSubmission submission,
                                                                @Nullable PlayerReportRuntimeContext playerContext) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (!usesLocalCoordinator(active)) {
            return manualReportResultFromMap(active.submitManualReport(
                    projectId,
                    submissionToMap(submission),
                    playerContextToMap(playerContext)
            ));
        }
        return manualReportResultFromMap(coordinator.submitManualReport(
                projectId,
                submissionToMap(submission),
                playerContextToMap(playerContext)
        ));
    }

    @Nonnull
    @Override
    public String manualReportReceiptStatus(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return !usesLocalCoordinator(active)
                ? active.manualReportReceiptStatus(reportId)
                : coordinator.manualReportReceiptStatus(reportId);
    }

    @Nonnull
    @Override
    public List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return !usesLocalCoordinator(active)
                ? TelemetryManualReportBridgePayload.envelopesFromSummaries(active.manualReportsForReview(maxReportsPerProject))
                : coordinator.manualReportsForReview(maxReportsPerProject);
    }

    @Override
    public boolean approveManualReport(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return !usesLocalCoordinator(active)
                ? active.approveManualReport(reportId)
                : coordinator.approveManualReport(reportId);
    }

    @Override
    public boolean rejectManualReport(@Nonnull String reportId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return !usesLocalCoordinator(active)
                ? active.rejectManualReport(reportId)
                : coordinator.rejectManualReport(reportId);
    }

    @Nonnull
    @Override
    public List<String> submittedManualReportAuditLines(int maxLines) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return !usesLocalCoordinator(active)
                ? active.submittedManualReportAuditLines(maxLines)
                : coordinator.submittedManualReportAuditLines(maxLines);
    }

    @Override
    public void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
        recordBreadcrumb(projectId, TelemetryBreadcrumbContext.of(category, detail));
    }

    @Override
    public void recordBreadcrumb(@Nonnull String projectId,
                                 @Nonnull TelemetryBreadcrumbContext context) {
        TelemetryBreadcrumbContext normalized = context.normalize();
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active == null) {
            coordinator.recordBreadcrumb(projectId, normalized);
        } else {
            active.recordBreadcrumb(
                    projectId,
                    normalized.category(),
                    normalized.detail(),
                    TelemetryBreadcrumbBridgePayload.summary(normalized)
            );
        }
    }

    @Override
    public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active == null) {
            coordinator.captureSetupFailure(projectId, throwable);
        } else {
            active.captureSetupFailure(projectId, throwable);
        }
    }

    @Override
    public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active == null) {
            coordinator.captureStartFailure(projectId, throwable);
        } else {
            active.captureStartFailure(projectId, throwable);
        }
    }

    @Override
    public void recordErrorWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable Throwable throwable,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        Map<String, Object> details = detailsFrom(context);
        if (active == null) {
            coordinator.recordError(projectId, eventName, throwable, details);
        } else {
            active.recordError(projectId, eventName, throwable, details);
        }
    }

    void recordSelfError(@Nonnull String eventName,
                         @Nullable Throwable throwable,
                         @Nonnull Map<String, ?> details) {
        TelemetryEventContext.Builder context = TelemetryEventContext.error()
                .featureKey("runtime")
                .entryPoint(eventName)
                .runtimeSide("server")
                .detail("component", "self_runtime");
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            if (entry != null && entry.getKey() != null) {
                if ("operation".equals(entry.getKey()) && entry.getValue() != null) {
                    context.operation(entry.getValue().toString());
                    context.detail("operationKind", entry.getValue());
                } else {
                    context.detail(entry.getKey(), entry.getValue());
                }
            }
        }
        recordErrorWithContext(TelemetrySelfProjectRegistration.PROJECT_ID, eventName, throwable, context.build());
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           int durationMs,
                                           boolean success,
                                           @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        Map<String, Object> details = detailsFrom(context);
        if (active == null) {
            coordinator.recordLifecycle(projectId, eventName, durationMs, success, details);
        } else {
            active.recordLifecycle(projectId, eventName, durationMs, success, details);
        }
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String projectId,
                                             @Nonnull String eventName,
                                             int durationMs,
                                             @Nullable Double metricValue,
                                             @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        Map<String, Object> details = detailsFrom(context);
        if (active == null) {
            coordinator.recordPerformance(projectId, eventName, durationMs, metricValue, details);
        } else {
            active.recordPerformance(projectId, eventName, durationMs, metricValue, details);
        }
    }

    @Override
    public void recordUsageWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        Map<String, Object> details = detailsFrom(context);
        if (active == null) {
            coordinator.recordUsage(projectId, eventName, details);
        } else {
            active.recordUsage(projectId, eventName, details);
        }
    }

    @Override
    public void recordStatsWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        Map<String, Object> details = detailsFrom(context);
        if (active == null) {
            coordinator.recordStats(projectId, eventName, details);
        } else {
            active.recordStats(projectId, eventName, details);
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
                .map(TelemetryRuntimeProviderHandle::loadedModToMap)
                .toList());
        return Map.copyOf(values);
    }

    @Nonnull
    private static Map<String, Object> loadedModToMap(@Nonnull com.alechilles.alecstelemetry.crash.CrashReportEnvelope.LoadedModMetadata loadedMod) {
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

    @Nonnull
    TelemetryRuntimeBootstrapRequest request() {
        return request;
    }

    private boolean saveConsentOverride(@Nonnull Path overrideFile,
                                        @Nonnull TelemetryConsentSnapshot snapshot,
                                        @Nonnull TelemetryConsentSnapshot supported) {
        return overrideStore.saveConsentSnapshot(overrideFile, snapshot, supported);
    }

    private boolean applyRuntimeConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            boolean runtimeProject = !active.findProjectSummary(projectId).isEmpty();
            boolean applied = active.setProjectEnabled(projectId, snapshot.projectEnabled());
            if (!runtimeProject && !applied) {
                return findConsentProject(projectId) != null;
            }
            if (runtimeProject) {
                applied &= active.setCrashEnabled(projectId, snapshot.crashEnabled());
                applied &= active.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
                applied &= active.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
                applied &= active.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
                applied &= active.setUsageEnabled(projectId, snapshot.usageEnabled());
                applied &= active.setStatsEnabled(projectId, snapshot.statsEnabled());
                applied &= active.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
            }
            return applied;
        }
        boolean applied = true;
        if (coordinator.findProject(projectId) != null || findManualReportProject(projectId) != null) {
            applied &= coordinator.setProjectEnabled(projectId, snapshot.projectEnabled());
        }
        if (coordinator.findProject(projectId) != null) {
            applied &= coordinator.setCrashEnabled(projectId, snapshot.crashEnabled());
            applied &= coordinator.setErrorEventsEnabled(projectId, snapshot.errorEnabled());
            applied &= coordinator.setLifecycleEventsEnabled(projectId, snapshot.lifecycleEnabled());
            applied &= coordinator.setPerformanceEnabled(projectId, snapshot.performanceEnabled());
            applied &= coordinator.setUsageEnabled(projectId, snapshot.usageEnabled());
            applied &= coordinator.setStatsEnabled(projectId, snapshot.statsEnabled());
            applied &= coordinator.setBreadcrumbsEnabled(projectId, snapshot.breadcrumbsEnabled());
        }
        return applied;
    }

    @Nonnull
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics buildLocalProjectDiagnostics(
            @Nonnull TelemetryProjectRegistration project) {
        CrashReportClient.DeliveryTarget target = project.resolveDeliveryTarget(settings);
        boolean registeredForRuntime = coordinator.findProject(project.projectId()) != null;
        int pendingReports = registeredForRuntime ? coordinator.pendingReports(project.projectId()) : 0;
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
                registeredForRuntime ? coordinator.isCrashEnabled(project.projectId()) : project.isCrashTelemetryEnabled(),
                registeredForRuntime ? coordinator.isErrorEventsEnabled(project.projectId()) : project.events().errors().enabled(),
                registeredForRuntime ? coordinator.isLifecycleEventsEnabled(project.projectId()) : project.events().lifecycle().enabled(),
                registeredForRuntime ? coordinator.isPerformanceEnabled(project.projectId()) : project.performance().enabled(),
                registeredForRuntime ? coordinator.isUsageEnabled(project.projectId()) : project.usage().enabled(),
                registeredForRuntime ? coordinator.isStatsEnabled(project.projectId()) : project.stats().enabled(),
                registeredForRuntime ? coordinator.isBreadcrumbsEnabled(project.projectId()) : project.events().breadcrumbs().enabled(),
                supportsCrash(project),
                project.descriptor().events().errors().supported(),
                project.descriptor().events().lifecycle().supported(),
                project.descriptor().performance().supported(),
                project.descriptor().usage().supported(),
                project.descriptor().stats().supported(),
                project.descriptor().events().breadcrumbs().supported()
        );
    }

    @Nonnull
    private static Map<String, Object> diagnosticsSummary(@Nonnull TelemetryRuntimeDiagnostics diagnostics) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", diagnostics.enabled());
        summary.put("registeredProjects", diagnostics.registeredProjects());
        summary.put("loadedMods", diagnostics.loadedMods());
        summary.put("totalPendingReports", diagnostics.totalPendingReports());
        summary.put("flushInProgress", diagnostics.flushInProgress());
        summary.put("lastFlushResult", diagnostics.lastFlushResult());
        putIfPresent(summary, "modsDirectory", diagnostics.modsDirectory());
        summary.put("registrationWarnings", diagnostics.registrationWarnings());
        summary.put("projects", diagnostics.projects().stream()
                .map(TelemetryRuntimeProviderHandle::projectDiagnosticsSummary)
                .toList());
        return Map.copyOf(summary);
    }

    @Nonnull
    private static Map<String, Object> projectDiagnosticsSummary(
            @Nonnull TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", project.projectId());
        summary.put("displayName", project.displayName());
        summary.put("enabled", project.enabled());
        summary.put("overridePresent", project.overridePresent());
        summary.put("destinationMode", project.destinationMode());
        putIfPresent(summary, "endpoint", project.endpoint());
        summary.put("pendingReports", project.pendingReports());
        summary.put("pluginIdentifier", project.pluginIdentifier());
        summary.put("pluginVersion", project.pluginVersion());
        putIfPresent(summary, "sourcePath", project.sourcePath());
        putIfPresent(summary, "iconTexturePath", project.iconTexturePath());
        summary.put("packagePrefixes", project.packagePrefixes());
        summary.put("runtimeMode", project.runtimeMode());
        summary.put("crashEnabled", project.crashEnabled());
        summary.put("errorEnabled", project.errorEnabled());
        summary.put("lifecycleEnabled", project.lifecycleEnabled());
        summary.put("performanceEnabled", project.performanceEnabled());
        summary.put("usageEnabled", project.usageEnabled());
        summary.put("statsEnabled", project.statsEnabled());
        summary.put("breadcrumbsEnabled", project.breadcrumbsEnabled());
        summary.put("crashSupported", project.crashSupported());
        summary.put("errorSupported", project.errorSupported());
        summary.put("lifecycleSupported", project.lifecycleSupported());
        summary.put("performanceSupported", project.performanceSupported());
        summary.put("usageSupported", project.usageSupported());
        summary.put("statsSupported", project.statsSupported());
        summary.put("breadcrumbsSupported", project.breadcrumbsSupported());
        return Map.copyOf(summary);
    }

    @Nonnull
    private static TelemetryRuntimeDiagnostics diagnosticsFromSummary(@Nonnull Map<String, Object> summary) {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projects = new ArrayList<>();
        Object rawProjects = summary.get("projects");
        if (rawProjects instanceof List<?> projectList) {
            for (Object rawProject : projectList) {
                if (rawProject instanceof Map<?, ?> projectSummary) {
                    projects.add(projectDiagnosticsFromSummary(stringObjectMap(projectSummary)));
                }
            }
        }
        return new TelemetryRuntimeDiagnostics(
                booleanValue(summary, "enabled"),
                intValue(summary, "registeredProjects"),
                intValue(summary, "loadedMods"),
                intValue(summary, "totalPendingReports"),
                booleanValue(summary, "flushInProgress"),
                firstNonBlank(stringValue(summary.get("lastFlushResult")), "<unavailable>"),
                stringValue(summary.get("modsDirectory")),
                stringList(summary.get("registrationWarnings")),
                List.copyOf(projects)
        );
    }

    @Nonnull
    private static TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnosticsFromSummary(
            @Nonnull Map<String, Object> summary) {
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                firstNonBlank(stringValue(summary.get("projectId")), "unknown"),
                firstNonBlank(stringValue(summary.get("displayName")), "Unknown Project"),
                booleanValue(summary, "enabled"),
                booleanValue(summary, "overridePresent"),
                firstNonBlank(stringValue(summary.get("destinationMode")), "hosted"),
                stringValue(summary.get("endpoint")),
                intValue(summary, "pendingReports"),
                firstNonBlank(stringValue(summary.get("pluginIdentifier")), "unknown:unknown"),
                firstNonBlank(stringValue(summary.get("pluginVersion")), "unknown"),
                stringValue(summary.get("sourcePath")),
                stringValue(summary.get("iconTexturePath")),
                stringList(summary.get("packagePrefixes")),
                firstNonBlank(stringValue(summary.get("runtimeMode")), TelemetryProjectDescriptor.RUNTIME_MODE_DEPENDENCY),
                booleanValue(summary, "crashEnabled"),
                booleanValue(summary, "errorEnabled"),
                booleanValue(summary, "lifecycleEnabled"),
                booleanValue(summary, "performanceEnabled"),
                booleanValue(summary, "usageEnabled"),
                booleanValue(summary, "statsEnabled"),
                booleanValue(summary, "breadcrumbsEnabled"),
                booleanValue(summary, "crashSupported"),
                booleanValue(summary, "errorSupported"),
                booleanValue(summary, "lifecycleSupported"),
                booleanValue(summary, "performanceSupported"),
                booleanValue(summary, "usageSupported"),
                booleanValue(summary, "statsSupported"),
                booleanValue(summary, "breadcrumbsSupported")
        );
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

    @Nonnull
    private static Map<String, Object> snapshotSummary(@Nonnull TelemetryConsentSnapshot snapshot) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectEnabled", snapshot.projectEnabled());
        summary.put("crashEnabled", snapshot.crashEnabled());
        summary.put("errorEnabled", snapshot.errorEnabled());
        summary.put("lifecycleEnabled", snapshot.lifecycleEnabled());
        summary.put("performanceEnabled", snapshot.performanceEnabled());
        summary.put("usageEnabled", snapshot.usageEnabled());
        summary.put("statsEnabled", snapshot.statsEnabled());
        summary.put("breadcrumbsEnabled", snapshot.breadcrumbsEnabled());
        return Map.copyOf(summary);
    }

    @Nonnull
    private static TelemetryConsentSnapshot snapshotFromSummary(@Nonnull Map<String, Object> summary) {
        return new TelemetryConsentSnapshot(
                booleanValue(summary, "projectEnabled"),
                booleanValue(summary, "crashEnabled"),
                booleanValue(summary, "errorEnabled"),
                booleanValue(summary, "lifecycleEnabled"),
                booleanValue(summary, "performanceEnabled"),
                booleanValue(summary, "usageEnabled"),
                booleanValue(summary, "statsEnabled"),
                booleanValue(summary, "breadcrumbsEnabled")
        );
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
        TelemetryProjectRegistration runtimeProject = coordinator.findProject(normalized);
        if (runtimeProject != null) {
            return runtimeProject;
        }
        return consentProject;
    }

    @Nonnull
    private List<TelemetryProjectRegistration> currentConsentProjects() {
        LinkedHashMap<String, TelemetryProjectRegistration> projects = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : consentProjects) {
            projects.put(project.projectId().toLowerCase(Locale.ROOT), project);
        }
        for (TelemetryProjectRegistration project : coordinator.projects()) {
            String key = project.projectId().toLowerCase(Locale.ROOT);
            TelemetryProjectRegistration existing = projects.get(key);
            if (existing == null || !existing.hasOverride() || project.hasOverride()) {
                projects.put(key, project);
            }
        }
        return List.copyOf(projects.values());
    }


    private void replaceConsentProject(@Nonnull TelemetryProjectRegistration replacement) {
        ArrayList<TelemetryProjectRegistration> updated = new ArrayList<>(consentProjects.size());
        for (TelemetryProjectRegistration project : consentProjects) {
            updated.add(project.projectId().equalsIgnoreCase(replacement.projectId()) ? replacement : project);
        }
        consentProjects = List.copyOf(updated);
    }

    @Nonnull
    private static TelemetryRuntimeCandidate candidate(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                                       @Nonnull TelemetryDataPaths dataPaths) {
        return new TelemetryRuntimeCandidate(
                request.origin().name().toLowerCase(Locale.ROOT) + ":" + request.providerPluginIdentifier(),
                request.origin(),
                request.runtimeVersion(),
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                request.providerPluginIdentifier(),
                request.providerPluginVersion(),
                request.sourcePath(),
                dataPaths.runtimeRoot()
        );
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

    private boolean usesLocalCoordinator(@Nullable TelemetryCoordinatorBridge active) {
        return active == null || bridge.providerId().equals(active.providerId());
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
                : projectDiagnosticsFromSummary(diagnostics).enabled();
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> manualReportProjectsFromSummaries(@Nonnull List<Map<String, Object>> summaries) {
        ArrayList<TelemetryProjectRegistration> projects = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            TelemetryProjectRegistration project = projectFromSummary(summary);
            if (project != null) {
                projects.add(project);
            }
        }
        return List.copyOf(projects);
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

    private static boolean booleanValue(@Nonnull Map<String, Object> values, @Nonnull String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool && bool;
    }

    private static int intValue(@Nonnull Map<String, Object> values, @Nonnull String key) {
        Object value = values.get(key);
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

    private final class ProviderBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final TelemetryCoordinatorService service;
        private final AtomicBoolean active = new AtomicBoolean(false);

        private ProviderBridge(@Nonnull TelemetryRuntimeCandidate candidate,
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
            TelemetryRuntimeLocator.register(api);
            commandRegistrar.register();
            sparkProfilerMonitor.activate();
        }

        @Override
        public void shutdown() {
            sparkProfilerMonitor.suspend();
            commandRegistrar.unregister();
            service.shutdown();
            TelemetryRuntimeLocator.clearIfCurrent(api);
        }

        @Override
        public boolean reconcileProjectContributions(long revision,
                                                     @Nonnull List<Map<String, Object>> contributions) {
            boolean reconciled = service.reconcileProjectContributions(revision, contributions);
            if (reconciled) {
                consentProjects = mergedProjects(service.projects(), service.manualReportProjects());
            }
            return reconciled;
        }

        @Nonnull
        @Override
        public Map<String, Object> dispatchProjectContribution(@Nonnull String token,
                                                               @Nonnull String operation,
                                                               @Nonnull Map<String, Object> payload,
                                                               @Nullable Throwable throwable) {
            return service.dispatchProjectContribution(token, operation, payload, throwable);
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

        @Nonnull
        @Override
        public Map<String, Object> consentDiagnostics() {
            return diagnosticsSummary(localConsentDiagnostics());
        }

        @Nonnull
        @Override
        public Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
            TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = TelemetryRuntimeProviderHandle.this
                    .consentProjectDiagnostics(projectId);
            return diagnostics == null ? Map.of() : projectDiagnosticsSummary(diagnostics);
        }

        @Override
        public boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
            return applyLocalConsentToAll(snapshotFromSummary(snapshot));
        }

        @Override
        public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
            return applyLocalConsentCategoryToAll(category, enabled);
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
            return applyLocalConsent(projectId, snapshotFromSummary(snapshot));
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            return TelemetryRuntimeProviderHandle.this.markConsentReviewed(projectId);
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
        public boolean recordBreadcrumb(@Nonnull String projectId,
                                        @Nonnull String category,
                                        @Nonnull String detail,
                                        @Nonnull Map<String, Object> context) {
            return service.recordBreadcrumb(projectId, category, detail, context);
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
}
