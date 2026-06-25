package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerIntervalSnapshot;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Emits aggregate stats heartbeats from the active coordinator for every registered project.
 */
final class TelemetryCoordinatorStatsHeartbeat {

    static final String EVENT_HEARTBEAT = "heartbeat";
    static final int HEARTBEAT_INTERVAL_SECONDS = 1800;

    private final TelemetryCoreEngine engine;
    private final Supplier<TelemetryPlayerIntervalSnapshot> playerIntervalSnapshot;
    private final ScheduledExecutorService executor;
    private final HytaleLogger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> future;

    TelemetryCoordinatorStatsHeartbeat(@Nonnull TelemetryCoreEngine engine,
                                       @Nonnull IntSupplier onlinePlayers,
                                       @Nullable ScheduledExecutorService executor,
                                       @Nullable HytaleLogger logger) {
        this(engine, () -> TelemetryPlayerIntervalSnapshot.single(onlinePlayers.getAsInt()), executor, logger);
    }

    TelemetryCoordinatorStatsHeartbeat(@Nonnull TelemetryCoreEngine engine,
                                       @Nonnull Supplier<TelemetryPlayerIntervalSnapshot> playerIntervalSnapshot,
                                       @Nullable ScheduledExecutorService executor,
                                       @Nullable HytaleLogger logger) {
        this.engine = engine;
        this.playerIntervalSnapshot = playerIntervalSnapshot;
        this.executor = executor;
        this.logger = logger;
    }

    void start() {
        if (executor == null || !started.compareAndSet(false, true)) {
            return;
        }
        scheduleNext(true);
    }

    void shutdown() {
        ScheduledFuture<?> scheduled = future;
        future = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        boolean wasStarted = started.getAndSet(false);
        if (wasStarted) {
            emitHeartbeatSafelyWithoutReschedule();
        }
    }

    void emitHeartbeatNow() {
        engine.recordAggregateStatsHeartbeat(engine.projects(), playerIntervalSnapshot.get());
    }

    private void emitHeartbeatSafely() {
        try {
            emitHeartbeatNow();
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Telemetry coordinator stats heartbeat failed.");
            }
        } finally {
            if (started.get()) {
                scheduleNext(false);
            }
        }
    }

    private void emitHeartbeatSafelyWithoutReschedule() {
        try {
            emitHeartbeatNow();
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Telemetry coordinator stats shutdown heartbeat failed.");
            }
        }
    }

    private void scheduleNext(boolean firstHeartbeat) {
        if (executor == null) {
            return;
        }
        future = executor.schedule(
                this::emitHeartbeatSafely,
                engine.statsHeartbeatDelaySeconds(firstHeartbeat),
                TimeUnit.SECONDS
        );
    }
}
