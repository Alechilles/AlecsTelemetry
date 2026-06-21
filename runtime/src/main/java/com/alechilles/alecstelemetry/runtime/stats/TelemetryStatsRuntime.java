package com.alechilles.alecstelemetry.runtime.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    default void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                               int playersOnline) {
        if (projects.isEmpty()) {
            return;
        }
        recordStatsWithContext(
                projects.getFirst().projectId(),
                "heartbeat",
                TelemetryEventContext.stats()
                        .featureKey("stats")
                        .entryPoint("heartbeat")
                        .runtimeSide("server")
                        .detail("playersOnline", Math.max(0, playersOnline))
                        .detail("projects", aggregateProjectDetails(projects))
                        .build()
        );
    }

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

            @Override
            public void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                                      int playersOnline) {
                runtimeService.recordAggregateStatsHeartbeat(projects, playersOnline);
            }

        };
    }

    @Nonnull
    private static List<Map<String, Object>> aggregateProjectDetails(@Nonnull List<TelemetryProjectRegistration> projects) {
        return projects.stream()
                .map(project -> {
                    LinkedHashMap<String, Object> details = new LinkedHashMap<>();
                    details.put("projectId", project.projectId());
                    details.put("projectDisplayName", project.displayName());
                    details.put("pluginIdentifier", project.pluginIdentifier());
                    details.put("pluginVersion", project.pluginVersion());
                    return Map.copyOf(details);
                })
                .toList();
    }
}
