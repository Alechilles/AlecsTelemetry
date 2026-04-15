package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
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
    public boolean requestFlush() {
        return runtimeService.triggerFlushAsync(projectId);
    }
}
