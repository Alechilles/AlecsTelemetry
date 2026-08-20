package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in maximum-bounds benchmark for local profiler signal/context publication.
 *
 * <p>This is not part of the normal test suite. Enable it only on a controlled
 * machine with {@code ALECS_TELEMETRY_PROFILER_BENCHMARK=true}.</p>
 */
@EnabledIfEnvironmentVariable(
        named = "ALECS_TELEMETRY_PROFILER_BENCHMARK",
        matches = "true"
)
class ProfilerSignalPublicationBenchmarkTest {
    private static final int PROJECT_COUNT = 100;
    private static final int SNAPSHOTS_PER_PROJECT = 5;
    private static final int PATH_OCCURRENCES_PER_SNAPSHOT = 5;
    private static final int BREADCRUMB_CATEGORIES_PER_PROJECT = 16;
    private static final int WARM_UP_COUNT = 50;
    private static final int MEASURED_COUNT = 200;
    private static final int HIGH_WORK_PROJECT_COUNT = 10;
    private static final double P95_LIMIT_MILLIS = 25.0d;

    private static final List<String> CATEGORIES = IntStream.range(
                    0,
                    BREADCRUMB_CATEGORIES_PER_PROJECT
            )
            .mapToObj(index -> "profiler.category." + index)
            .toList();

    @Test
    void boundedSignalAndContextPublicationMeetsP95Target() {
        List<TelemetryProjectRegistration> registrations = registrations();
        TelemetryProfilerBreadcrumbCounter breadcrumbs = new TelemetryProfilerBreadcrumbCounter(
                () -> 0L
        );
        seedBreadcrumbs(breadcrumbs, registrations);
        TelemetryProfilerContextProvider contextProvider = new TelemetryProfilerContextProvider(
                () -> 42,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                ignored -> true,
                () -> 0L,
                null
        );
        TelemetryProjectProfilerService service = new TelemetryProjectProfilerService(
                () -> registrations,
                ignored -> true,
                "3.2.0",
                null,
                new ProfilerSignalEngine(),
                contextProvider
        );
        service.activateProvider();

        List<SparkProfileReadResult> fixtureResults = IntStream.rangeClosed(
                        1,
                        SNAPSHOTS_PER_PROJECT
                )
                .mapToObj(ProfilerSignalPublicationBenchmarkTest::result)
                .toList();

        try {
            // Seed five complete snapshots per project before the timed section.
            for (SparkProfileReadResult fixtureResult : fixtureResults) {
                service.publish(fixtureResult);
            }

            for (int iteration = 0; iteration < WARM_UP_COUNT; iteration++) {
                service.publish(fixtureResults.get(iteration % fixtureResults.size()));
            }

            long[] durationsNanos = new long[MEASURED_COUNT];
            for (int iteration = 0; iteration < MEASURED_COUNT; iteration++) {
                SparkProfileReadResult fixtureResult = fixtureResults.get(
                        iteration % fixtureResults.size()
                );
                long startedAt = System.nanoTime();
                service.publish(fixtureResult);
                durationsNanos[iteration] = Math.max(0L, System.nanoTime() - startedAt);
            }

            Arrays.sort(durationsNanos);
            int p95Index = (int) Math.ceil(0.95d * durationsNanos.length) - 1;
            double p95Millis = durationsNanos[p95Index] / 1_000_000.0d;
            int retainedPathCount = registrations.stream()
                    .mapToInt(registration -> service.view(registration.projectId())
                            .history()
                            .stream()
                            .mapToInt(snapshot -> snapshot.paths().size())
                            .sum())
                    .sum();

            assertTrue(
                    retainedPathCount <= TelemetryProjectProfilerService.MAX_RETAINED_PATH_ENTRIES,
                    "retained path bound exceeded: " + retainedPathCount
            );
            assertTrue(
                    registrations.stream().allMatch(registration ->
                            service.view(registration.projectId()).latest().isPresent()),
                    "each eligible project must have a published snapshot"
            );
            assertTrue(
                    registrations.stream().allMatch(registration -> service
                            .view(registration.projectId())
                            .latest()
                            .orElseThrow()
                            .context()
                            .breadcrumbCategoryCounts()
                            .size() == BREADCRUMB_CATEGORIES_PER_PROJECT),
                    "each published context must retain all approved categories"
            );
            System.out.printf(
                    Locale.ROOT,
                    "PROFILER_PUBLICATION_BENCHMARK jvmVersion=%s projectCount=%d "
                            + "retainedPathCount=%d warmUpCount=%d measuredCount=%d p95Millis=%.3f%n",
                    System.getProperty("java.version", "<unknown>"),
                    PROJECT_COUNT,
                    retainedPathCount,
                    WARM_UP_COUNT,
                    MEASURED_COUNT,
                    p95Millis
            );
            assertTrue(
                    p95Millis <= P95_LIMIT_MILLIS,
                    "publication p95 exceeded " + P95_LIMIT_MILLIS + " ms: " + p95Millis
            );
        } finally {
            service.close();
        }
    }

    private static void seedBreadcrumbs(
            TelemetryProfilerBreadcrumbCounter breadcrumbs,
            List<TelemetryProjectRegistration> registrations) {
        breadcrumbs.refreshProjects(registrations);
        for (TelemetryProjectRegistration registration : registrations) {
            for (String category : CATEGORIES) {
                breadcrumbs.record(registration.projectId(), category);
            }
        }
    }

    private static SparkProfileReadResult result(int windowKey) {
        List<SparkProfileWindow.Frame> frames = new ArrayList<>(
                PROJECT_COUNT * PATH_OCCURRENCES_PER_SNAPSHOT
        );
        for (int projectIndex = 0; projectIndex < PROJECT_COUNT; projectIndex++) {
            String projectId = projectId(projectIndex);
            for (int pathIndex = 0; pathIndex < PATH_OCCURRENCES_PER_SNAPSHOT; pathIndex++) {
                double selfMilliseconds = projectIndex < HIGH_WORK_PROJECT_COUNT && pathIndex == 0
                        ? 1_000.0d
                        : 1.0d;
                frames.add(new SparkProfileWindow.Frame(
                        -1,
                        pluginIdentifier(projectId),
                        "benchmark." + projectId + ".Path" + pathIndex,
                        "run" + pathIndex,
                        "()V",
                        selfMilliseconds
                ));
            }
        }
        SparkProfileWindow window = new SparkProfileWindow(
                "spark-1.10.172-beta7",
                windowKey,
                1,
                1,
                frames.size(),
                frames.size(),
                frames.stream().mapToDouble(SparkProfileWindow.Frame::selfMilliseconds).sum(),
                List.of(new SparkProfileWindow.ThreadFrames("WorldThread - default", frames))
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "spark-1.10.172-beta7",
                windowKey,
                1,
                frames.size(),
                window.totalSelfMilliseconds(),
                List.of()
        );
        return SparkProfileReadResult.complete(snapshot, window);
    }

    private static List<TelemetryProjectRegistration> registrations() {
        String categoriesJson = CATEGORIES.stream()
                .map(category -> "\"" + category + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return IntStream.range(0, PROJECT_COUNT)
                .mapToObj(index -> {
                    String projectId = projectId(index);
                    String pluginIdentifier = pluginIdentifier(projectId);
                    String packagePrefix = "benchmark." + projectId;
                    String json = "{"
                            + "\"projectId\":\"" + projectId + "\","
                            + "\"projectVersion\":\"1.0.0\","
                            + "\"displayName\":\"" + projectId + "\","
                            + "\"ownerPluginIdentifiers\":[\"" + pluginIdentifier + "\"],"
                            + "\"packagePrefixes\":[\"" + packagePrefix + "\"],"
                            + "\"telemetry\":{"
                            + "\"events\":{\"breadcrumbs\":{"
                            + "\"supported\":true,\"defaultEnabled\":true,"
                            + "\"profilerCorrelationCategories\":[" + categoriesJson + "]"
                            + "}},"
                            + "\"performance\":{\"supported\":true,\"defaultEnabled\":true}"
                            + "}"
                            + "}";
                    TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(json, null);
                    return new TelemetryProjectRegistration(
                            descriptor,
                            pluginIdentifier,
                            "1.0.0",
                            null
                    );
                })
                .toList();
    }

    private static String projectId(int index) {
        return "benchmark-" + index;
    }

    private static String pluginIdentifier(String projectId) {
        return "Benchmark:" + projectId;
    }
}
