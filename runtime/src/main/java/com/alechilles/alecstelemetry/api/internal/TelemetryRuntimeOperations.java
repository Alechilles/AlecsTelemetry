package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TelemetryRuntimeOperations {
    @Nonnull
    List<TelemetryProjectRegistration> projects();

    @Nullable
    TelemetryProjectRegistration findProject(@Nonnull String projectId);

    boolean isProjectEnabled(@Nonnull String projectId);

    boolean requestFlush(@Nullable String projectId);

    boolean captureTestReport(@Nonnull String projectId, @Nullable String detail);

    void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail);

    void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void recordErrorWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable Throwable throwable,
                                @Nullable TelemetryEventContext context);

    void recordLifecycleWithContext(@Nonnull String projectId,
                                    @Nonnull String eventName,
                                    int durationMs,
                                    boolean success,
                                    @Nullable TelemetryEventContext context);

    void recordPerformanceWithContext(@Nonnull String projectId,
                                      @Nonnull String eventName,
                                      int durationMs,
                                      @Nullable Double metricValue,
                                      @Nullable TelemetryEventContext context);

    void recordUsageWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);
}
