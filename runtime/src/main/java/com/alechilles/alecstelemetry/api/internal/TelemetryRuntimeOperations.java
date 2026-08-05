package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerIntervalSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface TelemetryRuntimeOperations {
    boolean isEnabled();

    @Nonnull
    List<TelemetryProjectRegistration> projects();

    @Nullable
    TelemetryProjectRegistration findProject(@Nonnull String projectId);

    boolean isProjectEnabled(@Nonnull String projectId);

    default boolean canEmitStatsHeartbeat() {
        return true;
    }

    default int statsHeartbeatDelaySeconds(boolean firstHeartbeat) {
        return firstHeartbeat ? 120 : 1800;
    }

    boolean requestFlush(@Nullable String projectId);

    boolean captureTestReport(@Nonnull String projectId, @Nullable String detail);

    boolean openReportPage(@Nonnull String projectId,
                           @Nonnull Ref<EntityStore> playerEntityRef,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull TelemetryReportOpenRequest request);

    void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail);

    default void recordBreadcrumb(@Nonnull String projectId,
                                  @Nonnull TelemetryBreadcrumbContext context) {
        TelemetryBreadcrumbContext normalized = context.normalize();
        recordBreadcrumb(projectId, normalized.category(), normalized.detail());
    }

    void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void recordErrorWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable Throwable throwable,
                                @Nullable TelemetryEventContext context);

    void recordLifecycleWithContext(@Nonnull String projectId,
                                    @Nonnull String eventName,
                                    int durationMs,
                                    boolean success,
                                    @Nullable TelemetryEventContext context);

    void recordPerformanceWithContext(@Nonnull String projectId,
                                      @Nonnull String eventName,
                                      int durationMs,
                                      @Nullable Double metricValue,
                                      @Nullable TelemetryEventContext context);

    void recordUsageWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);

    default void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                               @Nonnull TelemetryPlayerIntervalSnapshot players) {
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
                        .detail("playersOnline", Math.max(0, players.currentPlayers()))
                        .detail("maxPlayersSinceLastReport", Math.max(0, players.maxPlayersSinceLastReport()))
                        .detail("avgPlayersSinceLastReport", Math.max(0.0, players.avgPlayersSinceLastReport()))
                        .detail("projects", aggregateProjectDetails(projects))
                        .build()
        );
    }

    default void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                               int playersOnline) {
        recordAggregateStatsHeartbeat(projects, TelemetryPlayerIntervalSnapshot.single(playersOnline));
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
