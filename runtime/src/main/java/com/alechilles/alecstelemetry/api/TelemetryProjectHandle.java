package com.alechilles.alecstelemetry.api;

import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Project-scoped telemetry handle exposed to consumer mods.
 */
public interface TelemetryProjectHandle {

    @Nonnull
    String projectId();

    @Nonnull
    String displayName();

    boolean isEnabled();

    void recordBreadcrumb(@Nonnull String category, @Nonnull String detail);

    /**
     * Records a bounded structured breadcrumb when the active runtime supports it.
     * Older handle implementations inherit the legacy category/detail fallback.
     */
    default void recordBreadcrumb(@Nonnull TelemetryBreadcrumbContext context) {
        TelemetryBreadcrumbContext normalized = context.normalize();
        recordBreadcrumb(normalized.category(), normalized.detail());
    }

    void captureSetupFailure(@Nullable Throwable throwable);

    void captureStartFailure(@Nullable Throwable throwable);

    void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail);

    default void recordErrorWithContext(@Nonnull String eventName,
                                        @Nullable Throwable throwable,
                                        @Nullable TelemetryEventContext context) {
        recordError(eventName, throwable, context == null ? null : context.detail());
    }

    void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail);

    default void recordLifecycleWithContext(@Nonnull String eventName,
                                            int durationMs,
                                            boolean success,
                                            @Nullable TelemetryEventContext context) {
        recordLifecycle(eventName, durationMs, success, context == null ? null : context.detail());
    }

    void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail);

    default void recordPerformanceWithContext(@Nonnull String eventName,
                                              int durationMs,
                                              @Nullable Double metricValue,
                                              @Nullable TelemetryEventContext context) {
        recordPerformance(eventName, durationMs, metricValue, context == null ? null : context.detail());
    }

    void recordUsage(@Nonnull String eventName, @Nullable String detail);

    default void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        recordUsage(eventName, context == null ? null : context.detail());
    }

    void recordStats(@Nonnull String eventName, @Nullable String detail);

    default void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        recordStats(eventName, context == null ? null : context.detail());
    }

    default boolean openReportPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef playerRef,
                                   @Nonnull TelemetryReportOpenRequest request) {
        return false;
    }

    boolean requestFlush();
}
