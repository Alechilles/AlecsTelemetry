package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class TelemetryRuntimeProviderHandle implements TelemetryRuntimeHostHandle {
    private final TelemetryRuntimeBootstrapRequest request;

    @Nonnull
    static TelemetryRuntimeProviderHandle create(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        return new TelemetryRuntimeProviderHandle(request);
    }

    private TelemetryRuntimeProviderHandle(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        this.request = request;
    }

    @Override
    public void start() {
        throw unsupported();
    }

    @Override
    public void shutdown() {
        throw unsupported();
    }

    @Override
    public boolean ownsActiveCoordinator() {
        throw unsupported();
    }

    @Nullable
    @Override
    public String activeCoordinatorProviderId() {
        throw unsupported();
    }

    @Override
    public int registeredProjectCount() {
        throw unsupported();
    }

    @Nonnull
    @Override
    public TelemetryRuntimeApi api() {
        throw unsupported();
    }

    @Nonnull
    TelemetryRuntimeBootstrapRequest request() {
        return request;
    }

    @Nonnull
    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "TelemetryRuntimeProviderHandle is a transitional Task 6 compile stub; Task 7 owns the runtime implementation."
        );
    }
}
