package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
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

class EmbeddedTelemetryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void embeddedServiceCapturesAndFlushesForOneOwningProject() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(500, "server error"),
                CrashReportClient.UploadResult.success(204)
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        service.recordBreadcrumb("bootstrap", "Embedded config loaded.");
        RuntimeException throwable = new RuntimeException("embedded setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.embedded.EmbeddedMod", "setup", "EmbeddedMod.java", 12)
        });

        service.captureSetupFailure(throwable);
        assertEquals(1, service.pendingReports());
        assertEquals(1, service.flushPendingReportsNow("embedded-first").attempted());
        assertEquals(1, service.flushPendingReportsNow("embedded-second").attempted());
        assertEquals(0, service.flushPendingReportsNow("embedded-third").attempted());
        assertEquals(2, client.calls);
        JsonObject firstPayload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", firstPayload.get("projectId").getAsString());
        assertTrue(firstPayload.getAsJsonArray("breadcrumbs").size() > 0);
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
