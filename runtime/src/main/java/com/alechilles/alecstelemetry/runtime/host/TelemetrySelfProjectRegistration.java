package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

final class TelemetrySelfProjectRegistration {
    static final String PROJECT_ID = "alecs-telemetry";

    private static final String PUBLIC_PROJECT_KEY = "proj_aqocXOn4Gw3unr0p22QoNd3k";
    private static final TelemetryProjectDescriptor DESCRIPTOR = TelemetryProjectDescriptor.fromJson(
            """
            {
              "projectId": "alecs-telemetry",
              "displayName": "Alec's Telemetry",
              "runtimeMode": "dependency",
              "ownerPluginIdentifiers": ["Alechilles:Alec's Telemetry"],
              "packagePrefixes": ["com.alechilles.alecstelemetry"],
              "capture": {
                "uncaughtExceptions": true,
                "setupFailures": true,
                "startFailures": true,
                "exceptionalWorldRemovals": true
              },
              "events": {
                "errors": {
                  "enabled": true,
                  "details": {
                    "runtime_command_register_failed": {
                      "allowedFields": {
                        "component": { "type": "enum", "values": ["self_runtime"] },
                        "operationKind": { "type": "enum", "values": ["command_register"] }
                      }
                    },
                    "runtime_event_register_failed": {
                      "allowedFields": {
                        "component": { "type": "enum", "values": ["self_runtime"] },
                        "operationKind": { "type": "enum", "values": ["event_register"] },
                        "event": { "type": "string", "maxLength": 80 }
                      }
                    },
                    "runtime_event_unregister_failed": {
                      "allowedFields": {
                        "component": { "type": "enum", "values": ["self_runtime"] },
                        "operationKind": { "type": "enum", "values": ["event_unregister"] },
                        "event": { "type": "string", "maxLength": 80 }
                      }
                    }
                  }
                },
                "lifecycle": { "enabled": false },
                "breadcrumbs": { "enabled": false }
              },
              "performance": {
                "enabled": false
              },
              "usage": {
                "enabled": false
              },
              "stats": {
                "enabled": true,
                "allowedEvents": ["heartbeat"]
              },
              "ui": {
                "iconTexturePath": "icon-256.png"
              },
              "hosted": {
                "projectKey": "%s"
              }
            }
            """.formatted(PUBLIC_PROJECT_KEY),
            null
    );

    private TelemetrySelfProjectRegistration() {
    }

    @Nonnull
    static TelemetryProjectRegistration create(@Nonnull String pluginIdentifier,
                                               @Nonnull String pluginVersion,
                                               @Nullable Path sourcePath,
                                               @Nullable TelemetryProjectOverride override) {
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                DESCRIPTOR,
                pluginIdentifier,
                pluginVersion,
                sourcePath
        );
        return override == null ? registration : registration.withOverride(override);
    }
}
