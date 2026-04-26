package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Project-scoped API handle backed by the active telemetry runtime.
 */
public final class TelemetryProjectHandleImpl implements TelemetryProjectHandle {

    private final TelemetryRuntimeService runtimeService;
    private final String projectId;
    private final String displayName;

    public TelemetryProjectHandleImpl(@Nonnull TelemetryRuntimeService runtimeService,
                                      @Nonnull String projectId,
                                      @Nonnull String displayName) {
        this.runtimeService = runtimeService;
        this.projectId = projectId;
        this.displayName = displayName;
    }

    @Nonnull
    @Override
    public String projectId() {
        return projectId;
    }

    @Nonnull
    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isEnabled() {
        return runtimeService.isProjectEnabled(projectId);
    }

    @Override
    public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        runtimeService.recordBreadcrumb(projectId, category, detail);
    }

    @Override
    public void captureSetupFailure(@Nullable Throwable throwable) {
        runtimeService.captureSetupFailure(projectId, throwable);
    }

    @Override
    public void captureStartFailure(@Nullable Throwable throwable) {
        runtimeService.captureStartFailure(projectId, throwable);
    }

    @Override
    public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        runtimeService.recordError(projectId, eventName, throwable, detail);
    }

    @Override
    public void recordErrorWithContext(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable TelemetryEventContext context) {
        runtimeService.recordErrorWithContext(projectId, eventName, throwable, context);
    }

    @Override
    public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        runtimeService.recordLifecycle(projectId, eventName, durationMs, success, detail);
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, @Nullable TelemetryEventContext context) {
        runtimeService.recordLifecycleWithContext(projectId, eventName, durationMs, success, context);
    }

    @Override
    public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        runtimeService.recordPerformance(projectId, eventName, durationMs, metricValue, detail);
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable TelemetryEventContext context) {
        runtimeService.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, context);
    }

    @Override
    public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        runtimeService.recordUsage(projectId, eventName, detail);
    }

    @Override
    public void recordUsageWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
        runtimeService.recordUsageWithContext(projectId, eventName, context);
    }

    @Override
    public boolean requestFlush() {
        return runtimeService.triggerFlushAsync(projectId);
    }
}
