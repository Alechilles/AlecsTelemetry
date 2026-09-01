package com.alechilles.beacon.runtime;

import com.alechilles.beacon.consent.TelemetryConsentStateStore;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryDataMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesKnownRuntimeStateFromLowercaseSaveRootAndPreservesSources() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");

        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("crash-reports").resolve("alecs-cats").resolve("pending"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("pending"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("review"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("rejected"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("unrelated"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("consent-reviewed-projects.json"), "{\"projects\":[]}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("server-identity.json"), "{\"serverId\":\"legacy\"}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("server-id.txt"), "legacy-server");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("unrelated.json"), "do-not-copy");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("crash-reports").resolve("alecs-cats").resolve("pending").resolve("crash.json"), "crash");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json"), "event");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("pending").resolve("pending.json"), "pending");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("review").resolve("review.json"), "review");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("rejected").resolve("rejected.json"), "rejected");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("submitted-reports.jsonl"), "submitted\n");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("manual-reports").resolve("receipts.json"), "{\"reports\":[]}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("unrelated").resolve("unrelated.json"), "do-not-copy");
        Files.writeString(legacyRoot.resolve("hytale-session.jsonl.gz"), "hytale-log");

        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"projects\":[]}", Files.readString(canonicalRoot.resolve("Settings").resolve("consent-reviewed-projects.json")));
        assertEquals("{\"serverId\":\"legacy\"}", Files.readString(canonicalRoot.resolve("Settings").resolve("server-identity.json")));
        assertEquals("legacy-server", Files.readString(canonicalRoot.resolve("Settings").resolve("server-id.txt")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertEquals("crash", Files.readString(canonicalRoot.resolve("Telemetry").resolve("crash-reports").resolve("alecs-cats").resolve("pending").resolve("crash.json")));
        assertEquals("event", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json")));
        assertEquals("pending", Files.readString(canonicalRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("pending").resolve("pending.json")));
        assertEquals("review", Files.readString(canonicalRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("review").resolve("review.json")));
        assertEquals("rejected", Files.readString(canonicalRoot.resolve("Telemetry").resolve("manual-reports").resolve("alecs-cats").resolve("rejected").resolve("rejected.json")));
        assertEquals("submitted\n", Files.readString(canonicalRoot.resolve("Telemetry").resolve("manual-reports").resolve("submitted-reports.jsonl")));
        assertEquals("{\"reports\":[]}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("manual-reports").resolve("receipts.json")));

        assertTrue(Files.isRegularFile(legacyRoot.resolve("Settings").resolve("runtime.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("hytale-session.jsonl.gz")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("Settings").resolve("unrelated.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("Telemetry").resolve("unrelated").resolve("unrelated.json")));
        assertFalse(Files.exists(canonicalRoot.resolve("Settings").resolve("unrelated.json")));
        assertFalse(Files.exists(canonicalRoot.resolve("Telemetry").resolve("unrelated")));
        assertTrue(Files.isDirectory(legacyRoot));
    }

    @Test
    void copiesKnownStateFromUppercaseSaveRootWithoutRemovingSessionFiles() throws Exception {
        Path saveRoot = tempDir.resolve("container");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("Telemetry");
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"flushIntervalSeconds\":99}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json"), "{}");
        Files.writeString(legacyRoot.resolve("2026-06-20_12-58-00_server.jsonl.gz"), "hytale-log");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals("{\"flushIntervalSeconds\":99}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("Settings").resolve("runtime.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("2026-06-20_12-58-00_server.jsonl.gz")));
    }

    @Test
    void keepsBeaconFilesAndLegacyFilesWhenBothPathsExist() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Path canonicalSettings = canonicalRoot.resolve("Settings").resolve("runtime.json");
        Path legacySettings = legacyRoot.resolve("Settings").resolve("runtime.json");
        Path canonicalEvent = canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json");
        Path legacyEvent = legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json");
        Files.createDirectories(canonicalSettings.getParent());
        Files.createDirectories(legacySettings.getParent());
        Files.createDirectories(canonicalEvent.getParent());
        Files.createDirectories(legacyEvent.getParent());
        Files.writeString(canonicalSettings, "{\"enabled\":true}");
        Files.writeString(legacySettings, "{\"enabled\":false}");
        Files.writeString(canonicalEvent, "beacon-event");
        Files.writeString(legacyEvent, "legacy-event");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals("{\"enabled\":true}", Files.readString(canonicalSettings));
        assertEquals("{\"enabled\":false}", Files.readString(legacySettings));
        assertEquals("beacon-event", Files.readString(canonicalEvent));
        assertEquals("legacy-event", Files.readString(legacyEvent));
        assertTrue(Files.isDirectory(legacyRoot));
    }

    @Test
    void copiesPreviousNoBangPluginRootIntoBeaconRootWithoutRemovingSource() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Tamework!");
        Path legacyRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Files.createDirectories(ownerRoot);
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json"), "{}");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals(canonicalRoot.toAbsolutePath().normalize(), paths(canonicalRoot, modsDir).runtimeRoot());
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("Settings").resolve("runtime.json")));
        assertTrue(Files.isDirectory(legacyRoot));
    }

    @Test
    void copiesEmbeddedOwnerOverridesButLeavesOwnerStorageUntouched() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Cats!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-cats.json");
        Files.createDirectories(ownerOverride.getParent());
        Files.createDirectories(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats"));
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        Files.writeString(ownerRoot.resolve("Telemetry").resolve("Settings").resolve("runtime.json"), "owner-setting");
        Files.writeString(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("event.json"), "owner-event");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertTrue(Files.isRegularFile(ownerOverride));
        assertTrue(Files.isRegularFile(ownerRoot.resolve("Telemetry").resolve("Settings").resolve("runtime.json")));
        assertTrue(Files.isRegularFile(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("event.json")));
        assertFalse(Files.exists(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertFalse(Files.exists(canonicalRoot.resolve("Telemetry").resolve("events")));
    }

    @Test
    void keepsOwnerOverrideWhenBeaconOverrideAlreadyExists() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path canonicalOverride = canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Tamework!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Files.createDirectories(canonicalOverride.getParent());
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(canonicalOverride, "{\"enabled\":true}");
        Files.writeString(ownerOverride, "{\"enabled\":false}");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals("{\"enabled\":true}", Files.readString(canonicalOverride));
        assertEquals("{\"enabled\":false}", Files.readString(ownerOverride));
    }

    @Test
    void continuesAfterAFileCopyCannotCreateItsDestination() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Path blockedDestination = canonicalRoot.resolve("Telemetry").resolve("crash-reports").resolve("blocked-project");
        Path blockedSource = legacyRoot.resolve("Telemetry").resolve("crash-reports").resolve("blocked-project").resolve("pending").resolve("crash.json");
        Path successfulSource = legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json");
        Files.createDirectories(blockedSource.getParent());
        Files.createDirectories(successfulSource.getParent());
        Files.createDirectories(blockedDestination.getParent());
        Files.writeString(blockedDestination, "not-a-directory");
        Files.writeString(blockedSource, "blocked");
        Files.writeString(successfulSource, "event");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertTrue(Files.isRegularFile(blockedDestination));
        assertTrue(Files.isRegularFile(blockedSource));
        assertEquals("event", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json")));
    }

    @Test
    void rejectsBeaconDestinationSymlinkWithoutWritingOutsideBeacon() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Path source = legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats")
                .resolve("pending").resolve("event.json");
        Path destination = canonicalRoot.resolve("Telemetry").resolve("events");
        Path outsideBeacon = tempDir.resolve("outside-beacon");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "legacy-event");
        Files.createDirectories(destination.getParent());
        Files.createDirectories(outsideBeacon);
        try {
            Files.createSymbolicLink(destination, outsideBeacon);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            // Windows hosts without symlink privileges skip this test; the production guard remains required.
            Assumptions.assumeTrue(false, "Destination symlinks are unavailable on this filesystem: " + ex.getMessage());
        }

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertTrue(Files.isSymbolicLink(destination));
        assertTrue(Files.isRegularFile(source));
        assertFalse(Files.exists(outsideBeacon.resolve("alecs-cats").resolve("pending").resolve("event.json")));
    }

    @Test
    void ignoresWrongTypeLegacyDirectoryNodesAndLeavesBeaconPathsUsable() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path lowerLegacyRoot = saveRoot.resolve("telemetry");
        Path pluginLegacyRoot = modsDir.resolve("Alechilles_Alec's Telemetry");

        Files.createDirectories(pluginLegacyRoot.resolve("Settings"));
        Files.createDirectories(pluginLegacyRoot.resolve("Telemetry"));
        Files.writeString(pluginLegacyRoot.resolve("Settings").resolve("projects"), "not-a-directory");
        Files.writeString(pluginLegacyRoot.resolve("Telemetry").resolve("crash-reports"), "not-a-directory");
        Files.writeString(pluginLegacyRoot.resolve("Telemetry").resolve("events"), "not-a-directory");
        Files.writeString(pluginLegacyRoot.resolve("Telemetry").resolve("manual-reports"), "not-a-directory");
        Files.createDirectories(lowerLegacyRoot);
        Files.writeString(lowerLegacyRoot.resolve("Settings"), "not-a-directory");
        Files.writeString(lowerLegacyRoot.resolve("Telemetry"), "not-a-directory");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertFalse(Files.exists(canonicalRoot.resolve("Settings")));
        assertFalse(Files.exists(canonicalRoot.resolve("Telemetry")));
        assertTrue(Files.isRegularFile(pluginLegacyRoot.resolve("Settings").resolve("projects")));
        assertTrue(Files.isRegularFile(pluginLegacyRoot.resolve("Telemetry").resolve("crash-reports")));
        assertTrue(Files.isRegularFile(lowerLegacyRoot.resolve("Settings")));
        assertTrue(Files.isRegularFile(lowerLegacyRoot.resolve("Telemetry")));

        Files.createDirectories(canonicalRoot.resolve("Settings"));
        Files.createDirectories(canonicalRoot.resolve("Telemetry"));
        assertTrue(Files.isDirectory(canonicalRoot.resolve("Settings")));
        assertTrue(Files.isDirectory(canonicalRoot.resolve("Telemetry")));
    }

    @Test
    void renamesLegacySelfOverrideAndConsentIdentityIntoBeaconWithoutRemovingSources() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Path legacyOverride = legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-telemetry.json");
        Path legacyConsent = legacyRoot.resolve("Settings").resolve("consent-reviewed-projects.json");
        Files.createDirectories(legacyOverride.getParent());
        Files.writeString(legacyOverride, "{\"enabled\":false}");
        Files.writeString(legacyConsent, """
                {
                  "version": 1,
                  "reviewedProjects": [
                    {
                      "projectId": "alecs-telemetry",
                      "pluginIdentifier": "Alechilles:Alec's Telemetry!",
                      "pluginVersion": "2.0.0",
                      "supportedCategories": []
                    }
                  ]
                }
                """);

        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        Path beaconOverride = canonicalRoot.resolve("Settings").resolve("projects").resolve("beacon.json");
        assertEquals("{\"enabled\":false}", Files.readString(beaconOverride));
        assertFalse(Files.exists(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-telemetry.json")));
        assertTrue(Files.isRegularFile(legacyOverride));
        assertTrue(Files.isRegularFile(legacyConsent));

        TelemetryProjectRegistration beacon = registration("beacon", "Alechilles:Beacon", "2.0.0");
        assertTrue(new TelemetryConsentStateStore(null).isReviewed(paths.consentStateFile(), beacon));
        assertNotNull(new TelemetryProjectOverrideStore(null).load(beaconOverride));
        assertEquals(
                Boolean.FALSE,
                new TelemetryProjectOverrideStore(null).load(beaconOverride).enabled()
        );
    }

    @Test
    void keepsBeaconSelfOverrideWhenLegacySelfOverrideConflicts() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Beacon");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Path beaconOverride = canonicalRoot.resolve("Settings").resolve("projects").resolve("beacon.json");
        Path legacyOverride = legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-telemetry.json");
        Files.createDirectories(beaconOverride.getParent());
        Files.createDirectories(legacyOverride.getParent());
        Files.writeString(beaconOverride, "{\"enabled\":true}");
        Files.writeString(legacyOverride, "{\"enabled\":false}");

        TelemetryDataMigrator.migrate(paths(canonicalRoot, modsDir), null);

        assertEquals("{\"enabled\":true}", Files.readString(beaconOverride));
        assertEquals("{\"enabled\":false}", Files.readString(legacyOverride));
    }

    private static TelemetryDataPaths paths(Path root, Path modsDir) {
        Path settingsRoot = root.resolve("Settings");
        Path telemetryRoot = root.resolve("Telemetry");
        return new TelemetryDataPaths(
                root,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                modsDir
        );
    }

    private static TelemetryProjectRegistration registration(String projectId,
                                                              String pluginIdentifier,
                                                              String pluginVersion) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\",\"displayName\":\"Beacon\"}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, pluginVersion, Path.of("Beacon.jar"));
    }
}
