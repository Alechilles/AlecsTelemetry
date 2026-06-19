package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;

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

    default boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        return false;
    }

    default boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return false;
    }
}
