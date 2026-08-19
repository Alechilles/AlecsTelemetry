package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerDiagnostics;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TelemetryCommandRuntime {
    boolean ownsActiveCoordinator();

    @Nullable
    String activeCoordinatorProviderId();

    int registeredProjectCount();

    @Nonnull
    List<TelemetryProjectRegistration> projects();

    @Nullable
    TelemetryProjectRegistration findProject(@Nonnull String projectId);

    @Nonnull
    TelemetryRuntimeDiagnostics diagnostics();

    @Nonnull
    default SparkProfilerDiagnostics sparkProfilerDiagnostics() {
        return SparkProfilerDiagnostics.disabled();
    }

    @Nonnull
    default List<SparkProfileSnapshot> sparkProfilerHistory() {
        return List.of();
    }

    default void logProfilerCommandOutput(@Nonnull String message) {
    }

    @Nonnull
    TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics(@Nonnull String projectId);

    int pendingReports(@Nullable String projectId);

    @Nonnull
    String lastFlushResult();

    boolean requestFlush(@Nullable String projectId);

    @Nonnull
    TelemetryServerVerificationResult requestServerVerification();

    @Nonnull
    TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken);

    boolean captureTestReport(@Nonnull String projectId, @Nullable String detail);

    @Nonnull
    List<TelemetryProjectRegistration> unreviewedConsentProjects();

    boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot);

    boolean markConsentReviewed(@Nonnull String projectId);

    boolean openConsentPage(@Nonnull Ref<EntityStore> playerEntityRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull PlayerRef playerRef,
                            boolean firstRun);

    boolean openReportPage(@Nonnull String projectId,
                           @Nonnull Ref<EntityStore> playerEntityRef,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull TelemetryReportOpenRequest request);

    @Nonnull
    List<TelemetryProjectRegistration> manualReportProjects();

    @Nullable
    TelemetryProjectRegistration findManualReportProject(@Nonnull String projectId);

    @Nonnull
    TelemetryRuntimeSettings.ManualReportSettings manualReportSettings();

    @Nonnull
    PlayerReportRuntimeContext currentPlayerReportRuntimeContext();

    @Nonnull
    ManualReportEnvelope.CreateResult submitManualReport(@Nonnull String projectId,
                                                         @Nonnull ManualReportSubmission submission,
                                                         @Nullable PlayerReportRuntimeContext playerContext);

    @Nonnull
    String manualReportReceiptStatus(@Nonnull String reportId);

    @Nonnull
    List<ManualReportEnvelope> manualReportsForReview(int maxReportsPerProject);

    boolean approveManualReport(@Nonnull String reportId);

    boolean rejectManualReport(@Nonnull String reportId);

    @Nonnull
    List<String> submittedManualReportAuditLines(int maxLines);
}
