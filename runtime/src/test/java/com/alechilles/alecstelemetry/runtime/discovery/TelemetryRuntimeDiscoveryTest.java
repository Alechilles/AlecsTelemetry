package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void filtersDescriptorsToLoadedEnabledMods() {
        List<TelemetryProjectRegistration> filtered = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(
                        registration("active-project", "Example:Active Mod", "Active Mod"),
                        registration("inactive-project", "Example:Inactive Mod", "Inactive Mod")
                ),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Active Mod", "1.0.0"))
        );

        assertEquals(1, filtered.size());
        assertEquals("active-project", filtered.getFirst().projectId());
    }

    @Test
    void discoverActiveAppliesCentralOverrideToActiveProject() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Active Mod",
                "Example",
                "Active Mod",
                "1.0.0",
                """
                {
                  "projectId": "active-project",
                  "displayName": "Active Mod",
                  "packagePrefixes": ["example.active"]
                }
                """
        );
        TelemetryDataPaths dataPaths = dataPaths(modsDirectory);
        Files.createDirectories(dataPaths.projectSettingsDirectory());
        Files.writeString(
                dataPaths.projectOverrideFile("active-project"),
                """
                {
                  "enabled": false
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Active Mod", "1.0.0")
                ))
        );

        assertEquals(1, result.projects().size());
        assertFalse(result.projects().getFirst().isEnabled());
        assertFalse(result.consentProjects().getFirst().isEnabled());
    }

    @Test
    void discoverActiveRemovesUnsupportedCentralConsentOverrides() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Stats Mod",
                "Example",
                "Stats Mod",
                "1.0.0",
                """
                {
                  "projectId": "stats-project",
                  "displayName": "Stats Mod",
                  "packagePrefixes": ["example.stats"],
                  "capture": {
                    "uncaughtExceptions": false,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  },
                  "events": {
                    "errors": { "enabled": false },
                    "lifecycle": { "enabled": false },
                    "breadcrumbs": { "enabled": false }
                  },
                  "performance": { "enabled": false },
                  "usage": { "enabled": false },
                  "stats": { "enabled": true }
                }
                """
        );
        TelemetryDataPaths dataPaths = dataPaths(modsDirectory);
        Files.createDirectories(dataPaths.projectSettingsDirectory());
        Files.writeString(
                dataPaths.projectOverrideFile("stats-project"),
                """
                {
                  "enabled": true,
                  "capture": {
                    "uncaughtExceptions": false,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  },
                  "events": {
                    "errors": { "enabled": false },
                    "lifecycle": { "enabled": false },
                    "breadcrumbs": { "enabled": false }
                  },
                  "performance": { "enabled": false },
                  "usage": { "enabled": false },
                  "stats": { "enabled": false }
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Stats Mod", "1.0.0")
                ))
        );

        assertEquals(1, result.consentProjects().size());
        assertFalse(result.consentProjects().getFirst().stats().enabled());
        String cleaned = Files.readString(dataPaths.projectOverrideFile("stats-project"));
        assertFalse(cleaned.contains("\"capture\""));
        assertFalse(cleaned.contains("\"events\""));
        assertFalse(cleaned.contains("\"performance\""));
        assertFalse(cleaned.contains("\"usage\""));
        assertTrue(cleaned.contains("\"stats\""));
    }

    @Test
    void discoverActiveFallsBackToEmbeddedOverrideWhenCentralOverrideIsAbsent() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Embedded Mod",
                "Example",
                "Embedded Mod",
                "1.0.0",
                """
                {
                  "projectId": "embedded-project",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "packagePrefixes": ["example.embedded"]
                }
                """
        );
        TelemetryDataPaths dataPaths = dataPaths(modsDirectory);
        Path embeddedOverride = modsDirectory.resolve("Example_Embedded Mod")
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve("embedded-project.json");
        Files.createDirectories(embeddedOverride.getParent());
        Files.writeString(
                embeddedOverride,
                """
                {
                  "enabled": false
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")
                ))
        );

        assertEquals(1, result.projects().size());
        assertTrue(result.projects().getFirst().isEnabled());
        assertEquals(1, result.consentProjects().size());
        assertFalse(result.consentProjects().getFirst().isEnabled());
    }

    @Test
    void centralOverrideTakesPrecedenceOverEmbeddedOverride() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Embedded Mod",
                "Example",
                "Embedded Mod",
                "1.0.0",
                """
                {
                  "projectId": "embedded-project",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "packagePrefixes": ["example.embedded"]
                }
                """
        );
        TelemetryDataPaths dataPaths = dataPaths(modsDirectory);
        Files.createDirectories(dataPaths.projectSettingsDirectory());
        Files.writeString(
                dataPaths.projectOverrideFile("embedded-project"),
                """
                {
                  "enabled": false
                }
                """
        );
        Path embeddedOverride = modsDirectory.resolve("Example_Embedded Mod")
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve("embedded-project.json");
        Files.createDirectories(embeddedOverride.getParent());
        Files.writeString(
                embeddedOverride,
                """
                {
                  "enabled": true
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths,
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")
                ))
        );

        assertFalse(result.projects().getFirst().isEnabled());
        assertFalse(result.consentProjects().getFirst().isEnabled());
    }

    @Test
    void discoverActiveAggregatesCollisionWarnings() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "First Mod",
                "Example",
                "First Mod",
                "1.0.0",
                """
                {
                  "projectId": "first-project",
                  "displayName": "First Mod",
                  "packagePrefixes": ["example.shared"]
                }
                """
        );
        writeModFolder(
                modsDirectory,
                "Second Mod",
                "Example",
                "Second Mod",
                "1.0.0",
                """
                {
                  "projectId": "second-project",
                  "displayName": "Second Mod",
                  "packagePrefixes": ["example.shared.feature"]
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths(modsDirectory),
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:First Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Second Mod", "1.0.0")
                ))
        );

        assertEquals(1, result.collisions().size());
        assertEquals(1, result.registrationWarnings().size());
        assertTrue(result.registrationWarnings().getFirst().contains("first-project <-> second-project"));
    }

    @Test
    void discoveryResultListsAreImmutable() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Active Mod",
                "Example",
                "Active Mod",
                "1.0.0",
                """
                {
                  "projectId": "active-project",
                  "displayName": "Active Mod",
                  "packagePrefixes": ["example.active"]
                }
                """
        );

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null).discoverActive(
                dataPaths(modsDirectory),
                TelemetryLoadedModSnapshotProvider.fixed(List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Active Mod", "1.0.0")
                ))
        );

        assertThrows(UnsupportedOperationException.class, () -> result.projects().add(result.projects().getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> result.consentProjects().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.loadedMods().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.collisions().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.registrationWarnings().add("mutated"));
    }

    @Test
    void discoverActiveCanUseDiscoveredLoadedModsAsFallbackSnapshot() throws Exception {
        Path modsDirectory = tempDir.resolve("mods");
        writeModFolder(
                modsDirectory,
                "Discovered Mod",
                "Example",
                "Discovered Mod",
                "1.0.0",
                """
                {
                  "projectId": "discovered-project",
                  "displayName": "Discovered Mod",
                  "packagePrefixes": ["example.discovered"]
                }
                """
        );
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(null).discover(modsDirectory);

        TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null)
                .discoverActive(dataPaths(modsDirectory), discoveryResult);

        assertEquals(1, result.loadedMods().size());
        assertEquals("Example:Discovered Mod", result.loadedMods().getFirst().identifier());
        assertEquals(1, result.projects().size());
        assertEquals("discovered-project", result.projects().getFirst().projectId());
    }

    private TelemetryDataPaths dataPaths(Path modsDirectory) {
        Path runtimeRoot = tempDir.resolve("runtime");
        Path settingsRoot = runtimeRoot.resolve("Settings");
        Path telemetryRoot = runtimeRoot.resolve("Telemetry");
        return new TelemetryDataPaths(
                runtimeRoot,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                modsDirectory
        );
    }

    private static void writeModFolder(Path modsDirectory,
                                       String folderName,
                                       String group,
                                       String name,
                                       String version,
                                       String descriptorJson) throws Exception {
        Path modFolder = modsDirectory.resolve(folderName);
        Files.createDirectories(modFolder.resolve("Server").resolve("Telemetry"));
        Files.writeString(
                modFolder.resolve("manifest.json"),
                """
                {
                  "Group": "%s",
                  "Name": "%s",
                  "Version": "%s",
                  "Main": "example.%s.Mod"
                }
                """.formatted(group, name, version, slug(name))
        );
        Files.writeString(modFolder.resolve("Server").resolve("Telemetry").resolve("project.json"), descriptorJson);
    }

    private static TelemetryProjectRegistration registration(String projectId,
                                                            String pluginIdentifier,
                                                            String displayName) {
        String descriptorJson = """
                {
                  "schemaVersion": 1,
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["%s"],
                  "packagePrefixes": ["example.mod"]
                }
                """.formatted(projectId, displayName, pluginIdentifier);
        return new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(descriptorJson, null),
                pluginIdentifier,
                "1.0.0",
                null
        );
    }

    private static String slug(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }
}
