package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectProfilerServiceTest {
    private static final AtomicBoolean performanceEnabled = new AtomicBoolean(true);

    @Test
    void publicationAddsHistoryBeforeNotifyingAndKeepsNewestTen() throws Exception {
        TelemetryProjectProfilerService service = service();
        AtomicBoolean historyVisible = new AtomicBoolean();
        CountDownLatch delivered = new CountDownLatch(1);
        TelemetryProfilerView view = service.view("mod-a");
        TelemetryProfilerSubscription subscription = view.subscribe(snapshot -> {
            historyVisible.set(view.history().contains(snapshot));
            delivered.countDown();
        });
        try {
            for (int window = 1; window <= 11; window++) {
                service.publish(result(window));
            }
            assertTrue(delivered.await(1, SECONDS));
            assertTrue(historyVisible.get());
            assertEquals(IntStream.rangeClosed(2, 11).boxed().toList(),
                    view.history().stream().map(TelemetryProfilerSnapshot::windowKey).toList());
        } finally {
            subscription.close();
            service.close();
        }
    }

    @Test
    void ineligibleReadMethodsReturnEmptyBeforeInvalidate() {
        TelemetryProjectProfilerService service = service();
        TelemetryProfilerView view = service.view("mod-a");
        try {
            service.publish(result(1));
            assertTrue(view.latest().isPresent());
            assertFalse(view.history().isEmpty());

            performanceEnabled.set(false);

            assertTrue(view.latest().isEmpty());
            assertTrue(view.history().isEmpty());
            assertEquals(TelemetryProfilerStatus.State.DISABLED, view.status().state());
        } finally {
            service.close();
        }
    }

    @Test
    void blockedListenerCoalescesLatestWithoutStarvingAnotherListener() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger healthyLatest = new AtomicInteger();
        TelemetryProjectProfilerService service = service();
        TelemetryProfilerSubscription slow = service.view("mod-a").subscribe(snapshot -> {
            blocked.countDown();
            awaitIgnoringInterrupt(release);
        });
        TelemetryProfilerSubscription healthy = service.view("mod-a").subscribe(
                snapshot -> healthyLatest.set(snapshot.windowKey()));
        try {
            service.publish(result(1));
            assertTrue(blocked.await(1, SECONDS));
            service.publish(result(2));
            service.publish(result(3));
            awaitValue(healthyLatest, 3);
        } finally {
            release.countDown();
            slow.close();
            healthy.close();
            service.close();
        }
    }

    @Test
    void mailboxKeepsNewerDeliveryAcrossInvalidationAndPausedOlderOffer() throws Exception {
        AtomicBoolean armOfferHook = new AtomicBoolean();
        AtomicBoolean pauseOlderOffer = new AtomicBoolean();
        CountDownLatch olderOfferPaused = new CountDownLatch(1);
        CountDownLatch releaseOlderOffer = new CountDownLatch(1);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch newerDelivered = new CountDownLatch(1);
        List<Integer> delivered = new CopyOnWriteArrayList<>();
        TelemetryProjectProfilerService service = serviceWithDeliveryOfferHook(() -> {
            if (armOfferHook.get() && pauseOlderOffer.compareAndSet(false, true)) {
                olderOfferPaused.countDown();
                awaitIgnoringInterrupt(releaseOlderOffer);
            }
        });
        TelemetryProfilerSubscription subscription = service.view("mod-a").subscribe(snapshot -> {
            delivered.add(snapshot.windowKey());
            if (snapshot.windowKey() == 0) {
                firstEntered.countDown();
                awaitIgnoringInterrupt(releaseFirst);
            }
            if (snapshot.windowKey() == 2) {
                newerDelivered.countDown();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.publish(result(0));
            assertTrue(firstEntered.await(1, SECONDS));
            armOfferHook.set(true);

            Future<?> olderPublish = executor.submit(() -> service.publish(result(1)));
            assertTrue(olderOfferPaused.await(1, SECONDS));

            performanceEnabled.set(false);
            service.invalidate("mod-a");
            performanceEnabled.set(true);
            service.publish(result(2));

            releaseOlderOffer.countDown();
            olderPublish.get(1, SECONDS);
            releaseFirst.countDown();

            assertTrue(newerDelivered.await(1, SECONDS));
            assertEquals(List.of(0, 2), delivered);
        } finally {
            releaseOlderOffer.countDown();
            releaseFirst.countDown();
            subscription.close();
            service.close();
            executor.shutdownNow();
        }
    }

    @Test
    void closedMailboxRejectsPausedOlderOffer() throws Exception {
        AtomicBoolean armOfferHook = new AtomicBoolean();
        AtomicBoolean pauseOffer = new AtomicBoolean();
        CountDownLatch offerPaused = new CountDownLatch(1);
        CountDownLatch releaseOffer = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        TelemetryProjectProfilerService service = serviceWithDeliveryOfferHook(() -> {
            if (armOfferHook.get() && pauseOffer.compareAndSet(false, true)) {
                offerPaused.countDown();
                awaitIgnoringInterrupt(releaseOffer);
            }
        });
        TelemetryProfilerSubscription subscription = service.view("mod-a")
                .subscribe(snapshot -> delivered.incrementAndGet());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            armOfferHook.set(true);
            Future<?> publish = executor.submit(() -> service.publish(result(1)));
            assertTrue(offerPaused.await(1, SECONDS));
            subscription.close();
            releaseOffer.countDown();
            publish.get(1, SECONDS);
            service.publish(result(2));
            Thread.sleep(100L);
            assertEquals(0, delivered.get());
        } finally {
            releaseOffer.countDown();
            subscription.close();
            service.close();
            executor.shutdownNow();
        }
    }

    @Test
    void closedBlockedWorkersHoldProjectPermitsUntilTheyExit() throws Exception {
        TelemetryProjectProfilerService service = service();
        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        List<TelemetryProfilerSubscription> blocked = IntStream.range(0, 4)
                .mapToObj(ignored -> service.view("mod-a").subscribe(snapshot -> {
                    entered.countDown();
                    awaitIgnoringInterrupt(release);
                })).toList();
        AtomicInteger rejectedDelivery = new AtomicInteger();
        try {
            service.publish(result(1));
            assertTrue(entered.await(1, SECONDS));
            blocked.forEach(TelemetryProfilerSubscription::close);
            TelemetryProfilerSubscription rejected = service.view("mod-a")
                    .subscribe(snapshot -> rejectedDelivery.incrementAndGet());
            service.publish(result(2));
            assertEquals(0, rejectedDelivery.get());
            rejected.close();
        } finally {
            release.countDown();
            blocked.forEach(TelemetryProfilerSubscription::close);
            service.close();
        }
    }

    @Test
    void retainedHistoryNeverExceedsFiveHundredPathEntries() {
        List<String> projectIds = projectIds(101);
        TelemetryProjectProfilerService service = serviceWithProjects(projectIds);
        try {
            service.publish(resultWithFivePathsPerProject(1, projectIds));
            int retained = projectIds.stream()
                    .mapToInt(id -> service.view(id).history().stream()
                            .mapToInt(snapshot -> snapshot.paths().size()).sum())
                    .sum();
            assertTrue(retained <= 500);
            assertTrue(service.view(projectIds.getLast()).status().omittedPathCount() > 0);
        } finally {
            service.close();
        }
    }

    @Test
    void projectNoDataStatusOverridesGlobalCompleteDiagnostics() {
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(() -> SparkProfilerDiagnostics.simple(
                SparkProfilerDiagnostics.State.COMPLETE,
                "spark-1",
                "Global monitor completed a window."
        ));
        try {
            service.publish(SparkProfileReadResult.noActionableData(
                    "spark-1",
                    "No actionable project data."
            ));
            assertEquals(TelemetryProfilerStatus.State.NO_DATA,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    @Test
    void projectAnalysisFailureStatusOverridesGlobalCompleteDiagnostics() {
        TelemetryProjectProfilerService service = serviceWithBeforeInsertHook(analysis -> {
            throw new IllegalStateException("analysis failed");
        });
        service.attachMonitorDiagnostics(() -> SparkProfilerDiagnostics.simple(
                SparkProfilerDiagnostics.State.COMPLETE,
                "spark-1",
                "Global monitor completed a window."
        ));
        try {
            service.publish(result(1));
            assertEquals(TelemetryProfilerStatus.State.FAILED,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    @Test
    void consentRevocationPreventsInFlightAnalysisFromReinsertingEvidence() throws Exception {
        CountDownLatch analyzed = new CountDownLatch(1);
        CountDownLatch allowInsert = new CountDownLatch(1);
        TelemetryProjectProfilerService service = serviceWithBeforeInsertHook(analysis -> {
            analyzed.countDown();
            await(allowInsert);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> publish = executor.submit(() -> service.publish(result(1)));
            assertTrue(analyzed.await(1, SECONDS));
            performanceEnabled.set(false);
            service.invalidate("mod-a");
            allowInsert.countDown();
            publish.get(1, SECONDS);
            assertTrue(service.view("mod-a").history().isEmpty());
            assertEquals(TelemetryProfilerStatus.State.DISABLED,
                    service.view("mod-a").status().state());
        } finally {
            allowInsert.countDown();
            service.close();
            executor.shutdownNow();
        }
    }

    @Test
    void consentRevocationDrainsQueuedListenerDelivery() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> delivered = new CopyOnWriteArrayList<>();
        TelemetryProjectProfilerService service = service();
        TelemetryProfilerSubscription subscription = service.view("mod-a").subscribe(snapshot -> {
            delivered.add(snapshot.windowKey());
            if (snapshot.windowKey() == 1) {
                firstEntered.countDown();
                awaitIgnoringInterrupt(releaseFirst);
            }
        });
        try {
            service.publish(result(1));
            assertTrue(firstEntered.await(1, SECONDS));
            service.publish(result(2));
            performanceEnabled.set(false);
            service.invalidate("mod-a");
            releaseFirst.countDown();
            Thread.sleep(100L);
            assertEquals(List.of(1), delivered);
        } finally {
            releaseFirst.countDown();
            subscription.close();
            service.close();
        }
    }

    @Test
    void projectRetirementClearsHistoryClosesSubscriptionsAndDisablesView() throws Exception {
        AtomicReference<List<TelemetryProjectRegistration>> projects =
                new AtomicReference<>(List.of(project("mod-a", "example.a", "ModA")));
        TelemetryProjectProfilerService service = service(projects::get);
        TelemetryProfilerView view = service.view("mod-a");
        AtomicInteger deliveries = new AtomicInteger();
        TelemetryProfilerSubscription subscription = view.subscribe(
                snapshot -> deliveries.incrementAndGet());
        service.publish(result(1));
        awaitValue(deliveries, 1);
        assertFalse(view.history().isEmpty());

        projects.set(List.of());
        service.refreshProjects();

        assertEquals(TelemetryProfilerStatus.State.UNAVAILABLE, view.status().state());
        assertTrue(view.history().isEmpty());
        projects.set(List.of(project("mod-a", "example.a", "ModA")));
        service.refreshProjects();
        service.publish(result(2));
        Thread.sleep(100L);
        assertEquals(1, deliveries.get());
        subscription.close();
        service.close();
    }

    private static TelemetryProjectProfilerService service() {
        return service(() -> List.of(project("mod-a", "example.a", "ModA")));
    }

    private static TelemetryProjectProfilerService serviceWithProjects(List<String> projectIds) {
        return service(() -> projectIds.stream()
                .map(id -> project(id, "example." + id, "Example:" + id))
                .toList());
    }

    private static TelemetryProjectProfilerService service(
            Supplier<List<TelemetryProjectRegistration>> projects) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                projects,
                ignored -> performanceEnabled.get(),
                "runtime-1",
                (level, message) -> {
                }
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProjectProfilerService serviceWithBeforeInsertHook(
            Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsert) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(project("mod-a", "example.a", "ModA")),
                ignored -> performanceEnabled.get(),
                "runtime-1",
                (level, message) -> {
                },
                beforeInsert
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProjectProfilerService serviceWithDeliveryOfferHook(
            Runnable beforeDeliveryOffer) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(project("mod-a", "example.a", "ModA")),
                ignored -> performanceEnabled.get(),
                "runtime-1",
                (level, message) -> {
                },
                ignored -> {
                },
                beforeDeliveryOffer
        );
        service.activateProvider();
        return service;
    }

    private static SparkProfileReadResult result(int windowKey) {
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1",
                windowKey,
                1,
                1,
                1,
                1,
                1.0d,
                List.of(new SparkProfileWindow.ThreadFrames(
                        "WorldThread - default",
                        List.of(new SparkProfileWindow.Frame(
                                -1, "ModA", "example.a.Tick", "tick", "()V", 1.0d
                        ))
                ))
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1", windowKey, 1, 1, 1.0d, List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static SparkProfileReadResult resultWithFivePathsPerProject(
            int windowKey, List<String> projectIds) {
        ArrayList<SparkProfileWindow.Frame> frames = new ArrayList<>(projectIds.size() * 5);
        for (String projectId : projectIds) {
            for (int path = 0; path < 5; path++) {
                frames.add(new SparkProfileWindow.Frame(
                        -1,
                        "Example:" + projectId,
                        "example." + projectId + ".Path" + path,
                        "run" + path,
                        "()V",
                        1.0d
                ));
            }
        }
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1",
                windowKey,
                1,
                1,
                frames.size(),
                frames.size(),
                frames.size(),
                List.of(new SparkProfileWindow.ThreadFrames("WorldThread - default", frames))
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1", windowKey, 1, frames.size(), frames.size(), List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static List<String> projectIds(int count) {
        return IntStream.range(0, count).mapToObj(index -> "mod-" + index).toList();
    }

    private static TelemetryProjectRegistration project(String projectId,
                                                         String packagePrefix,
                                                         String pluginIdentifier) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\","
                        + "\"projectVersion\":\"1.0.0\","
                        + "\"displayName\":\"" + projectId + "\","
                        + "\"ownerPluginIdentifiers\":[\"" + pluginIdentifier + "\"],"
                        + "\"packagePrefixes\":[\"" + packagePrefix + "\"],"
                        + "\"telemetry\":{\"performance\":{\"supported\":true,\"defaultEnabled\":true}}}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, "1.0.0", null);
    }

    private static void awaitValue(AtomicInteger value, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (value.get() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(expected, value.get());
    }

    private static void await(CountDownLatch latch) {
        awaitIgnoringInterrupt(latch);
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
