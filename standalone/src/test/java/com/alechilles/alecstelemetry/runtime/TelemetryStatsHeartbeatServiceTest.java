package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.stats.TelemetryPlayerCounter;
import com.alechilles.alecstelemetry.stats.TelemetryStatsHeartbeatService;
import com.alechilles.alecstelemetry.stats.TelemetryStatsRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryStatsHeartbeatServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void playerCounterTracksUniqueReadyPlayersAndDisconnects() {
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        counter.markReady(first);
        counter.markReady(first);
        counter.markReady(second);
        counter.markDisconnected(first);

        assertEquals(1, counter.onlinePlayers());
    }

    @Test
    void heartbeatQueuesStatsEventWithPlayersOnline() {
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
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        CapturingClient client = new CapturingClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService runtimeService = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        counter.markReady(UUID.randomUUID());
        counter.markReady(UUID.randomUUID());

        new TelemetryStatsHeartbeatService(TelemetryStatsRuntime.from(runtimeService), counter, null, null).emitHeartbeatNow();

        assertEquals(1, runtimeService.flushPendingReportsNow("test-stats-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals("stats", payload.get("featureKey").getAsString());
        assertEquals("heartbeat", payload.get("entryPoint").getAsString());
        assertEquals("server", payload.get("runtimeSide").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
    }

    @Test
    void simpleChartHelperQueuesChartSampleStatsEvent() {
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
                  },
                  "stats": {
                    "enabled": true,
                    "allowedEvents": ["chart_sample"]
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
        CapturingClient client = new CapturingClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService runtimeService = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = runtimeService.api().findProject("example-mod");
        handle.recordSimpleStatChart("language", "English");

        assertEquals(1, runtimeService.flushPendingReportsNow("test-chart-sample").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("chart_sample", payload.get("eventName").getAsString());
        assertEquals("language", payload.getAsJsonObject("details").get("chartId").getAsString());
        assertEquals("English", payload.getAsJsonObject("details").get("value").getAsString());
    }

    private static final class CapturingClient implements CrashReportClient {
        private final Queue<UploadResult> responses = new ArrayDeque<>();
        private final java.util.ArrayList<String> payloads = new java.util.ArrayList<>();

        private CapturingClient(UploadResult... uploadResults) {
            for (UploadResult uploadResult : uploadResults) {
                responses.add(uploadResult);
            }
        }

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            payloads.add(payloadJson);
            UploadResult next = responses.poll();
            return next == null ? UploadResult.success(200) : next;
        }
    }
}
