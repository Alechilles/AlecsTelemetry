package com.alechilles.alecstelemetry.runtime.profiler;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.sampler.Sampler;
import me.lucko.spark.common.sampler.async.AsyncDataAggregator;
import me.lucko.spark.common.sampler.async.AsyncSampler;
import me.lucko.spark.common.sampler.node.StackTraceNode;
import me.lucko.spark.common.sampler.node.ThreadNode;
import me.lucko.spark.hytale.HytaleSparkPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SparkProfileReflectionBridgeTest {

    @Test
    void readsNewestCompletedWindowWithoutExportingSparkProtobuf() {
        StackTraceNode leaf = node("example.Leaf", "work")
                .time(100, 100_000L)
                .time(200, 20_000L);
        StackTraceNode parent = node("example.Parent", "run")
                .time(100, 300_000L)
                .time(200, 100_000L)
                .child(leaf);
        StackTraceNode other = node("example.Other", "tick")
                .time(100, 50_000L)
                .time(200, 60_000L);
        ThreadNode worldThread = worldThread()
                .time(100, 350_000L)
                .time(200, 160_000L)
                .child(parent)
                .child(other);
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, worldThread);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 2);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        assertEquals(0, sampler.toProtoCalls());
        assertNotNull(result.snapshot());
        assertEquals(200, result.snapshot().windowKey());
        assertEquals(160.0d, result.snapshot().totalSelfMilliseconds(), 0.001d);
        assertEquals("example.Parent#run()V", result.snapshot().hotPaths().getFirst().frame());
        assertEquals(80.0d, result.snapshot().hotPaths().getFirst().selfMilliseconds(), 0.001d);
        assertEquals("<unknown>", result.snapshot().hotPaths().getFirst().source());
        assertNotNull(result.window());
        List<SparkProfileWindow.Frame> frames = result.window().threads().getFirst().frames();
        int parentIndex = frameIndex(frames, "example.Parent");
        int leafIndex = frameIndex(frames, "example.Leaf");
        int otherIndex = frameIndex(frames, "example.Other");
        assertEquals(-1, frames.get(parentIndex).parentIndex());
        assertEquals(parentIndex, frames.get(leafIndex).parentIndex());
        assertEquals(-1, frames.get(otherIndex).parentIndex());
    }

    @Test
    void ranksOnlyWorldThreadJavaSelfTime() {
        ThreadNode background = new ThreadNode("ServerWorker-1")
                .time(200, 600_000L)
                .child(node("com.example.Background", "run").time(200, 600_000L));
        StackTraceNode leaf = node("com.game.Tick", "work").time(200, 300_000L);
        StackTraceNode tick = node("com.game.Tick", "tick")
                .time(200, 500_000L)
                .child(leaf);
        ThreadNode world = worldThread()
                .time(200, 950_000L)
                .child(new StackTraceNode("libc.so.6", "wait", "()L;")
                        .time(200, 400_000L))
                .child(tick)
                .child(node("com.game.Tick", "work").time(200, 50_000L));
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, background, world);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        SparkProfileSnapshot snapshot = result.snapshot();
        assertNotNull(snapshot);
        assertEquals(2, snapshot.totalThreadCount());
        assertEquals(1, snapshot.selectedThreadCount());
        assertEquals(550.0d, snapshot.totalSelfMilliseconds(), 0.001d);
        assertEquals("com.game.Tick#work()V", snapshot.hotPaths().getFirst().frame());
        assertEquals(350.0d, snapshot.hotPaths().getFirst().selfMilliseconds(), 0.001d);
        assertEquals("com.game.Tick#tick()V", snapshot.hotPaths().get(1).frame());
        assertEquals(200.0d, snapshot.hotPaths().get(1).selfMilliseconds(), 0.001d);
    }

    @Test
    void reportsNoActionableDataWhenCompletedWindowHasNoWorldThreadSamples() {
        ThreadNode background = new ThreadNode("ServerWorker-1")
                .time(700, 900_000L)
                .child(node("com.example.Background", "run").time(700, 900_000L));
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, background);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.NO_ACTIONABLE_DATA, result.status());
        assertNull(result.snapshot());
    }

    @Test
    void doesNotReadUserStartedOrUnsupportedSamplers() {
        ThreadNode world = worldThread()
                .time(1, 10_000L)
                .child(node("example.Tick", "run").time(1, 10_000L));
        AsyncSampler foreground = sampler(false, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);
        AsyncSampler javaSampler = sampler(true, Sampler.SamplerType.JAVA,
                Sampler.SamplerMode.EXECUTION, world);

        SparkProfileReadResult foregroundResult = trustedBridge().read(plugin(foreground), 5);
        SparkProfileReadResult javaResult = trustedBridge().read(plugin(javaSampler), 5);

        assertEquals(SparkProfileReadResult.Status.NOT_BACKGROUND, foregroundResult.status());
        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_SAMPLER, javaResult.status());
        assertEquals(0, foreground.toProtoCalls());
        assertEquals(0, javaSampler.toProtoCalls());
    }

    @Test
    void rejectsUnknownVersionAndArtifactBeforeReadingSamplerState() {
        ThreadNode world = worldThread()
                .time(1, 10_000L)
                .child(node("example.Tick", "run").time(1, 10_000L));
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);

        SparkProfileReadResult version = new SparkProfileReflectionBridge().read(
                new HytaleSparkPlugin("1.10.999-SNAPSHOT", new SparkPlatform(sampler)),
                5
        );
        SparkProfileReadResult artifact = new SparkProfileReflectionBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_VERSION, version.status());
        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_ARTIFACT, artifact.status());
        assertEquals(0, sampler.toProtoCalls());
    }

    @Test
    void recursiveFramesDoNotInflateReportedSelfTime() {
        StackTraceNode inner = node("example.Recursive", "run").time(300, 100_000L);
        StackTraceNode outer = node("example.Recursive", "run")
                .time(300, 100_000L)
                .child(inner);
        ThreadNode world = worldThread().time(300, 100_000L).child(outer);
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        assertEquals(100.0d, result.snapshot().totalSelfMilliseconds(), 0.001d);
        assertEquals(1, result.snapshot().hotPaths().size());
        assertEquals(100.0d, result.snapshot().hotPaths().getFirst().selfMilliseconds(), 0.001d);
    }

    @Test
    void activeTreeOverNodeLimitFailsClosed() {
        ThreadNode world = worldThread().time(400, 25_001_000L);
        for (int index = 0; index < 25_001; index++) {
            world.child(node("example.Active" + index, "run").time(400, 1_000L));
        }
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.TOO_COMPLEX, result.status());
        assertNull(result.snapshot());
        assertEquals(0, sampler.toProtoCalls());
    }

    @Test
    void inactiveHistoricalBranchDoesNotConsumeActiveNodeLimit() {
        StackTraceNode historicalRoot = node("example.Historical0", "run")
                .time(400, 1_000L);
        StackTraceNode historicalTail = historicalRoot;
        for (int index = 1; index < 25_001; index++) {
            StackTraceNode child = node("example.Historical" + index, "run")
                    .time(400, 1_000L);
            historicalTail.child(child);
            historicalTail = child;
        }
        StackTraceNode active = node("example.Active", "run").time(401, 100_000L);
        ThreadNode world = worldThread()
                .time(400, 1_000L)
                .time(401, 100_000L)
                .child(historicalRoot)
                .child(active);
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);

        SparkProfileReadResult result = trustedBridge().read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        assertEquals(1, result.snapshot().totalFrameCount());
        assertEquals("example.Active#run()V", result.snapshot().hotPaths().getFirst().frame());
    }

    @Test
    void directReadBudgetFailsClosedWithoutPartialSnapshot() {
        ThreadNode world = worldThread()
                .time(500, 10_000L)
                .child(node("example.Tick", "run").time(500, 10_000L));
        AsyncSampler sampler = sampler(true, Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION, world);
        AtomicLong clock = new AtomicLong();
        SparkProfileReflectionBridge bridge = new SparkProfileReflectionBridge(
                (pluginClass, reportedVersion) -> reportedVersion,
                () -> clock.getAndAdd(10L),
                5L
        );

        SparkProfileReadResult result = bridge.read(plugin(sampler), 5);

        assertEquals(SparkProfileReadResult.Status.TOO_COMPLEX, result.status());
        assertNull(result.snapshot());
        assertEquals(0, sampler.toProtoCalls());
    }

    private static SparkProfileReflectionBridge trustedBridge() {
        return new SparkProfileReflectionBridge(
                (pluginClass, reportedVersion) -> reportedVersion,
                () -> 0L,
                20_000_000L
        );
    }

    private static HytaleSparkPlugin plugin(AsyncSampler sampler) {
        return new HytaleSparkPlugin("1.10.172-SNAPSHOT", new SparkPlatform(sampler));
    }

    private static AsyncSampler sampler(boolean background,
                                        Sampler.SamplerType type,
                                        Sampler.SamplerMode mode,
                                        ThreadNode... threads) {
        AsyncDataAggregator aggregator = new AsyncDataAggregator();
        for (int index = 0; index < threads.length; index++) {
            aggregator.thread("thread-" + index, threads[index]);
        }
        return new AsyncSampler(background, type, mode, aggregator);
    }

    private static ThreadNode worldThread() {
        return new ThreadNode("WorldThread - default");
    }

    private static StackTraceNode node(String className, String methodName) {
        return new StackTraceNode(className, methodName, "()V");
    }

    private static int frameIndex(List<SparkProfileWindow.Frame> frames, String className) {
        for (int index = 0; index < frames.size(); index++) {
            if (className.equals(frames.get(index).className())) {
                return index;
            }
        }
        throw new AssertionError("Missing frame " + className);
    }
}
