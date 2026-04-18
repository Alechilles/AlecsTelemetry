package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelemetryProjectRegistrationTest {

    @TempDir
    Path tempDir;

    @Test
    void eventTargetFallsBackToConfiguredCustomUrl() {
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
                    "url": "https://example.com/custom-ingest"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Custom Mod",
                "1.0.0",
                null
        );

        var target = registration.resolveEventDeliveryTarget(TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null));

        assertNotNull(target);
        assertEquals("https://example.com/custom-ingest", target.endpoint());
    }

    @Test
    void eventTargetFallsBackToHostedEndpointOverride() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "hosted-mod",
                  "displayName": "Hosted Mod",
                  "ownerPluginIdentifiers": ["Example:Hosted Mod"],
                  "packagePrefixes": ["com.example.hosted"],
                  "hosted": {
                    "endpoint": "https://example.com/project-hosted",
                    "projectKey": "public-key"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Hosted Mod",
                "1.0.0",
                null
        );

        var target = registration.resolveEventDeliveryTarget(TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null));

        assertNotNull(target);
        assertEquals("https://example.com/project-hosted", target.endpoint());
        assertEquals("public-key", target.headers().get(TelemetryProjectDescriptor.PROJECT_KEY_HEADER));
    }
}
