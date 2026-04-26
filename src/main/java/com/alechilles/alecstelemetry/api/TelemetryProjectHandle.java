package com.alechilles.alecstelemetry.api;

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

    boolean requestFlush();
}
