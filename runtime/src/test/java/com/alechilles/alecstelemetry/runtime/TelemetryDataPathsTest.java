package com.alechilles.alecstelemetry.runtime;

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
    void descriptorDirectoriesIncludeSaveLocalAndGlobalUserModsDirectories() throws Exception {
        Path runtimeRoot = tempDir.resolve("data").resolve("pre-release").resolve("Saves")
                .resolve("Demo World").resolve("mods").resolve("Alechilles_Alec's Telemetry");
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

        assertEquals(2, descriptorDirectories.size());
        assertEquals(saveModsDirectory.toAbsolutePath().normalize(), descriptorDirectories.get(0));
        assertEquals(globalModsDirectory.toAbsolutePath().normalize(), descriptorDirectories.get(1));
    }

    @Test
    void sharedCoordinatorRootUsesGlobalUserDataTelemetryDirectory() throws Exception {
        Path hytaleRoot = tempDir.resolve("Hytale");
        Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Mods").resolve("Alechilles_Alec's Tamework!");
        Files.createDirectories(pluginDataDirectory);

        TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinatorDataDirectory(pluginDataDirectory);

        assertEquals(
                hytaleRoot.resolve("UserData").resolve("Telemetry").toAbsolutePath().normalize(),
                paths.runtimeRoot()
        );
        assertEquals(paths.runtimeRoot().resolve("Settings").resolve("runtime.json"), paths.settingsFile());
        assertEquals(paths.runtimeRoot().resolve("Settings").resolve("projects"), paths.projectSettingsDirectory());
        assertEquals(paths.runtimeRoot().resolve("Telemetry"), paths.telemetryRoot());
        assertEquals(hytaleRoot.resolve("UserData").resolve("Mods").toAbsolutePath().normalize(), paths.modsDirectory());
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
