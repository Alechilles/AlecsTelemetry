package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryDataMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyLowercaseCentralSettingsAndQueuesWithoutDeletingHytaleSessionLogs() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("server-id.txt"), "legacy-server");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json"), "{}");
        Files.writeString(legacyRoot.resolve("2026-06-16_01-49-25_a109ad9b.jsonl.gz"), "hytale-log");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("legacy-server", Files.readString(canonicalRoot.resolve("Settings").resolve("server-id.txt")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("2026-06-16_01-49-25_a109ad9b.jsonl.gz")));
        assertFalse(Files.exists(legacyRoot.resolve("Settings")));
        assertFalse(Files.exists(legacyRoot.resolve("Telemetry")));
        assertTrue(Files.isDirectory(legacyRoot));
    }

    @Test
    void migratesLegacyUppercaseServerRootTelemetryDirectory() throws Exception {
        Path serverRoot = tempDir.resolve("container");
        Path modsDir = serverRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = serverRoot.resolve("Telemetry");
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"flushIntervalSeconds\":99}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json"), "{}");
        Files.createDirectories(serverRoot.resolve("telemetry"));
        Files.writeString(serverRoot.resolve("telemetry").resolve("2026-06-20_12-58-00_server.jsonl.gz"), "hytale-log");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"flushIntervalSeconds\":99}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json")));
        assertFalse(Files.exists(legacyRoot.resolve("Settings")));
        assertFalse(Files.exists(legacyRoot.resolve("Telemetry")));
        assertTrue(Files.isRegularFile(serverRoot.resolve("telemetry").resolve("2026-06-20_12-58-00_server.jsonl.gz")));
    }

    @Test
    void removesLegacyFilesWhenCanonicalFilesAlreadyExist() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Files.createDirectories(canonicalRoot.resolve("Settings"));
        Files.createDirectories(legacyRoot.resolve("Settings"));
        Files.writeString(canonicalRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":true}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":true}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertFalse(Files.exists(legacyRoot.resolve("Settings").resolve("runtime.json")));
        assertFalse(Files.exists(legacyRoot.resolve("Settings")));
        assertFalse(Files.exists(legacyRoot));
    }

    @Test
    void migratesEmbeddedOwnerOverridesAndRemovesEmptyTelemetryFolders() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Cats!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-cats.json");
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertFalse(Files.exists(ownerRoot.resolve("Telemetry")));
        assertTrue(Files.isDirectory(ownerRoot));
    }

    @Test
    void removesEmbeddedOwnerOverrideWhenCanonicalOverrideAlreadyExists() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path canonicalOverride = canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Tamework!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Files.createDirectories(canonicalOverride.getParent());
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(canonicalOverride, "{\"enabled\":true}");
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":true}", Files.readString(canonicalOverride));
        assertFalse(Files.exists(ownerOverride));
        assertFalse(Files.exists(ownerRoot.resolve("Telemetry")));
        assertTrue(Files.isDirectory(ownerRoot));
    }

    @Test
    void keepsEmbeddedOwnerTelemetryFolderWhenItContainsNonSettingsData() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Tamework!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        Files.createDirectories(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        Files.writeString(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json"), "{}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertTrue(Files.isDirectory(ownerRoot.resolve("Telemetry").resolve("events")));
        assertEquals("{}", Files.readString(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json")));
        assertFalse(Files.exists(ownerOverride));
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
}
