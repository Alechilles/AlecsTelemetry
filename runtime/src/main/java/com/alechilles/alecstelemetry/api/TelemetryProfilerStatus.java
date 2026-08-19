package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Current state of the local profiler capability. */
public record TelemetryProfilerStatus(@Nonnull State state,
                                      @Nonnull String detail,
                                      boolean active,
                                      boolean circuitOpen,
                                      @Nullable String sparkVersion,
                                      @Nullable String runtimeVersion,
                                      long nextCaptureAtMillis,
                                      long analysisDurationMillis,
                                      int omittedPathCount,
                                      int truncatedPrefixCount) {

    public enum State {
        UNAVAILABLE,
        DISABLED,
        READY,
        WAITING,
        CAPTURING,
        COMPLETE,
        ABSENT,
        NOT_RUNNING,
        NO_DATA,
        LOW_HEAP,
        UNSUPPORTED,
        TIMED_OUT,
        FAILED,
        NOT_OWNER,
        SHUTDOWN
    }

    public TelemetryProfilerStatus {
        if (state == null) {
            state = State.FAILED;
        }
        detail = detail == null ? "" : detail.trim();
        sparkVersion = optionalText(sparkVersion);
        runtimeVersion = optionalText(runtimeVersion);
        if (nextCaptureAtMillis < 0L) {
            throw new IllegalArgumentException("nextCaptureAtMillis must be non-negative");
        }
        if (analysisDurationMillis < 0L) {
            throw new IllegalArgumentException("analysisDurationMillis must be non-negative");
        }
        if (omittedPathCount < 0) {
            throw new IllegalArgumentException("omittedPathCount must be non-negative");
        }
        if (truncatedPrefixCount < 0) {
            throw new IllegalArgumentException("truncatedPrefixCount must be non-negative");
        }
    }

    @Nonnull
    public static TelemetryProfilerStatus unavailable() {
        return new TelemetryProfilerStatus(State.UNAVAILABLE,
                "Profiler API is unavailable from the active runtime.",
                false, false, null, null, 0L, 0L, 0, 0);
    }

    @Nullable
    private static String optionalText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
