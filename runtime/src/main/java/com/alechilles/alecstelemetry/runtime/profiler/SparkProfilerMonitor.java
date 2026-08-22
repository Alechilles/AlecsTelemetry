package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Reads bounded summaries from Spark's passive background profiler.
 *
 * <p>The monitor keeps all raw profile work transient. Only immutable summaries
 * remain in the in-memory history.</p>
 */
public final class SparkProfilerMonitor implements AutoCloseable {
    private final TelemetryRuntimeSettings.SparkProfilerSettings settings;
    private final Supplier<?> sparkPluginLookup;
    private final BooleanSupplier activeCoordinatorOwner;
    private final SparkProfileReader profileReader;
    private final LongSupplier heapHeadroomBytes;
    private final HytaleLogger logger;
    private final BiConsumer<Level, String> logSink;
    private final SparkProfilePublicationSink publicationSink;
    private final Object lifecycleLock = new Object();
    private final ArrayDeque<SparkProfileSnapshot> history = new ArrayDeque<>();

    private volatile SparkProfilerDiagnostics diagnostics;
    private ScheduledThreadPoolExecutor executor;
    private ScheduledFuture<?> scheduledTask;
    private SparkProfilerCaptureAttempt inFlight;
    private long lifecycleGeneration;
    private boolean active;
    private boolean circuitOpen;
    private boolean closed;
    private boolean immediateAfterStaleExport;
    private Integer lastCompletedWindowKey;
    private SparkProfilerDiagnostics.State lastLoggedState;

    @FunctionalInterface
    public interface SparkProfilePublicationSink {
        void publish(@Nonnull SparkProfileReadResult result);
    }

    @Nonnull
    public static SparkProfilePublicationSink publicationSink(
            @Nonnull TelemetryProjectProfilerService service) {
        return service::publish;
    }

    public SparkProfilerMonitor(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nullable HytaleLogger logger) {
        this(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                new SparkProfileReflectionBridge(),
                SparkProfilerMonitorSupport::currentHeapHeadroomBytes,
                logger,
                null,
                null
        );
    }

    public SparkProfilerMonitor(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nullable HytaleLogger logger,
            @Nullable SparkProfilePublicationSink publicationSink) {
        this(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                new SparkProfileReflectionBridge(),
                SparkProfilerMonitorSupport::currentHeapHeadroomBytes,
                logger,
                null,
                publicationSink
        );
    }

    SparkProfilerMonitor(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger) {
        this(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                profileReader,
                heapHeadroomBytes,
                logger,
                null,
                null
        );
    }

    SparkProfilerMonitor(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger,
            @Nullable BiConsumer<Level, String> logSink) {
        this(
                settings,
                sparkPluginLookup,
                activeCoordinatorOwner,
                profileReader,
                heapHeadroomBytes,
                logger,
                logSink,
                null
        );
    }

    SparkProfilerMonitor(
            @Nonnull TelemetryRuntimeSettings.SparkProfilerSettings settings,
            @Nonnull Supplier<?> sparkPluginLookup,
            @Nonnull BooleanSupplier activeCoordinatorOwner,
            @Nonnull SparkProfileReader profileReader,
            @Nonnull LongSupplier heapHeadroomBytes,
            @Nullable HytaleLogger logger,
            @Nullable BiConsumer<Level, String> logSink,
            @Nullable SparkProfilePublicationSink publicationSink) {
        this.settings = settings;
        this.sparkPluginLookup = sparkPluginLookup;
        this.activeCoordinatorOwner = activeCoordinatorOwner;
        this.profileReader = profileReader;
        this.heapHeadroomBytes = heapHeadroomBytes;
        this.logger = logger;
        this.logSink = logSink;
        this.publicationSink = publicationSink;
        this.diagnostics = settings.enabled()
                ? SparkProfilerMonitorSupport.runtimeDiagnostics(
                SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.READY,
                        null,
                        "Waiting for coordinator activation."
                ),
                false,
                false,
                0L
        )
                : SparkProfilerDiagnostics.disabled();
    }

    /**
     * Activates the monitor. Activation is safe to repeat and can resume after
     * suspension. A permanent compatibility circuit cannot be reopened.
     */
    public void activate() {
        if (!activateInternal(false)) {
            return;
        }
        verifyOwnerBeforeScheduling();
    }

    /**
     * Suspends future work and invalidates any result that is still returning.
     * History and window de-duplication remain available for a later activation.
     */
    public void suspend() {
        synchronized (lifecycleLock) {
            if (closed || !active) {
                return;
            }
            active = false;
            lifecycleGeneration++;
            cancelScheduledTaskLocked();
            invalidateInFlightLocked();
            shutdownExecutorLocked(true);
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(
                            SparkProfilerDiagnostics.State.READY,
                            diagnostics.sparkVersion(),
                            "Monitor is suspended."
                    ),
                    false,
                    circuitOpen,
                    0L
            );
        }
    }

    /**
     * Closes the monitor permanently. Calls after close are no-ops.
     */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            active = false;
            lifecycleGeneration++;
            cancelScheduledTaskLocked();
            invalidateInFlightLocked();
            shutdownExecutorLocked(true);
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(
                            SparkProfilerDiagnostics.State.SHUTDOWN,
                            diagnostics.sparkVersion(),
                            "Monitor executor is shut down."
                    ),
                    false,
                    circuitOpen,
                    0L
            );
        }
    }

    /**
     * Returns the current immutable local diagnostics.
     */
    @Nonnull
    public SparkProfilerDiagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Returns immutable summaries in oldest-to-newest order.
     */
    @Nonnull
    public List<SparkProfileSnapshot> history() {
        synchronized (lifecycleLock) {
            return List.copyOf(history);
        }
    }

    /**
     * Returns whether a permanent session circuit is open.
     */
    public boolean isCircuitOpen() {
        synchronized (lifecycleLock) {
            return circuitOpen;
        }
    }

    /**
     * Returns whether the monitor is currently active.
     */
    public boolean isActive() {
        synchronized (lifecycleLock) {
            return active;
        }
    }

    /**
     * Starts one capture immediately. This package-private operation keeps
     * focused tests independent of the production startup delay.
     */
    void activateNow() {
        if (!activateInternal(true)) {
            return;
        }
        verifyOwnerBeforeCapture();
    }

    /**
     * Requests one poll immediately. The normal recurring scheduler uses the
     * same capture path, so this is also useful for deterministic tests.
     */
    void pollNow() {
        synchronized (lifecycleLock) {
            if (closed || !active || circuitOpen) {
                return;
            }
            cancelScheduledTaskLocked();
        }
        beginCapture();
    }

    private boolean activateInternal(boolean immediate) {
        synchronized (lifecycleLock) {
            if (!settings.enabled()) {
                return false;
            }
            if (closed || circuitOpen || active) {
                return false;
            }
            active = true;
            lifecycleGeneration++;
            immediateAfterStaleExport = inFlight != null;
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(
                            SparkProfilerDiagnostics.State.WAITING,
                            diagnostics.sparkVersion(),
                            immediate
                                    ? "Capture is starting now."
                                    : "Capture is scheduled after the startup delay."
                    ),
                    true,
                    false,
                    0L
            );
            if (!immediate) {
                scheduleCaptureLocked(settings.initialDelaySeconds(), "startup delay");
            }
            return true;
        }
    }

    private void verifyOwnerBeforeScheduling() {
        if (!isActiveOwner()) {
            deactivateForLostOwnership();
            return;
        }
        synchronized (lifecycleLock) {
            if (closed || !active || circuitOpen || scheduledTask != null) {
                return;
            }
            scheduleCaptureLocked(settings.initialDelaySeconds(), "startup delay");
        }
    }

    private void verifyOwnerBeforeCapture() {
        if (!isActiveOwner()) {
            deactivateForLostOwnership();
            return;
        }
        beginCapture();
    }

    private void beginCapture() {
        long admittedGeneration;
        synchronized (lifecycleLock) {
            if (closed || !active || circuitOpen || inFlight != null) {
                return;
            }
            scheduledTask = null;
            admittedGeneration = lifecycleGeneration;
        }
        if (!isCurrentGeneration(admittedGeneration)) {
            return;
        }
        if (!isActiveOwner(admittedGeneration)) {
            deactivateForLostOwnership(admittedGeneration);
            return;
        }
        if (!isCurrentGeneration(admittedGeneration)) {
            return;
        }

        Object sparkPlugin;
        try {
            sparkPlugin = sparkPluginLookup.get();
        } catch (Throwable failure) {
            failForGeneration(admittedGeneration, "Spark plugin lookup failed", failure);
            return;
        }
        if (!isCurrentGeneration(admittedGeneration)) {
            return;
        }
        if (sparkPlugin == null) {
            finishWithoutCapture(
                    admittedGeneration,
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
            failForGeneration(admittedGeneration, "Heap headroom check failed", failure);
            return;
        }
        if (!isCurrentGeneration(admittedGeneration)) {
            return;
        }
        if (headroom < SparkProfilerMonitorSupport.MIN_HEAP_HEADROOM_BYTES) {
            finishWithoutCapture(
                    admittedGeneration,
                    SparkProfilerDiagnostics.State.LOW_HEAP,
                    "Capture was skipped because JVM heap headroom was below 256 MiB.",
                    false,
                    "Spark profiler monitor skipped a capture because JVM heap headroom was below 256 MiB."
            );
            return;
        }

        startCapture(sparkPlugin, admittedGeneration);
    }

    private void startCapture(@Nonnull Object sparkPlugin, long admittedGeneration) {
        long startedAtNanos = System.nanoTime();
        SparkProfilerCaptureAttempt attempt = new SparkProfilerCaptureAttempt(
                admittedGeneration,
                startedAtNanos,
                SparkProfilerMonitorSupport.usedHeapBytes(),
                sparkPlugin
        );
        if (!SparkProfilerMonitorSupport.tryAcquireJvmCapture(attempt)) {
            deferBusyCapture(admittedGeneration);
            return;
        }
        SparkProfilerCaptureTask task = new SparkProfilerCaptureTask(attempt, () -> {
            capture(attempt);
            return null;
        });
        attempt.task = task;
        boolean submitted = false;

        try {
            synchronized (lifecycleLock) {
                if (closed || !active || circuitOpen || attempt.generation != lifecycleGeneration
                        || inFlight != null) {
                    return;
                }
                inFlight = attempt;
                diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                        SparkProfilerDiagnostics.simple(
                                SparkProfilerDiagnostics.State.CAPTURING,
                                null,
                                "Reading Spark's latest completed background window."
                        ),
                        true,
                        false,
                        0L
                );
                try {
                    ScheduledThreadPoolExecutor currentExecutor = executorLocked();
                    attempt.timeoutTask = currentExecutor.schedule(
                            () -> timeOut(attempt),
                            settings.timeoutSeconds(),
                            TimeUnit.SECONDS
                    );
                    currentExecutor.execute(task);
                    submitted = true;
                } catch (RuntimeException failure) {
                    if (attempt.timeoutTask != null) {
                        attempt.timeoutTask.cancel(false);
                    }
                    if (inFlight == attempt) {
                        inFlight = null;
                    }
                    task.cancel(true);
                    failLocked("Spark capture scheduling failed", failure);
                }
            }
        } finally {
            if (!submitted) {
                attempt.releaseJvmCapture();
            }
        }
    }

    private void deferBusyCapture(long admittedGeneration) {
        synchronized (lifecycleLock) {
            if (closed || !active || circuitOpen || inFlight != null
                    || lifecycleGeneration != admittedGeneration) {
                return;
            }
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(
                            SparkProfilerDiagnostics.State.WAITING,
                            diagnostics.sparkVersion(),
                            "Another Telemetry runtime is reading Spark; capture will retry."
                    ),
                    true,
                    circuitOpen,
                    0L
            );
            scheduleCaptureLocked(
                    SparkProfilerMonitorSupport.jvmCaptureRetrySeconds(),
                    "shared Spark capture gate"
            );
        }
    }

    private void capture(@Nonnull SparkProfilerCaptureAttempt attempt) {
        synchronized (lifecycleLock) {
            if (inFlight != attempt || !isCurrentAttemptLocked(attempt)) {
                retireLateAttemptLocked(attempt);
                attempt.releaseJvmCapture();
                return;
            }
            attempt.started = true;
        }
        SparkProfileReadResult result = null;
        Throwable failure = null;
        long allocatedBefore = SparkProfilerMonitorSupport.currentThreadAllocatedBytes();
        long allocatedAfter;
        try {
            result = profileReader.read(attempt.sparkPlugin, settings.maxSummaryEntries());
        } catch (Throwable caught) {
            SparkProfilerMonitorSupport.rethrowIfFatal(caught);
            failure = caught;
        } finally {
            allocatedAfter = SparkProfilerMonitorSupport.currentThreadAllocatedBytes();
            attempt.releaseJvmCapture();
        }
        if (failure != null) {
            handleCaptureFailure(attempt, failure);
        } else {
            publishCapture(
                    attempt,
                    result,
                    SparkProfilerMonitorSupport.allocationDelta(allocatedBefore, allocatedAfter)
            );
        }
    }

    private void handleCaptureFailure(@Nonnull SparkProfilerCaptureAttempt attempt,
                                      @Nonnull Throwable failure) {
        synchronized (lifecycleLock) {
            if (inFlight != attempt || retireLateAttemptLocked(attempt)) {
                return;
            }
            inFlight = null;
            cancelTimeoutLocked(attempt);
            failLocked("Spark snapshot failed", failure);
        }
    }

    private void publishCapture(@Nonnull SparkProfilerCaptureAttempt attempt,
                                @Nullable SparkProfileReadResult result,
                                long captureAllocatedBytes) {
        long durationMillis = SparkProfilerMonitorSupport.elapsedMillis(attempt.startedAtNanos);
        long heapDelta = SparkProfilerMonitorSupport.usedHeapBytes() - attempt.heapBefore;
        synchronized (lifecycleLock) {
            if (inFlight != attempt || retireLateAttemptLocked(attempt)) {
                return;
            }
        }
        if (!isActiveOwner()) {
            deactivateForLostOwnership();
            synchronized (lifecycleLock) {
                retireLateAttemptLocked(attempt);
            }
            return;
        }
        boolean publish = false;
        boolean publishProject = false;
        SparkProfilerDiagnostics resultDiagnostics;
        synchronized (lifecycleLock) {
            if (inFlight != attempt || retireLateAttemptLocked(attempt)) {
                return;
            }
            inFlight = null;
            cancelTimeoutLocked(attempt);
            if (result == null) {
                failLocked(
                        "Spark snapshot failed",
                        new IllegalStateException("Spark returned no read result.")
                );
                return;
            }
            SparkProfilerDiagnostics completed = SparkProfilerMonitorSupport.diagnosticsFrom(
                    result,
                    durationMillis,
                    heapDelta,
                    captureAllocatedBytes
            );
            boolean opensCircuit = opensCircuit(result.status());
            if (opensCircuit) {
                circuitOpen = true;
                active = false;
                lifecycleGeneration++;
                cancelScheduledTaskLocked();
                shutdownExecutorLocked(true);
            }
            resultDiagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    completed,
                    active && !closed,
                    circuitOpen,
                    0L
            );
            diagnostics = resultDiagnostics;
            if (completed.state() == SparkProfilerDiagnostics.State.COMPLETE
                    && result.snapshot() != null
                    && !Integer.valueOf(result.snapshot().windowKey()).equals(lastCompletedWindowKey)) {
                lastCompletedWindowKey = result.snapshot().windowKey();
                history.addLast(result.snapshot());
                while (history.size() > Math.max(1, settings.maxHistorySnapshots())) {
                    history.removeFirst();
                }
                publish = true;
                publishProject = publicationSink != null && result.window() != null;
            }
            if (!opensCircuit && active && !closed) {
                scheduleCaptureLocked(settings.intervalSeconds(), "poll interval");
                resultDiagnostics = diagnostics;
            }
        }

        if (publishProject && !isCurrentOwnerForPublication(attempt.generation)) {
            deactivateForLostOwnership(attempt.generation);
            publishProject = false;
        }
        if (publishProject) {
            try {
                publicationSink.publish(result);
            } catch (Throwable failure) {
                SparkProfilerMonitorSupport.rethrowIfFatal(failure);
                log(
                        Level.WARNING,
                        "Spark project publication failed: "
                                + SparkProfilerMonitorSupport.bounded(failure.toString())
                );
            }
        }
        if (publish) {
            logSnapshot(resultDiagnostics);
        } else if (shouldLogState(resultDiagnostics.state())) {
            logNonActionable(resultDiagnostics);
        }
    }

    private boolean isCurrentOwnerForPublication(long admittedGeneration) {
        synchronized (lifecycleLock) {
            if (closed || !active || circuitOpen || lifecycleGeneration != admittedGeneration) {
                return false;
            }
            try {
                return activeCoordinatorOwner.getAsBoolean();
            } catch (Throwable failure) {
                SparkProfilerMonitorSupport.rethrowIfFatal(failure);
                log(
                        Level.WARNING,
                        "Spark project publication owner check failed: "
                                + SparkProfilerMonitorSupport.bounded(failure.toString())
                );
                return false;
            }
        }
    }

    private void timeOut(@Nonnull SparkProfilerCaptureAttempt attempt) {
        synchronized (lifecycleLock) {
            if (!isCurrentAttemptLocked(attempt)) {
                return;
            }
            lifecycleGeneration++;
            circuitOpen = true;
            active = false;
            cancelTimeoutLocked(attempt);
            cancelScheduledTaskLocked();
            attempt.task.cancel(true);
            if (!attempt.started) {
                inFlight = null;
            }
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    new SparkProfilerDiagnostics(
                            SparkProfilerDiagnostics.State.TIMED_OUT,
                            null,
                            "Spark snapshot exceeded the time limit. Late results will be discarded; direct-read work may continue until it returns.",
                            SparkProfilerMonitorSupport.elapsedMillis(attempt.startedAtNanos),
                            0L,
                            0,
                            0,
                            0,
                            List.of()
                    ),
                    false,
                    true,
                    0L
            );
            shutdownExecutorLocked(true);
            log(Level.WARNING, "Spark profiler monitor timed out. Late results will be discarded; direct-read work may continue until it returns.");
        }
    }

    private void finishWithoutCapture(long admittedGeneration,
                                      @Nonnull SparkProfilerDiagnostics.State state,
                                      @Nonnull String detail,
                                      boolean openCircuit,
                                      @Nullable String warning) {
        boolean logWarning = false;
        synchronized (lifecycleLock) {
            if (closed || !active || lifecycleGeneration != admittedGeneration) {
                return;
            }
            if (openCircuit) {
                circuitOpen = true;
                active = false;
                lifecycleGeneration++;
                cancelScheduledTaskLocked();
                shutdownExecutorLocked(true);
            }
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(state, null, detail),
                    active,
                    circuitOpen,
                    0L
            );
            if (!openCircuit && active) {
                scheduleCaptureLocked(settings.intervalSeconds(), "poll interval");
            }
            if (warning != null && lastLoggedState != state) {
                lastLoggedState = state;
                logWarning = true;
            }
        }
        if (logWarning) {
            log(Level.WARNING, SparkProfilerMonitorSupport.bounded(warning));
        }
    }

    private boolean isActiveOwner() {
        try {
            return activeCoordinatorOwner.getAsBoolean();
        } catch (Throwable failure) {
            fail("Active coordinator check failed", failure);
            return false;
        }
    }

    private boolean isActiveOwner(long admittedGeneration) {
        try {
            return activeCoordinatorOwner.getAsBoolean();
        } catch (Throwable failure) {
            failForGeneration(admittedGeneration, "Active coordinator check failed", failure);
            return false;
        }
    }

    private void deactivateForLostOwnership() {
        deactivateForLostOwnership(null);
    }

    private void deactivateForLostOwnership(@Nullable Long admittedGeneration) {
        synchronized (lifecycleLock) {
            if (closed || !active
                    || (admittedGeneration != null && lifecycleGeneration != admittedGeneration)) {
                return;
            }
            active = false;
            lifecycleGeneration++;
            cancelScheduledTaskLocked();
            invalidateInFlightLocked();
            shutdownExecutorLocked(true);
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    SparkProfilerDiagnostics.simple(
                            SparkProfilerDiagnostics.State.NOT_OWNER,
                            diagnostics.sparkVersion(),
                            "This runtime does not own the active Telemetry coordinator."
                    ),
                    false,
                    circuitOpen,
                    0L
            );
        }
    }

    private void fail(@Nonnull String operation, @Nonnull Throwable failure) {
        SparkProfilerMonitorSupport.rethrowIfFatal(failure);
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            failLocked(operation, failure);
        }
    }

    private void failForGeneration(long admittedGeneration,
                                   @Nonnull String operation,
                                   @Nonnull Throwable failure) {
        SparkProfilerMonitorSupport.rethrowIfFatal(failure);
        synchronized (lifecycleLock) {
            if (closed || !active || lifecycleGeneration != admittedGeneration) {
                return;
            }
            failLocked(operation, failure);
        }
    }

    private void failLocked(@Nonnull String operation, @Nonnull Throwable failure) {
        SparkProfilerMonitorSupport.rethrowIfFatal(failure);
        inFlight = null;
        lifecycleGeneration++;
        circuitOpen = true;
        active = false;
        cancelScheduledTaskLocked();
        cancelTimeoutLocked(null);
        shutdownExecutorLocked(true);
        String detail = SparkProfilerMonitorSupport.bounded(operation + ": " + failure.getClass().getSimpleName()
                + (failure.getMessage() == null || failure.getMessage().isBlank()
                ? ""
                : ": " + failure.getMessage()));
        diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                SparkProfilerDiagnostics.simple(SparkProfilerDiagnostics.State.FAILED, null, detail),
                false,
                true,
                0L
        );
        log(Level.WARNING, "Spark profiler monitor failed safely: " + detail);
    }

    private boolean opensCircuit(@Nonnull SparkProfileReadResult.Status status) {
        return switch (status) {
            case UNSUPPORTED_PLUGIN, UNSUPPORTED_VERSION, UNSUPPORTED_ARTIFACT,
                    UNSUPPORTED_SAMPLER, TOO_COMPLEX, INCOMPATIBLE -> true;
            default -> false;
        };
    }

    private boolean shouldLogState(@Nonnull SparkProfilerDiagnostics.State state) {
        if (state == SparkProfilerDiagnostics.State.COMPLETE
                || state == SparkProfilerDiagnostics.State.ABSENT
                || state == SparkProfilerDiagnostics.State.NOT_RUNNING
                || state == SparkProfilerDiagnostics.State.NO_DATA
                || state == SparkProfilerDiagnostics.State.NOT_OWNER) {
            return false;
        }
        synchronized (lifecycleLock) {
            if (lastLoggedState == state) {
                return false;
            }
            lastLoggedState = state;
            return true;
        }
    }

    private void logSnapshot(@Nonnull SparkProfilerDiagnostics result) {
        for (String line : SparkProfilerMonitorSupport.snapshotLogLines(result)) {
            log(Level.INFO, line);
        }
    }

    private void logNonActionable(@Nonnull SparkProfilerDiagnostics result) {
        log(Level.WARNING, SparkProfilerMonitorSupport.nonActionableLogMessage(result));
    }

    private void log(@Nonnull Level level, @Nonnull String message) {
        String safe = SparkProfilerMonitorSupport.bounded(message);
        if (logSink != null) {
            try {
                logSink.accept(level, safe);
            } catch (RuntimeException ignored) {
            }
        }
        if (logger != null) {
            try {
                logger.at(level).log(safe);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void scheduleCaptureLocked(long delaySeconds, @Nonnull String reason) {
        if (closed || !active || circuitOpen || inFlight != null) {
            return;
        }
        try {
            scheduledTask = executorLocked().schedule(
                    this::beginCapture,
                    Math.max(0L, delaySeconds),
                    TimeUnit.SECONDS
            );
            diagnostics = SparkProfilerMonitorSupport.runtimeDiagnostics(
                    diagnostics,
                    true,
                    false,
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.max(0L, delaySeconds))
            );
        } catch (RejectedExecutionException failure) {
            failLocked("Spark monitor scheduling failed while " + reason, failure);
        } catch (RuntimeException failure) {
            failLocked("Spark monitor scheduling failed while " + reason, failure);
        }
    }

    @Nonnull
    private ScheduledThreadPoolExecutor executorLocked() {
        if (closed) {
            throw new IllegalStateException("Spark profiler monitor is closed.");
        }
        if (executor == null || executor.isShutdown()) {
            executor = new ScheduledThreadPoolExecutor(2, new MonitorThreadFactory());
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }
        return executor;
    }

    private void cancelScheduledTaskLocked() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    private void invalidateInFlightLocked() {
        if (inFlight == null) {
            return;
        }
        SparkProfilerCaptureAttempt attempt = inFlight;
        cancelTimeoutLocked(attempt);
        attempt.task.cancel(true);
        if (!attempt.started) {
            inFlight = null;
        }
    }

    private void cancelTimeoutLocked(@Nullable SparkProfilerCaptureAttempt attempt) {
        if (attempt == null) {
            if (inFlight != null && inFlight.timeoutTask != null) {
                inFlight.timeoutTask.cancel(false);
            }
            return;
        }
        if (attempt.timeoutTask != null) {
            attempt.timeoutTask.cancel(false);
            attempt.timeoutTask = null;
        }
    }

    private void shutdownExecutorLocked(boolean interrupt) {
        if (executor == null) {
            return;
        }
        if (interrupt) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }
        executor = null;
    }

    private boolean retireLateAttemptLocked(@Nonnull SparkProfilerCaptureAttempt attempt) {
        if (isCurrentAttemptLocked(attempt)) {
            return false;
        }
        cancelTimeoutLocked(attempt);
        if (inFlight == attempt) {
            inFlight = null;
        }
        if (active && !closed && !circuitOpen && scheduledTask == null) {
            long delaySeconds = immediateAfterStaleExport ? 0L : settings.intervalSeconds();
            immediateAfterStaleExport = false;
            scheduleCaptureLocked(delaySeconds, "poll interval after stale export");
        }
        return true;
    }

    private boolean isCurrentAttemptLocked(@Nonnull SparkProfilerCaptureAttempt attempt) {
        return !closed
                && active
                && inFlight == attempt
                && attempt.generation == lifecycleGeneration;
    }

    private boolean isCurrentGeneration(long admittedGeneration) {
        synchronized (lifecycleLock) {
            return !closed
                    && active
                    && !circuitOpen
                    && lifecycleGeneration == admittedGeneration;
        }
    }

    private static final class MonitorThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public synchronized Thread newThread(@Nonnull Runnable task) {
            Thread thread = new Thread(task, "AlecsTelemetry-SparkMonitor-" + ++sequence);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
