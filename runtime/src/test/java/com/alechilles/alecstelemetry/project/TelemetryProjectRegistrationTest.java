package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
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

    @Test
    void reportTargetDerivesFromHostedEventEndpoint() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "hosted-mod",
                  "displayName": "Hosted Mod",
                  "ownerPluginIdentifiers": ["Example:Hosted Mod"],
                  "packagePrefixes": ["com.example.hosted"],
                  "hosted": {
                    "eventEndpoint": "https://telemetry-dev.example.com/ingest/event",
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

        var target = registration.resolveReportDeliveryTarget(TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null));

        assertNotNull(target);
        assertEquals("https://telemetry-dev.example.com/ingest/report", target.endpoint());
        assertEquals("public-key", target.headers().get(TelemetryProjectDescriptor.PROJECT_KEY_HEADER));
    }

    @Test
    void explicitRuntimeReportEndpointOverridesDerivedHostedEndpoint() throws Exception {
        Path settingsFile = tempDir.resolve("explicit-runtime.json");
        java.nio.file.Files.writeString(
                settingsFile,
                """
                {
                  "manualReports": {
                    "hostedReportIngestEndpoint": "https://reports.example.com/custom-report"
                  }
                }
                """
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "hosted-mod",
                  "displayName": "Hosted Mod",
                  "ownerPluginIdentifiers": ["Example:Hosted Mod"],
                  "packagePrefixes": ["com.example.hosted"],
                  "hosted": {
                    "eventEndpoint": "https://telemetry-dev.example.com/ingest/event",
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

        var target = registration.resolveReportDeliveryTarget(TelemetryRuntimeSettings.load(settingsFile, null));

        assertNotNull(target);
        assertEquals("https://reports.example.com/custom-report", target.endpoint());
    }

    @Test
    void consentSnapshotReflectsDescriptorDefaults() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "consent-mod",
                  "displayName": "Consent Mod",
                  "ownerPluginIdentifiers": ["Example:Consent Mod"],
                  "packagePrefixes": ["com.example.consent"],
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  },
                  "events": {
                    "errors": { "enabled": false },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": false }
                  },
                  "performance": {
                    "enabled": false
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"]
                  },
                  "stats": {
                    "enabled": false,
                    "allowedEvents": ["heartbeat"]
                  },
                  "defaults": {
                    "enabled": true
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Consent Mod",
                "1.0.0",
                null
        );

        TelemetryConsentSnapshot snapshot = registration.consentSnapshot();

        assertEquals(true, snapshot.projectEnabled());
        assertEquals(true, snapshot.crashEnabled());
        assertEquals(false, snapshot.errorEnabled());
        assertEquals(true, snapshot.lifecycleEnabled());
        assertEquals(false, snapshot.performanceEnabled());
        assertEquals(true, snapshot.usageEnabled());
        assertEquals(false, snapshot.statsEnabled());
        assertEquals(false, snapshot.breadcrumbsEnabled());
    }

    @Test
    void consentSnapshotKeepsStatsSeparateFromUsage() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "consent-mod",
                  "displayName": "Consent Mod",
                  "usage": {
                    "enabled": false,
                    "allowedEvents": ["settings_opened"]
                  },
                  "stats": {
                    "enabled": true,
                    "allowedEvents": ["heartbeat"]
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Consent Mod",
                "1.0.0",
                null
        );

        TelemetryConsentSnapshot snapshot = registration.consentSnapshot();

        assertEquals(false, snapshot.usageEnabled());
        assertEquals(true, snapshot.statsEnabled());
    }
}
