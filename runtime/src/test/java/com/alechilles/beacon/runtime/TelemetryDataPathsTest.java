package com.alechilles.beacon.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryDataPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void descriptorDirectoriesIncludeSaveAndInstalledModsDirectories() throws Exception {
        Path runtimeRoot = tempDir.resolve("UserData").resolve("Saves")
                .resolve("Demo World").resolve("mods").resolve("Alechilles_Beacon");
        Files.createDirectories(runtimeRoot);
        Path saveModsDirectory = runtimeRoot.getParent();
        Path globalModsDirectory = tempDir.resolve("UserData").resolve("Mods");
        Files.createDirectories(globalModsDirectory);
        TelemetryDataPaths paths = new TelemetryDataPaths(
                runtimeRoot,
                runtimeRoot.resolve("Settings").resolve("runtime.json"),
                runtimeRoot.resolve("Settings").resolve("projects"),
                runtimeRoot.resolve("Telemetry"),
                runtimeRoot.resolve("Telemetry").resolve("crash-reports"),
                runtimeRoot.resolve("Telemetry").resolve("events"),
                saveModsDirectory
        );

        List<Path> descriptorDirectories = paths.descriptorDirectories();

        assertEquals(
                List.of(
                        saveModsDirectory.toAbsolutePath().normalize(),
                        globalModsDirectory.toAbsolutePath().normalize()
                ),
                descriptorDirectories
        );
    }

    @Test
    void sharedCoordinatorRootUsesBeaconPluginDataDirectoryInsideSaveMods() throws Exception {
        Path hytaleRoot = tempDir.resolve("Hytale");
        Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
                .resolve("Demo World")
                .resolve("mods")
                .resolve("Alechilles_Alec's Tamework!");
        Files.createDirectories(pluginDataDirectory);

        TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinatorDataDirectory(pluginDataDirectory);

        Path saveModsDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
                .resolve("Demo World")
                .resolve("mods")
                .toAbsolutePath()
                .normalize();
        Path expectedRoot = saveModsDirectory.resolve("Alechilles_Beacon").toAbsolutePath().normalize();
        assertEquals(expectedRoot, paths.runtimeRoot());
        assertEquals(expectedRoot.resolve("Settings").resolve("runtime.json"), paths.settingsFile());
        assertEquals(expectedRoot.resolve("Settings").resolve("projects"), paths.projectSettingsDirectory());
        assertEquals(expectedRoot.resolve("Telemetry"), paths.telemetryRoot());
        assertEquals(saveModsDirectory, paths.modsDirectory());
    }

    @Test
    void sharedCoordinatorExposesAllKnownLegacyCentralRootsForMigration() throws Exception {
        Path hytaleRoot = tempDir.resolve("Hytale");
        Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
                .resolve("Demo World")
                .resolve("mods")
                .resolve("Alechilles_Alec's Tamework!");
        Files.createDirectories(pluginDataDirectory);
        Path lowerSaveRoot = pluginDataDirectory.getParent().getParent().resolve("telemetry");
        Path upperSaveRoot = pluginDataDirectory.getParent().getParent().resolve("Telemetry");
        Files.createDirectories(lowerSaveRoot);
        Files.createDirectories(upperSaveRoot);

        TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinatorDataDirectory(pluginDataDirectory);

        List<Path> expectedRoots = new java.util.ArrayList<>(List.of(
                hytaleRoot.resolve("UserData").resolve("Saves").resolve("Demo World").resolve("mods").resolve("Alechilles_Alec's Telemetry!").toAbsolutePath().normalize(),
                hytaleRoot.resolve("UserData").resolve("Saves").resolve("Demo World").resolve("mods").resolve("Alechilles_Alec's Telemetry").toAbsolutePath().normalize(),
                lowerSaveRoot.toAbsolutePath().normalize(),
                upperSaveRoot.toAbsolutePath().normalize()
        ));
        if (Files.isSameFile(lowerSaveRoot, upperSaveRoot)) {
            expectedRoots.remove(upperSaveRoot.toAbsolutePath().normalize());
        }

        assertEquals(
                expectedRoots,
                paths.legacyRuntimeRoots()
        );
    }

    @Test
    void defaultsManualReportRuntimeSettings() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null);

        assertTrue(settings.manualReports().enabled());
        assertFalse(settings.manualReports().manualReviewRequired());
        assertTrue(settings.manualReports().allowContact());
        assertTrue(settings.manualReports().allowResolutionUpdates());
        assertTrue(settings.manualReports().allowCurrentServerLog());
        assertTrue(settings.manualReports().allowPreviousServerLog());
        assertTrue(settings.manualReports().allowLoadedModList());
        assertTrue(settings.manualReports().allowDiagnostics());
        assertEquals(262144, settings.manualReports().maxLogAttachmentBytes());
        assertEquals(200, settings.manualReports().maxPendingManualReportsPerProject());
        assertEquals("https://telemetry.alecsmods.com/ingest/report", settings.manualReports().hostedReportIngestEndpoint());
    }

    @Test
    void exposesManualReportPaths() {
        Path runtimeRoot = tempDir.resolve("runtime");
        TelemetryDataPaths paths = new TelemetryDataPaths(
                runtimeRoot,
                runtimeRoot.resolve("Settings").resolve("runtime.json"),
                runtimeRoot.resolve("Settings").resolve("projects"),
                runtimeRoot.resolve("Telemetry"),
                runtimeRoot.resolve("Telemetry").resolve("crash-reports"),
                runtimeRoot.resolve("Telemetry").resolve("events"),
                null
        );

        assertEquals(
                runtimeRoot.resolve("Telemetry").resolve("manual-reports").resolve("example-mod").resolve("pending"),
                paths.pendingManualReportsDirectory("example-mod")
        );
        assertEquals(
                runtimeRoot.resolve("Telemetry").resolve("manual-reports").resolve("example-mod").resolve("review"),
                paths.reviewManualReportsDirectory("example-mod")
        );
        assertEquals(
                runtimeRoot.resolve("Telemetry").resolve("manual-reports").resolve("submitted-reports.jsonl"),
                paths.submittedManualReportsLog()
        );
        assertEquals(
                runtimeRoot.resolve("Telemetry").resolve("manual-reports").resolve("receipts.json"),
                paths.manualReportReceiptsFile()
        );
    }

}
