package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Fixed stats heartbeat for embedded telemetry projects.
 */
final class EmbeddedTelemetryStatsHeartbeat {

    static final String EVENT_HEARTBEAT = "heartbeat";
    static final int HEARTBEAT_INTERVAL_SECONDS = 300;

    private final String projectId;
    private final TelemetryCoreEngine engine;
    private final EmbeddedTelemetryPlayerCounter playerCounter;
    private final ScheduledExecutorService executor;
    private final HytaleLogger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> future;

    EmbeddedTelemetryStatsHeartbeat(@Nonnull String projectId,
                                    @Nonnull TelemetryCoreEngine engine,
                                    @Nonnull EmbeddedTelemetryPlayerCounter playerCounter,
                                    @Nullable ScheduledExecutorService executor,
                                    @Nullable HytaleLogger logger) {
        this.projectId = projectId;
        this.engine = engine;
        this.playerCounter = playerCounter;
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
        emitHeartbeatSafely();
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
        engine.recordStatsWithContext(
                projectId,
                EVENT_HEARTBEAT,
                TelemetryEventContext.stats()
                        .featureKey("stats")
                        .entryPoint(EVENT_HEARTBEAT)
                        .runtimeSide("server")
                        .detail("playersOnline", playerCounter.onlinePlayers())
                        .build()
        );
    }

    private void emitHeartbeatSafely() {
        try {
            emitHeartbeatNow();
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Embedded telemetry stats heartbeat failed.");
            }
        }
    }
}
