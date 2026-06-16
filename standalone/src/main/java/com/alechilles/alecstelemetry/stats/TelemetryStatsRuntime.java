package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Minimal runtime surface needed by the heartbeat emitter.
 */
public interface TelemetryStatsRuntime {

    @Nonnull
    List<TelemetryProjectRegistration> projects();

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull TelemetryEventContext context);

    int heartbeatIntervalSeconds();

    @Nonnull
    static TelemetryStatsRuntime from(@Nonnull TelemetryRuntimeService runtimeService) {
        return new TelemetryStatsRuntime() {
            @Nonnull
            @Override
            public List<TelemetryProjectRegistration> projects() {
                return runtimeService.projects();
            }

            @Override
            public void recordStatsWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               @Nonnull TelemetryEventContext context) {
                runtimeService.recordStatsWithContext(projectId, eventName, context);
            }

            @Override
            public int heartbeatIntervalSeconds() {
                return runtimeService.statsHeartbeatIntervalSeconds();
            }
        };
    }
}
