package com.alechilles.beacon.runtime.stats;

import com.alechilles.beacon.project.TelemetryProjectRegistration;
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
    public static final int HEARTBEAT_INTERVAL_SECONDS = 1800;

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
        scheduleNext(true);
    }

    public void shutdown() {
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

    public void emitHeartbeatNow() {
        if (!runtime.canEmitHeartbeat()) {
            return;
        }
        TelemetryPlayerIntervalSnapshot players = playerCounter.snapshotAndResetInterval();
        java.util.ArrayList<TelemetryProjectRegistration> projects = new java.util.ArrayList<>();
        for (TelemetryProjectRegistration project : runtime.projects()) {
            if (!runtime.canEmitHeartbeat()) {
                return;
            }
            projects.add(project);
        }
        if (!projects.isEmpty()) {
            runtime.recordAggregateStatsHeartbeat(projects, players);
        }
    }

    private void emitHeartbeatSafely() {
        try {
            emitHeartbeatNow();
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Telemetry stats heartbeat failed.");
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
                logger.at(Level.WARNING).withCause(ex).log("Telemetry stats shutdown heartbeat failed.");
            }
        }
    }

    private void scheduleNext(boolean firstHeartbeat) {
        if (executor == null) {
            return;
        }
        future = executor.schedule(
                this::emitHeartbeatSafely,
                runtime.statsHeartbeatDelaySeconds(firstHeartbeat),
                TimeUnit.SECONDS
        );
    }
}
