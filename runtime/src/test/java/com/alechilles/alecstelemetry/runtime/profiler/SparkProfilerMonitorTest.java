package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkProfilerMonitorTest {

    @Test
    void cancelledQueuedCaptureTaskReleasesGateBeforeReaderStarts() throws Exception {
        SparkProfilerCaptureAttempt attempt = new SparkProfilerCaptureAttempt(
                1L,
                System.nanoTime(),
                0L,
                new Object()
        );
        assertTrue(SparkProfilerMonitorSupport.tryAcquireJvmCapture(attempt));
        AtomicBoolean readerStarted = new AtomicBoolean();
        SparkProfilerCaptureTask task = new SparkProfilerCaptureTask(
                attempt,
                () -> {
                    readerStarted.set(true);
                    return null;
                }
        );
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            executor.execute(() -> {
                blockerEntered.countDown();
                awaitIgnoringInterrupt(releaseBlocker);
            });
            assertTrue(blockerEntered.await(1, TimeUnit.SECONDS));
            executor.execute(task);
            assertTrue(task.cancel(false));
            assertFalse(readerStarted.get());

            Object replacement = new Object();
            assertTrue(SparkProfilerMonitorSupport.tryAcquireJvmCapture(replacement));
            SparkProfilerMonitorSupport.releaseJvmCapture(replacement);
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
            SparkProfilerMonitorSupport.releaseJvmCapture(attempt);
        }
    }

    @Test
    void publishesEachNewWindowOnceAndKeepsBoundedHistoryAndLogs() throws Exception {
        Queue<SparkProfileReadResult> results = new ConcurrentLinkedQueue<>(List.of(
                complete(1),
                complete(1),
                complete(2),
                complete(3)
        ));
        AtomicInteger reads = new AtomicInteger();
        List<String> logs = new ArrayList<>();
        SparkProfilerMonitor monitor = newMonitor(
                settings(2, 2, 5),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return results.remove();
                },
                logs
        );
        try {
            monitor.activateNow();
            awaitHistorySize(monitor, 1, Duration.ofSeconds(2));
            monitor.pollNow();
            awaitReads(reads, 2, Duration.ofSeconds(2));
            monitor.pollNow();
            awaitHistorySize(monitor, 2, Duration.ofSeconds(2));
            monitor.pollNow();
            awaitReads(reads, 4, Duration.ofSeconds(2));
            awaitLatestWindow(monitor, 3, Duration.ofSeconds(2));

            assertEquals(List.of(2, 3), monitor.history().stream()
                    .map(SparkProfileSnapshot::windowKey)
                    .toList());
            assertEquals(1, logs.stream().filter(message -> message.contains("window=1")).count());
            assertEquals(1, logs.stream().filter(message -> message.contains("window=2")).count());
            assertEquals(1, logs.stream().filter(message -> message.contains("window=3")).count());
        } finally {
            monitor.close();
        }
    }

    @Test
    void suspensionRejectsLateCaptureAndReactivationRetainsWindowDedupe() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    if (reads.incrementAndGet() == 1) {
                        entered.countDown();
                        await(release);
                    }
                    return complete(7);
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            monitor.suspend();
            release.countDown();
            awaitReads(reads, 1, Duration.ofSeconds(2));
            assertTrue(monitor.history().isEmpty());

            monitor.activateNow();
            awaitHistorySize(monitor, 1, Duration.ofSeconds(2));
            monitor.suspend();

            assertEquals(2, reads.get());
            assertEquals(7, monitor.history().get(0).windowKey());
            assertFalse(monitor.diagnostics().active());
        } finally {
            release.countDown();
            monitor.close();
        }
    }

    @Test
    void staleAdmittedPollRetiresAfterReactivationWithoutStartingAnotherTimerChain() throws Exception {
        CountDownLatch oldLookupEntered = new CountDownLatch(1);
        CountDownLatch releaseOldLookup = new CountDownLatch(1);
        CountDownLatch oldRead = new CountDownLatch(1);
        AtomicInteger lookupCalls = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        Queue<Object> readPlugins = new ConcurrentLinkedQueue<>();
        Object oldPlugin = new Object();
        Object newPlugin = new Object();
        SparkProfilerMonitor monitor = new SparkProfilerMonitor(
                settings(1, 5, 5),
                () -> {
                    if (lookupCalls.incrementAndGet() == 1) {
                        oldLookupEntered.countDown();
                        awaitIgnoringInterrupt(releaseOldLookup);
                        return oldPlugin;
                    }
                    return newPlugin;
                },
                () -> true,
                (plugin, maxEntries) -> {
                    readPlugins.add(plugin);
                    if (plugin == oldPlugin) {
                        oldRead.countDown();
                    }
                    return complete(reads.incrementAndGet());
                },
                () -> Long.MAX_VALUE,
                null,
                (level, message) -> {
                }
        );
        try {
            monitor.activate();
            assertTrue(oldLookupEntered.await(1, TimeUnit.SECONDS));

            monitor.suspend();
            monitor.activateNow();
            awaitHistorySize(monitor, 1, Duration.ofSeconds(2));
            assertEquals(1, reads.get());
            assertTrue(readPlugins.peek() == newPlugin);

            releaseOldLookup.countDown();
            assertFalse(oldRead.await(250L, TimeUnit.MILLISECONDS));
            assertEquals(1, reads.get());

            awaitReads(reads, 2, Duration.ofSeconds(2));
            Thread.sleep(100L);
            assertEquals(2, reads.get());
        } finally {
            releaseOldLookup.countDown();
            monitor.close();
        }
    }

    @Test
    void recurringPollStartsOnlyAfterThePriorCaptureResolves() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(0, 5, 5),
                (plugin, maxEntries) -> {
                    int read = reads.incrementAndGet();
                    if (read == 1) {
                        entered.countDown();
                        await(release);
                        return complete(10);
                    }
                    if (read == 2) {
                        return complete(11);
                    }
                    return SparkProfileReadResult.noData("no additional completed window");
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            Thread.sleep(100L);
            assertEquals(1, reads.get());

            release.countDown();
            awaitLatestWindow(monitor, 11, Duration.ofSeconds(2));
            assertTrue(reads.get() >= 2);
        } finally {
            release.countDown();
            monitor.close();
        }
    }

    @Test
    void suspensionDoesNotAllowASecondExportWhileTheFirstIgnoresInterruption() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger activeReads = new AtomicInteger();
        AtomicInteger maxConcurrentReads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    int read = reads.incrementAndGet();
                    int concurrent = activeReads.incrementAndGet();
                    maxConcurrentReads.accumulateAndGet(concurrent, Math::max);
                    try {
                        if (read == 1) {
                            entered.countDown();
                            awaitIgnoringInterrupt(release);
                        }
                        return complete(read);
                    } finally {
                        activeReads.decrementAndGet();
                    }
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            monitor.suspend();
            monitor.activateNow();
            Thread.sleep(100L);

            assertEquals(1, reads.get());
            assertEquals(1, maxConcurrentReads.get());

            release.countDown();
            awaitReads(reads, 2, Duration.ofSeconds(2));
            assertEquals(1, maxConcurrentReads.get());
        } finally {
            release.countDown();
            monitor.close();
        }
    }

    @Test
    void boundedDedupeRetainsOnlyTheLatestWindowAcrossHistoryRollAndReactivation() throws Exception {
        Queue<SparkProfileReadResult> results = new ConcurrentLinkedQueue<>(List.of(
                complete(20),
                complete(21),
                complete(22),
                complete(20),
                complete(22)
        ));
        List<String> logs = new ArrayList<>();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 2, 5),
                (plugin, maxEntries) -> results.remove(),
                logs
        );
        try {
            monitor.activateNow();
            awaitLatestWindow(monitor, 20, Duration.ofSeconds(2));
            monitor.pollNow();
            awaitLatestWindow(monitor, 21, Duration.ofSeconds(2));
            monitor.pollNow();
            awaitLatestWindow(monitor, 22, Duration.ofSeconds(2));
            monitor.suspend();
            monitor.activateNow();
            awaitLatestWindow(monitor, 20, Duration.ofSeconds(2));

            assertEquals(List.of(22, 20), monitor.history().stream()
                    .map(SparkProfileSnapshot::windowKey)
                    .toList());
            assertEquals(2, logs.stream().filter(message -> message.contains("window=20")).count());
            assertEquals(1, logs.stream().filter(message -> message.contains("window=22")).count());
        } finally {
            monitor.close();
        }
    }

    @Test
    void reportsNoActionableDataAsDistinctDiagnosticsState() throws Exception {
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> SparkProfileReadResult.noActionableData(
                        "1.10.172",
                        "completed window has no WorldThread samples"
                ),
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            awaitState(monitor, SparkProfilerDiagnostics.State.NO_ACTIONABLE_DATA, Duration.ofSeconds(2));

            assertFalse(monitor.diagnostics().circuitOpen());
            assertTrue(monitor.history().isEmpty());
        } finally {
            monitor.close();
        }
    }

    @Test
    void timeoutOpensCircuitAndPreventsLaterCaptures() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 1),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return complete(9);
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            awaitState(monitor, SparkProfilerDiagnostics.State.TIMED_OUT, Duration.ofSeconds(2));

            monitor.pollNow();
            monitor.activate();

            assertTrue(monitor.diagnostics().circuitOpen());
            assertEquals(1, reads.get());
        } finally {
            release.countDown();
            monitor.close();
        }
    }

    @Test
    void unsupportedSparkOpensCircuitAndPreventsLaterCaptures() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return SparkProfileReadResult.of(
                            SparkProfileReadResult.Status.UNSUPPORTED_ARTIFACT,
                            "1.10.172",
                            "artifact is not approved"
                    );
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            awaitState(monitor, SparkProfilerDiagnostics.State.UNSUPPORTED, Duration.ofSeconds(2));
            monitor.pollNow();

            assertTrue(monitor.diagnostics().circuitOpen());
            assertEquals(1, reads.get());
        } finally {
            monitor.close();
        }
    }

    @Test
    void incompatibleSparkOpensCircuitAndPreventsLaterCaptures() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return SparkProfileReadResult.of(
                            SparkProfileReadResult.Status.INCOMPATIBLE,
                            "1.10.172",
                            "changed reflected member"
                    );
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            awaitState(monitor, SparkProfilerDiagnostics.State.INCOMPATIBLE, Duration.ofSeconds(2));
            monitor.pollNow();

            assertTrue(monitor.diagnostics().circuitOpen());
            assertEquals(1, reads.get());
        } finally {
            monitor.close();
        }
    }

    @Test
    void excessiveProfileComplexityOpensCircuitAndPreventsLaterCaptures() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return SparkProfileReadResult.of(
                            SparkProfileReadResult.Status.TOO_COMPLEX,
                            "1.10.172",
                            "profile exceeded the bounded frame limit"
                    );
                },
                new ArrayList<>()
        );
        try {
            monitor.activateNow();
            awaitState(monitor, SparkProfilerDiagnostics.State.TOO_COMPLEX, Duration.ofSeconds(2));
            monitor.pollNow();

            assertTrue(monitor.diagnostics().circuitOpen());
            assertEquals(1, reads.get());
        } finally {
            monitor.close();
        }
    }

    @Test
    void closeIsTerminalAndLifecycleCallsAreIdempotent() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerMonitor monitor = newMonitor(
                settings(10, 5, 5),
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return complete(4);
                },
                new ArrayList<>()
        );
        monitor.activate();
        monitor.activate();
        monitor.suspend();
        monitor.suspend();
        monitor.close();
        monitor.close();
        monitor.activate();
        monitor.pollNow();

        assertEquals(SparkProfilerDiagnostics.State.SHUTDOWN, monitor.diagnostics().state());
        assertEquals(0, reads.get());
    }

    private static SparkProfilerMonitor newMonitor(
            TelemetryRuntimeSettings.SparkProfilerSettings settings,
            SparkProfileReader reader,
            List<String> logs) {
        return new SparkProfilerMonitor(
                settings,
                Object::new,
                () -> true,
                reader,
                () -> Long.MAX_VALUE,
                null,
                (level, message) -> logs.add(message)
        );
    }

    private static TelemetryRuntimeSettings.SparkProfilerSettings settings(
            int intervalSeconds,
            int maxHistorySnapshots,
            int timeoutSeconds) {
        return new TelemetryRuntimeSettings.SparkProfilerSettings(
                true,
                0,
                intervalSeconds,
                timeoutSeconds,
                5,
                maxHistorySnapshots
        );
    }

    private static SparkProfileReadResult complete(int windowKey) {
        return SparkProfileReadResult.complete(new SparkProfileSnapshot(
                "1.10.172",
                windowKey,
                3,
                1,
                8,
                2,
                20.0d,
                List.of(new SparkProfileSnapshot.HotPath(
                        "example-mod",
                        "Example.tick()",
                        20.0d,
                        100.0d
                ))
        ));
    }

    private static void awaitHistorySize(SparkProfilerMonitor monitor,
                                         int expected,
                                         Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (monitor.history().size() >= expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, monitor.history().size());
    }

    private static void awaitReads(AtomicInteger reads,
                                   int expected,
                                   Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (reads.get() >= expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, reads.get());
    }

    private static void awaitState(SparkProfilerMonitor monitor,
                                   SparkProfilerDiagnostics.State expected,
                                   Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (monitor.diagnostics().state() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, monitor.diagnostics().state());
    }

    private static void awaitLatestWindow(SparkProfilerMonitor monitor,
                                          int expected,
                                          Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<SparkProfileSnapshot> snapshots = monitor.history();
            if (!snapshots.isEmpty() && snapshots.get(snapshots.size() - 1).windowKey() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        List<SparkProfileSnapshot> snapshots = monitor.history();
        assertEquals(expected, snapshots.get(snapshots.size() - 1).windowKey());
    }

    private static void await(CountDownLatch latch) {
        while (latch.getCount() > 0) {
            try {
                latch.await(50L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (latch.getCount() > 0) {
            try {
                latch.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
