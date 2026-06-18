package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryLoadedModSnapshotProvider;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryRuntimeProviderHandle implements TelemetryRuntimeHostHandle, TelemetryRuntimeOperations {
    private final TelemetryRuntimeBootstrapRequest request;
    private final TelemetryCoordinatorService coordinator;
    private final ProviderBridge bridge;
    private final TelemetryRuntimePluginEvents pluginEvents;
    private final TelemetryRuntimeCommandRegistrar commandRegistrar;
    private final TelemetryRuntimeApi api;

    @Nonnull
    static TelemetryRuntimeProviderHandle create(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        HytaleLogger logger = request.plugin().getLogger();
        TelemetryDataPaths dataPaths = request.embedded()
                ? TelemetryDataPaths.forSharedCoordinator(request.plugin())
                : TelemetryDataPaths.from(request.plugin());
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscovery(logger).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.hytalePluginManager(List.of(), logger)
        );
        TelemetryPlayerCounter playerCounter = new TelemetryPlayerCounter();
        CrashReportClient client = new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger);
        TelemetryCoordinatorService coordinator = TelemetryCoordinatorService.fromDiscovery(
                settings,
                dataPaths,
                discovery,
                client,
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                playerCounter::onlinePlayers
        );
        return new TelemetryRuntimeProviderHandle(
                request,
                candidate(request, dataPaths),
                coordinator,
                new TelemetryRuntimePluginEvents(),
                new TelemetryRuntimeCommandRegistrar()
        );
    }

    TelemetryRuntimeProviderHandle(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                   @Nonnull TelemetryRuntimeCandidate candidate,
                                   @Nonnull TelemetryCoordinatorService coordinator,
                                   @Nonnull TelemetryRuntimePluginEvents pluginEvents,
                                   @Nonnull TelemetryRuntimeCommandRegistrar commandRegistrar) {
        this.request = request;
        this.coordinator = coordinator;
        this.bridge = new ProviderBridge(candidate, coordinator);
        this.pluginEvents = pluginEvents;
        this.commandRegistrar = commandRegistrar;
        this.api = new TelemetryRuntimeApiImpl(this);
    }

    @Override
    public void start() {
        pluginEvents.register();
        TelemetryRuntimeLocator.register(api);
        TelemetryCoordinatorRegistry.register(bridge);
        if (ownsActiveCoordinator()) {
            commandRegistrar.register();
        }
    }

    @Override
    public void shutdown() {
        commandRegistrar.unregister();
        TelemetryCoordinatorRegistry.unregister(bridge.providerId());
        TelemetryRuntimeLocator.clear();
        pluginEvents.unregister();
    }

    @Override
    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && bridge.providerId().equals(active.providerId());
    }

    @Nullable
    @Override
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
    }

    @Override
    public int registeredProjectCount() {
        return coordinator.registeredProjectCount();
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
        return coordinator.projects();
    }

    @Nullable
    @Override
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        return coordinator.findProject(projectId);
    }

    @Override
    public boolean isProjectEnabled(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.isProjectEnabled(projectId) : active.isProjectEnabled(projectId);
    }

    @Override
    public boolean requestFlush(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.requestFlush(projectId) : active.requestFlush(projectId);
    }

    @Override
    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? coordinator.captureTestReport(projectId, detail) : active.captureTestReport(projectId, detail);
    }

    @Override
    public boolean openReportPage(@Nonnull String projectId,
                                  @Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull TelemetryReportOpenRequest request) {
        return false;
    }

    @Override
    public void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active == null) {
            coordinator.recordBreadcrumb(projectId, category, detail);
        } else {
            active.recordBreadcrumb(projectId, category, detail);
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
    TelemetryRuntimeBootstrapRequest request() {
        return request;
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

    private static final class ProviderBridge implements TelemetryCoordinatorBridge {
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
        }

        @Override
        public void shutdown() {
            service.shutdown();
        }

        @Override
        public boolean isEnabled() {
            return service.isEnabled();
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
