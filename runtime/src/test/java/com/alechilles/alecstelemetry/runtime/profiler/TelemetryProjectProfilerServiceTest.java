package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    void laterGlobalFailureOverridesEarlierProjectCompleteStatus() {
        AtomicReference<SparkProfilerDiagnostics> diagnostics = new AtomicReference<>(
                SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.COMPLETE,
                        "spark-1",
                        "Global monitor completed a window."
                )
        );
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(diagnostics::get);
        try {
            service.publish(result(1));
            assertEquals(TelemetryProfilerStatus.State.COMPLETE,
                    service.view("mod-a").status().state());

            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.FAILED,
                    "spark-1",
                    "Global monitor failed after the publication."
            ));

            assertEquals(TelemetryProfilerStatus.State.FAILED,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    @Test
    void statusOnlyFailureDoesNotHideLaterGlobalRecovery() {
        AtomicReference<SparkProfilerDiagnostics> diagnostics = new AtomicReference<>(
                SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.FAILED,
                        "spark-1",
                        "Global monitor failed."
                )
        );
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(diagnostics::get);
        try {
            service.publish(SparkProfileReadResult.of(null, "spark-1", "Unknown read status."));
            assertEquals(TelemetryProfilerStatus.State.FAILED,
                    service.view("mod-a").status().state());

            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.COMPLETE,
                    "spark-1",
                    "Global monitor recovered."
            ));

            assertEquals(TelemetryProfilerStatus.State.COMPLETE,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    @Test
    void nullMonitorStateMapsToFailedWithGenericBoundedDetail() {
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(() -> new SparkProfilerDiagnostics(
                null,
                "spark-1",
                "supplied detail must not cross the public status boundary",
                0L,
                0L,
                0,
                0,
                0,
                List.of(),
                true,
                false,
                0L
        ));
        try {
            TelemetryProfilerStatus status = service.view("mod-a").status();
            assertEquals(TelemetryProfilerStatus.State.FAILED, status.state());
            assertEquals("Profiler monitor returned an unknown state.", status.detail());
            assertFalse(status.detail().contains("supplied detail"));
            assertTrue(status.detail().length() <= 240);
        } finally {
            service.close();
        }
    }

    @Test
    void nullMonitorStateOverridesEarlierAuthoritativeProjectDetail() {
        AtomicReference<SparkProfilerDiagnostics> diagnostics = new AtomicReference<>(
                SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.COMPLETE,
                        "spark-1",
                        "Global monitor completed a window."
                )
        );
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(diagnostics::get);
        try {
            service.publish(SparkProfileReadResult.noData("previous internal no-data detail"));
            assertEquals(TelemetryProfilerStatus.State.NO_DATA,
                    service.view("mod-a").status().state());

            diagnostics.set(new SparkProfilerDiagnostics(
                    null,
                    "spark-1",
                    "supplied detail must not cross the public status boundary",
                    0L,
                    0L,
                    0,
                    0,
                    0,
                    List.of(),
                    true,
                    false,
                    0L
            ));

            TelemetryProfilerStatus status = service.view("mod-a").status();
            assertEquals(TelemetryProfilerStatus.State.FAILED, status.state());
            assertEquals("Profiler monitor returned an unknown state.", status.detail());
            assertFalse(status.detail().contains("previous internal no-data detail"));
            assertFalse(status.detail().contains("supplied detail"));
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

    // Regression: a path that remains above both signal thresholds in three
    // analyzed windows must be published as a repeated signal on the third.
    @Test
    void threeQualifyingWindowsPublishRepeatedOnTheThirdPath() {
        TelemetryProjectProfilerService service = service();
        try {
            service.publish(qualifyingResult(1, 40.0d));
            service.publish(qualifyingResult(2, 40.0d));
            service.publish(qualifyingResult(3, 40.0d));

            TelemetryProfilerSnapshot latest = service.view("mod-a").latest().orElseThrow();
            assertEquals(TelemetryProfilerQualification.REPEATED,
                    latest.paths().getFirst().qualification());
            assertEquals(TelemetryProfilerQualification.REPEATED,
                    new ProfilerSignalEngine().signals(service.view("mod-a").history())
                            .getFirst().qualification());
        } finally {
            service.close();
        }
    }

    // Regression: one severe path must be visible as provisional evidence even
    // before it repeats in a later Spark window.
    @Test
    void severeSingleWindowPublishesProvisional() {
        TelemetryProjectProfilerService service = service();
        try {
            service.publish(qualifyingResult(1, 100.0d));

            assertEquals(TelemetryProfilerQualification.PROVISIONAL,
                    service.view("mod-a").latest().orElseThrow()
                            .paths().getFirst().qualification());
        } finally {
            service.close();
        }
    }

    // Regression: actionable empty windows must advance the rolling history so
    // an old candidate expires from the signal engine's five-window horizon.
    @Test
    void fiveActionableEmptyWindowsExpireAnOldCandidate() {
        TelemetryProjectProfilerService service = service();
        try {
            service.publish(qualifyingResult(1, 40.0d));
            service.publish(qualifyingResult(2, 40.0d));
            service.publish(qualifyingResult(3, 40.0d));
            for (int window = 4; window <= 8; window++) {
                service.publish(emptyResult(window));
            }

            assertTrue(new ProfilerSignalEngine().signals(service.view("mod-a").history()).isEmpty());
        } finally {
            service.close();
        }
    }

    // Regression: a completed window with samples but no owned path still
    // publishes a zero-path snapshot with the window metadata and context.
    @Test
    void actionableEmptyWindowRetainsCompleteSnapshotAndContext() {
        AtomicInteger contextCalls = new AtomicInteger();
        TelemetryProfilerContextProvider provider = contextProvider(
                () -> {
                    contextCalls.incrementAndGet();
                    return 7;
                },
                "hytale-0.5.9",
                () -> 1234L
        );
        TelemetryProjectProfilerService service = service(provider);
        service.attachMonitorDiagnostics(() -> SparkProfilerDiagnostics.simple(
                SparkProfilerDiagnostics.State.COMPLETE,
                "spark-1",
                "Spark completed."
        ));
        try {
            service.publish(emptyResult(37));

            TelemetryProfilerSnapshot snapshot = service.view("mod-a").latest().orElseThrow();
            assertEquals(37, snapshot.windowKey());
            assertEquals("spark-1", snapshot.sparkVersion());
            assertEquals(0, snapshot.paths().size());
            assertEquals(ProfilerSignalEngine.RULE_SET, snapshot.qualificationRuleSet());
            assertEquals("hytale-0.5.9", snapshot.context().hytaleVersion());
            assertEquals(7, snapshot.context().playerCount());
            assertEquals(1, contextCalls.get());
            assertEquals(TelemetryProfilerStatus.State.COMPLETE,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    // Regression: independent context-source failures must not discard valid
    // Phase 1 path evidence or alter the Spark monitor state.
    @Test
    void contextFailurePublishesUnavailableContextAndObservedPath() {
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> {
                    throw new IllegalStateException("players unavailable");
                },
                null,
                new SparkPublicMetricsAdapter(() -> {
                    throw new IllegalStateException("Spark unavailable");
                }),
                new TelemetryProfilerBreadcrumbCounter(),
                ignored -> {
                    throw new IllegalStateException("breadcrumbs unavailable");
                },
                () -> 0L,
                null
        );
        TelemetryProjectProfilerService service = service(provider);
        service.attachMonitorDiagnostics(() -> SparkProfilerDiagnostics.simple(
                SparkProfilerDiagnostics.State.COMPLETE,
                "spark-1",
                "Spark completed."
        ));
        try {
            service.publish(qualifyingResult(1, 40.0d));

            TelemetryProfilerSnapshot snapshot = service.view("mod-a").latest().orElseThrow();
            assertEquals(TelemetryProfilerQualification.OBSERVED,
                    snapshot.paths().getFirst().qualification());
            assertEquals(TelemetryProfilerContext.unavailable(), snapshot.context());
            assertEquals(TelemetryProfilerStatus.State.COMPLETE,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    // Regression: a complete result with no usable WorldThread sample must
    // publish NO_DATA and must not advance rolling evidence history.
    @Test
    void completeWindowWithoutUsableSamplesDoesNotAdvanceHistory() {
        TelemetryProjectProfilerService service = service();
        try {
            service.publish(noUsableResult(9));

            assertTrue(service.view("mod-a").history().isEmpty());
            assertEquals(TelemetryProfilerStatus.State.NO_DATA,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
    }

    // Regression: both global limits must evict oldest immutable snapshots and
    // path occurrences without leaving stale evidence for a repeated signal.
    @Test
    void globalSnapshotAndPathLimitsRetainNewestEvidenceWithoutFalseRepeat() {
        List<String> projectIds = projectIds(51);
        TelemetryProjectProfilerService service = serviceWithProjects(projectIds);
        try {
            for (int window = 1; window <= 11; window++) {
                service.publish(emptyResult(window, projectIds));
            }
            int retainedSnapshots = projectIds.stream()
                    .mapToInt(id -> service.view(id).history().size())
                    .sum();
            assertEquals(500, retainedSnapshots);
            assertEquals(11, service.view(projectIds.getLast()).latest().orElseThrow().windowKey());

            for (int window = 12; window <= 14; window++) {
                service.publish(resultWithFivePathsPerProject(window, projectIds));
            }
            int retainedPaths = projectIds.stream()
                    .flatMap(id -> service.view(id).history().stream())
                    .mapToInt(snapshot -> snapshot.paths().size())
                    .sum();
            assertTrue(retainedPaths <= TelemetryProjectProfilerService.MAX_RETAINED_PATH_ENTRIES);
            assertTrue(new ProfilerSignalEngine().signals(service.view(projectIds.getFirst()).history())
                    .stream()
                    .noneMatch(signal -> signal.qualification() == TelemetryProfilerQualification.REPEATED));
        } finally {
            service.close();
        }
    }

    // Regression: invalidation during the existing pre-insert race hook must
    // discard prepared signal/context work and leave no retained evidence.
    @Test
    void invalidationDuringBeforeInsertRaceDiscardsSignalAndContext() throws Exception {
        CountDownLatch analyzed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger contextCalls = new AtomicInteger();
        TelemetryProfilerContextProvider provider = contextProvider(
                () -> {
                    contextCalls.incrementAndGet();
                    return 3;
                },
                "hytale-0.5.9",
                () -> 1234L
        );
        TelemetryProjectProfilerService service = serviceWithBeforeInsertHook(
                analysis -> {
                    analyzed.countDown();
                    await(release);
                },
                provider
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> publish = executor.submit(() -> service.publish(qualifyingResult(1, 40.0d)));
            assertTrue(analyzed.await(1, SECONDS));
            performanceEnabled.set(false);
            service.invalidate("mod-a");
            release.countDown();
            publish.get(1, SECONDS);

            assertTrue(service.view("mod-a").history().isEmpty());
            assertEquals(0, contextCalls.get());
            assertEquals(TelemetryProfilerStatus.State.DISABLED,
                    service.view("mod-a").status().state());
        } finally {
            release.countDown();
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: a public eligibility transition must clear breadcrumb
    // context before a later false-to-true publication can reuse the project.
    @Test
    void eligibilityRevocationClearsContextBeforeReauthorization() {
        AtomicBoolean eligible = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                new AtomicInteger()
        );
        TelemetryProjectProfilerService service = serviceWithEligibility(
                eligible,
                provider,
                projectWithBreadcrumb()
        );
        try {
            provider.recordBreadcrumb("mod-a", "companion.ai.tick");
            service.publish(qualifyingResult(1, 40.0d));
            assertEquals(Map.of("companion.ai.tick", 1),
                    service.view("mod-a").latest().orElseThrow()
                            .context().breadcrumbCategoryCounts());

            eligible.set(false);
            assertEquals(TelemetryProfilerStatus.State.DISABLED,
                    service.view("mod-a").status().state());
            assertTrue(service.view("mod-a").history().isEmpty());

            eligible.set(true);
            service.publish(qualifyingResult(2, 40.0d));
            assertEquals(Map.of(),
                    service.view("mod-a").latest().orElseThrow()
                            .context().breadcrumbCategoryCounts());
        } finally {
            service.close();
        }
    }

    // Regression: an explicit invalidation must fail closed while its
    // outside-lock context clear is blocked, even if consent returns first.
    @Test
    void explicitInvalidationBlocksCaptureUntilContextClearCompletes() throws Exception {
        AtomicBoolean eligible = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        AtomicInteger contextReads = new AtomicInteger();
        AtomicBoolean armAnalysis = new AtomicBoolean();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                contextReads
        );
        CountDownLatch ineligibleObserved = new CountDownLatch(1);
        CountDownLatch analyzed = new CountDownLatch(1);
        TelemetryProjectProfilerService service = serviceWithEligibilityAndHook(
                eligible,
                provider,
                projectWithBreadcrumb(),
                ignored -> {
                    if (armAnalysis.get()) {
                        analyzed.countDown();
                    }
                },
                ineligibleObserved
        );
        provider.recordBreadcrumb("mod-a", "companion.ai.tick");
        service.publish(qualifyingResult(1, 40.0d));
        contextReads.set(0);
        armAnalysis.set(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> invalidation = null;
        Future<?> publication = null;
        try {
            synchronized (breadcrumbs) {
                eligible.set(false);
                invalidation = executor.submit(() -> service.invalidate("mod-a"));
                assertTrue(ineligibleObserved.await(1, SECONDS));

                eligible.set(true);
                publication = executor.submit(() -> service.publish(qualifyingResult(2, 40.0d)));

                assertFalse(analyzed.await(150, TimeUnit.MILLISECONDS));
                assertEquals(0, contextReads.get());
                assertFalse(invalidation.isDone());
            }

            invalidation.get(1, SECONDS);
            publication.get(1, SECONDS);
            assertEquals(Map.of(),
                    service.view("mod-a").latest().orElseThrow()
                            .context().breadcrumbCategoryCounts());
        } finally {
            if (invalidation != null) {
                invalidation.cancel(true);
            }
            if (publication != null) {
                publication.cancel(true);
            }
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: a registration refresh must fail closed before its outside-
    // lock context refresh and clear complete.
    @Test
    void registrationRefreshBlocksCaptureUntilContextClearCompletes() throws Exception {
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicBoolean armEligibility = new AtomicBoolean();
        AtomicBoolean armAnalysis = new AtomicBoolean();
        AtomicReference<List<TelemetryProjectRegistration>> registrations =
                new AtomicReference<>(List.of(projectWithBreadcrumb("1.0.0")));
        CountDownLatch eligibilityObserved = new CountDownLatch(1);
        CountDownLatch analyzed = new CountDownLatch(1);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        AtomicInteger contextReads = new AtomicInteger();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                contextReads
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                registrations::get,
                ignored -> {
                    if (armEligibility.get()) {
                        eligibilityObserved.countDown();
                    }
                    return eligible.get();
                },
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider,
                ignored -> {
                    if (armAnalysis.get()) {
                        analyzed.countDown();
                    }
                },
                () -> {
                }
        );
        service.activateProvider();
        provider.recordBreadcrumb("mod-a", "companion.ai.tick");
        service.publish(qualifyingResult(1, 40.0d));
        contextReads.set(0);
        armEligibility.set(true);
        armAnalysis.set(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> refresh = null;
        Future<?> publication = null;
        try {
            synchronized (breadcrumbs) {
                registrations.set(List.of(projectWithBreadcrumb("2.0.0")));
                refresh = executor.submit(service::refreshProjects);
                assertTrue(eligibilityObserved.await(1, SECONDS));

                publication = executor.submit(() -> service.publish(qualifyingResult(2, 40.0d)));

                assertFalse(analyzed.await(150, TimeUnit.MILLISECONDS));
                assertEquals(0, contextReads.get());
                assertFalse(refresh.isDone());
            }

            refresh.get(1, SECONDS);
            publication.get(1, SECONDS);
            TelemetryProfilerSnapshot latest = service.view("mod-a").latest().orElseThrow();
            assertEquals("2.0.0", latest.projectVersion());
            assertEquals(Map.of(), latest.context().breadcrumbCategoryCounts());
        } finally {
            if (refresh != null) {
                refresh.cancel(true);
            }
            if (publication != null) {
                publication.cancel(true);
            }
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: a failed context clear must leave the project fail-closed,
    // then allow publication only after a later confirmed clear succeeds.
    @Test
    void failedContextClearKeepsCaptureFailClosedUntilRetrySucceeds() throws Exception {
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicBoolean failClear = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        AtomicInteger contextReads = new AtomicInteger();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                contextReads,
                projectId -> {
                    if (failClear.get()) {
                        throw new IllegalStateException("clear failed");
                    }
                    breadcrumbs.clear(projectId);
                }
        );
        TelemetryProjectProfilerService service = serviceWithEligibility(
                eligible,
                provider,
                projectWithBreadcrumb()
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            provider.recordBreadcrumb("mod-a", "companion.ai.tick");
            service.publish(qualifyingResult(1, 40.0d));
            contextReads.set(0);

            service.invalidate("mod-a");
            Future<?> failedPublication = executor.submit(
                    () -> service.publish(qualifyingResult(2, 40.0d))
            );
            failedPublication.get(1, SECONDS);
            assertTrue(service.view("mod-a").history().isEmpty());
            assertEquals(0, contextReads.get());

            failClear.set(false);
            service.publish(qualifyingResult(3, 40.0d));
            assertEquals(List.of(3), service.view("mod-a").history().stream()
                    .map(TelemetryProfilerSnapshot::windowKey)
                    .toList());
            assertEquals(Map.of(), service.view("mod-a").latest().orElseThrow()
                    .context().breadcrumbCategoryCounts());
        } finally {
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: concurrent drain triggers must not repeat a clear after the
    // first request is acknowledged and a new breadcrumb count is recorded.
    @Test
    void concurrentDrainTriggersDoNotRepeatAcknowledgedClear() throws Exception {
        AtomicBoolean eligible = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        CountDownLatch firstCleared = new CountDownLatch(1);
        CountDownLatch allowFirstReturn = new CountDownLatch(1);
        CountDownLatch drainTriggeredTwice = new CountDownLatch(2);
        CountDownLatch allowSecondClear = new CountDownLatch(1);
        AtomicInteger clearCalls = new AtomicInteger();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                new AtomicInteger(),
                projectId -> {
                    int call = clearCalls.incrementAndGet();
                    if (call == 1) {
                        breadcrumbs.clear(projectId);
                        firstCleared.countDown();
                        await(allowFirstReturn);
                    } else {
                        await(allowSecondClear);
                        breadcrumbs.clear(projectId);
                    }
                }
        );
        TelemetryProjectProfilerService service = serviceWithContextDrainHook(
                eligible,
                provider,
                projectWithBreadcrumb(),
                drainTriggeredTwice::countDown
        );
        provider.recordBreadcrumb("mod-a", "companion.ai.tick");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> invalidation = null;
        Future<?> publication = null;
        try {
            eligible.set(false);
            invalidation = executor.submit(() -> service.invalidate("mod-a"));
            assertTrue(firstCleared.await(1, SECONDS));

            publication = executor.submit(() -> service.publish(qualifyingResult(2, 40.0d)));
            assertTrue(drainTriggeredTwice.await(1, SECONDS));

            eligible.set(true);
            provider.recordBreadcrumb("mod-a", "companion.ai.tick");
            allowFirstReturn.countDown();
            invalidation.get(1, SECONDS);
            allowSecondClear.countDown();
            publication.get(1, SECONDS);

            assertEquals(1, clearCalls.get());
            assertEquals(Map.of("companion.ai.tick", 1),
                    service.view("mod-a").latest().orElseThrow()
                            .context().breadcrumbCategoryCounts());
        } finally {
            allowFirstReturn.countDown();
            allowSecondClear.countDown();
            if (invalidation != null) {
                invalidation.cancel(true);
            }
            if (publication != null) {
                publication.cancel(true);
            }
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: an older concurrent registration refresh must not apply its
    // provider allowlist after a newer refresh has opened.
    @Test
    void concurrentRegistrationRefreshesKeepNewestProviderAllowlist() throws Exception {
        AtomicReference<List<TelemetryProjectRegistration>> registrations =
                new AtomicReference<>(List.of(projectWithBreadcrumb("1.0.0", "version-1")));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        CountDownLatch firstRefreshStarted = new CountDownLatch(1);
        CountDownLatch allowFirstRefresh = new CountDownLatch(1);
        CountDownLatch secondRefreshCompleted = new CountDownLatch(1);
        AtomicInteger refreshCalls = new AtomicInteger();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null,
                next -> {
                    int call = refreshCalls.incrementAndGet();
                    if (call == 2) {
                        firstRefreshStarted.countDown();
                        await(allowFirstRefresh);
                    }
                    if (call == 3) {
                        secondRefreshCompleted.countDown();
                    }
                    breadcrumbs.refreshProjects(next);
                },
                breadcrumbs::clear,
                breadcrumbs::clearAll
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                registrations::get,
                ignored -> true,
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider
        );
        service.activateProvider();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstRefresh = null;
        Future<?> secondRefresh = null;
        try {
            registrations.set(List.of(projectWithBreadcrumb("2.0.0", "version-2")));
            firstRefresh = executor.submit(service::refreshProjects);
            assertTrue(firstRefreshStarted.await(1, SECONDS));

            registrations.set(List.of(projectWithBreadcrumb("3.0.0", "version-3")));
            secondRefresh = executor.submit(service::refreshProjects);
            assertFalse(secondRefreshCompleted.await(150, TimeUnit.MILLISECONDS));

            allowFirstRefresh.countDown();
            firstRefresh.get(1, SECONDS);
            secondRefresh.get(1, SECONDS);

            provider.recordBreadcrumb("mod-a", "version-3");
            provider.recordBreadcrumb("mod-a", "version-2");
            assertEquals(Map.of("version-3", 1), provider.snapshot("mod-a")
                    .breadcrumbCategoryCounts());
        } finally {
            allowFirstRefresh.countDown();
            if (firstRefresh != null) {
                firstRefresh.cancel(true);
            }
            if (secondRefresh != null) {
                secondRefresh.cancel(true);
            }
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: a failed provider refresh must keep changed and new
    // registrations closed until the latest complete set is confirmed.
    @Test
    void failedProviderRefreshKeepsChangedAndNewProjectsClosedUntilRetry() {
        AtomicBoolean failRefresh = new AtomicBoolean();
        AtomicReference<List<TelemetryProjectRegistration>> registrations =
                new AtomicReference<>(List.of(projectWithBreadcrumb("1.0.0", "version-1")));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null,
                next -> {
                    if (failRefresh.get()) {
                        throw new IllegalStateException("refresh failed");
                    }
                    breadcrumbs.refreshProjects(next);
                },
                breadcrumbs::clear,
                breadcrumbs::clearAll
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                registrations::get,
                ignored -> true,
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider
        );
        service.activateProvider();
        try {
            provider.recordBreadcrumb("mod-a", "version-1");
            service.publish(qualifyingResult(1, 40.0d));

            failRefresh.set(true);
            registrations.set(List.of(
                    projectWithBreadcrumb("2.0.0", "version-2"),
                    projectWithBreadcrumb("mod-b", "1.0.0", "new")
            ));
            service.refreshProjects();
            service.publish(qualifyingResult(2, 40.0d));

            assertTrue(service.view("mod-a").history().isEmpty());
            assertTrue(service.view("mod-b").history().isEmpty());

            failRefresh.set(false);
            service.publish(qualifyingResult(3, 40.0d));
            provider.recordBreadcrumb("mod-a", "version-2");
            provider.recordBreadcrumb("mod-b", "new");
            service.publish(qualifyingResult(4, 40.0d));

            assertEquals(Map.of("version-2", 1), service.view("mod-a").latest()
                    .orElseThrow().context().breadcrumbCategoryCounts());
            assertEquals(Map.of("new", 1), service.view("mod-b").latest()
                    .orElseThrow().context().breadcrumbCategoryCounts());
        } finally {
            service.close();
        }
    }

    // Regression: a failed refresh retry must never apply an older captured
    // registration set after a newer refresh has succeeded.
    @Test
    void failedRefreshRetryUsesNewestRegistrationSet() {
        AtomicReference<List<TelemetryProjectRegistration>> registrations =
                new AtomicReference<>(List.of(projectWithBreadcrumb("1.0.0", "version-1")));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        AtomicInteger refreshCalls = new AtomicInteger();
        List<List<TelemetryProjectRegistration>> refreshSets = new ArrayList<>();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null,
                next -> {
                    refreshSets.add(List.copyOf(next));
                    if (refreshCalls.incrementAndGet() == 2) {
                        throw new IllegalStateException("first changed refresh failed");
                    }
                    breadcrumbs.refreshProjects(next);
                },
                breadcrumbs::clear,
                breadcrumbs::clearAll
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                registrations::get,
                ignored -> true,
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider
        );
        service.activateProvider();
        try {
            registrations.set(List.of(projectWithBreadcrumb("2.0.0", "version-2")));
            service.refreshProjects();
            registrations.set(List.of(projectWithBreadcrumb("3.0.0", "version-3")));
            service.refreshProjects();
            service.publish(qualifyingResult(1, 40.0d));

            assertEquals(3, refreshCalls.get());
            assertEquals(List.of("3.0.0"), refreshSets.get(2).stream()
                    .map(registration -> registration.descriptor().projectVersion())
                    .toList());
            provider.recordBreadcrumb("mod-a", "version-3");
            provider.recordBreadcrumb("mod-a", "version-2");
            assertEquals(Map.of("version-3", 1), provider.snapshot("mod-a")
                    .breadcrumbCategoryCounts());
        } finally {
            service.close();
        }
    }

    // Regression: a successful clear must reopen its project for the same
    // actionable window even when another project's clear still fails.
    @Test
    void mixedClearResultsPublishProjectsThatReopened() {
        AtomicBoolean eligible = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> eligible.get(),
                () -> 1000L,
                null,
                breadcrumbs::refreshProjects,
                projectId -> {
                    if ("mod-b".equals(projectId)) {
                        throw new IllegalStateException("project B clear failed");
                    }
                    breadcrumbs.clear(projectId);
                    eligible.set(true);
                },
                breadcrumbs::clearAll
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(
                        projectWithBreadcrumb("1.0.0", "companion.ai.tick"),
                        projectWithBreadcrumb("mod-b", "1.0.0", "new")
                ),
                ignored -> eligible.get(),
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider
        );
        service.activateProvider();
        try {
            eligible.set(false);
            service.publish(qualifyingResult(7, 40.0d));

            assertEquals(List.of(7), service.view("mod-a").history().stream()
                    .map(TelemetryProfilerSnapshot::windowKey).toList());
            assertTrue(service.view("mod-b").history().isEmpty());
        } finally {
            service.close();
        }
    }

    // Regression: persistent provider refresh failure must make bounded
    // progress per lifecycle trigger and must not spin inside publication.
    @Test
    void persistentProviderRefreshFailureDoesNotBusyLoop() {
        AtomicBoolean failRefresh = new AtomicBoolean();
        AtomicInteger refreshCalls = new AtomicInteger();
        AtomicReference<List<TelemetryProjectRegistration>> registrations =
                new AtomicReference<>(List.of(projectWithBreadcrumb("1.0.0")));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null,
                next -> {
                    refreshCalls.incrementAndGet();
                    if (failRefresh.get()) {
                        throw new IllegalStateException("persistent refresh failure");
                    }
                    breadcrumbs.refreshProjects(next);
                },
                breadcrumbs::clear,
                breadcrumbs::clearAll
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                registrations::get,
                ignored -> true,
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                provider
        );
        service.activateProvider();
        try {
            failRefresh.set(true);
            registrations.set(List.of(projectWithBreadcrumb("2.0.0")));
            service.refreshProjects();
            int callsBeforePublish = refreshCalls.get();
            service.publish(qualifyingResult(2, 40.0d));

            assertTrue(service.view("mod-a").history().isEmpty());
            assertTrue(refreshCalls.get() <= callsBeforePublish + 2);
        } finally {
            service.close();
        }
    }

    // Regression: a failed clear must not prevent terminal shutdown.
    @Test
    void failedContextClearDoesNotBlockTerminalShutdown() throws Exception {
        AtomicBoolean eligible = new AtomicBoolean(true);
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider provider = breadcrumbContextProvider(
                eligible,
                breadcrumbs,
                new AtomicInteger(),
                ignored -> {
                    throw new IllegalStateException("clear failed");
                }
        );
        TelemetryProjectProfilerService service = serviceWithEligibility(
                eligible,
                provider,
                projectWithBreadcrumb()
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.invalidate("mod-a");
            Future<?> close = executor.submit(service::close);
            close.get(1, SECONDS);
        } finally {
            service.close();
            executor.shutdownNow();
        }
    }

    // Regression: a completed empty project window remains visible while the
    // monitor is transiently waiting, but later failure states remain visible.
    @Test
    void completedEmptyStatusRemainsAuthoritativeOverWaiting() {
        AtomicReference<SparkProfilerDiagnostics> diagnostics = new AtomicReference<>(
                SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.COMPLETE,
                        "spark-1",
                        "Spark completed."
                )
        );
        TelemetryProjectProfilerService service = service();
        service.attachMonitorDiagnostics(diagnostics::get);
        try {
            service.publish(emptyResult(41));
            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.WAITING,
                    "spark-1",
                    "A later capture is waiting."
            ));

            TelemetryProfilerStatus status = service.view("mod-a").status();
            assertEquals(TelemetryProfilerStatus.State.COMPLETE, status.state());
            assertEquals("Latest Spark project profile window completed with no owned paths.",
                    status.detail());

            diagnostics.set(SparkProfilerDiagnostics.simple(
                    SparkProfilerDiagnostics.State.FAILED,
                    "spark-1",
                    "The later monitor failed."
            ));
            assertEquals(TelemetryProfilerStatus.State.FAILED,
                    service.view("mod-a").status().state());
        } finally {
            service.close();
        }
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

    private static TelemetryProjectProfilerService serviceWithBeforeInsertHook(
            Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsert,
            TelemetryProfilerContextProvider contextProvider) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(project("mod-a", "example.a", "ModA")),
                ignored -> performanceEnabled.get(),
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                contextProvider,
                beforeInsert,
                () -> {
                }
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProjectProfilerService service(
            TelemetryProfilerContextProvider contextProvider) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(project("mod-a", "example.a", "ModA")),
                ignored -> performanceEnabled.get(),
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                contextProvider,
                ignored -> {
                },
                () -> {
                }
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProjectProfilerService serviceWithEligibility(
            AtomicBoolean eligible,
            TelemetryProfilerContextProvider contextProvider,
            TelemetryProjectRegistration registration) {
        return serviceWithEligibilityAndHook(
                eligible,
                contextProvider,
                registration,
                ignored -> {
                },
                null
        );
    }

    private static TelemetryProjectProfilerService serviceWithEligibilityAndHook(
            AtomicBoolean eligible,
            TelemetryProfilerContextProvider contextProvider,
            TelemetryProjectRegistration registration,
            Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsert,
            CountDownLatch ineligibleObserved) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(registration),
                ignored -> {
                    boolean current = eligible.get();
                    if (!current && ineligibleObserved != null) {
                        ineligibleObserved.countDown();
                    }
                    return current;
                },
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                contextProvider,
                beforeInsert,
                () -> {
                }
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProjectProfilerService serviceWithContextDrainHook(
            AtomicBoolean eligible,
            TelemetryProfilerContextProvider contextProvider,
            TelemetryProjectRegistration registration,
            Runnable beforeContextDrain) {
        performanceEnabled.set(true);
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> List.of(registration),
                ignored -> eligible.get(),
                "runtime-1",
                (level, message) -> {
                },
                new ProfilerSignalEngine(),
                contextProvider,
                ignored -> {
                },
                () -> {
                },
                beforeContextDrain
        );
        service.activateProvider();
        return service;
    }

    private static TelemetryProfilerContextProvider breadcrumbContextProvider(
            AtomicBoolean eligible,
            TelemetryProfilerBreadcrumbCounter breadcrumbs,
            AtomicInteger contextReads) {
        return new TelemetryProfilerContextProvider(
                () -> {
                    contextReads.incrementAndGet();
                    return 7;
                },
                "hytale-0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> eligible.get(),
                () -> 1000L,
                null
        );
    }

    private static TelemetryProfilerContextProvider breadcrumbContextProvider(
            AtomicBoolean eligible,
            TelemetryProfilerBreadcrumbCounter breadcrumbs,
            AtomicInteger contextReads,
            TelemetryProfilerContextProvider.ClearOperation clearOperation) {
        return new TelemetryProfilerContextProvider(
                () -> {
                    contextReads.incrementAndGet();
                    return 7;
                },
                "hytale-0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> eligible.get(),
                () -> 1000L,
                null,
                breadcrumbs::refreshProjects,
                clearOperation,
                breadcrumbs::clearAll
        );
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

    private static SparkProfileReadResult qualifyingResult(int windowKey,
                                                           double sampledMilliseconds) {
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1",
                windowKey,
                1,
                1,
                1,
                1,
                sampledMilliseconds,
                List.of(new SparkProfileWindow.ThreadFrames(
                        "WorldThread - default",
                        List.of(new SparkProfileWindow.Frame(
                                -1, "ModA", "example.a.Tick", "tick", "()V", sampledMilliseconds
                        ))
                ))
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1", windowKey, 1, 1, sampledMilliseconds, List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static SparkProfileReadResult emptyResult(int windowKey) {
        return emptyResult(windowKey, List.of("mod-a"));
    }

    private static SparkProfileReadResult emptyResult(int windowKey,
                                                      List<String> projectIds) {
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1",
                windowKey,
                1,
                1,
                1,
                1,
                40.0d,
                List.of(new SparkProfileWindow.ThreadFrames(
                        "WorldThread - default",
                        List.of(new SparkProfileWindow.Frame(
                                -1, "hytale", "com.hytale.Unowned", "tick", "()V", 40.0d
                        ))
                ))
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1", windowKey, 1, 1, 40.0d, List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static SparkProfileReadResult noUsableResult(int windowKey) {
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1",
                windowKey,
                1,
                0,
                0,
                0,
                0.0d,
                List.of()
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1", windowKey, 1, 0, 0.0d, List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static TelemetryProfilerContextProvider contextProvider(
            java.util.function.IntSupplier playerCount,
            String hytaleVersion,
            java.util.function.LongSupplier clock) {
        return new TelemetryProfilerContextProvider(
                playerCount,
                hytaleVersion,
                new SparkPublicMetricsAdapter(() -> null),
                new TelemetryProfilerBreadcrumbCounter(),
                ignored -> false,
                clock,
                null
        );
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

    private static TelemetryProjectRegistration projectWithBreadcrumb() {
        return projectWithBreadcrumb("1.0.0");
    }

    private static TelemetryProjectRegistration projectWithBreadcrumb(String projectVersion) {
        return projectWithBreadcrumb(projectVersion, "companion.ai.tick");
    }

    private static TelemetryProjectRegistration projectWithBreadcrumb(
            String projectVersion,
            String category) {
        return projectWithBreadcrumb("mod-a", projectVersion, category);
    }

    private static TelemetryProjectRegistration projectWithBreadcrumb(
            String projectId,
            String projectVersion,
            String category) {
        String pluginIdentifier = "mod-a".equals(projectId) ? "ModA" : "ModB";
        String packagePrefix = "mod-a".equals(projectId) ? "example.a" : "example.b";
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\","
                        + "\"projectVersion\":\"" + projectVersion + "\","
                        + "\"displayName\":\"" + projectId + "\","
                        + "\"ownerPluginIdentifiers\":[\"" + pluginIdentifier + "\"],"
                        + "\"packagePrefixes\":[\"" + packagePrefix + "\"],"
                        + "\"telemetry\":{"
                        + "\"performance\":{\"supported\":true,\"defaultEnabled\":true},"
                        + "\"events\":{\"breadcrumbs\":{"
                        + "\"enabled\":true,"
                        + "\"profilerCorrelationCategories\":[\"" + category + "\"]"
                        + "}}}}",
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
