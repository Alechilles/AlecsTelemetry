package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Runs one bounded, read-only Spark compatibility canary after server boot.
 */
public final class SparkProfilerCanary implements AutoCloseable {
    private static final long MIN_HEAP_HEADROOM_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_DETAIL_LENGTH = 240;

    private final TelemetryRuntimeSettings.SparkProfilerCanarySettings settings;
    private final Supplier<?> sparkPluginLookup;
    private final BooleanSupplier activeCoordinatorOwner;
    private final SparkProfileReader profileReader;
    private final LongSupplier heapHeadroomBytes;
    private final HytaleLogger logger;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicReference<SparkProfilerDiagnostics> diagnostics;
    private final AtomicReference<ScheduledFuture<?>> delayedTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();
    private final AtomicReference<FutureTask<Void>> captureTask = new AtomicReference<>();
    private volatile ScheduledThreadPoolExecutor executor;

    public SparkProfilerCanary(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerCanarySettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nullable HytaleLogger logger) {
        this(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                new SparkProfileReflectionBridge(),
                SparkProfilerCanary::currentHeapHeadroomBytes,
                logger
        );
    }

    SparkProfilerCanary(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerCanarySettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger) {
        this.settings = settings;
        this.sparkPluginLookup = sparkPluginLookup;
        this.activeCoordinatorOwner = activeCoordinatorOwner;
        this.profileReader = profileReader;
        this.heapHeadroomBytes = heapHeadroomBytes;
        this.logger = logger;
        this.diagnostics = new AtomicReference<>(settings.enabled()
                ? SparkProfilerDiagnostics.simple(
                SparkProfilerDiagnostics.State.READY,
                null,
                "Waiting for server boot."
        )
                : SparkProfilerDiagnostics.disabled());
    }

    /**
     * Schedules the only capture attempt. Calls after the first call do nothing.
     */
    public void start() {
        if (!beginStart()) {
            return;
        }
        if (!isActiveOwner()) {
            return;
        }
        synchronized (lifecycleLock) {
            if (closed.get() || circuitOpen.get()) {
                return;
            }
            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.WAITING,
                    null,
                    "Capture is scheduled after the startup delay."
            ));
            try {
                ScheduledFuture<?> scheduled = executor().schedule(
                        this::beginCapture,
                        settings.initialDelaySeconds(),
                        TimeUnit.SECONDS
                );
                delayedTask.set(scheduled);
            } catch (RuntimeException failure) {
                fail("Spark canary scheduling failed", failure);
            }
        }
    }

    void startNow() {
        if (!beginStart()) {
            return;
        }
        beginCapture();
    }

    private boolean beginStart() {
        return settings.enabled()
                && !closed.get()
                && !circuitOpen.get()
                && started.compareAndSet(false, true);
    }

    private void beginCapture() {
        if (closed.get() || circuitOpen.get() || !isActiveOwner()) {
            return;
        }

        Object sparkPlugin;
        try {
            sparkPlugin = sparkPluginLookup.get();
        } catch (Throwable failure) {
            fail("Spark plugin lookup failed", failure);
            return;
        }
        if (sparkPlugin == null) {
            finishWithoutCapture(
                    SparkProfilerDiagnostics.State.ABSENT,
                    "Spark is not loaded.",
                    false,
                    null
            );
            return;
        }

        long headroom;
        try {
            headroom = heapHeadroomBytes.getAsLong();
        } catch (Throwable failure) {
            fail("Heap headroom check failed", failure);
            return;
        }
        if (headroom < MIN_HEAP_HEADROOM_BYTES) {
            finishWithoutCapture(
                    SparkProfilerDiagnostics.State.LOW_HEAP,
                    "Capture was skipped because JVM heap headroom was below 256 MiB.",
                    true,
                    "Spark profiler canary skipped: JVM heap headroom was below 256 MiB."
            );
            return;
        }
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }

        long startedAtNanos = System.nanoTime();
        long heapBefore = usedHeapBytes();
        FutureTask<Void> task = new FutureTask<>(() -> {
            capture(sparkPlugin, startedAtNanos, heapBefore);
            return null;
        });
        synchronized (lifecycleLock) {
            if (closed.get() || circuitOpen.get()) {
                inFlight.set(false);
                return;
            }
            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.CAPTURING,
                    null,
                    "Reading Spark's latest completed background window."
            ));
            captureTask.set(task);
            ScheduledFuture<?> timeout = null;
            try {
                ScheduledThreadPoolExecutor currentExecutor = executor();
                timeout = currentExecutor.schedule(
                        () -> timeOut(task, startedAtNanos),
                        settings.timeoutSeconds(),
                        TimeUnit.SECONDS
                );
                timeoutTask.set(timeout);
                currentExecutor.execute(task);
            } catch (RuntimeException failure) {
                if (timeout != null) {
                    timeout.cancel(false);
                }
                inFlight.set(false);
                task.cancel(true);
                fail("Spark capture scheduling failed", failure);
            }
        }
    }

    private void capture(@Nonnull Object sparkPlugin, long startedAtNanos, long heapBefore) {
        SparkProfileReadResult result;
        try {
            result = profileReader.read(sparkPlugin, settings.maxSummaryEntries());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            if (inFlight.compareAndSet(true, false)) {
                cancelTimeout();
                fail("Spark snapshot failed", failure);
            }
            return;
        }

        long durationMillis = elapsedMillis(startedAtNanos);
        long heapDelta = usedHeapBytes() - heapBefore;
        if (!inFlight.compareAndSet(true, false)) {
            return;
        }
        cancelTimeout();

        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            SparkProfilerDiagnostics completed = diagnosticsFrom(result, durationMillis, heapDelta);
            diagnostics.set(completed);
            if (completed.state() == SparkProfilerDiagnostics.State.INCOMPATIBLE) {
                circuitOpen.set(true);
            }
            logResult(completed);
            shutdownExecutor(false);
        }
    }

    @Nonnull
    private static SparkProfilerDiagnostics diagnosticsFrom(@Nonnull SparkProfileReadResult result,
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
                java.util.List.of()
        );
    }

    private void timeOut(@Nonnull FutureTask<Void> task, long startedAtNanos) {
        if (task.isDone() || !inFlight.compareAndSet(true, false)) {
            return;
        }
        task.cancel(true);
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            circuitOpen.set(true);
            diagnostics.set(new SparkProfilerDiagnostics(
                    SparkProfilerDiagnostics.State.TIMED_OUT,
                    null,
                    "Spark snapshot exceeded the time limit. Late results will be discarded; export work may continue until Spark returns.",
                    elapsedMillis(startedAtNanos),
                    0L,
                    0,
                    0,
                    0,
                    java.util.List.of()
            ));
            log(Level.WARNING, "Spark profiler canary timed out. Late results will be discarded; export work may continue until Spark returns.");
            shutdownExecutor(true);
        }
    }

    private boolean isActiveOwner() {
        try {
            if (activeCoordinatorOwner.getAsBoolean()) {
                return true;
            }
            finishWithoutCapture(
                    SparkProfilerDiagnostics.State.NOT_OWNER,
                    "This runtime does not own the active Telemetry coordinator.",
                    false,
                    null
            );
            return false;
        } catch (Throwable failure) {
            fail("Active coordinator check failed", failure);
            return false;
        }
    }

    private void fail(@Nonnull String operation, @Nonnull Throwable failure) {
        rethrowIfFatal(failure);
        inFlight.set(false);
        String detail = bounded(operation + ": " + failure.getClass().getSimpleName()
                + (failure.getMessage() == null || failure.getMessage().isBlank()
                ? ""
                : ": " + failure.getMessage()));
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            circuitOpen.set(true);
            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.FAILED,
                    null,
                    detail
            ));
            log(Level.WARNING, "Spark profiler canary failed safely: " + detail);
            shutdownExecutor(true);
        }
    }

    private void logResult(@Nonnull SparkProfilerDiagnostics result) {
        if (result.state() != SparkProfilerDiagnostics.State.COMPLETE) {
            Level level = result.state() == SparkProfilerDiagnostics.State.INCOMPATIBLE
                    ? Level.WARNING
                    : Level.INFO;
            log(level, "Spark profiler canary: " + result.commandSummary());
            return;
        }
        log(Level.INFO, "Spark profiler canary completed: " + result.commandSummary());
        int rank = 1;
        for (SparkProfileSnapshot.HotPath hotPath : result.hotPaths()) {
            log(Level.INFO, String.format(
                    Locale.ROOT,
                    "Spark hot path #%d: selfShare=%.2f%%, selfMs=%.3f, source=%s, frame=%s",
                    rank++,
                    hotPath.selfSharePercent(),
                    hotPath.selfMilliseconds(),
                    hotPath.source(),
                    hotPath.frame()
            ));
        }
    }

    private void finishWithoutCapture(@Nonnull SparkProfilerDiagnostics.State state,
                                      @Nonnull String detail,
                                      boolean openCircuit,
                                      @Nullable String warning) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            if (openCircuit) {
                circuitOpen.set(true);
            }
            diagnostics.set(SparkProfilerDiagnostics.simple(state, null, detail));
            if (warning != null) {
                log(Level.WARNING, warning);
            }
            shutdownExecutor(false);
        }
    }

    private void log(@Nonnull Level level, @Nonnull String message) {
        if (logger == null) {
            return;
        }
        try {
            logger.at(level).log(message);
        } catch (RuntimeException ignored) {
        }
    }

    @Nonnull
    public SparkProfilerDiagnostics diagnostics() {
        return diagnostics.get();
    }

    private void cancelTimeout() {
        ScheduledFuture<?> timeout = timeoutTask.getAndSet(null);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    @Nonnull
    private ScheduledThreadPoolExecutor executor() {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                throw new IllegalStateException("Spark canary is closed.");
            }
            if (executor == null) {
                executor = new ScheduledThreadPoolExecutor(2, new CanaryThreadFactory());
                executor.setRemoveOnCancelPolicy(true);
                executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
                executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            }
            return executor;
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            inFlight.set(false);
            ScheduledFuture<?> delayed = delayedTask.getAndSet(null);
            if (delayed != null) {
                delayed.cancel(false);
            }
            cancelTimeout();
            FutureTask<Void> capture = captureTask.getAndSet(null);
            if (capture != null) {
                capture.cancel(true);
            }
            shutdownExecutor(true);
            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.SHUTDOWN,
                    diagnostics.get().sparkVersion(),
                    "Canary executor is shut down."
            ));
        }
    }

    private static long currentHeapHeadroomBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() - usedHeapBytes();
    }

    private void shutdownExecutor(boolean interrupt) {
        ScheduledThreadPoolExecutor current = executor;
        if (current == null) {
            return;
        }
        if (interrupt) {
            current.shutdownNow();
        } else {
            current.shutdown();
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    @Nonnull
    private static String bounded(@Nullable String text) {
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

    private static void rethrowIfFatal(@Nonnull Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }

    private static final class CanaryThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(@Nonnull Runnable task) {
            Thread thread = new Thread(task, "AlecsTelemetry-SparkCanary-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
