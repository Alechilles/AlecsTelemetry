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

    void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail);

    void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail);

    void recordUsage(@Nonnull String eventName, @Nullable String detail);

    boolean requestFlush();
}
