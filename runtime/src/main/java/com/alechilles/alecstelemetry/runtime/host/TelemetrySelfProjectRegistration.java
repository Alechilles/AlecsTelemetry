package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

final class TelemetrySelfProjectRegistration {
    static final String PROJECT_ID = "alecs-telemetry";

    private static final String PUBLIC_PROJECT_KEY = "proj_ZPSm_mSfIhTqg9NlND_G82k1";
    private static final TelemetryProjectDescriptor DESCRIPTOR = TelemetryProjectDescriptor.fromJson(
            """
            {
              "projectId": "alecs-telemetry",
              "displayName": "Alec's Telemetry",
              "runtimeMode": "dependency",
              "ownerPluginIdentifiers": ["Alechilles:Alec's Telemetry"],
              "packagePrefixes": ["com.alechilles.alecstelemetry"],
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
