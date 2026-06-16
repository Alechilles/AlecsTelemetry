package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Emits BStats/HStats-like heartbeat observations for projects that opt into stats telemetry.
 */
public final class TelemetryStatsHeartbeatService {

    public static final String EVENT_HEARTBEAT = "heartbeat";

    private final TelemetryStatsRuntime runtime;
    private final TelemetryPlayerCounter playerCounter;
    private final ScheduledExecutorService executor;
    private final HytaleLogger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> future;

    public TelemetryStatsHeartbeatService(@Nonnull TelemetryStatsRuntime runtime,
                                          @Nonnull TelemetryPlayerCounter playerCounter,
                                          @Nullable ScheduledExecutorService executor,
                                          @Nullable HytaleLogger logger) {
        this.runtime = runtime;
        this.playerCounter = playerCounter;
        this.executor = executor;
        this.logger = logger;
    }

    public void start() {
        if (executor == null || !started.compareAndSet(false, true)) {
            return;
        }
        int intervalSeconds = runtime.heartbeatIntervalSeconds();
        future = executor.scheduleWithFixedDelay(
                this::emitHeartbeatSafely,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        ScheduledFuture<?> scheduled = future;
        future = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        started.set(false);
    }

    public void emitHeartbeatNow() {
        int playersOnline = playerCounter.onlinePlayers();
        for (TelemetryProjectRegistration project : runtime.projects()) {
            runtime.recordStatsWithContext(
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
                logger.at(Level.WARNING).withCause(ex).log("Telemetry stats heartbeat failed.");
            }
        }
    }
}
