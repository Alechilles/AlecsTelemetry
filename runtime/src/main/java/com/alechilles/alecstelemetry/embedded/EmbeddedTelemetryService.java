package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
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
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
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
        this(settings, dataPaths, project, List.of(project), loadedMods, client, logger, executor, playerCounter);
    }

    EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                             @Nonnull TelemetryDataPaths dataPaths,
                             @Nonnull TelemetryProjectRegistration project,
                             @Nonnull List<TelemetryProjectRegistration> projects,
                             @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                             @Nonnull CrashReportClient client,
                             @Nullable HytaleLogger logger,
                             @Nullable ScheduledExecutorService executor,
                             @Nonnull EmbeddedTelemetryPlayerCounter playerCounter) {
        this.project = project;
        this.dataPaths = dataPaths;
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
        this.settings = settings;
        this.engine = new TelemetryCoreEngine(settings, dataPaths, projects, loadedMods, client, logger, executor);
        this.statsHeartbeat = new EmbeddedTelemetryStatsHeartbeat(engine, playerCounter, executor, logger);
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
        return engine != null && engine.isProjectEnabled(project.projectId());
    }

    @Nullable
    @Override
    public String disabledReason() {
        return disabledReason;
    }

    @Override
    public boolean setProjectEnabled(boolean enabled) {
        if (engine == null || dataPaths == null || overrideStore == null) {
            return false;
        }
        boolean saved = overrideStore.saveProjectEnabled(dataPaths.projectOverrideFile(project.projectId()), enabled);
        engine.setProjectEnabled(project.projectId(), enabled);
        return saved;
    }

    @Override
    public boolean setBreadcrumbsEnabled(boolean enabled) {
        if (engine == null || dataPaths == null || overrideStore == null) {
            return false;
        }
        boolean saved = overrideStore.saveBreadcrumbsEnabled(dataPaths.projectOverrideFile(project.projectId()), enabled);
        engine.setBreadcrumbsEnabled(project.projectId(), enabled);
        return saved;
    }

    @Override
    public void start() {
        if (engine != null) {
            engine.start();
            statsHeartbeat.start();
        }
    }

    @Override
    public void shutdown() {
        if (engine != null) {
            statsHeartbeat.shutdown();
            engine.shutdown();
        }
    }

    @Override
    public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        if (engine != null) {
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
        if (engine != null) {
            engine.captureSetupFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void captureStartFailure(@Nullable Throwable throwable) {
        if (engine != null) {
            engine.captureStartFailure(project.projectId(), throwable);
        }
    }

    @Override
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        if (engine != null) {
            engine.recordError(project.projectId(), eventName, throwable, detail);
        }
    }

    @Override
    public void recordErrorWithContext(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordErrorWithContext(project.projectId(), eventName, throwable, context);
        }
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        if (engine != null) {
            engine.recordLifecycle(project.projectId(), eventName, durationMs, success, detail);
        }
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordLifecycleWithContext(project.projectId(), eventName, durationMs, success, context);
        }
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        if (engine != null) {
            engine.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detail);
        }
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordPerformanceWithContext(project.projectId(), eventName, durationMs, metricValue, context);
        }
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        if (engine != null) {
            engine.recordUsage(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordUsageWithContext(project.projectId(), eventName, context);
        }
    }

    @Override
    public void recordStats(@Nonnull String eventName, @Nullable String detail) {
        if (engine != null) {
            engine.recordStats(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        if (engine != null) {
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
        return engine != null && engine.triggerFlushAsync(project.projectId());
    }

    @Override
    public boolean captureTestReport(@Nullable String detail) {
        return engine != null && engine.captureTestReport(project.projectId(), detail);
    }

    @Nonnull
    public EmbeddedTelemetryDiagnostics diagnostics() {
        return new EmbeddedTelemetryDiagnostics(
                isEnabled(),
                resolveEndpoint(),
                pendingReports(),
                engine != null && engine.flushInProgress(),
                engine == null ? disabledReason : engine.lastFlushResult()
        );
    }

    int pendingReports() {
        return engine == null ? 0 : engine.pendingReports(project.projectId());
    }

    @Nonnull
    TelemetryCoreEngine.FlushSummary flushPendingReportsNow(@Nonnull String reason) {
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
