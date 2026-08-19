package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerProjectOwnershipIndexTest {

    @Test
    void exactPluginIdentifierWinsBeforePackageFallback() {
        ProfilerProjectOwnershipIndex.BuildResult built = build(
                project("cats", "Alechilles:Cats", "com.shared"),
                project("tamework", "Alechilles:Tamework", "com.shared.tamework")
        );

        assertEquals("cats", built.index().resolve(
                " Alechilles:Cats ",
                "com.shared.tamework.Tick"
        ).orElseThrow().projectId());
    }

    @Test
    void longestCompletePrefixWinsAndEqualBestIsAmbiguous() {
        ProfilerProjectOwnershipIndex.BuildResult built = build(
                project("root", "Root:Mod", "com.example"),
                project("specific", "Specific:Mod", "com.example.feature")
        );

        assertEquals("specific", built.index().resolve(
                "<unknown>",
                "com.example.feature.Tick"
        ).orElseThrow().projectId());
        assertTrue(built.index().resolve("<unknown>", "com.exampleish.Tick").isEmpty());

        ProfilerProjectOwnershipIndex.BuildResult tied = build(
                project("a", "A:Mod", "com.tie"),
                project("b", "B:Mod", "com.tie")
        );
        assertTrue(tied.index().resolve("<unknown>", "com.tie.Tick").isEmpty());
    }

    @Test
    void profilerUsesOnlyFirstThirtyTwoNormalizedPrefixes() {
        ProfilerProjectOwnershipIndex.BuildResult built = ProfilerProjectOwnershipIndex.build(
                List.of(projectWithPrefixes(40)),
                "runtime-1"
        );

        assertEquals(8, built.truncatedPrefixCount());
        assertTrue(built.index().resolve("<unknown>", "example.p31.Tick").isPresent());
        assertTrue(built.index().resolve("<unknown>", "example.p32.Tick").isEmpty());
    }

    private static ProfilerProjectOwnershipIndex.BuildResult build(TelemetryProjectRegistration... projects) {
        return ProfilerProjectOwnershipIndex.build(List.of(projects), "runtime-1");
    }

    private static TelemetryProjectRegistration project(String projectId,
                                                         String pluginIdentifier,
                                                         String packagePrefix) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["%s"],
                  "packagePrefixes": ["%s"]
                }
                """.formatted(projectId, projectId, pluginIdentifier, packagePrefix),
                null
        );
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, "1.0.0", null);
    }

    private static TelemetryProjectRegistration projectWithPrefixes(int count) {
        List<String> prefixes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            prefixes.add("example.p" + index);
        }
        String packagePrefixes = prefixes.stream()
                .map(prefix -> "\"" + prefix + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "many-prefixes",
                  "displayName": "Many Prefixes",
                  "ownerPluginIdentifiers": ["Example:Many Prefixes"],
                  "packagePrefixes": [%s]
                }
                """.formatted(packagePrefixes),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Many Prefixes", "1.0.0", null);
    }
}
