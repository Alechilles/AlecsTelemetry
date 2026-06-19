package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeProviderParityTest {

    @Test
    void standaloneAndEmbeddedUseSameLoadedModFilteringRules() {
        TelemetryProjectRegistration enabled = registration("enabled-mod", "Alechilles:Enabled Mod");
        TelemetryProjectRegistration notLoaded = registration("not-loaded-mod", "Alechilles:Not Loaded Mod");
        List<CrashReportEnvelope.LoadedModMetadata> loadedMods = List.of(
                new CrashReportEnvelope.LoadedModMetadata("Alechilles:Enabled Mod", "1.0.0")
        );

        List<TelemetryProjectRegistration> standalone = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(enabled, notLoaded),
                loadedMods
        );
        List<TelemetryProjectRegistration> embedded = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(enabled, notLoaded),
                loadedMods
        );

        assertEquals(
                standalone.stream().map(TelemetryProjectRegistration::projectId).toList(),
                embedded.stream().map(TelemetryProjectRegistration::projectId).toList()
        );
    }

    @Test
    void providerOriginsAreMetadataOnlyForSharedDiscoveryRules() {
        assertEquals(TelemetryRuntimeOrigin.STANDALONE.name(), "STANDALONE");
        assertEquals(TelemetryRuntimeOrigin.EMBEDDED.name(), "EMBEDDED");
    }

    private static TelemetryProjectRegistration registration(String projectId, String ownerPluginIdentifier) {
        return new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "%s",
                          "displayName": "%s",
                          "runtimeMode": "embedded",
                          "ownerPluginIdentifiers": ["%s"],
                          "packagePrefixes": ["com.example"],
                          "stats": {
                            "enabled": true,
                            "allowedEvents": ["heartbeat"]
                          }
                        }
                        """.formatted(projectId, projectId, ownerPluginIdentifier),
                        null
                ),
                ownerPluginIdentifier,
                "1.0.0",
                Path.of(projectId + ".jar")
        );
    }
}
