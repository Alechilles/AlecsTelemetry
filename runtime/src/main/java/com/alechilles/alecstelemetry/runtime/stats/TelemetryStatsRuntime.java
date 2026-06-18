package com.alechilles.alecstelemetry.runtime.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Minimal runtime surface needed by the heartbeat emitter.
 */
public interface TelemetryStatsRuntime {

    @Nonnull
    List<TelemetryProjectRegistration> projects();

    default boolean canEmitHeartbeat() {
        return true;
    }

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull TelemetryEventContext context);

    @Nonnull
    static TelemetryStatsRuntime from(@Nonnull TelemetryRuntimeOperations runtimeService) {
        return new TelemetryStatsRuntime() {
            @Nonnull
            @Override
            public List<TelemetryProjectRegistration> projects() {
                return runtimeService.projects();
            }

            @Override
            public boolean canEmitHeartbeat() {
                return runtimeService.canEmitStatsHeartbeat();
            }

            @Override
            public void recordStatsWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               @Nonnull TelemetryEventContext context) {
                runtimeService.recordStatsWithContext(projectId, eventName, context);
            }

        };
    }
}
