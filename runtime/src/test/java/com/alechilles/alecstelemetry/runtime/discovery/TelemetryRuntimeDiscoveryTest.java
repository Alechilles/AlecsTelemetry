package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeDiscoveryTest {

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
}
