package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerProjectAnalyzerTest {

    private static final String DESCRIPTOR = "()V";

    @Test
    void analyzerSeparatesSelfAndNearestOwnerDownstreamCost() {
        List<ProfilerProjectWindow> projects = analyze(nestedWindow(), owners(), id -> true);
        ProfilerProjectWindow modA = project(projects, "mod-a");
        assertPath(modA, TelemetryProfilerAttribution.SELF,
                "example.a.Tick", "tick", 20.0d);
        assertPath(modA, TelemetryProfilerAttribution.DOWNSTREAM,
                "example.a.Tick", "tick", "com.hypixel.Store", "tick", 40.0d);
        ProfilerProjectWindow modB = project(projects, "mod-b");
        assertPath(modB, TelemetryProfilerAttribution.SELF,
                "example.b.Callback", "run", 10.0d);
        assertPath(modB, TelemetryProfilerAttribution.DOWNSTREAM,
                "example.b.Callback", "run", "com.hypixel.Metric", "add", 30.0d);
        assertFalse(modA.paths().stream().anyMatch(path -> path.sampledMilliseconds() == 30.0d));
        assertEquals("unqualified-v1", modA.qualificationRuleSet());
        assertTrue(modA.paths().stream().allMatch(path ->
                path.qualification() == TelemetryProfilerQualification.OBSERVED));
    }

    @Test
    void fingerprintIgnoresVersionsAndWindowContext() {
        String first = analyze(window(1, "1.0.0"), owners("1.0.0"), id -> true)
                .getFirst().paths().getFirst().fingerprint();
        String second = analyze(window(99, "2.0.0"), owners("2.0.0"), id -> true)
                .getFirst().paths().getFirst().fingerprint();
        assertEquals(first, second);
        assertEquals(32, first.length());
    }

    @Test
    void disabledProjectIsCheckedAtAnalysisTime() {
        List<ProfilerProjectWindow> result = analyze(nestedWindow(), owners(), id -> !id.equals("mod-a"));
        assertTrue(result.stream().noneMatch(project -> project.projectId().equals("mod-a")));
        assertTrue(result.stream().anyMatch(project -> project.projectId().equals("mod-b")));
    }

    @Test
    void zeroSelfOwnedWrapperStillAttributesDownstreamWork() {
        SparkProfileWindow window = new SparkProfileWindow(
                "1.0.0",
                1,
                1,
                1,
                2,
                2,
                100.0d,
                List.of(new SparkProfileWindow.ThreadFrames(
                        "WorldThread - default",
                        List.of(
                                new SparkProfileWindow.Frame(-1, "mod-a", "example.a.Tick", "tick", DESCRIPTOR, 0.0d),
                                new SparkProfileWindow.Frame(0, "hytale", "com.hypixel.Store", "tick", DESCRIPTOR, 100.0d)
                        )
                ))
        );
        List<ProfilerProjectWindow> projects = analyze(window, owners(), id -> true);
        assertPath(project(projects, "mod-a"), TelemetryProfilerAttribution.DOWNSTREAM,
                "example.a.Tick", "tick", "com.hypixel.Store", "tick", 100.0d);
        assertFalse(project(projects, "mod-a").paths().stream()
                .anyMatch(path -> path.attribution() == TelemetryProfilerAttribution.SELF));
    }

    @Test
    void downstreamRepresentativePathKeepsOwnerAndFirstSevenDescendants() {
        List<SparkProfileWindow.Frame> frames = new ArrayList<>();
        frames.add(new SparkProfileWindow.Frame(
                -1, "mod-a", "example.a.Owner", "owner", DESCRIPTOR, 0.0d));
        for (int index = 1; index <= 9; index++) {
            frames.add(new SparkProfileWindow.Frame(
                    index - 1,
                    "foreign",
                    "foreign.Layer" + index,
                    "run" + index,
                    DESCRIPTOR,
                    index == 9 ? 1.0d : 0.0d
            ));
        }

        ProfilerProjectAnalyzer.Analysis analysis = ProfilerProjectAnalyzer.analyze(
                window(1, "1.0.0", frames),
                owners().index(),
                id -> true,
                5
        );

        ProfilerProjectWindow.Path path = analysis.projects().getFirst().paths().getFirst();
        assertEquals(TelemetryProfilerAttribution.DOWNSTREAM, path.attribution());
        assertEquals("example.a.Owner", path.ownedClassName());
        assertEquals("foreign.Layer1", path.firstExternalClassName());
        assertEquals(
                List.of(
                        "example.a.Owner#owner()V",
                        "foreign.Layer1#run1()V",
                        "foreign.Layer2#run2()V",
                        "foreign.Layer3#run3()V",
                        "foreign.Layer4#run4()V",
                        "foreign.Layer5#run5()V",
                        "foreign.Layer6#run6()V",
                        "foreign.Layer7#run7()V"
                ),
                path.representativePath()
        );
    }

    @Test
    void disabledNearestOwnerBlocksAttributionToFartherOwner() {
        SparkProfileWindow window = window(1, "1.0.0", List.of(
                new SparkProfileWindow.Frame(-1, "mod-a", "example.a.Tick", "tick", DESCRIPTOR, 0.0d),
                new SparkProfileWindow.Frame(0, "hytale", "com.hypixel.Store", "tick", DESCRIPTOR, 0.0d),
                new SparkProfileWindow.Frame(1, "mod-b", "example.b.Callback", "run", DESCRIPTOR, 0.0d),
                new SparkProfileWindow.Frame(2, "hytale", "com.hypixel.Metric", "add", DESCRIPTOR, 10.0d)
        ));

        List<ProfilerProjectWindow> projects = analyze(window, owners(), id -> !id.equals("mod-b"));

        assertTrue(projects.isEmpty());
    }

    @Test
    void nonfatalEligibilityFailureReturnsLocalAnalysisFailure() {
        ProfilerProjectAnalyzer.Analysis analysis = ProfilerProjectAnalyzer.analyze(
                window(1, "1.0.0"),
                owners().index(),
                ignored -> {
                    throw new IllegalStateException("eligibility failed");
                },
                5
        );

        assertEquals(ProfilerProjectAnalyzer.Analysis.Status.FAILED, analysis.status());
        assertTrue(analysis.detail().contains("eligibility failed"));
        assertTrue(analysis.projects().isEmpty());
    }

    @Test
    void analysisClampsPathBoundToFive() {
        ProfilerProjectAnalyzer.Analysis analysis = ProfilerProjectAnalyzer.analyze(
                manyPathsWindow(),
                owners().index(),
                id -> true,
                10
        );

        assertEquals(ProfilerProjectAnalyzer.Analysis.Status.COMPLETE, analysis.status());
        assertEquals(5, analysis.projects().getFirst().paths().size());
        assertEquals(1, analysis.omittedPathCount());
    }

    @Test
    void analyzerProcessesTwentyFiveThousandFramesWithOneEligibilityCheckPerOwnedFrame() {
        List<TelemetryProjectRegistration> registrations = IntStream.range(0, 100)
                .mapToObj(ProfilerProjectAnalyzerTest::stressProject)
                .toList();
        ProfilerProjectOwnershipIndex.BuildResult ownership =
                ProfilerProjectOwnershipIndex.build(registrations, "hytale-1");

        List<SparkProfileWindow.Frame> frames = new ArrayList<>(25_000);
        frames.add(new SparkProfileWindow.Frame(
                -1, "Stress:0", "stress.owner.Root", "root", DESCRIPTOR, 0.0d));
        for (int index = 1; index < 25_000; index++) {
            frames.add(new SparkProfileWindow.Frame(
                    index - 1,
                    "<unknown>",
                    "foreign.Layer" + index,
                    "run",
                    DESCRIPTOR,
                    index == 24_999 ? 2.0d : 1.0d
            ));
        }

        AtomicInteger eligibilityChecks = new AtomicInteger();
        ProfilerProjectAnalyzer.Analysis analysis = ProfilerProjectAnalyzer.analyze(
                window(1, "1.0.0", frames),
                ownership.index(),
                ignored -> {
                    eligibilityChecks.incrementAndGet();
                    return true;
                },
                5
        );

        assertEquals(ProfilerProjectAnalyzer.Analysis.Status.COMPLETE, analysis.status());
        assertEquals(1, eligibilityChecks.get());
        ProfilerProjectWindow project = project(analysis.projects(), "stress-0");
        ProfilerProjectWindow.Path deepest = project.paths().stream()
                .filter(path -> path.firstExternalClassName().equals("foreign.Layer1"))
                .findFirst()
                .orElseThrow();
        assertEquals(8, deepest.representativePath().size());
        assertEquals("foreign.Layer7#run()V", deepest.representativePath().getLast());
    }

    private static List<ProfilerProjectWindow> analyze(SparkProfileWindow window,
                                                        ProfilerProjectOwnershipIndex.BuildResult owners,
                                                        Predicate<String> eligible) {
        return ProfilerProjectAnalyzer.analyze(window, owners.index(), eligible, 5).projects();
    }

    private static SparkProfileWindow nestedWindow() {
        return window(1, "1.0.0", List.of(
                new SparkProfileWindow.Frame(-1, "hytale", "com.hypixel.Hytale", "tick", DESCRIPTOR, 5.0d),
                new SparkProfileWindow.Frame(0, "mod-a", "example.a.Tick", "tick", DESCRIPTOR, 20.0d),
                new SparkProfileWindow.Frame(1, "hytale", "com.hypixel.Store", "tick", DESCRIPTOR, 40.0d),
                new SparkProfileWindow.Frame(2, "mod-b", "example.b.Callback", "run", DESCRIPTOR, 10.0d),
                new SparkProfileWindow.Frame(3, "hytale", "com.hypixel.Metric", "add", DESCRIPTOR, 30.0d)
        ));
    }

    private static SparkProfileWindow manyPathsWindow() {
        List<SparkProfileWindow.Frame> frames = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new SparkProfileWindow.Frame(
                        -1,
                        "hytale",
                        "example.a.Path" + index,
                        "run" + index,
                        DESCRIPTOR,
                        index + 1.0d
                ))
                .toList();
        return window(1, "1.0.0", frames);
    }

    private static SparkProfileWindow window(int key, String version) {
        return window(key, version, List.of(
                new SparkProfileWindow.Frame(-1, "mod-a", "example.a.Tick", "tick", DESCRIPTOR, 20.0d)
        ));
    }

    private static SparkProfileWindow window(int key, String version,
                                             List<SparkProfileWindow.Frame> frames) {
        return new SparkProfileWindow(
                version,
                key,
                1,
                1,
                frames.size(),
                frames.size(),
                frames.stream().mapToDouble(SparkProfileWindow.Frame::selfMilliseconds).sum(),
                List.of(new SparkProfileWindow.ThreadFrames("WorldThread - default", frames))
        );
    }

    private static ProfilerProjectOwnershipIndex.BuildResult owners() {
        return owners("1.0.0");
    }

    private static ProfilerProjectOwnershipIndex.BuildResult owners(String version) {
        return ProfilerProjectOwnershipIndex.build(List.of(
                project("mod-a", "example.a", "ModA", version),
                project("mod-b", "example.b", "ModB", version)
        ), "hytale-1");
    }

    private static TelemetryProjectRegistration stressProject(int index) {
        StringBuilder packagePrefixes = new StringBuilder();
        for (int prefix = 0; prefix < 32; prefix++) {
            if (prefix > 0) {
                packagePrefixes.append(',');
            }
            packagePrefixes.append("\"stress.").append(index).append(".p").append(prefix).append("\"");
        }
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"stress-" + index + "\","
                        + "\"projectVersion\":\"1.0.0\","
                        + "\"displayName\":\"stress-" + index + "\","
                        + "\"ownerPluginIdentifiers\":[\"Stress:" + index + "\"],"
                        + "\"packagePrefixes\":[" + packagePrefixes + "]}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Stress:" + index, "1.0.0", null);
    }

    private static TelemetryProjectRegistration project(String projectId,
                                                         String packagePrefix,
                                                         String pluginIdentifier,
                                                         String version) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\","
                        + "\"projectVersion\":\"" + version + "\","
                        + "\"displayName\":\"" + projectId + "\","
                        + "\"ownerPluginIdentifiers\":[\"" + pluginIdentifier + "\"],"
                        + "\"packagePrefixes\":[\"" + packagePrefix + "\"]}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, version, null);
    }

    private static TelemetryProjectRegistration project(String projectId,
                                                         String packagePrefix,
                                                         String pluginIdentifier) {
        return project(projectId, packagePrefix, pluginIdentifier, "1.0.0");
    }

    private static ProfilerProjectWindow project(List<ProfilerProjectWindow> projects,
                                                 String projectId) {
        return projects.stream()
                .filter(project -> project.projectId().equals(projectId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertPath(ProfilerProjectWindow project,
                                   TelemetryProfilerAttribution attribution,
                                   String ownedClass,
                                   String ownedMethod,
                                   double sampledMilliseconds) {
        assertTrue(project.paths().stream().anyMatch(path ->
                path.attribution() == attribution
                        && path.ownedClassName().equals(ownedClass)
                        && path.ownedMethodName().equals(ownedMethod)
                        && path.sampledMilliseconds() == sampledMilliseconds));
    }

    private static void assertPath(ProfilerProjectWindow project,
                                   TelemetryProfilerAttribution attribution,
                                   String ownedClass,
                                   String ownedMethod,
                                   String externalClass,
                                   String externalMethod,
                                   double sampledMilliseconds) {
        assertTrue(project.paths().stream().anyMatch(path ->
                path.attribution() == attribution
                        && path.ownedClassName().equals(ownedClass)
                        && path.ownedMethodName().equals(ownedMethod)
                        && path.firstExternalClassName().equals(externalClass)
                        && path.firstExternalMethodName().equals(externalMethod)
                        && path.sampledMilliseconds() == sampledMilliseconds));
    }
}
