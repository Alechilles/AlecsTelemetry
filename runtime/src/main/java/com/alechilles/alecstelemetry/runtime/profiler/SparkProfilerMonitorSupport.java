package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Stateless value and formatting helpers for the local Spark profiler monitor.
 */
final class SparkProfilerMonitorSupport {
    static final long MIN_HEAP_HEADROOM_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DETAIL_LENGTH = 240;

    private SparkProfilerMonitorSupport() {
    }

    @Nonnull
    static SparkProfilerDiagnostics diagnosticsFrom(@Nonnull SparkProfileReadResult result,
                                                     long durationMillis,
                                                     long heapDeltaBytes) {
        SparkProfileSnapshot snapshot = result.snapshot();
        if (result.status() == SparkProfileReadResult.Status.COMPLETE && snapshot != null) {
            return new SparkProfilerDiagnostics(
                    SparkProfilerDiagnostics.State.COMPLETE,
                    snapshot.sparkVersion(),
                    "Raw Spark profile data was not retained.",
                    durationMillis,
                    heapDeltaBytes,
                    snapshot.windowKey(),
                    snapshot.threadCount(),
                    snapshot.frameCount(),
                    snapshot.hotPaths()
            );
        }
        SparkProfilerDiagnostics.State state = switch (result.status()) {
            case ABSENT -> SparkProfilerDiagnostics.State.ABSENT;
            case NOT_RUNNING, NOT_BACKGROUND -> SparkProfilerDiagnostics.State.NOT_RUNNING;
            case UNSUPPORTED_PLUGIN, UNSUPPORTED_VERSION, UNSUPPORTED_ARTIFACT, UNSUPPORTED_SAMPLER ->
                    SparkProfilerDiagnostics.State.UNSUPPORTED;
            case NO_DATA -> SparkProfilerDiagnostics.State.NO_DATA;
            case NO_ACTIONABLE_DATA -> SparkProfilerDiagnostics.State.NO_ACTIONABLE_DATA;
            case TOO_COMPLEX -> SparkProfilerDiagnostics.State.TOO_COMPLEX;
            case INCOMPATIBLE -> SparkProfilerDiagnostics.State.INCOMPATIBLE;
            case COMPLETE -> SparkProfilerDiagnostics.State.NO_DATA;
        };
        return new SparkProfilerDiagnostics(
                state,
                result.sparkVersion(),
                bounded(result.detail()),
                durationMillis,
                heapDeltaBytes,
                0,
                0,
                0,
                List.of()
        );
    }

    @Nonnull
    static SparkProfilerDiagnostics runtimeDiagnostics(
            @Nonnull SparkProfilerDiagnostics base,
            boolean active,
            boolean circuitOpen,
            long nextCaptureAtMillis) {
        return new SparkProfilerDiagnostics(
                base.state(),
                base.sparkVersion(),
                base.detail(),
                base.captureDurationMillis(),
                base.heapDeltaBytes(),
                base.windowKey(),
                base.threadCount(),
                base.frameCount(),
                base.hotPaths(),
                active,
                circuitOpen,
                nextCaptureAtMillis
        );
    }

    @Nonnull
    static List<String> snapshotLogLines(@Nonnull SparkProfilerDiagnostics result) {
        List<String> lines = new ArrayList<>(result.hotPaths().size() + 1);
        lines.add("Spark profiler monitor completed: " + result.commandSummary());
        int rank = 1;
        for (SparkProfileSnapshot.HotPath hotPath : result.hotPaths()) {
            lines.add(String.format(
                    Locale.ROOT,
                    "Spark hot path #%d: selfShare=%.2f%%, selfMs=%.3f, source=%s, frame=%s",
                    rank++,
                    hotPath.selfSharePercent(),
                    hotPath.selfMilliseconds(),
                    hotPath.source(),
                    hotPath.frame()
            ));
        }
        return lines;
    }

    @Nonnull
    static String nonActionableLogMessage(@Nonnull SparkProfilerDiagnostics result) {
        return "Spark profiler monitor: " + result.commandSummary();
    }

    static long currentHeapHeadroomBytes() {
        return Runtime.getRuntime().maxMemory() - usedHeapBytes();
    }

    static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    @Nonnull
    static String bounded(@Nullable String text) {
        if (text == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(text.length(), MAX_DETAIL_LENGTH));
        for (int index = 0; index < text.length() && safe.length() < MAX_DETAIL_LENGTH; index++) {
            char character = text.charAt(index);
            safe.append(Character.isISOControl(character) ? ' ' : character);
        }
        return safe.toString().trim();
    }

    static void rethrowIfFatal(@Nonnull Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }
}
