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

    boolean requestFlush();
}
