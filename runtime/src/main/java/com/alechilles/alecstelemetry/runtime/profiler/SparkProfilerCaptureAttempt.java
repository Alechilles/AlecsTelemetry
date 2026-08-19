package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One Spark export and its lifecycle fencing metadata.
 */
final class SparkProfilerCaptureAttempt {
    final long generation;
    final long startedAtNanos;
    final long heapBefore;
    final Object sparkPlugin;
    private final AtomicBoolean jvmCaptureReleased = new AtomicBoolean();
    volatile boolean started;
    FutureTask<Void> task;
    ScheduledFuture<?> timeoutTask;

    SparkProfilerCaptureAttempt(long generation,
                                long startedAtNanos,
                                long heapBefore,
                                @Nonnull Object sparkPlugin) {
        this.generation = generation;
        this.startedAtNanos = startedAtNanos;
        this.heapBefore = heapBefore;
        this.sparkPlugin = sparkPlugin;
    }

    void releaseJvmCapture() {
        if (jvmCaptureReleased.compareAndSet(false, true)) {
            SparkProfilerMonitorSupport.releaseJvmCapture(this);
        }
    }
}
