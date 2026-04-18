package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertEquals(TelemetryProjectDescriptor.RUNTIME_MODE_DEPENDENCY, descriptor.runtimeMode());
        assertTrue(descriptor.isDependencyMode());
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

    @Test
    void parsesPerformanceAndEventDestinationOptions() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "custom-mod",
                  "displayName": "Custom Mod",
                  "ownerPluginIdentifiers": ["Example:Custom Mod"],
                  "packagePrefixes": ["com.example.custom"],
                  "performance": {
                    "enabled": true,
                    "sampleRate": 0.5,
                    "thresholdMs": 250
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_page_opened", "reload_config_command_used"]
                  },
                  "hosted": {
                    "projectKey": "public-key",
                    "eventEndpoint": "https://example.com/ingest/event"
                  }
                }
                """,
                null
        );

        assertTrue(descriptor.performance().enabled());
        assertEquals(0.5d, descriptor.performance().sampleRate());
        assertEquals(250, descriptor.performance().thresholdMs());
        assertTrue(descriptor.usage().enabled());
        assertTrue(descriptor.usage().allows("settings_page_opened"));
        assertEquals("https://example.com/ingest/event", descriptor.hosted().eventEndpoint());
    }

    @Test
    void parsesExplicitEmbeddedRuntimeMode() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "hosted": {
                    "projectKey": "public-key"
                  }
                }
                """,
                new TelemetryProjectDescriptor.Fallbacks(
                        "embedded-mod",
                        "Embedded Mod",
                        "Example:Embedded Mod",
                        List.of("com.example.embedded")
                )
        );

        assertEquals(TelemetryProjectDescriptor.RUNTIME_MODE_EMBEDDED, descriptor.runtimeMode());
        assertTrue(descriptor.isEmbeddedMode());
    }
}
