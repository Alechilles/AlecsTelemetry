package com.alechilles.alecstelemetry.runtime.profiler;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.sampler.Sampler;
import me.lucko.spark.common.sampler.async.AsyncDataAggregator;
import me.lucko.spark.common.sampler.async.AsyncSampler;
import me.lucko.spark.common.sampler.node.StackTraceNode;
import me.lucko.spark.common.sampler.node.ThreadNode;
import me.lucko.spark.hytale.HytaleSparkPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in benchmark for the bounded direct Spark window reader.
 */
@EnabledIfEnvironmentVariable(
        named = "ALECS_TELEMETRY_PROFILER_BENCHMARK",
        matches = "true"
)
class SparkDirectWindowReaderBenchmarkTest {
    private static final int ACTIVE_NODE_COUNT = 5_000;
    private static final int RETAINED_WINDOW_COUNT = 60;
    private static final int WARM_UP_COUNT = 30;
    private static final int MEASURED_COUNT = 100;
    private static final double P95_LIMIT_MILLIS = 10.0d;
    private static final long ALLOCATION_LIMIT_BYTES = 8L * 1024L * 1024L;

    @Test
    void representativeDirectReadMeetsTimeAndAllocationTargets() {
        HytaleSparkPlugin plugin = fixture();
        SparkProfileReflectionBridge bridge = new SparkProfileReflectionBridge(
                (pluginClass, reportedVersion) -> reportedVersion
        );
        com.sun.management.ThreadMXBean allocationBean = allocationBean();

        for (int iteration = 0; iteration < WARM_UP_COUNT; iteration++) {
            assertEquals(SparkProfileReadResult.Status.COMPLETE, bridge.read(plugin, 5).status());
        }

        long[] durationsNanos = new long[MEASURED_COUNT];
        long[] allocatedBytes = new long[MEASURED_COUNT];
        for (int iteration = 0; iteration < MEASURED_COUNT; iteration++) {
            long allocationBefore = allocationBean == null
                    ? -1L
                    : allocationBean.getCurrentThreadAllocatedBytes();
            long startedAt = System.nanoTime();
            SparkProfileReadResult result = bridge.read(plugin, 5);
            durationsNanos[iteration] = Math.max(0L, System.nanoTime() - startedAt);
            long allocationAfter = allocationBean == null
                    ? -1L
                    : allocationBean.getCurrentThreadAllocatedBytes();
            allocatedBytes[iteration] = allocationBefore < 0L || allocationAfter < allocationBefore
                    ? -1L
                    : allocationAfter - allocationBefore;
            assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
            assertEquals(ACTIVE_NODE_COUNT, result.snapshot().selectedFrameCount());
        }

        Arrays.sort(durationsNanos);
        Arrays.sort(allocatedBytes);
        int p95Index = (int) Math.ceil(0.95d * MEASURED_COUNT) - 1;
        double p95Millis = durationsNanos[p95Index] / 1_000_000.0d;
        long p95AllocatedBytes = allocatedBytes[p95Index];
        System.out.printf(
                Locale.ROOT,
                "SPARK_DIRECT_READER_BENCHMARK jvmVersion=%s activeNodes=%d retainedWindows=%d "
                        + "warmUpCount=%d measuredCount=%d p95Millis=%.3f p95AllocatedBytes=%d%n",
                System.getProperty("java.version", "<unknown>"),
                ACTIVE_NODE_COUNT,
                RETAINED_WINDOW_COUNT,
                WARM_UP_COUNT,
                MEASURED_COUNT,
                p95Millis,
                p95AllocatedBytes
        );
        assertTrue(
                p95Millis <= P95_LIMIT_MILLIS,
                "direct reader p95 exceeded " + P95_LIMIT_MILLIS + " ms: " + p95Millis
        );
        if (p95AllocatedBytes >= 0L) {
            assertTrue(
                    p95AllocatedBytes <= ALLOCATION_LIMIT_BYTES,
                    "direct reader allocation exceeded " + ALLOCATION_LIMIT_BYTES
                            + " bytes: " + p95AllocatedBytes
            );
        }
    }

    private static HytaleSparkPlugin fixture() {
        ThreadNode world = new ThreadNode("WorldThread - benchmark");
        for (int window = 1; window <= RETAINED_WINDOW_COUNT; window++) {
            world.time(window, ACTIVE_NODE_COUNT * 1_000L);
        }
        for (int nodeIndex = 0; nodeIndex < ACTIVE_NODE_COUNT; nodeIndex++) {
            StackTraceNode node = new StackTraceNode(
                    "benchmark.mod.Path" + nodeIndex,
                    "run",
                    "()V"
            );
            for (int window = 1; window <= RETAINED_WINDOW_COUNT; window++) {
                node.time(window, 1_000L);
            }
            world.child(node);
        }
        AsyncDataAggregator aggregator = new AsyncDataAggregator().thread("world", world);
        AsyncSampler sampler = new AsyncSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                aggregator
        );
        return new HytaleSparkPlugin("1.10.172-SNAPSHOT", new SparkPlatform(sampler));
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocationBean
                && allocationBean.isThreadAllocatedMemorySupported()
                && allocationBean.isThreadAllocatedMemoryEnabled()) {
            return allocationBean;
        }
        return null;
    }
}
