package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelemetryProjectDescriptorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesHostedDefaultsAndFallbacks() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "schemaVersion": 1,
                  "packagePrefixes": ["com.example.telemetry"],
                  "hosted": {
                    "projectKey": "public-key"
                  }
                }
                """,
                new TelemetryProjectDescriptor.Fallbacks(
                        "example-mod",
                        "Example Mod",
                        "Example:Example Mod",
                        java.util.List.of("com.example.telemetry")
                )
        );

        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null);
        assertEquals("example-mod", descriptor.projectId());
        assertEquals("Example Mod", descriptor.displayName());
        assertEquals("Example:Example Mod", descriptor.ownerPluginIdentifiers().getFirst());
        assertEquals("com.example.telemetry", descriptor.packagePrefixes().getFirst());
        assertEquals("hosted", descriptor.defaults().destinationMode());
        assertNotNull(descriptor.resolveDeliveryTarget(settings));
    }

    @Test
    void parsesCustomDestinationMode() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "custom-mod",
                  "displayName": "Custom Mod",
                  "ownerPluginIdentifiers": ["Example:Custom Mod"],
                  "packagePrefixes": ["com.example.custom"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.com/telemetry",
                    "headers": {
                      "Authorization": "Bearer token"
                    }
                  }
                }
                """,
                null
        );

        assertEquals("custom", descriptor.defaults().destinationMode());
        assertEquals("https://example.com/telemetry", descriptor.customEndpoint().url());
        assertEquals("Bearer token", descriptor.customEndpoint().headers().get("Authorization"));
    }
}
