package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoordinatorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void coordinatorIncludesEmbeddedProjectsInRuntimeRegistrations() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                descriptor("embedded-mod", "Embedded Mod", "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded.jar")
        );
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        assertEquals(1, service.registeredProjectCount());
        assertEquals("embedded-mod", service.projects().getFirst().projectId());
        assertEquals("embedded", service.projects().getFirst().runtimeMode());
    }

    @Test
    void fromDiscoveryRegistersSuppliedProjectsAndLoadedMods() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration statsProject = new TelemetryProjectRegistration(
                statsDescriptor("stats-mod", "Stats Mod", "standalone"),
                "Example:Stats Mod",
                "7.8.9",
                tempDir.resolve("Stats.jar")
        );
        CrashReportEnvelope.LoadedModMetadata loadedMod =
                new CrashReportEnvelope.LoadedModMetadata("Example:Stats Mod", "7.8.9");
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscoveryResult(
                List.of(statsProject),
                List.of(statsProject),
                List.of(loadedMod),
                List.of(),
                List.of()
        );

        TelemetryCoordinatorService service = TelemetryCoordinatorService.fromDiscovery(
                settings,
                dataPaths,
                discovery,
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null,
                null
        );

        assertEquals(1, service.registeredProjectCount());
        assertEquals("stats-mod", service.projects().getFirst().projectId());
        assertTrue(service.isStatsEnabled("stats-mod"));
        assertEquals(1, service.loadedMods().size());
        assertEquals("Example:Stats Mod", service.loadedMods().getFirst().identifier());
        assertEquals("7.8.9", service.loadedMods().getFirst().version());
    }

    @Test
    void bridgeFriendlyUsageCallPersistsForEmbeddedProject() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                descriptor("embedded-mod", "Embedded Mod", "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertTrue(service.recordUsage(
                "embedded-mod",
                "settings_opened",
                Map.of("detail", "Opened settings", "source", "test")
        ));
        assertEquals(1, service.flushPendingReportsNow("test", "embedded-mod").attempted());

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", payload.get("projectId").getAsString());
        assertEquals("settings_opened", payload.get("eventName").getAsString());
        assertEquals("test", payload.getAsJsonObject("details").get("source").getAsString());
    }

    @Test
    void bridgeFriendlyExceptionalWorldRemovalCapturesForAttributedProject() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                descriptor("embedded-mod", "Embedded Mod", "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );
        RuntimeException throwable = new RuntimeException("world crashed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.WorldSystem", "tick", "WorldSystem.java", 42)
        });

        assertTrue(service.captureExceptionalWorldRemoval(
                throwable,
                "Default",
                "EXCEPTIONAL",
                "Example:Embedded Mod"
        ));
        assertEquals(1, service.flushPendingReportsNow("test", "embedded-mod").attempted());

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", payload.get("projectId").getAsString());
        assertEquals("exceptional_world_removal", payload.get("source").getAsString());
        assertEquals("Default", payload.get("worldName").getAsString());
        assertEquals("EXCEPTIONAL", payload.get("worldRemovalReason").getAsString());
        assertEquals("Example:Embedded Mod", payload.get("worldFailurePluginIdentifier").getAsString());
    }

    @Test
    void coordinatorStatsHeartbeatPersistsForEveryStatsProject() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                statsDescriptor("embedded-mod", "Embedded Mod", "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded.jar")
        );
        TelemetryProjectRegistration dependency = new TelemetryProjectRegistration(
                statsDescriptor("dependency-mod", "Dependency Mod", "dependency"),
                "Example:Dependency Mod",
                "4.5.6",
                tempDir.resolve("Dependency.jar")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.success(204),
                CrashReportClient.UploadResult.success(204)
        );
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded, dependency),
                List.of(embedded, dependency),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Dependency Mod", "4.5.6")
                ),
                client,
                null,
                null,
                () -> 3
        );

        service.emitStatsHeartbeatNow();

        assertEquals(1, service.flushPendingReportsNow("test", "embedded-mod").attempted());
        assertEquals(1, service.flushPendingReportsNow("test", "dependency-mod").attempted());
        assertEquals(2, client.payloads.size());

        JsonObject embeddedPayload = JsonParser.parseString(client.payloads.get(0)).getAsJsonObject();
        JsonObject dependencyPayload = JsonParser.parseString(client.payloads.get(1)).getAsJsonObject();
        assertEquals("embedded-mod", embeddedPayload.get("projectId").getAsString());
        assertEquals("dependency-mod", dependencyPayload.get("projectId").getAsString());
        assertEquals("runtime_api", embeddedPayload.get("source").getAsString());
        assertEquals("runtime_api", dependencyPayload.get("source").getAsString());
        assertEquals("heartbeat", embeddedPayload.get("eventName").getAsString());
        assertEquals("heartbeat", dependencyPayload.get("eventName").getAsString());
        assertEquals(3, embeddedPayload.getAsJsonObject("details").get("playersOnline").getAsInt());
        assertEquals(3, dependencyPayload.getAsJsonObject("details").get("playersOnline").getAsInt());
    }

    private TelemetryDataPaths dataPaths(TelemetryRuntimeSettings settings) {
        return new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir.resolve("Mods")
        );
    }

    private static TelemetryProjectDescriptor descriptor(String projectId, String displayName, String runtimeMode) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "runtimeMode": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"],
                    "details": {
                      "settings_opened": {
                        "allowedFields": {
                          "source": { "type": "string" }
                        }
                      }
                    }
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """.formatted(projectId, displayName, runtimeMode, displayName),
                null
        );
    }

    private static TelemetryProjectDescriptor statsDescriptor(String projectId, String displayName, String runtimeMode) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "runtimeMode": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "stats": {
                    "enabled": true,
                    "allowedEvents": ["heartbeat"]
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """.formatted(projectId, displayName, runtimeMode, displayName),
                null
        );
    }

    private static final class SequencedClient implements CrashReportClient {
        private final Queue<UploadResult> responses = new ArrayDeque<>();
        private final java.util.ArrayList<String> payloads = new java.util.ArrayList<>();

        private SequencedClient(UploadResult... uploadResults) {
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
