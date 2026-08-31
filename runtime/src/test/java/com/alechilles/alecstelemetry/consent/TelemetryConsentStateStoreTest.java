package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void recordsConsentFunnelEventsOncePerViewerProjectVersionAndEvent() {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration firstVersion = registration("example-mod", "Example:Example Mod", "1.0.0");
        TelemetryProjectRegistration secondVersion = registration("example-mod", "Example:Example Mod", "1.1.0");

        assertTrue(store.markFunnelEventRecorded(stateFile, "player-a", "prompt_shown", firstVersion));
        assertFalse(store.markFunnelEventRecorded(stateFile, "player-a", "prompt_shown", firstVersion));
        assertTrue(store.markFunnelEventRecorded(stateFile, "player-a", "ui_opened", firstVersion));
        assertTrue(store.markFunnelEventRecorded(stateFile, "player-b", "prompt_shown", firstVersion));
        assertTrue(store.markFunnelEventRecorded(stateFile, "player-a", "prompt_shown", secondVersion));

        TelemetryConsentStateStore reloaded = new TelemetryConsentStateStore(null);
        assertFalse(reloaded.markFunnelEventRecorded(stateFile, "player-a", "prompt_shown", firstVersion));
        assertFalse(reloaded.isReviewed(stateFile, firstVersion));
    }

    @Test
    void detectsReviewedCapabilityExpansionsWithoutAlertingOnRemovals() throws Exception {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration statsOnly = registrationWithCapabilities(false, false, true);
        TelemetryProjectRegistration expanded = registrationWithCapabilities(true, true, true);
        TelemetryProjectRegistration removed = registrationWithCapabilities(false, false, true);

        assertTrue(store.markReviewed(stateFile, statsOnly));
        assertEquals(List.of("error", "usage"), store.addedSupportedCategories(stateFile, expanded));
        assertFalse(store.isReviewed(stateFile, expanded));
        assertEquals(List.of(expanded), store.unreviewedProjects(stateFile, List.of(expanded)));

        assertTrue(store.markReviewed(stateFile, expanded));
        assertEquals(List.of(), store.addedSupportedCategories(stateFile, expanded));
        assertTrue(store.isReviewed(stateFile, expanded));
        assertEquals(List.of(), store.addedSupportedCategories(stateFile, removed));
    }

    @Test
    void requiresDiagnosticsReviewForModernReviewedProjectsAndDisablesItUntilReview() throws Exception {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration withoutDiagnostics = registrationWithCapabilities(false, false, true);
        TelemetryProjectRegistration withDiagnostics = registrationWithCapabilitiesAndDiagnostics(
                "1.0.0",
                false,
                false,
                true,
                true
        );

        assertTrue(store.markReviewed(stateFile, withoutDiagnostics));
        assertEquals(List.of("diagnostics"), store.addedSupportedCategories(stateFile, withDiagnostics));
        assertFalse(store.isReviewed(stateFile, withDiagnostics));

        Path overrideFile = tempDir.resolve("Settings").resolve("projects").resolve("example-mod.json");
        TelemetryProjectOverrideStore overrideStore = new TelemetryProjectOverrideStore(null);
        assertTrue(overrideStore.disableCategories(
                overrideFile,
                store.addedSupportedCategories(stateFile, withDiagnostics)
        ));
        TelemetryProjectOverride protectiveOverride = overrideStore.load(overrideFile);
        assertNotNull(protectiveOverride);
        assertEquals(Boolean.FALSE, protectiveOverride.diagnostics().enabled());
    }

    @Test
    void requiresDiagnosticsReviewForLegacyReviewedEntriesButKeepsOtherLegacyCategoriesCompatible() throws Exception {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, """
                {
                  "version": 1,
                  "reviewedProjects": [
                    {
                      "projectId": "example-mod",
                      "pluginIdentifier": "Example:Example Mod",
                      "pluginVersion": "1.0.0"
                    }
                  ]
                }
                """);
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration expanded = registrationWithCapabilitiesAndDiagnostics(
                "1.0.0",
                true,
                true,
                true,
                true
        );

        assertFalse(store.isReviewed(stateFile, expanded));
        assertEquals(List.of("diagnostics"), store.addedSupportedCategories(stateFile, expanded));
        assertEquals(List.of(expanded), store.unreviewedProjects(stateFile, List.of(expanded)));

        Path overrideFile = tempDir.resolve("Settings").resolve("projects").resolve("example-mod.json");
        TelemetryProjectOverrideStore overrideStore = new TelemetryProjectOverrideStore(null);
        assertTrue(overrideStore.disableCategories(
                overrideFile,
                store.addedSupportedCategories(stateFile, expanded)
        ));
        TelemetryProjectOverride protectiveOverride = overrideStore.load(overrideFile);
        assertNotNull(protectiveOverride);
        assertEquals(Boolean.FALSE, protectiveOverride.diagnostics().enabled());
    }

    @Test
    void fallbackBaselineUsesMostRecentlyReviewedVersion() {
        Path stateFile = tempDir.resolve("Settings").resolve("consent-reviewed-projects.json");
        TelemetryConsentStateStore store = new TelemetryConsentStateStore(null);
        TelemetryProjectRegistration versionOne = registrationWithCapabilities("1.0.0", false, false, true);
        TelemetryProjectRegistration versionTwo = registrationWithCapabilities("2.0.0", true, false, true);
        TelemetryProjectRegistration versionThree = registrationWithCapabilities("3.0.0", true, true, true);

        assertTrue(store.markReviewed(stateFile, versionOne));
        assertTrue(store.markReviewed(stateFile, versionTwo));
        assertTrue(store.markReviewed(stateFile, versionOne));

        assertEquals(List.of("error", "usage"),
                store.addedSupportedCategories(stateFile, versionThree));
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

    private static TelemetryProjectRegistration registrationWithCapabilities(boolean error,
                                                                              boolean usage,
                                                                              boolean stats) {
        return registrationWithCapabilities("1.0.0", error, usage, stats);
    }

    private static TelemetryProjectRegistration registrationWithCapabilities(String version,
                                                                              boolean error,
                                                                              boolean usage,
                                                                              boolean stats) {
        return registrationWithCapabilitiesAndDiagnostics(version, error, usage, stats, false);
    }

    private static TelemetryProjectRegistration registrationWithCapabilitiesAndDiagnostics(String version,
                                                                                             boolean error,
                                                                                             boolean usage,
                                                                                             boolean stats,
                                                                                             boolean diagnostics) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "projectVersion": "%s",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "telemetry": {
                    "diagnostics": { "supported": %s, "defaultEnabled": true },
                    "events": {
                      "errors": { "supported": %s }
                    },
                    "usage": { "supported": %s },
                    "stats": { "supported": %s, "allowedEvents": ["heartbeat"] }
                  }
                }
                """.formatted(version, diagnostics, error, usage, stats),
                null
        );
        return new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                version,
                Path.of("Example Mod")
        );
    }
}
