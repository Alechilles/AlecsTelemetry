package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Current local state of the optional local Spark profiler monitor.
 */
public record SparkProfilerDiagnostics(@Nonnull State state,
                                       @Nullable String sparkVersion,
                                       @Nonnull String detail,
                                       long captureDurationMillis,
                                       long heapDeltaBytes,
                                       int windowKey,
                                       int threadCount,
                                       int frameCount,
                                       @Nonnull List<SparkProfileSnapshot.HotPath> hotPaths,
                                       boolean active,
                                       boolean circuitOpen,
                                       long nextCaptureAtMillis) {

    public enum State {
        DISABLED,
        READY,
        WAITING,
        CAPTURING,
        COMPLETE,
        ABSENT,
        NOT_OWNER,
        NOT_RUNNING,
        UNSUPPORTED,
        NO_DATA,
        NO_ACTIONABLE_DATA,
        TOO_COMPLEX,
        LOW_HEAP,
        INCOMPATIBLE,
        TIMED_OUT,
        FAILED,
        SHUTDOWN
    }

    public SparkProfilerDiagnostics {
        detail = detail == null ? "" : detail;
        hotPaths = hotPaths == null ? List.of() : List.copyOf(hotPaths);
    }

    public SparkProfilerDiagnostics(@Nonnull State state,
                                    @Nullable String sparkVersion,
                                    @Nonnull String detail,
                                    long captureDurationMillis,
                                    long heapDeltaBytes,
                                    int windowKey,
                                    int threadCount,
                                    int frameCount,
                                    @Nonnull List<SparkProfileSnapshot.HotPath> hotPaths) {
        this(
                state,
                sparkVersion,
                detail,
                captureDurationMillis,
                heapDeltaBytes,
                windowKey,
                threadCount,
                frameCount,
                hotPaths,
                false,
                false,
                0L
        );
    }

    @Nonnull
    public static SparkProfilerDiagnostics disabled() {
        return simple(State.DISABLED, null, "Disabled in Settings/runtime.json.");
    }

    @Nonnull
    static SparkProfilerDiagnostics simple(@Nonnull State state,
                                           @Nullable String sparkVersion,
                                           @Nonnull String detail) {
        return new SparkProfilerDiagnostics(
                state,
                sparkVersion,
                detail,
                0L,
                0L,
                0,
                0,
                0,
                List.of()
        );
    }

    @Nonnull
    public String commandSummary() {
        StringBuilder summary = new StringBuilder("state=").append(state.name());
        if (sparkVersion != null && !sparkVersion.isBlank()) {
            summary.append(", sparkVersion=").append(sparkVersion);
        }
        if (state == State.COMPLETE) {
            summary.append(", window=").append(windowKey)
                    .append(", durationMs=").append(captureDurationMillis)
                    .append(", heapDeltaBytes=").append(heapDeltaBytes)
                    .append(", threads=").append(threadCount)
                    .append(", frames=").append(frameCount)
                    .append(", hotPaths=").append(hotPaths.size());
        }
        summary.append(", active=").append(active)
                .append(", circuitOpen=").append(circuitOpen);
        if (nextCaptureAtMillis > 0L) {
            summary.append(", nextCaptureAtMillis=").append(nextCaptureAtMillis);
        }
        if (!detail.isBlank()) {
            summary.append(", detail=").append(detail);
        }
        return summary.toString();
    }
}
