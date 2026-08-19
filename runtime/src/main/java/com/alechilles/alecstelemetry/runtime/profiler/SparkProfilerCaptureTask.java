package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Capture work that releases an unstarted JVM gate token when cancellation prevents execution.
 */
final class SparkProfilerCaptureTask extends FutureTask<Void> {
    private final SparkProfilerCaptureAttempt attempt;

    SparkProfilerCaptureTask(@Nonnull SparkProfilerCaptureAttempt attempt,
                             @Nonnull Callable<Void> callable) {
        super(callable);
        this.attempt = attempt;
    }

    @Override
    protected void done() {
        if (isCancelled() && !attempt.started) {
            attempt.releaseJvmCapture();
        }
    }
}
