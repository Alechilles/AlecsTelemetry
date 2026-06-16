package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitCapturePersistsAndFlushRetainsFailuresForRetry() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(500, "server error"),
                CrashReportClient.UploadResult.success(204)
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordBreadcrumb("bootstrap", "Resolved optional dependency bridge.");
        }

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });

        service.captureSetupFailure("example-mod", throwable);
        assertEquals(1, service.flushPendingReportsNow("test-first").attempted());
        assertEquals(1, service.flushPendingReportsNow("test-second").attempted());
        assertEquals(2, client.calls);
        JsonObject firstPayload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("bootstrap", firstPayload.getAsJsonArray("breadcrumbs").get(0).getAsJsonObject().get("category").getAsString());
        assertTrue(firstPayload.get("sessionId").getAsString().length() > 10);
        UUID.fromString(firstPayload.get("serverId").getAsString());
        assertEquals("dependency", firstPayload.getAsJsonObject("environment").get("runtimeMode").getAsString());
        assertEquals(0, service.flushPendingReportsNow("test-third").attempted());
    }

    @Test
    void runtimeApiCanQueueAndFlushGenericEventsWithStableServerId() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordError("handled_exception", new IllegalStateException("bad state"), "Recovered after retry");
            handle.recordLifecycle("startup", 42, true, "Loaded telemetry bridge");
        }

        assertEquals(2, service.flushPendingReportsNow("test-events").attempted());
        JsonObject firstPayload = JsonParser.parseString(client.payloads.get(0)).getAsJsonObject();
        JsonObject secondPayload = JsonParser.parseString(client.payloads.get(1)).getAsJsonObject();
        assertEquals(2, firstPayload.get("schemaVersion").getAsInt());
        assertEquals("error", firstPayload.get("eventType").getAsString());
        assertEquals("handled_exception", firstPayload.get("eventName").getAsString());
        String serverId = firstPayload.get("serverId").getAsString();
        UUID.fromString(serverId);
        assertEquals(serverId, secondPayload.get("serverId").getAsString());

        SequencedClient secondClient = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService secondService = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                secondClient,
                null,
                null
        );
        TelemetryProjectHandle secondHandle = secondService.api().findProject("example-mod");
        if (secondHandle != null) {
            secondHandle.recordError("second_session_event", null, "Recorded after restart");
        }

        assertEquals(1, secondService.flushPendingReportsNow("test-events-second-session").attempted());
        JsonObject secondSessionPayload = JsonParser.parseString(secondClient.payloads.getFirst()).getAsJsonObject();
        assertEquals(serverId, secondSessionPayload.get("serverId").getAsString());
        assertNotEquals(firstPayload.get("sessionId").getAsString(), secondSessionPayload.get("sessionId").getAsString());
    }

    @Test
    void typedContextPromotesFieldsAndSanitizesUsageDetails() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"],
                    "details": {
                      "settings_opened": {
                        "allowedFields": {
                          "source": { "type": "enum", "values": ["command", "settings_ui"] },
                          "changedSettingCount": { "type": "number" }
                        }
                      }
                    }
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordUsageWithContext(
                    "settings_opened",
                    TelemetryEventContext.usage()
                            .subsystem("settings")
                            .featureKey("settings_page")
                            .entryPoint("/tw settings")
                            .runtimeSide("server")
                            .detail("source", "settings_ui")
                            .detail("changedSettingCount", 2)
                            .detail("ignored", "drop me")
                            .build()
            );
        }

        assertEquals(1, service.flushPendingReportsNow("test-events").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("usage", payload.get("eventType").getAsString());
        assertEquals("settings", payload.get("subsystem").getAsString());
        assertEquals("settings_page", payload.get("featureKey").getAsString());
        assertEquals("/tw settings", payload.get("entryPoint").getAsString());
        assertEquals("server", payload.get("runtimeSide").getAsString());
        assertEquals("settings_ui", payload.getAsJsonObject("details").get("source").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("changedSettingCount").getAsInt());
        assertTrue(!payload.getAsJsonObject("details").has("ignored"));
    }

    @Test
    void errorContextIncludesBreadcrumbDetailsAndHonorsEventControls() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "events": {
                    "errors": {
                      "enabled": true,
                      "details": {
                        "handled_exception": {
                          "allowedFields": {
                            "reason": { "type": "string", "maxLength": 120 }
                          }
                        }
                      }
                    },
                    "lifecycle": { "enabled": false },
                    "breadcrumbs": { "enabled": true }
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordBreadcrumb("ui", "Opened settings.");
            handle.recordLifecycleWithContext("suppressed_lifecycle", 10, true, TelemetryEventContext.lifecycle().phase("start").build());
            handle.recordErrorWithContext(
                    "handled_exception",
                    new IllegalStateException("bad state"),
                    TelemetryEventContext.error()
                            .subsystem("settings")
                            .operation("apply")
                            .fingerprint("settings-apply")
                            .detail("Recovered after retry")
                            .detail("reason", "bad_state")
                            .detail("ignored", "drop me")
                            .build()
            );
        }

        assertEquals(1, service.flushPendingReportsNow("test-events").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("handled_exception", payload.get("eventName").getAsString());
        assertEquals("settings-apply", payload.get("fingerprint").getAsString());
        assertEquals("settings", payload.get("subsystem").getAsString());
        assertEquals("apply", payload.get("operation").getAsString());
        assertEquals("Recovered after retry", payload.getAsJsonObject("attributes").get("detail").getAsString());
        assertEquals("bad_state", payload.getAsJsonObject("details").get("reason").getAsString());
        assertTrue(!payload.getAsJsonObject("details").has("ignored"));
        assertTrue(payload.getAsJsonObject("details").getAsJsonArray("breadcrumbs").size() > 0);
    }

    @Test
    void crashCaptureOverrideSuppressesCrashReports() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectOverride override = TelemetryProjectOverride.fromJson(
                """
                {
                  "capture": {
                    "uncaughtExceptions": false,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  }
                }
                """
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod"),
                override
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });

        service.captureSetupFailure("example-mod", throwable);

        assertEquals(0, service.flushPendingReportsNow("test-crash-consent").attempted());
        assertEquals(0, client.calls);
    }

    @Test
    void runtimeConsentApiPersistsAppliesAndMarksReviewedProjects() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "events": {
                    "errors": { "enabled": true },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "performance": {
                    "enabled": true,
                    "thresholdMs": 1
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"]
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertEquals(List.of(registration), service.unreviewedConsentProjects());
        assertTrue(service.applyConsent(
                "example-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false)
        ));
        assertFalse(service.isProjectEnabled("example-mod"));
        assertFalse(service.projectDiagnostics("example-mod").crashEnabled());
        assertFalse(service.projectDiagnostics("example-mod").errorEnabled());
        assertFalse(service.projectDiagnostics("example-mod").lifecycleEnabled());
        assertFalse(service.projectDiagnostics("example-mod").performanceEnabled());
        assertFalse(service.projectDiagnostics("example-mod").usageEnabled());
        assertFalse(service.projectDiagnostics("example-mod").breadcrumbsEnabled());

        TelemetryProjectOverride override = new TelemetryProjectOverrideStore(null)
                .load(dataPaths.projectOverrideFile("example-mod"));
        assertEquals(false, override.enabled());
        assertEquals(false, override.capture().setupFailures());
        assertEquals(false, override.events().errors().enabled());
        assertEquals(false, override.events().lifecycle().enabled());
        assertEquals(false, override.events().breadcrumbs().enabled());
        assertEquals(false, override.performance().enabled());
        assertEquals(false, override.usage().enabled());

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });
        service.captureSetupFailure("example-mod", throwable);
        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordError("handled_exception", new IllegalStateException("bad state"), "Recovered after retry");
            handle.recordPerformance("reload_config_duration", 10, null, "Reloaded config");
            handle.recordUsage("settings_opened", "Opened settings");
        }

        assertEquals(0, service.flushPendingReportsNow("test-consent-off").attempted());
        assertEquals(0, client.calls);
        assertTrue(service.markConsentReviewed("example-mod"));
        assertTrue(service.unreviewedConsentProjects().isEmpty());
    }

    @Test
    void embeddedConsentProjectIsVisibleAndMirrorsOverridesToOwnerSettings() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        Path modsDir = tempDir.resolve("mods");
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                modsDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "events": {
                    "errors": { "enabled": true },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "performance": {
                    "enabled": true
                  },
                  "usage": {
                    "enabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Embedded Mod",
                "1.2.3",
                modsDir.resolve("Embedded.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertTrue(service.projects().isEmpty());
        assertNull(service.api().findProject("embedded-mod"));
        assertEquals(1, service.diagnostics().registeredProjects());
        assertEquals(1, service.diagnostics().projects().size());
        assertEquals("embedded", service.diagnostics().projects().getFirst().runtimeMode());
        assertTrue(service.diagnostics().projects().getFirst().enabled());

        assertTrue(service.applyConsent(
                "embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false)
        ));

        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = service.projectDiagnostics("embedded-mod");
        assertFalse(diagnostics.enabled());
        assertFalse(diagnostics.crashEnabled());
        assertFalse(diagnostics.errorEnabled());
        assertFalse(diagnostics.lifecycleEnabled());
        assertFalse(diagnostics.performanceEnabled());
        assertFalse(diagnostics.usageEnabled());
        assertFalse(diagnostics.breadcrumbsEnabled());

        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(null);
        TelemetryProjectOverride centralOverride = store.load(dataPaths.projectOverrideFile("embedded-mod"));
        Path embeddedOverrideFile = modsDir
                .resolve("Example_Embedded Mod")
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve("embedded-mod.json");
        TelemetryProjectOverride embeddedOverride = store.load(embeddedOverrideFile);
        assertEquals(false, centralOverride.enabled());
        assertEquals(false, centralOverride.events().errors().enabled());
        assertEquals(false, embeddedOverride.enabled());
        assertEquals(false, embeddedOverride.events().errors().enabled());
        assertEquals(0, service.flushPendingReportsNow("embedded-consent").attempted());
        assertEquals(0, client.calls);
    }

    private static final class SequencedClient implements CrashReportClient {
        private final Queue<UploadResult> responses = new ArrayDeque<>();
        private final java.util.ArrayList<String> payloads = new java.util.ArrayList<>();
        private int calls;

        private SequencedClient(UploadResult... uploadResults) {
            for (UploadResult uploadResult : uploadResults) {
                responses.add(uploadResult);
            }
        }

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            calls++;
            payloads.add(payloadJson);
            UploadResult next = responses.poll();
            return next == null ? UploadResult.success(200) : next;
        }
    }
}
