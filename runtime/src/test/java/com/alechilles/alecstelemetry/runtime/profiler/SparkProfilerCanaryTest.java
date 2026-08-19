package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkProfilerCanaryTest {

    @Test
    void compatibilityFacadeDelegatesToRecurringMonitorAndExposesHistory() throws Exception {
        SparkProfilerCanary canary = new SparkProfilerCanary(
                enabledSettings(5),
                Object::new,
                () -> true,
                (plugin, maxEntries) -> SparkProfileReadResult.complete(new SparkProfileSnapshot(
                        "1.10.172",
                        12,
                        2,
                        1,
                        4,
                        1,
                        8.0d,
                        List.of()
                )),
                () -> Long.MAX_VALUE,
                null
        );
        try {
            canary.startNow();
            awaitState(canary, SparkProfilerDiagnostics.State.COMPLETE, Duration.ofSeconds(2));

            assertEquals(1, canary.history().size());
            assertEquals(12, canary.history().get(0).windowKey());
        } finally {
            canary.close();
        }
    }

    @Test
    void aSparkLinkageFailureOpensTheCircuitAndDoesNotEscape() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerCanary canary = new SparkProfilerCanary(
                enabledSettings(5),
                Object::new,
                () -> true,
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    throw new LinkageError("changed Spark internals");
                },
                () -> Long.MAX_VALUE,
                null
        );
        try {
            canary.startNow();
            awaitState(canary, SparkProfilerDiagnostics.State.FAILED, Duration.ofSeconds(2));

            canary.startNow();

            assertEquals(1, reads.get());
            assertTrue(canary.diagnostics().detail().contains("LinkageError"));
        } finally {
            canary.close();
        }
    }

    @Test
    void aHungSparkSnapshotTimesOutWithoutBlockingTheServerThread() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SparkProfilerCanary canary = new SparkProfilerCanary(
                enabledSettings(1),
                Object::new,
                () -> true,
                (plugin, maxEntries) -> {
                    entered.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await(50, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    return SparkProfileReadResult.noData("released");
                },
                () -> Long.MAX_VALUE,
                null
        );
        try {
            canary.startNow();

            assertTrue(entered.await(1, TimeUnit.SECONDS));
            awaitState(canary, SparkProfilerDiagnostics.State.TIMED_OUT, Duration.ofSeconds(2));
            assertTrue(canary.diagnostics().detail().contains("may continue until Spark returns"));
        } finally {
            release.countDown();
            canary.close();
        }
    }

    @Test
    void closeRemainsTerminalWhenStartIsCheckingCoordinatorOwnership() throws Exception {
        CountDownLatch ownerCheckEntered = new CountDownLatch(1);
        CountDownLatch releaseOwnerCheck = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SparkProfilerCanary canary = new SparkProfilerCanary(
                enabledSettings(5),
                Object::new,
                () -> {
                    ownerCheckEntered.countDown();
                    try {
                        releaseOwnerCheck.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                },
                (plugin, maxEntries) -> {
                    reads.incrementAndGet();
                    return SparkProfileReadResult.noData("unexpected read");
                },
                () -> Long.MAX_VALUE,
                null
        );
        Thread starter = Thread.ofPlatform().start(canary::start);
        try {
            assertTrue(ownerCheckEntered.await(1, TimeUnit.SECONDS));

            canary.close();
            releaseOwnerCheck.countDown();
            starter.join(1_000L);

            assertFalse(starter.isAlive());
            assertEquals(SparkProfilerDiagnostics.State.SHUTDOWN, canary.diagnostics().state());
            assertEquals(0, reads.get());
        } finally {
            releaseOwnerCheck.countDown();
            canary.close();
            starter.join(1_000L);
        }
    }

    private static TelemetryRuntimeSettings.SparkProfilerCanarySettings enabledSettings(int timeoutSeconds) {
        return new TelemetryRuntimeSettings.SparkProfilerCanarySettings(true, 60, timeoutSeconds, 5);
    }

    private static void awaitState(SparkProfilerCanary canary,
                                   SparkProfilerDiagnostics.State expected,
                                   Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (canary.diagnostics().state() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, canary.diagnostics().state());
    }
}
