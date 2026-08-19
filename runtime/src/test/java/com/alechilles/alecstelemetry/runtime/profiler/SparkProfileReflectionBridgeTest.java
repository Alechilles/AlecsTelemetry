package com.alechilles.alecstelemetry.runtime.profiler;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.sampler.Sampler;
import me.lucko.spark.hytale.HytaleSparkPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SparkProfileReflectionBridgeTest {

    @Test
    void readsOnlyTheLatestCompletedAsyncBackgroundWindow() {
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                profileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 2);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        SparkProfileSnapshot snapshot = result.snapshot();
        assertNotNull(snapshot);
        assertEquals("1.10.172-SNAPSHOT", snapshot.sparkVersion());
        assertEquals(200, snapshot.windowKey());
        assertEquals(1, snapshot.threadCount());
        assertEquals(3, snapshot.frameCount());
        assertEquals(160.0d, snapshot.totalSelfMilliseconds(), 0.001d);
        assertEquals(2, snapshot.hotPaths().size());
        assertEquals("ExampleMod", snapshot.hotPaths().getFirst().source());
        assertEquals("example.Parent#run()V", snapshot.hotPaths().getFirst().frame());
        assertEquals(80.0d, snapshot.hotPaths().getFirst().selfMilliseconds(), 0.001d);
        assertEquals(50.0d, snapshot.hotPaths().getFirst().selfSharePercent(), 0.001d);
        assertEquals("OtherMod", snapshot.hotPaths().get(1).source());
        assertEquals(1, sampler.toProtoCalls);
    }

    @Test
    void ranksOnlyWorldThreadJavaSelfTimeAndKeepsSourceAttribution() {
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                worldThreadRankingProfileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 2);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        SparkProfileSnapshot snapshot = result.snapshot();
        assertNotNull(snapshot);
        assertEquals(4, snapshot.totalThreadCount());
        assertEquals(2, snapshot.selectedThreadCount());
        assertEquals(8, snapshot.totalFrameCount());
        assertEquals(6, snapshot.selectedFrameCount());
        assertEquals(800.0d, snapshot.totalSelfMilliseconds(), 0.001d);
        assertEquals(2, snapshot.hotPaths().size());
        assertEquals("GameMod", snapshot.hotPaths().getFirst().source());
        assertEquals("com.game.Tick#work()V", snapshot.hotPaths().getFirst().frame());
        assertEquals(550.0d, snapshot.hotPaths().getFirst().selfMilliseconds(), 0.001d);
        assertEquals(68.75d, snapshot.hotPaths().getFirst().selfSharePercent(), 0.001d);
        assertEquals("com.game.Tick#tick()V", snapshot.hotPaths().get(1).frame());
        assertEquals(200.0d, snapshot.hotPaths().get(1).selfMilliseconds(), 0.001d);
        assertEquals(25.0d, snapshot.hotPaths().get(1).selfSharePercent(), 0.001d);
    }

    @Test
    void reportsNoActionableDataWhenCompletedWindowHasNoWorldThreadSamples() {
        SamplerData data = new SamplerData(
                List.of(700),
                List.of(new ThreadNode(
                        "ServerWorker-1",
                        List.of(new StackNode(
                                "com.example.Background",
                                "run",
                                "()V",
                                List.of(900.0d),
                                List.of()
                        )),
                        List.of()
                )),
                Map.of("com.example.Background", "OtherMod"),
                Map.of()
        );
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                data
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 5);

        assertEquals("NO_ACTIONABLE_DATA", result.status().name());
        assertNull(result.snapshot());
    }

    @Test
    void skipsAnUntestedSparkVersionBeforeReadingItsProfile() {
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                profileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.999-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = new SparkProfileReflectionBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_VERSION, result.status());
        assertEquals(0, sampler.toProtoCalls);
    }

    @Test
    void skipsAnUnknownArtifactEvenWhenItsVersionBaseIsTested() {
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                profileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = new SparkProfileReflectionBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_ARTIFACT, result.status());
        assertEquals(0, sampler.toProtoCalls);
    }

    @Test
    void doesNotReadAUserStartedForegroundProfile() {
        FakeSampler sampler = new FakeSampler(
                false,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                profileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.NOT_BACKGROUND, result.status());
        assertEquals(0, sampler.toProtoCalls);
    }

    @Test
    void doesNotReadTheJavaSamplerEngine() {
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.JAVA,
                Sampler.SamplerMode.EXECUTION,
                profileData()
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.UNSUPPORTED_SAMPLER, result.status());
        assertEquals(0, sampler.toProtoCalls);
    }

    @Test
    void recursiveFramesDoNotInflateReportedSelfTime() {
        StackNode outer = new StackNode(
                "example.Recursive",
                "run",
                "()V",
                List.of(100.0d),
                List.of(1)
        );
        StackNode inner = new StackNode(
                "example.Recursive",
                "run",
                "()V",
                List.of(100.0d),
                List.of()
        );
        SamplerData data = new SamplerData(
                List.of(300),
                List.of(new ThreadNode("WorldThread - default", List.of(outer, inner), List.of(0))),
                Map.of("example.Recursive", "ExampleMod"),
                Map.of()
        );
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                data
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.COMPLETE, result.status());
        SparkProfileSnapshot snapshot = result.snapshot();
        assertNotNull(snapshot);
        assertEquals(100.0d, snapshot.totalSelfMilliseconds(), 0.001d);
        assertEquals(1, snapshot.hotPaths().size());
        assertEquals(100.0d, snapshot.hotPaths().getFirst().selfMilliseconds(), 0.001d);
        assertEquals(100.0d, snapshot.hotPaths().getFirst().selfSharePercent(), 0.001d);
    }

    @Test
    void rejectsAWindowThatCannotProduceACompleteBoundedRanking() {
        List<StackNode> nodes = new ArrayList<>(25_001);
        for (int index = 0; index < 25_001; index++) {
            nodes.add(new StackNode(
                    "example.Frame" + index,
                    "run",
                    "()V",
                    List.of(index == 25_000 ? 10_000.0d : 1.0d),
                    List.of()
            ));
        }
        SamplerData data = new SamplerData(
                List.of(400),
                List.of(new ThreadNode("WorldThread - default", nodes, List.of())),
                Map.of(),
                Map.of()
        );
        FakeSampler sampler = new FakeSampler(
                true,
                Sampler.SamplerType.ASYNC,
                Sampler.SamplerMode.EXECUTION,
                data
        );
        HytaleSparkPlugin plugin = new HytaleSparkPlugin(
                "1.10.172-SNAPSHOT",
                new SparkPlatform(sampler)
        );

        SparkProfileReadResult result = trustedBridge().read(plugin, 5);

        assertEquals(SparkProfileReadResult.Status.TOO_COMPLEX, result.status());
        assertNull(result.snapshot());
        assertEquals(1, sampler.toProtoCalls);
    }

    private static SparkProfileReflectionBridge trustedBridge() {
        return new SparkProfileReflectionBridge((pluginClass, reportedVersion) -> reportedVersion);
    }

    private static SamplerData profileData() {
        StackNode leaf = new StackNode(
                "example.Leaf",
                "work",
                "()V",
                List.of(100.0d, 20.0d),
                List.of()
        );
        StackNode parent = new StackNode(
                "example.Parent",
                "run",
                "()V",
                List.of(300.0d, 100.0d),
                List.of(0)
        );
        StackNode other = new StackNode(
                "example.Other",
                "tick",
                "()V",
                List.of(50.0d, 60.0d),
                List.of()
        );
        ThreadNode thread = new ThreadNode(
                "WorldThread - default",
                List.of(leaf, parent, other),
                List.of(1, 2)
        );
        return new SamplerData(
                List.of(100, 200),
                List.of(thread),
                Map.of(
                        "example.Leaf", "ExampleMod",
                        "example.Parent", "FallbackMod",
                        "example.Other", "OtherMod"
                ),
                Map.of("example.Parent;run;()V", "ExampleMod")
        );
    }

    private static SamplerData worldThreadRankingProfileData() {
        StackNode nativeWait = new StackNode(
                "native",
                "Unsafe_Park",
                "",
                List.of(900.0d, 900.0d),
                List.of()
        );
        StackNode nonWorldJava = new StackNode(
                "com.example.Background",
                "run",
                "()V",
                List.of(600.0d, 600.0d),
                List.of()
        );
        StackNode worldNativeWait = new StackNode(
                "native",
                "Unsafe_Park",
                "",
                List.of(900.0d, 900.0d),
                List.of()
        );
        StackNode worldTick = new StackNode(
                "com.game.Tick",
                "tick",
                "()V",
                List.of(500.0d, 500.0d),
                List.of(2)
        );
        StackNode worldLeaf = new StackNode(
                "com.game.Tick",
                "work",
                "()V",
                List.of(300.0d, 300.0d),
                List.of()
        );
        StackNode repeatedWorldWork = new StackNode(
                "com.game.Tick",
                "work",
                "()V",
                List.of(150.0d, 150.0d),
                List.of()
        );
        StackNode netherWorldWork = new StackNode(
                "com.game.Tick",
                "work",
                "()V",
                List.of(100.0d, 100.0d),
                List.of()
        );
        StackNode worldRender = new StackNode(
                "com.game.Render",
                "render",
                "()V",
                List.of(50.0d, 50.0d),
                List.of()
        );
        return new SamplerData(
                List.of(100, 200),
                List.of(
                        new ThreadNode("NativeWait-1", List.of(nativeWait), List.of(0)),
                        new ThreadNode("ServerWorker-1", List.of(nonWorldJava), List.of(0)),
                        new ThreadNode(
                                "WorldThread - default",
                                List.of(worldNativeWait, worldTick, worldLeaf, repeatedWorldWork),
                                List.of(0, 2)
                        ),
                        new ThreadNode(
                                "WorldThread - nether",
                                List.of(netherWorldWork, worldRender),
                                List.of(0, 1)
                        )
                ),
                Map.of(
                        "com.example.Background", "OtherMod",
                        "com.game.Render", "RenderMod"
                ),
                Map.of(
                        "com.game.Tick;tick;()V", "GameMod",
                        "com.game.Tick;work;()V", "GameMod"
                )
        );
    }

    public static final class FakeSampler {
        private final boolean background;
        private final Sampler.SamplerType type;
        private final Sampler.SamplerMode mode;
        private final SamplerData data;
        private int toProtoCalls;

        private FakeSampler(boolean background,
                            Sampler.SamplerType type,
                            Sampler.SamplerMode mode,
                            SamplerData data) {
            this.background = background;
            this.type = type;
            this.mode = mode;
            this.data = data;
        }

        public boolean isRunningInBackground() {
            return background;
        }

        public Sampler.SamplerType getType() {
            return type;
        }

        public Sampler.SamplerMode getMode() {
            return mode;
        }

        public Object toProto(SparkPlatform platform, Sampler.ExportProps props) {
            props.classSourceLookup().get();
            toProtoCalls++;
            return data;
        }
    }

    public record SamplerData(List<Integer> timeWindows,
                              List<ThreadNode> threads,
                              Map<String, String> classSources,
                              Map<String, String> methodSources) {
        public List<Integer> getTimeWindowsList() {
            return timeWindows;
        }

        public List<ThreadNode> getThreadsList() {
            return threads;
        }

        public Map<String, String> getClassSourcesMap() {
            return classSources;
        }

        public Map<String, String> getMethodSourcesMap() {
            return methodSources;
        }
    }

    public record ThreadNode(String name,
                             List<StackNode> children,
                             List<Integer> childrenRefs) {
        public String getName() {
            return name;
        }

        public List<StackNode> getChildrenList() {
            return children;
        }

        public List<Integer> getChildrenRefsList() {
            return childrenRefs;
        }
    }

    public record StackNode(String className,
                            String methodName,
                            String methodDesc,
                            List<Double> times,
                            List<Integer> childrenRefs) {
        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDesc() {
            return methodDesc;
        }

        public List<Double> getTimesList() {
            return times;
        }

        public List<Integer> getChildrenRefsList() {
            return childrenRefs;
        }
    }
}
