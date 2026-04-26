package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("dependency", firstPayload.getAsJsonObject("environment").get("runtimeMode").getAsString());
        assertEquals(0, service.flushPendingReportsNow("test-third").attempted());
    }

    @Test
    void runtimeApiCanQueueAndFlushGenericEvents() {
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
        }

        assertEquals(1, service.flushPendingReportsNow("test-events").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("error", payload.get("eventType").getAsString());
        assertEquals("handled_exception", payload.get("eventName").getAsString());
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
                    "errors": { "enabled": true },
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
        assertTrue(payload.getAsJsonObject("details").getAsJsonArray("breadcrumbs").size() > 0);
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
