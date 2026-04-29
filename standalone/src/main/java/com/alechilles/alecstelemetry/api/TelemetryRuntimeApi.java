package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Public entrypoint for consumer mods that want to interact with Alec's Telemetry at runtime.
 */
public interface TelemetryRuntimeApi {

    boolean isEnabled();

    @Nonnull
    List<TelemetryProjectHandle> projects();

    @Nullable
    TelemetryProjectHandle findProject(@Nonnull String projectId);

    boolean requestFlush();
}
