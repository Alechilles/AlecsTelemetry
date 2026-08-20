package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import me.lucko.spark.api.AverageInfo;
import me.lucko.spark.api.MsptStatistic;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkPlugin;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.TpsStatistic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProfilerContextProviderTest {
    private static final String PROJECT_ID = "example";
    private static final String CATEGORY = "companion.ai.tick";
    private static final String OTHER_CATEGORY = "companion.lease.scan";

    @AfterEach
    void clearProvider() {
        SparkProvider.set(null);
    }

    // Regression: publication reads each source once and emits the normalized version and exact counts.
    @Test
    void publishesAllSourcesWithOnePublicationClockRead() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));
        AtomicLong counterClock = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter(counterClock::get);
        breadcrumbs.refreshProjects(List.of(registration(PROJECT_ID, List.of(CATEGORY, OTHER_CATEGORY))));
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        breadcrumbs.record(PROJECT_ID, OTHER_CATEGORY);
        breadcrumbs.record(PROJECT_ID, "undeclared");

        AtomicInteger publicationClockReads = new AtomicInteger();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 12,
                " 0.5.9 ",
                new SparkPublicMetricsAdapter(() -> new SparkPlugin()),
                breadcrumbs,
                ignored -> true,
                () -> {
                    publicationClockReads.incrementAndGet();
                    return 1234L;
                },
                null
        );

        assertEquals(new TelemetryProfilerContext(
                1234L,
                "0.5.9",
                12,
                29.5d,
                12.25d,
                Map.of(CATEGORY, 2, OTHER_CATEGORY, 1)
        ), provider.snapshot(PROJECT_ID));
        assertEquals(1, publicationClockReads.get());
    }

    // Regression: negative player counts become unknown without suppressing independent sources.
    @Test
    void clampsNegativePlayerCountToUnknown() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        TelemetryProfilerContextProvider provider = provider(() -> -1, breadcrumbs, ignored -> true);

        TelemetryProfilerContext context = provider.snapshot(PROJECT_ID);

        assertEquals(null, context.playerCount());
        assertEquals(29.5d, context.tps());
        assertEquals(Map.of(CATEGORY, 1), context.breadcrumbCategoryCounts());
    }

    // Regression: a failing player source must leave version, Spark metrics, and breadcrumbs present.
    @Test
    void playerFailureIsolatedFromOtherSources() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        TelemetryProfilerContextProvider provider = provider(
                () -> {
                    throw new IllegalStateException("players unavailable");
                },
                breadcrumbs,
                ignored -> true
        );

        TelemetryProfilerContext context = provider.snapshot(PROJECT_ID);

        assertEquals(new TelemetryProfilerContext(
                1000L,
                "0.5.9",
                null,
                29.5d,
                12.25d,
                Map.of(CATEGORY, 1)
        ), context);
    }

    // Regression: a non-fatal diagnostic sink failure must not suppress an otherwise publishable context.
    @Test
    void nonFatalDiagnosticSinkFailureDoesNotSuppressPublication() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> {
                    throw new IllegalStateException("players unavailable");
                },
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> new SparkPlugin()),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                (level, message) -> {
                    throw new AssertionError("diagnostic sink failed");
                }
        );

        assertEquals(new TelemetryProfilerContext(
                1000L,
                "0.5.9",
                null,
                29.5d,
                12.25d,
                Map.of(CATEGORY, 1)
        ), provider.snapshot(PROJECT_ID));
    }

    // Regression: a failing Spark supplier must leave player and breadcrumb sources present.
    @Test
    void sparkFailureIsolatedFromOtherSources() {
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 12,
                " 0.5.9 ",
                new SparkPublicMetricsAdapter(() -> {
                    throw new IllegalStateException("spark unavailable");
                }),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null
        );

        assertEquals(new TelemetryProfilerContext(
                1000L,
                "0.5.9",
                12,
                null,
                null,
                Map.of(CATEGORY, 1)
        ), provider.snapshot(PROJECT_ID));
    }

    // Regression: withdrawing breadcrumb consent clears counts and blocks new records until consent returns.
    @Test
    void consentClearingIsIndependentAndReversible() {
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        AtomicBoolean eligible = new AtomicBoolean(false);
        TelemetryProfilerContextProvider provider = provider(() -> 12, breadcrumbs, ignored -> eligible.get());

        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());

        eligible.set(true);
        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        assertEquals(Map.of(CATEGORY, 1), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());

        eligible.set(false);
        assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());

        eligible.set(true);
        assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        provider.clear(PROJECT_ID);
        assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        assertEquals(Map.of(CATEGORY, 1), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
    }

    // Regression: source failures are rate-limited per source while later minutes remain observable.
    @Test
    void rateLimitsSourceFailureLogsPerMinute() {
        AtomicLong now = new AtomicLong(1000L);
        List<String> logs = new ArrayList<>();
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> {
                    throw new IllegalStateException("players unavailable");
                },
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                registeredCounter(),
                ignored -> true,
                now::get,
                (level, message) -> logs.add(level + ":" + message)
        );

        provider.snapshot(PROJECT_ID);
        provider.snapshot(PROJECT_ID);
        assertEquals(1, logs.size());

        now.set(61_000L);
        provider.snapshot(PROJECT_ID);
        assertEquals(2, logs.size());
    }

    // Regression: clearAll removes every project's counts without removing their allowlists.
    @Test
    void clearAllClearsEveryProject() {
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter(() -> 0L);
        breadcrumbs.refreshProjects(List.of(
                registration(PROJECT_ID, List.of(CATEGORY)),
                registration("other", List.of(OTHER_CATEGORY))
        ));
        TelemetryProfilerContextProvider provider = provider(() -> 1, breadcrumbs, ignored -> true);
        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        provider.recordBreadcrumb("other", OTHER_CATEGORY);

        provider.clearAll();

        assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
        assertEquals(Map.of(), provider.snapshot("other").breadcrumbCategoryCounts());
        provider.recordBreadcrumb(PROJECT_ID, CATEGORY);
        assertEquals(Map.of(CATEGORY, 1), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
    }

    // Regression: a failed counter clear must be observable by the service
    // while the public fail-safe clear boundary remains non-throwing.
    @Test
    void confirmedClearReportsFailureWithoutClaimingCountsWereRemoved() {
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        breadcrumbs.record(PROJECT_ID, CATEGORY);
        AtomicBoolean failClear = new AtomicBoolean(true);
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 1000L,
                null,
                breadcrumbs::refreshProjects,
                ignored -> {
                    if (failClear.get()) {
                        throw new IllegalStateException("clear failed");
                    }
                    breadcrumbs.clear(PROJECT_ID);
                },
                breadcrumbs::clearAll
        );

        provider.clear(PROJECT_ID);
        assertEquals(Map.of(CATEGORY, 1), provider.snapshot(PROJECT_ID)
                .breadcrumbCategoryCounts());
        assertFalse(provider.clearConfirmed(PROJECT_ID));
        assertEquals(Map.of(CATEGORY, 1), provider.snapshot(PROJECT_ID)
                .breadcrumbCategoryCounts());

        failClear.set(false);
        assertTrue(provider.clearConfirmed(PROJECT_ID));
        assertEquals(Map.of(), provider.snapshot(PROJECT_ID)
                .breadcrumbCategoryCounts());
    }

    // Regression: a consent/provider clear cannot be overtaken by a record
    // that already passed the eligibility check.
    @Test
    void projectClearWinsAgainstAnEligibleRecordThatStartedBeforeConsentRevocation() throws Exception {
        assertClearWinsAgainstBlockedRecord(false);
    }

    @Test
    void clearAllWinsAgainstAnEligibleRecordThatStartedBeforeProviderDeactivation() throws Exception {
        assertClearWinsAgainstBlockedRecord(true);
    }

    private static void assertClearWinsAgainstBlockedRecord(boolean clearAll) throws Exception {
        TelemetryProfilerBreadcrumbCounter breadcrumbs = registeredCounter();
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicBoolean blockFirstEligibility = new AtomicBoolean(true);
        CountDownLatch eligibilityEntered = new CountDownLatch(1);
        CountDownLatch releaseEligibility = new CountDownLatch(1);
        TelemetryProfilerContextProvider provider = new TelemetryProfilerContextProvider(
                () -> 1,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> {
                    boolean observed = eligible.get();
                    if (blockFirstEligibility.compareAndSet(true, false)) {
                        eligibilityEntered.countDown();
                        try {
                            if (!releaseEligibility.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("record eligibility was not released");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("record eligibility was interrupted", interrupted);
                        }
                    }
                    return observed;
                },
                () -> 1000L,
                null
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> record = executor.submit(() -> provider.recordBreadcrumb(PROJECT_ID, CATEGORY));
            assertTrue(eligibilityEntered.await(1, TimeUnit.SECONDS));

            eligible.set(false);
            CountDownLatch clearRequested = new CountDownLatch(1);
            Future<?> clear = executor.submit(() -> {
                clearRequested.countDown();
                if (clearAll) {
                    provider.clearAll();
                } else {
                    provider.clear(PROJECT_ID);
                }
            });
            assertTrue(clearRequested.await(1, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> clear.get(1, TimeUnit.SECONDS));

            releaseEligibility.countDown();
            record.get(1, TimeUnit.SECONDS);
            clear.get(1, TimeUnit.SECONDS);

            eligible.set(true);
            assertEquals(Map.of(), provider.snapshot(PROJECT_ID).breadcrumbCategoryCounts());
        } finally {
            releaseEligibility.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static TelemetryProfilerContextProvider provider(
            java.util.function.IntSupplier playerCount,
            TelemetryProfilerBreadcrumbCounter breadcrumbs,
            java.util.function.Predicate<String> eligible) {
        return new TelemetryProfilerContextProvider(
                playerCount,
                " 0.5.9 ",
                new SparkPublicMetricsAdapter(() -> new SparkPlugin()),
                breadcrumbs,
                eligible,
                () -> 1000L,
                null
        );
    }

    private static TelemetryProfilerBreadcrumbCounter registeredCounter() {
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(() -> 0L);
        counter.refreshProjects(List.of(registration(PROJECT_ID, List.of(CATEGORY))));
        return counter;
    }

    private static TelemetryProjectRegistration registration(String projectId, List<String> categories) {
        String categoryJson = categories.stream()
                .map(category -> "\"" + category + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "Example",
                  "events": {
                    "breadcrumbs": {
                      "enabled": true,
                      "profilerCorrelationCategories": [%s]
                    }
                  }
                }
                """.formatted(projectId, categoryJson),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Plugin", "1.0.0", null);
    }
}
