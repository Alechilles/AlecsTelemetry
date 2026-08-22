package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

/**
 * Stateless value and formatting helpers for the local Spark profiler monitor.
 */
final class SparkProfilerMonitorSupport {
    static final long MIN_HEAP_HEADROOM_BYTES = 256L * 1024L * 1024L;
    private static final String JVM_CAPTURE_GATE_KEY =
            "com.alechilles.alecstelemetry.runtime.profiler.spark.capture.gate.v1";
    private static final long JVM_CAPTURE_RETRY_SECONDS = 1L;
    private static final int MAX_DETAIL_LENGTH = 240;
    private static final com.sun.management.ThreadMXBean THREAD_ALLOCATION_BEAN =
            threadAllocationBean();

    private SparkProfilerMonitorSupport() {
    }

    @Nonnull
    static SparkProfilerDiagnostics diagnosticsFrom(@Nonnull SparkProfileReadResult result,
                                                     long durationMillis,
                                                     long heapDeltaBytes,
                                                     long captureAllocatedBytes) {
        SparkProfileSnapshot snapshot = result.snapshot();
        if (result.status() == SparkProfileReadResult.Status.COMPLETE && snapshot != null) {
            return new SparkProfilerDiagnostics(
                    SparkProfilerDiagnostics.State.COMPLETE,
                    snapshot.sparkVersion(),
                    "Raw Spark profile data was not retained; captureAllocatedBytes="
                            + allocationText(captureAllocatedBytes) + ".",
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

    static long currentThreadAllocatedBytes() {
        com.sun.management.ThreadMXBean bean = THREAD_ALLOCATION_BEAN;
        if (bean == null) {
            return -1L;
        }
        try {
            return bean.getCurrentThreadAllocatedBytes();
        } catch (RuntimeException | LinkageError ignored) {
            return -1L;
        }
    }

    static long allocationDelta(long before, long after) {
        if (before < 0L || after < before) {
            return -1L;
        }
        return after - before;
    }

    @Nullable
    private static com.sun.management.ThreadMXBean threadAllocationBean() {
        try {
            java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
            if (platformBean instanceof com.sun.management.ThreadMXBean allocationBean
                    && allocationBean.isThreadAllocatedMemorySupported()
                    && allocationBean.isThreadAllocatedMemoryEnabled()) {
                return allocationBean;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Allocation diagnostics are optional and must not affect capture.
        }
        return null;
    }

    @Nonnull
    private static String allocationText(long allocatedBytes) {
        return allocatedBytes < 0L ? "unavailable" : Long.toString(allocatedBytes);
    }

    static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    static boolean tryAcquireJvmCapture(@Nonnull Object token) {
        return jvmCaptureGate().compareAndSet(null, token);
    }

    static void releaseJvmCapture(@Nonnull Object token) {
        jvmCaptureGate().compareAndSet(token, null);
    }

    static long jvmCaptureRetrySeconds() {
        return JVM_CAPTURE_RETRY_SECONDS;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> jvmCaptureGate() {
        Properties properties = System.getProperties();
        Object existing = properties.get(JVM_CAPTURE_GATE_KEY);
        if (existing instanceof AtomicReference<?> reference) {
            return (AtomicReference<Object>) reference;
        }
        synchronized (properties) {
            existing = properties.get(JVM_CAPTURE_GATE_KEY);
            if (existing instanceof AtomicReference<?> reference) {
                return (AtomicReference<Object>) reference;
            }
            AtomicReference<Object> created = new AtomicReference<>();
            properties.put(JVM_CAPTURE_GATE_KEY, created);
            return created;
        }
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
