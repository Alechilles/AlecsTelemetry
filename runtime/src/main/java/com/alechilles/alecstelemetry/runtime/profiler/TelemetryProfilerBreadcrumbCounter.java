package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Counts approved profiler breadcrumb categories in a bounded rolling window.
 *
 * <p>Only the category count is retained. Breadcrumb details are never accepted
 * by this class.</p>
 */
public final class TelemetryProfilerBreadcrumbCounter {
    private static final long MINUTE_MILLIS = 60_000L;
    private static final int BUCKET_COUNT = 5;
    private static final long EMPTY_MINUTE = Long.MIN_VALUE;

    private final LongSupplier clock;
    private final Map<String, ProjectBuckets> projects = new LinkedHashMap<>();

    public TelemetryProfilerBreadcrumbCounter() {
        this(System::currentTimeMillis);
    }

    TelemetryProfilerBreadcrumbCounter(@Nonnull LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void refreshProjects(
            @Nonnull List<TelemetryProjectRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");

        Map<String, List<String>> nextAllowlists = new LinkedHashMap<>();
        for (TelemetryProjectRegistration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            nextAllowlists.putIfAbsent(
                    registration.projectId(),
                    new ArrayList<>(registration.events().breadcrumbs().profilerCorrelationCategories())
            );
        }

        projects.entrySet().removeIf(entry -> !nextAllowlists.containsKey(entry.getKey()));
        for (Map.Entry<String, List<String>> entry : nextAllowlists.entrySet()) {
            ProjectBuckets buckets = projects.computeIfAbsent(entry.getKey(), ignored -> new ProjectBuckets());
            buckets.retainCategories(entry.getValue());
        }
    }

    /**
     * Records one accepted category for a registered project.
     *
     * <p>The category is trimmed, but it is not lowercased or otherwise
     * normalized before the exact allowlist lookup.</p>
     */
    public synchronized void record(@Nonnull String projectId, @Nonnull String category) {
        if (projectId == null || category == null) {
            return;
        }
        ProjectBuckets buckets = projects.get(projectId);
        if (buckets == null) {
            return;
        }
        String normalizedCategory = category.trim();
        int[] counts = buckets.countsByCategory.get(normalizedCategory);
        if (counts == null) {
            return;
        }

        long minuteKey = minuteKey(clock.getAsLong());
        int slot = Math.floorMod(minuteKey, BUCKET_COUNT);
        buckets.activateMinute(minuteKey, slot);
        counts[slot] = saturatingIncrement(counts[slot]);
    }

    @Nonnull
    public synchronized Map<String, Integer> snapshot(@Nonnull String projectId) {
        if (projectId == null) {
            return Map.of();
        }
        ProjectBuckets buckets = projects.get(projectId);
        if (buckets == null) {
            return Map.of();
        }

        long currentMinute = minuteKey(clock.getAsLong());
        LinkedHashMap<String, Integer> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : buckets.countsByCategory.entrySet()) {
            int total = 0;
            for (int age = 0; age < BUCKET_COUNT; age++) {
                long bucketMinute = currentMinute - age;
                int slot = Math.floorMod(bucketMinute, BUCKET_COUNT);
                if (buckets.minuteKeys[slot] == bucketMinute) {
                    total = saturatingAdd(total, entry.getValue()[slot]);
                }
            }
            if (total > 0) {
                snapshot.put(entry.getKey(), total);
            }
        }
        if (snapshot.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public synchronized void clear(@Nonnull String projectId) {
        if (projectId == null) {
            return;
        }
        ProjectBuckets buckets = projects.get(projectId);
        if (buckets != null) {
            buckets.clearCounts();
        }
    }

    public synchronized void clearAll() {
        for (ProjectBuckets buckets : projects.values()) {
            buckets.clearCounts();
        }
    }

    /**
     * Seeds one current-minute bucket for saturation coverage.
     *
     * <p>This package-private path is intentionally limited to one registered
     * category and stores no data beyond the normal count bucket.</p>
     */
    synchronized void seedForTest(
            @Nonnull String projectId,
            @Nonnull String category,
            int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        ProjectBuckets buckets = projects.get(projectId);
        if (buckets == null || category == null) {
            return;
        }
        int[] counts = buckets.countsByCategory.get(category.trim());
        if (counts == null) {
            return;
        }
        long minuteKey = minuteKey(clock.getAsLong());
        int slot = Math.floorMod(minuteKey, BUCKET_COUNT);
        buckets.activateMinute(minuteKey, slot);
        counts[slot] = count;
    }

    private static long minuteKey(long nowMillis) {
        return Math.floorDiv(nowMillis, MINUTE_MILLIS);
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private static int saturatingAdd(int left, int right) {
        if ((long) left + right >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return left + right;
    }

    private static final class ProjectBuckets {
        final LinkedHashMap<String, int[]> countsByCategory = new LinkedHashMap<>();
        final long[] minuteKeys = new long[BUCKET_COUNT];

        private ProjectBuckets() {
            Arrays.fill(minuteKeys, EMPTY_MINUTE);
        }

        private void retainCategories(List<String> categories) {
            countsByCategory.keySet().removeIf(category -> !categories.contains(category));
            for (String category : categories) {
                countsByCategory.putIfAbsent(category, new int[BUCKET_COUNT]);
            }
        }

        private void activateMinute(long minuteKey, int slot) {
            if (minuteKeys[slot] == minuteKey) {
                return;
            }
            for (int[] counts : countsByCategory.values()) {
                counts[slot] = 0;
            }
            minuteKeys[slot] = minuteKey;
        }

        private void clearCounts() {
            for (int[] counts : countsByCategory.values()) {
                Arrays.fill(counts, 0);
            }
            Arrays.fill(minuteKeys, EMPTY_MINUTE);
        }
    }
}
