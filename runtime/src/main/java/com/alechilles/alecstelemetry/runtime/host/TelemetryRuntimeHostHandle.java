package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface TelemetryRuntimeHostHandle {
    void start();

    void shutdown();

    boolean ownsActiveCoordinator();

    @Nullable
    String activeCoordinatorProviderId();

    int registeredProjectCount();

    @Nonnull
    TelemetryRuntimeApi api();
}
