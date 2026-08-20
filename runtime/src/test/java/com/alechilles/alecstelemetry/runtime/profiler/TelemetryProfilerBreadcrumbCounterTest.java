package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryProfilerBreadcrumbCounterTest {
    private static final long MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1L);
    private static final String CATEGORY = "companion.ai.tick";

    // Regression: dynamic, undeclared, and mixed-case categories must not enter the count map.
    @Test
    void countsOnlyExactAllowlistedCategories() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(" companion.ai.tick "))));

        counter.record("example", CATEGORY);
        counter.record("example", " " + CATEGORY + " ");
        counter.record("example", "companion.ai.tick.detail");
        counter.record("example", "COMPANION.AI.TICK");
        counter.record("example", "dynamic-value-1");
        counter.record("unknown", CATEGORY);

        assertEquals(Map.of(CATEGORY, 2), counter.snapshot("example"));
    }

    // Regression: the rolling window must expire old minutes and clear a reused slot.
    @Test
    void rotatesFiveFixedMinuteBucketsAndClearsReusedSlots() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(CATEGORY))));

        counter.record("example", CATEGORY);
        for (int minute = 1; minute <= 4; minute++) {
            nowMillis.set(minute * MINUTE_MILLIS);
            counter.record("example", CATEGORY);
        }
        assertEquals(Map.of(CATEGORY, 5), counter.snapshot("example"));

        nowMillis.set(5L * MINUTE_MILLIS);
        assertEquals(Map.of(CATEGORY, 4), counter.snapshot("example"));
        counter.record("example", CATEGORY);
        assertEquals(Map.of(CATEGORY, 5), counter.snapshot("example"));

        nowMillis.set(10L * MINUTE_MILLIS);
        counter.record("example", CATEGORY);
        assertEquals(Map.of(CATEGORY, 1), counter.snapshot("example"));
    }

    // Regression: consent clearing must remove counts without discarding the project allowlist.
    @Test
    void clearsProjectCountsButPreservesTheProjectAllowlist() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(CATEGORY))));
        counter.record("example", CATEGORY);

        counter.clear("example");
        counter.record("example", CATEGORY);

        assertEquals(Map.of(CATEGORY, 1), counter.snapshot("example"));
    }

    // Regression: refresh must remove retired categories while retaining unchanged counts.
    @Test
    void refreshRemovesRetiredCategoriesButPreservesUnchangedCounts() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration(
                "example",
                List.of("companion.ai.tick", "companion.lease.scan")
        )));
        counter.record("example", "companion.ai.tick");
        counter.record("example", "companion.lease.scan");

        counter.refreshProjects(List.of(registration(
                "example",
                List.of("companion.lease.scan", "companion.path.scan")
        )));

        assertEquals(Map.of("companion.lease.scan", 1), counter.snapshot("example"));
    }

    // Regression: retired projects must lose their counts and reject later records.
    @Test
    void refreshRemovalClearsProjectCountsAndStopsRecording() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(CATEGORY))));
        counter.record("example", CATEGORY);

        counter.refreshProjects(List.of());
        counter.record("example", CATEGORY);

        assertEquals(Map.of(), counter.snapshot("example"));
    }

    // Regression: clearAll must clear every project while preserving registered categories.
    @Test
    void clearAllRemovesCountsButPreservesRegisteredAllowlists() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(
                registration("example", List.of(CATEGORY)),
                registration("other", List.of("companion.lease.scan"))
        ));
        counter.record("example", CATEGORY);
        counter.record("other", "companion.lease.scan");

        counter.clearAll();
        counter.record("example", CATEGORY);
        counter.record("other", "companion.lease.scan");

        assertEquals(Map.of(CATEGORY, 1), counter.snapshot("example"));
        assertEquals(Map.of("companion.lease.scan", 1), counter.snapshot("other"));
    }

    // Regression: an empty refreshed allowlist must not retain stale category counts.
    @Test
    void refreshWithEmptyAllowlistRemovesPriorCounts() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(CATEGORY))));
        counter.record("example", CATEGORY);

        counter.refreshProjects(List.of(registration("example", List.of())));

        assertEquals(Map.of(), counter.snapshot("example"));
    }

    // Regression: snapshot totals must saturate instead of wrapping on integer overflow.
    @Test
    void saturatesAtIntegerMaximumInsteadOfWrapping() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration("example", List.of(CATEGORY))));
        counter.seedForTest("example", CATEGORY, Integer.MAX_VALUE - 1);

        counter.record("example", CATEGORY);
        counter.record("example", CATEGORY);

        assertEquals(Map.of(CATEGORY, Integer.MAX_VALUE), counter.snapshot("example"));
    }

    // Regression: publication must preserve first-seen category order and reject map mutation.
    @Test
    void snapshotPreservesFirstSeenOrderAndIsImmutable() {
        AtomicLong nowMillis = new AtomicLong();
        TelemetryProfilerBreadcrumbCounter counter = new TelemetryProfilerBreadcrumbCounter(nowMillis::get);
        counter.refreshProjects(List.of(registration(
                "example",
                List.of("companion.lease.scan", CATEGORY)
        )));
        counter.record("example", "companion.lease.scan");
        counter.record("example", CATEGORY);

        Map<String, Integer> snapshot = counter.snapshot("example");

        assertEquals(List.of("companion.lease.scan", CATEGORY), new ArrayList<>(snapshot.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", 1));
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
