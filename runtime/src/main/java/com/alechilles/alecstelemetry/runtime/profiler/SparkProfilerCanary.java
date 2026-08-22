package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Compatibility facade for callers that still use the pre-recurring canary.
 * New lifecycle code should use {@link SparkProfilerMonitor} directly.
 */
public final class SparkProfilerCanary implements AutoCloseable {
    private final SparkProfilerMonitor monitor;

    public SparkProfilerCanary(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerCanarySettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nullable HytaleLogger logger) {
        this(
                toMonitorSettings(settings),
                sparkPluginLookup,
                activeCoordinatorOwner,
                new SparkProfileReflectionBridge(),
                SparkProfilerCanary::currentHeapHeadroomBytes,
                logger,
                null
        );
    }

    SparkProfilerCanary(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerCanarySettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger) {
        this(
                toMonitorSettings(settings),
                sparkPluginLookup,
                activeCoordinatorOwner,
                profileReader,
                heapHeadroomBytes,
                logger,
                null
        );
    }

    SparkProfilerCanary(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger,
            @Nullable BiConsumer<Level, String> logSink) {
        this.monitor = new SparkProfilerMonitor(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                profileReader,
                heapHeadroomBytes,
                logger,
                logSink
        );
    }

    /**
     * Keeps the old startup entry point source-compatible.
     */
    public void start() {
        monitor.activate();
    }

    /**
     * Keeps the old deterministic test entry point source-compatible.
     */
    void startNow() {
        monitor.activateNow();
    }

    public void suspend() {
        monitor.suspend();
    }

    @Nonnull
    public SparkProfilerDiagnostics diagnostics() {
        return monitor.diagnostics();
    }

    @Nonnull
    public List<SparkProfileSnapshot> history() {
        return monitor.history();
    }

    @Override
    public void close() {
        monitor.close();
    }

    private static long currentHeapHeadroomBytes() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return runtime.maxMemory() - used;
    }

    private static TelemetryRuntimeSettings.SparkProfilerSettings toMonitorSettings(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerCanarySettings settings) {
        return new TelemetryRuntimeSettings.SparkProfilerSettings(
                settings.enabled(),
                settings.initialDelaySeconds(),
                TelemetryRuntimeSettingsDefaults.INTERVAL_SECONDS,
                settings.timeoutSeconds(),
                settings.maxSummaryEntries(),
                TelemetryRuntimeSettingsDefaults.MAX_HISTORY_SNAPSHOTS
        );
    }

    private static final class TelemetryRuntimeSettingsDefaults {
        private static final int INTERVAL_SECONDS = 300;
        private static final int MAX_HISTORY_SNAPSHOTS = 10;

        private TelemetryRuntimeSettingsDefaults() {
        }
    }
}
