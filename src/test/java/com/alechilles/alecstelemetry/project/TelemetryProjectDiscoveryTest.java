package com.alechilles.alecstelemetry.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversProjectDescriptorFromFolder() throws Exception {
        Path modFolder = tempDir.resolve("Example Mod");
        Files.createDirectories(modFolder.resolve("telemetry"));
        Files.writeString(
                modFolder.resolve("manifest.json"),
                """
                {
                  "Group": "Example",
                  "Name": "Example Mod",
                  "Version": "1.2.3",
                  "Main": "com.example.telemetry.ExampleMod"
                }
                """
        );
        Files.writeString(
                modFolder.resolve("telemetry").resolve("project.json"),
                """
                {
                  "projectId": "example-mod"
                }
                """
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        assertEquals(1, result.loadedMods().size());
        assertEquals("example-mod", result.projects().getFirst().projectId());
        assertEquals("Example:Example Mod", result.projects().getFirst().pluginIdentifier());
        assertTrue(result.projects().getFirst().packagePrefixes().contains("com.example.telemetry"));
    }
}
