package com.alechilles.beacon.api.internal;

import com.alechilles.beacon.api.TelemetryProjectHandle;
import com.alechilles.beacon.api.TelemetryRuntimeApi;
import com.alechilles.beacon.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default runtime API implementation backed by the active telemetry service.
 */
public final class TelemetryRuntimeApiImpl implements TelemetryRuntimeApi {

    private final TelemetryRuntimeOperations runtime;

    public TelemetryRuntimeApiImpl(@Nonnull TelemetryRuntimeOperations runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean isEnabled() {
        return runtime.isEnabled();
    }

    @Nonnull
    @Override
    public List<TelemetryProjectHandle> projects() {
        ArrayList<TelemetryProjectHandle> handles = new ArrayList<>(runtime.projects().size());
        for (TelemetryProjectRegistration project : runtime.projects()) {
            handles.add(new TelemetryProjectHandleImpl(runtime, project.projectId()));
        }
        return List.copyOf(handles);
    }

    @Nullable
    @Override
    public TelemetryProjectHandle findProject(@Nonnull String projectId) {
        TelemetryProjectRegistration project = runtime.findProject(projectId);
        if (project == null) {
            return null;
        }
        return new TelemetryProjectHandleImpl(runtime, project.projectId());
    }

    @Override
    public boolean requestFlush() {
        return runtime.requestFlush(null);
    }
}
