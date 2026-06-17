package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Embedded telemetry runtime for one owning consumer mod.
 */
public final class EmbeddedTelemetryService implements EmbeddedTelemetryHandle {

    private final TelemetryProjectRegistration project;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryProjectOverrideStore overrideStore;
    private final TelemetryRuntimeSettings settings;
    private final TelemetryCoreEngine engine;
    private final EmbeddedTelemetryStatsHeartbeat statsHeartbeat;
    private final EmbeddedCoordinatorBridge coordinatorBridge;
    private final HytaleLogger logger;
    private final String disabledReason;

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
        if (engine != null) {
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

    private static final class EmbeddedCoordinatorBridge implements TelemetryCoordinatorBridge {
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
