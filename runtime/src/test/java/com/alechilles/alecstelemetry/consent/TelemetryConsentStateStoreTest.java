package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void tracksReviewedProjectsByProjectAndPluginVersion() throws Exception {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration firstVersion = registration("example-mod", "Example:Example Mod", "1.0.0");
        TelemetryProjectRegistration secondVersion = registration("example-mod", "Example:Example Mod", "1.1.0");

        assertFalse(store.isReviewed(stateFile, firstVersion));
        assertEquals(List.of(firstVersion), store.unreviewedProjects(stateFile, List.of(firstVersion)));

        assertTrue(store.markReviewed(stateFile, firstVersion));
        assertTrue(Files.isRegularFile(stateFile));

        TelemetryConsentStateStore reloaded = new TelemetryConsentStateStore(null);
        assertTrue(reloaded.isReviewed(stateFile, firstVersion));
        assertFalse(reloaded.isReviewed(stateFile, secondVersion));
        assertEquals(List.of(secondVersion), reloaded.unreviewedProjects(stateFile, List.of(firstVersion, secondVersion)));
    }

    @Test
    void tracksShownNoticeWithoutMarkingProjectsReviewed() {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration firstVersion = registration("example-mod", "Example:Example Mod", "1.0.0");
        TelemetryProjectRegistration secondVersion = registration("example-mod", "Example:Example Mod", "1.1.0");

        assertFalse(store.isNoticeShown(stateFile, "player-a", List.of(firstVersion)));
        assertTrue(store.markNoticeShown(stateFile, "player-a", List.of(firstVersion)));

        TelemetryConsentStateStore reloaded = new TelemetryConsentStateStore(null);
        assertTrue(reloaded.isNoticeShown(stateFile, "player-a", List.of(firstVersion)));
        assertFalse(reloaded.isNoticeShown(stateFile, "player-b", List.of(firstVersion)));
        assertFalse(reloaded.isNoticeShown(stateFile, "player-a", List.of(secondVersion)));
        assertFalse(reloaded.isReviewed(stateFile, firstVersion));
        assertEquals(List.of(firstVersion), reloaded.unreviewedProjects(stateFile, List.of(firstVersion)));

        assertTrue(reloaded.markReviewed(stateFile, firstVersion));
        TelemetryConsentStateStore reviewed = new TelemetryConsentStateStore(null);
        assertTrue(reviewed.isNoticeShown(stateFile, "player-a", List.of(firstVersion)));
        assertTrue(reviewed.isReviewed(stateFile, firstVersion));
    }

    private static TelemetryProjectRegistration registration(String projectId,
                                                            String pluginIdentifier,
                                                            String pluginVersion) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["%s"],
                  "packagePrefixes": ["com.example.telemetry"]
                }
                """.formatted(projectId, pluginIdentifier),
                null
        );
        return new TelemetryProjectRegistration(
                descriptor,
                pluginIdentifier,
                pluginVersion,
                Path.of("Example Mod")
        );
    }
}
