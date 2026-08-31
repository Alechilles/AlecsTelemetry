package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundleResult;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Project-scoped API handle backed by the active telemetry runtime.
 */
public final class TelemetryProjectHandleImpl implements TelemetryProjectHandle {

    private final TelemetryRuntimeOperations runtime;
    private final String projectId;

    public TelemetryProjectHandleImpl(@Nonnull TelemetryRuntimeOperations runtime,
                                      @Nonnull String projectId) {
        this.runtime = runtime;
        this.projectId = projectId;
    }

    @Nonnull
    @Override
    public String projectId() {
        return projectId;
    }

    @Nonnull
    @Override
    public String displayName() {
        TelemetryProjectRegistration project = runtime.findProject(projectId);
        return project == null ? projectId : project.displayName();
    }

    @Override
    public boolean isEnabled() {
        return runtime.isProjectEnabled(projectId);
    }

    @Override
    public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        runtime.recordBreadcrumb(projectId, category, detail);
    }

    @Override
    public void recordBreadcrumb(@Nonnull TelemetryBreadcrumbContext context) {
        runtime.recordBreadcrumb(projectId, context);
    }

    @Override
    public void captureSetupFailure(@Nullable Throwable throwable) {
        runtime.captureSetupFailure(projectId, throwable);
    }

    @Override
    public void captureStartFailure(@Nullable Throwable throwable) {
        runtime.captureStartFailure(projectId, throwable);
    }

    @Override
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        runtime.recordErrorWithContext(projectId, eventName, throwable, TelemetryEventContext.error().detail(detail).build());
    }

    @Override
    public void recordErrorWithContext(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        runtime.recordErrorWithContext(projectId, eventName, throwable, context);
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        runtime.recordLifecycleWithContext(projectId, eventName, durationMs, success, TelemetryEventContext.lifecycle().detail(detail).build());
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        runtime.recordLifecycleWithContext(projectId, eventName, durationMs, success, context);
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        runtime.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, TelemetryEventContext.performance().detail(detail).build());
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        runtime.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, context);
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        runtime.recordUsageWithContext(projectId, eventName, TelemetryEventContext.usage().detail(detail).build());
    }

    @Override
    public void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        runtime.recordUsageWithContext(projectId, eventName, context);
    }

    @Override
    public void recordStats(@Nonnull String eventName, @Nullable String detail) {
        runtime.recordStatsWithContext(projectId, eventName, TelemetryEventContext.stats().detail(detail).build());
    }

    @Override
    public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        runtime.recordStatsWithContext(projectId, eventName, context);
    }

    @Override
    public boolean openReportPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull TelemetryReportOpenRequest request) {
        return runtime.openReportPage(projectId, playerEntityRef, store, playerRef, request);
    }

    @Nonnull
    @Override
    public TelemetryDiagnosticBundleResult submitDiagnosticBundle(
            @Nonnull TelemetryDiagnosticBundle bundle
    ) {
        return runtime.submitDiagnosticBundle(projectId, bundle);
    }

    @Override
    public boolean requestFlush() {
        return runtime.requestFlush(projectId);
    }
}
