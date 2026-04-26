package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
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
    private final TelemetryCoreEngine engine;
    private final HytaleLogger logger;
    private final String disabledReason;

    EmbeddedTelemetryService(@Nonnull TelemetryRuntimeSettings settings,
                             @Nonnull TelemetryDataPaths dataPaths,
                             @Nonnull TelemetryProjectRegistration project,
                             @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                             @Nonnull CrashReportClient client,
                             @Nullable HytaleLogger logger,
                             @Nullable ScheduledExecutorService executor) {
        this.project = project;
        this.engine = new TelemetryCoreEngine(settings, dataPaths, List.of(project), loadedMods, client, logger, executor);
        this.logger = logger;
        this.disabledReason = null;
    }

    private EmbeddedTelemetryService(@Nonnull String projectId,
                                     @Nonnull String displayName,
                                     @Nullable HytaleLogger logger,
                                     @Nonnull String disabledReason) {
        this.project = nullRegistration(projectId, displayName);
        this.engine = null;
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
    public void start() {
        if (engine != null) {
            engine.start();
        }
    }

    @Override
    public void shutdown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Override
    public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        if (engine != null) {
            engine.recordBreadcrumb(project.projectId(), category, detail);
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
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordError(project.projectId(), eventName, throwable, context);
        }
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        if (engine != null) {
            engine.recordLifecycle(project.projectId(), eventName, durationMs, success, detail);
        }
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordLifecycle(project.projectId(), eventName, durationMs, success, context);
        }
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        if (engine != null) {
            engine.recordPerformance(project.projectId(), eventName, durationMs, metricValue, detail);
        }
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordPerformance(project.projectId(), eventName, durationMs, metricValue, context);
        }
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        if (engine != null) {
            engine.recordUsage(project.projectId(), eventName, detail);
        }
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        if (engine != null) {
            engine.recordUsage(project.projectId(), eventName, context);
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
