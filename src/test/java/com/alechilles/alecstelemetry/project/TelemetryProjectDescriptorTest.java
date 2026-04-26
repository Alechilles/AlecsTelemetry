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
    void parsesExplicitEventControlsAndDetailAllowlists() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "context-mod",
                  "displayName": "Context Mod",
                  "events": {
                    "errors": { "enabled": false },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true, "automatic": false }
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"],
                    "details": {
                      "settings_opened": {
                        "allowedFields": {
                          "source": { "type": "enum", "values": ["command", "settings_ui"] },
                          "changedSettingCount": { "type": "number" },
                          "configArea": { "type": "string", "maxLength": 24 }
                        }
                      }
                    }
                  },
                  "performance": {
                    "enabled": true,
                    "details": {
                      "reload_config_duration": {
                        "allowedFields": {
                          "configFileCount": { "type": "number" }
                        }
                      }
                    }
                  }
                }
                """,
                null
        );

        assertTrue(!descriptor.events().errors().enabled());
        assertTrue(descriptor.events().lifecycle().enabled());
        assertTrue(descriptor.events().breadcrumbs().enabled());
        assertTrue(!descriptor.events().breadcrumbs().automatic());
        assertEquals("settings_ui", descriptor.usage().sanitizeDetails(
                "settings_opened",
                java.util.Map.of(
                        "source", "settings_ui",
                        "changedSettingCount", 3,
                        "configArea", "companions-and-too-long-for-max",
                        "ignored", "drop me"
                )
        ).get("source"));
        assertEquals(3, descriptor.usage().sanitizeDetails(
                "settings_opened",
                java.util.Map.of("changedSettingCount", 3)
        ).get("changedSettingCount"));
        assertEquals("companions-and-too-long-", descriptor.usage().sanitizeDetails(
                "settings_opened",
                java.util.Map.of("configArea", "companions-and-too-long-for-max")
        ).get("configArea"));
        assertTrue(descriptor.usage().sanitizeDetails("settings_opened", java.util.Map.of("source", "unknown")).isEmpty());
        assertEquals(7, descriptor.performance().sanitizeDetails(
                "reload_config_duration",
                java.util.Map.of("configFileCount", 7, "ignored", true)
        ).get("configFileCount"));
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
