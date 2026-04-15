package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default runtime API implementation backed by the active telemetry service.
 */
public final class TelemetryRuntimeApiImpl implements TelemetryRuntimeApi {

    private final TelemetryRuntimeService runtimeService;

    public TelemetryRuntimeApiImpl(@Nonnull TelemetryRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public boolean isEnabled() {
        return runtimeService.isEnabled();
    }

    @Nonnull
    @Override
    public List<TelemetryProjectHandle> projects() {
        ArrayList<TelemetryProjectHandle> handles = new ArrayList<>(runtimeService.projects().size());
        for (TelemetryProjectRegistration project : runtimeService.projects()) {
            handles.add(new TelemetryProjectHandleImpl(runtimeService, project.projectId(), project.displayName()));
        }
        return List.copyOf(handles);
    }

    @Nullable
    @Override
    public TelemetryProjectHandle findProject(@Nonnull String projectId) {
        TelemetryProjectRegistration project = runtimeService.findProject(projectId);
        if (project == null) {
            return null;
        }
        return new TelemetryProjectHandleImpl(runtimeService, project.projectId(), project.displayName());
    }

    @Override
    public boolean requestFlush() {
        return runtimeService.triggerFlushAsync();
    }
}
