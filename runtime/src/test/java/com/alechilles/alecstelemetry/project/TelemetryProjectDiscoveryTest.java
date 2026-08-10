package com.alechilles.alecstelemetry.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    @Test
    void discoversPassiveDescriptorFromNamespacedArchive() throws Exception {
        Path hostJar = tempDir.resolve("Alec's Tamework! v3.0.0.jar");
        writeJar(
                hostJar,
                """
                {
                  "Group": "Example",
                  "Name": "Tamework",
                  "Version": "3.0.0",
                  "Main": "com.example.tamework.Tamework"
                }
                """,
                Map.of(
                        "META-INF/alecs-telemetry/projects/creditor.json",
                        passiveDescriptor("creditor", "1.4.0", "Creditor")
                )
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        TelemetryProjectRegistration project = result.projects().getFirst();
        assertEquals("creditor", project.projectId());
        assertEquals("1.4.0", project.descriptor().projectVersion());
        assertEquals("Example:Tamework", project.hostPluginIdentifier());
        assertTrue(project.isPassiveDescriptor());
        assertFalse(project.descriptor().capture().supported());
        assertFalse(project.descriptor().events().errors().supported());
        assertFalse(project.descriptor().events().lifecycle().supported());
        assertFalse(project.descriptor().performance().supported());
        assertFalse(project.descriptor().usage().supported());
        assertTrue(project.descriptor().stats().supported());
        assertEquals(List.of("heartbeat"), project.descriptor().stats().allowedEvents());
        assertFalse(project.descriptor().events().breadcrumbs().supported());
    }

    @Test
    void electsHighestPassiveLogicalVersion() throws Exception {
        writeJar(
                tempDir.resolve("host-old.jar"),
                hostManifest("Example", "Old Host", "3.0.0"),
                Map.of(
                        "META-INF/alecs-telemetry/projects/creditor.json",
                        passiveDescriptor("creditor", "1.3.0", "Creditor")
                )
        );
        writeJar(
                tempDir.resolve("host-new.jar"),
                hostManifest("Example", "New Host", "3.0.0"),
                Map.of(
                        "META-INF/alecs-telemetry/projects/creditor.json",
                        passiveDescriptor("creditor", "1.4.0", "Creditor")
                )
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(1, result.projects().size());
        assertEquals("1.4.0", result.projects().getFirst().descriptor().projectVersion());
        assertEquals("Example:New Host", result.projects().getFirst().hostPluginIdentifier());
    }

    @Test
    void skipsMalformedPassiveDescriptorWithoutAffectingValidProject() throws Exception {
        writeJar(
                tempDir.resolve("host.jar"),
                hostManifest("Example", "Host", "3.0.0"),
                Map.of(
                        "META-INF/alecs-telemetry/projects/valid.json",
                        passiveDescriptor("valid", "1.0.0", "Valid"),
                        "META-INF/alecs-telemetry/projects/incomplete.json",
                        """
                        {
                          "projectId": "incomplete",
                          "displayName": "Incomplete",
                          "ownerPluginIdentifiers": ["Example:Library"]
                        }
                        """
                )
        );

        TelemetryProjectDiscovery.DiscoveryResult result = new TelemetryProjectDiscovery(null).discover(tempDir);

        assertEquals(List.of("valid"), result.projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertEquals(1, result.skippedRegistrationWarnings().size());
        assertTrue(result.skippedRegistrationWarnings().getFirst().contains("incomplete.json"));
    }

    private static void writeJar(@Nonnull Path jarPath, @Nonnull String manifest, @Nonnull String descriptor) throws Exception {
        writeJar(jarPath, manifest, Map.of("Server/Telemetry/project.json", descriptor));
    }

    private static void writeJar(@Nonnull Path jarPath,
                                 @Nonnull String manifest,
                                 @Nonnull Map<String, String> entries) throws Exception {
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            stream.putNextEntry(new ZipEntry("manifest.json"));
            stream.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.closeEntry();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                stream.putNextEntry(new ZipEntry(entry.getKey()));
                stream.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.closeEntry();
            }
        }
    }

    private static String hostManifest(String group, String name, String version) {
        return """
                {
                  "Group": "%s",
                  "Name": "%s",
                  "Version": "%s",
                  "Main": "example.host.Host"
                }
                """.formatted(group, name, version);
    }

    private static String passiveDescriptor(String projectId, String projectVersion, String displayName) {
        return """
                {
                  "schemaVersion": 1,
                  "projectId": "%s",
                  "projectVersion": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:Library"],
                  "telemetry": {
                    "stats": {
                      "supported": true,
                      "allowedEvents": ["heartbeat"]
                    }
                  }
                }
                """.formatted(projectId, projectVersion, displayName);
    }
}
