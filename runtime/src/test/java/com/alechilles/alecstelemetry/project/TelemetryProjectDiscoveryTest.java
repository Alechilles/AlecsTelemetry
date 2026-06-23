package com.alechilles.alecstelemetry.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversProjectDescriptorFromFolder() throws Exception {
        Path modFolder = tempDir.resolve("Example Mod");
        Files.createDirectories(modFolder.resolve("Server").resolve("Telemetry"));
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
                modFolder.resolve("Server").resolve("Telemetry").resolve("project.json"),
                """
                {
                  "projectId": "example-mod"
                }
                """
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        assertEquals(1, result.consentProjects().size());
        assertEquals(1, result.loadedMods().size());
        assertTrue(result.skippedRegistrationWarnings().isEmpty());
        assertEquals("example-mod", result.projects().getFirst().projectId());
        assertEquals("example-mod", result.consentProjects().getFirst().projectId());
        assertEquals("Example:Example Mod", result.projects().getFirst().pluginIdentifier());
        assertTrue(result.projects().getFirst().packagePrefixes().contains("com.example.telemetry"));
    }

    @Test
    void discoversLegacyProjectDescriptorFromFolder() throws Exception {
        Path modFolder = tempDir.resolve("Legacy Mod");
        Files.createDirectories(modFolder.resolve("telemetry"));
        Files.writeString(
                modFolder.resolve("manifest.json"),
                """
                {
                  "Group": "Example",
                  "Name": "Legacy Mod",
                  "Version": "1.2.3",
                  "Main": "com.example.telemetry.LegacyMod"
                }
                """
        );
        Files.writeString(
                modFolder.resolve("telemetry").resolve("project.json"),
                """
                {
                  "projectId": "legacy-mod"
                }
                """
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        assertEquals("legacy-mod", result.projects().getFirst().projectId());
    }

    @Test
    void registersEmbeddedDescriptorsForCoordinatorRuntime() throws Exception {
        Path modFolder = tempDir.resolve("Embedded Mod");
        Files.createDirectories(modFolder.resolve("Server").resolve("Telemetry"));
        Files.writeString(
                modFolder.resolve("manifest.json"),
                """
                {
                  "Group": "Example",
                  "Name": "Embedded Mod",
                  "Version": "1.2.3",
                  "Main": "com.example.embedded.EmbeddedMod"
                }
                """
        );
        Files.writeString(
                modFolder.resolve("Server").resolve("Telemetry").resolve("project.json"),
                """
                {
                  "projectId": "embedded-mod",
                  "runtimeMode": "embedded"
                }
                """
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        assertEquals("embedded-mod", result.projects().getFirst().projectId());
        assertEquals("embedded", result.projects().getFirst().runtimeMode());
        assertEquals(1, result.consentProjects().size());
        assertEquals("embedded-mod", result.consentProjects().getFirst().projectId());
        assertEquals(1, result.loadedMods().size());
        assertTrue(result.skippedRegistrationWarnings().isEmpty());
    }

    @Test
    void discoversEmbeddedDescriptorFromAdditionalInstalledModsDirectory() throws Exception {
        Path saveModsDirectory = tempDir.resolve("data").resolve("pre-release").resolve("Saves")
                .resolve("Demo World").resolve("mods");
        Files.createDirectories(saveModsDirectory.resolve("Alechilles_Alec's Telemetry!"));
        Path installedModsDirectory = tempDir.resolve("UserData").resolve("Mods");
        Files.createDirectories(installedModsDirectory);
        writeJar(
                installedModsDirectory.resolve("Alec's Tamework! v2.14.1.jar"),
                """
                {
                  "Group": "Alechilles",
                  "Name": "Alec's Tamework!",
                  "Version": "2.14.1",
                  "Main": "com.alechilles.alecstamework.Tamework"
                }
                """,
                """
                {
                  "projectId": "alecs-tamework",
                  "displayName": "Alec's Tamework!",
                  "runtimeMode": "embedded"
                }
                """
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null)
                .discover(List.of(saveModsDirectory, installedModsDirectory));

        assertEquals(1, result.projects().size());
        assertEquals("alecs-tamework", result.projects().getFirst().projectId());
        assertEquals(1, result.consentProjects().size());
        assertEquals("alecs-tamework", result.consentProjects().getFirst().projectId());
        assertEquals("Alechilles:Alec's Tamework!", result.consentProjects().getFirst().pluginIdentifier());
        assertEquals(1, result.loadedMods().size());
    }

    private static void writeJar(@Nonnull Path jarPath, @Nonnull String manifest, @Nonnull String descriptor) throws Exception {
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            stream.putNextEntry(new ZipEntry("manifest.json"));
            stream.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("Server/Telemetry/project.json"));
            stream.write(descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.closeEntry();
        }
    }
}
