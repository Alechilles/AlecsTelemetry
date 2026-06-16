package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
