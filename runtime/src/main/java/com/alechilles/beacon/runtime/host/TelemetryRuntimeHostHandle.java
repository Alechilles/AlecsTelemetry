package com.alechilles.beacon.runtime.host;

import com.alechilles.beacon.api.TelemetryRuntimeApi;
import com.alechilles.beacon.consent.TelemetryConsentSnapshot;
import com.alechilles.beacon.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TelemetryRuntimeHostHandle {
    void start();

    void shutdown();

    boolean ownsActiveCoordinator();

    @Nullable
    String activeCoordinatorProviderId();

    int registeredProjectCount();

    default boolean ensureEmbeddedProject(@Nonnull TelemetryProjectRegistration project) {
        return false;
    }

    @Nonnull
    TelemetryRuntimeApi api();

    default boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean isDiagnosticsEnabled(@Nonnull String projectId) {
        return false;
    }

    default boolean setDiagnosticsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
        return false;
    }

    default boolean markConsentReviewed(@Nonnull String projectId) {
        return false;
    }

    default void recordConsentPromptShown(@Nonnull String viewerKey,
                                          @Nonnull List<TelemetryProjectRegistration> projects) {
    }

    default void recordConsentUiOpened(@Nonnull String viewerKey,
                                       @Nonnull List<TelemetryProjectRegistration> projects) {
    }

    default boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return false;
    }
}
