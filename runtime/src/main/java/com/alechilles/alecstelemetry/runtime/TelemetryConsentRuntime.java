package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Runtime operations needed by the shared telemetry consent UI.
 */
public interface TelemetryConsentRuntime {

    @Nonnull
    List<TelemetryProjectRegistration> unreviewedConsentProjects();

    /**
     * Returns newly supported consent categories for a project since its last
     * reviewed capability snapshot.
     */
    @Nonnull
    default List<String> addedConsentCategories(@Nonnull String projectId) {
        return List.of();
    }

    boolean isConsentNoticeShown(@Nonnull String viewerKey,
                                 @Nonnull List<TelemetryProjectRegistration> projects);

    boolean markConsentNoticeShown(@Nonnull String viewerKey,
                                   @Nonnull List<TelemetryProjectRegistration> projects);

    void recordConsentPromptShown(@Nonnull String viewerKey,
                                  @Nonnull List<TelemetryProjectRegistration> projects);

    void recordConsentUiOpened(@Nonnull String viewerKey,
                               @Nonnull List<TelemetryProjectRegistration> projects);

    @Nonnull
    TelemetryRuntimeDiagnostics consentDiagnostics();

    @Nullable
    TelemetryRuntimeDiagnostics.ProjectDiagnostics consentProjectDiagnostics(@Nonnull String projectId);

    boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot);

    boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot);

    boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled);

    boolean markConsentReviewed(@Nonnull String projectId);
}
