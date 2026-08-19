package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkProfilerMonitorTest {

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
}
