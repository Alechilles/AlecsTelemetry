package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.logging.Level;

/**
 * Emits aggregate stats heartbeats from the active coordinator for every registered project.
 */
final class TelemetryCoordinatorStatsHeartbeat {

    static final String EVENT_HEARTBEAT = "heartbeat";
    static final int HEARTBEAT_INTERVAL_SECONDS = 300;

    private final TelemetryCoreEngine engine;
    private final IntSupplier onlinePlayers;
    private final ScheduledExecutorService executor;
    private final HytaleLogger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> future;

    TelemetryCoordinatorStatsHeartbeat(@Nonnull TelemetryCoreEngine engine,
                                       @Nonnull IntSupplier onlinePlayers,
                                       @Nullable ScheduledExecutorService executor,
                                       @Nullable HytaleLogger logger) {
        this.engine = engine;
        this.onlinePlayers = onlinePlayers;
        this.executor = executor;
        this.logger = logger;
    }

    void start() {
        if (executor == null || !started.compareAndSet(false, true)) {
            return;
        }
        future = executor.scheduleWithFixedDelay(
                this::emitHeartbeatSafely,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    void shutdown() {
        ScheduledFuture<?> scheduled = future;
        future = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        started.set(false);
    }

    void emitHeartbeatNow() {
        int playersOnline = onlinePlayers.getAsInt();
        for (TelemetryProjectRegistration project : engine.projects()) {
            engine.recordStatsWithContext(
                    project.projectId(),
                    EVENT_HEARTBEAT,
                    TelemetryEventContext.stats()
                            .featureKey("stats")
                            .entryPoint(EVENT_HEARTBEAT)
                            .runtimeSide("server")
                            .detail("playersOnline", playersOnline)
                            .build()
            );
        }
    }

    private void emitHeartbeatSafely() {
        try {
            emitHeartbeatNow();
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Telemetry coordinator stats heartbeat failed.");
            }
        }
    }
}
