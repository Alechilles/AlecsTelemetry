package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void parsesUiIconTexturePath() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "custom-mod",
                  "displayName": "Custom Mod",
                  "ui": {
                    "iconTexturePath": "Tamework/Telemetry/TameworkConsentIcon.png"
                  }
                }
                """,
                null
        );

        assertEquals("Tamework/Telemetry/TameworkConsentIcon.png", descriptor.ui().iconTexturePath());
    }

    @Test
    void dropsUnsafeUiIconTexturePath() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "custom-mod",
                  "displayName": "Custom Mod",
                  "ui": {
                    "iconTexturePath": "../icon-256.png"
                  }
                }
                """,
                null
        );

        assertNull(descriptor.ui().iconTexturePath());
    }

    @Test
    void parsesExplicitEventControlsAndDetailAllowlists() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "context-mod",
                  "displayName": "Context Mod",
                  "events": {
                    "errors": {
                      "enabled": false,
                      "details": {
                        "needs_seek_failed": {
                          "allowedFields": {
                            "reason": { "type": "string", "maxLength": 120 },
                            "resource": { "type": "enum", "values": ["food", "water", "unknown"] }
                          }
                        }
                      }
                    },
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
        assertEquals("no_water_target", descriptor.events().errors().sanitizeDetails(
                "needs_seek_failed",
                java.util.Map.of("reason", "no_water_target", "resource", "water", "ignored", "drop me")
        ).get("reason"));
        assertEquals("water", descriptor.events().errors().sanitizeDetails(
                "needs_seek_failed",
                java.util.Map.of("resource", "water")
        ).get("resource"));
        assertTrue(descriptor.events().errors().sanitizeDetails(
                "needs_seek_failed",
                java.util.Map.of("resource", "lava")
        ).isEmpty());
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
    void parsesManualReportSchema() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "report-mod",
                  "displayName": "Report Mod",
                  "reports": {
                    "enabled": true,
                    "issue": {
                      "enabled": true,
                      "fields": {
                        "severity": {
                          "type": "enum",
                          "label": "Severity",
                          "required": true,
                          "values": ["minor", "moderate", "major", "blocking"]
                        },
                        "steps": {
                          "type": "text",
                          "label": "Steps to reproduce",
                          "required": false,
                          "maxLength": 2000
                        }
                      }
                    },
                    "suggestion": {
                      "enabled": true,
                      "fields": {
                        "category": {
                          "type": "enum",
                          "label": "Category",
                          "required": false,
                          "values": ["balance", "content", "quality_of_life", "other"]
                        }
                      }
                    },
                    "attachments": {
                      "currentServerLog": true,
                      "previousServerLog": true,
                      "maxBytes": 262144
                    },
                    "contact": {
                      "enabled": true,
                      "maxLength": 160
                    },
                    "resolutionUpdates": {
                      "enabled": true
                    }
                  }
                }
                """,
                null
        );

        assertTrue(descriptor.reports().enabled());
        assertTrue(descriptor.reports().issue().enabled());
        assertTrue(descriptor.reports().suggestion().enabled());
        assertEquals("severity", descriptor.reports().issue().fields().getFirst().key());
        assertEquals("enum", descriptor.reports().issue().fields().getFirst().type().key());
        assertTrue(descriptor.reports().issue().fields().getFirst().required());
        assertEquals("steps", descriptor.reports().issue().fields().get(1).key());
        assertEquals("text", descriptor.reports().issue().fields().get(1).type().key());
        assertEquals(2000, descriptor.reports().issue().fields().get(1).maxLength());
        assertEquals("category", descriptor.reports().suggestion().fields().getFirst().key());
        assertTrue(descriptor.reports().attachments().currentServerLog());
        assertTrue(descriptor.reports().attachments().previousServerLog());
        assertEquals(262144, descriptor.reports().attachments().maxBytes());
        assertTrue(descriptor.reports().contact().enabled());
        assertEquals(160, descriptor.reports().contact().maxLength());
        assertTrue(descriptor.reports().resolutionUpdates().enabled());
    }

    @Test
    void disablesManualReportsWhenOmitted() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "no-reports-mod",
                  "displayName": "No Reports Mod"
                }
                """,
                null
        );

        assertTrue(!descriptor.reports().enabled());
        assertTrue(!descriptor.reports().issue().enabled());
        assertTrue(!descriptor.reports().suggestion().enabled());
        assertTrue(descriptor.reports().issue().fields().isEmpty());
        assertTrue(descriptor.reports().suggestion().fields().isEmpty());
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
