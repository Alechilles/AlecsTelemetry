package com.alechilles.beacon.embedded;

import com.alechilles.beacon.api.TelemetryEventContext;
import com.alechilles.beacon.api.TelemetryBreadcrumbContext;
import com.alechilles.beacon.api.TelemetryDiagnosticBundle;
import com.alechilles.beacon.api.TelemetryDiagnosticBundleResult;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Embedded telemetry handle owned by one consumer mod.
 */
public interface EmbeddedTelemetryHandle {

    @Nonnull
    String projectId();

    @Nonnull
    String displayName();

    boolean isEnabled();

    @Nullable
    String disabledReason();

    boolean setProjectEnabled(boolean enabled);

    boolean setBreadcrumbsEnabled(boolean enabled);

    void start();

    void shutdown();

    void recordBreadcrumb(@Nonnull String category, @Nonnull String detail);

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

    void captureExceptionalWorldRemoval(@Nullable World world, @Nullable RemoveWorldEvent.RemovalReason removalReason);

    @Nonnull
    default TelemetryDiagnosticBundleResult submitDiagnosticBundle(
            @Nonnull TelemetryDiagnosticBundle bundle
    ) {
        return TelemetryDiagnosticBundleResult.unsupported();
    }

    boolean requestFlush();

    boolean captureTestReport(@Nullable String detail);
}
