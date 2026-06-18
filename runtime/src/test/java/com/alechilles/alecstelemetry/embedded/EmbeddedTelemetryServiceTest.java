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
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        UUID.fromString(firstPayload.get("serverId").getAsString());
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-id.txt")));
        assertTrue(firstPayload.getAsJsonArray("breadcrumbs").size() > 0);
    }

    @Test
    void embeddedGenericEventsIncludeStableServerId() {
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
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        service.recordError("embedded_event", null, "Embedded runtime event");

        assertEquals(1, service.flushPendingReportsNow("embedded-event").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", payload.get("projectId").getAsString());
        assertEquals(2, payload.get("schemaVersion").getAsInt());
        UUID.fromString(payload.get("serverId").getAsString());
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-id.txt")));
    }

    @Test
    void embeddedStatsHeartbeatQueuesStandardStatsEvent() {
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
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptorWithStats(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryPlayerCounter playerCounter = new EmbeddedTelemetryPlayerCounter();
        playerCounter.markReady(UUID.randomUUID());
        playerCounter.markReady(UUID.randomUUID());
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null,
                playerCounter
        );

        service.emitStatsHeartbeatNow();

        assertEquals(1, service.flushPendingReportsNow("embedded-stats-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
    }

    @Test
    void embeddedStatsHeartbeatQueuesForEveryRegisteredStatsProject() {
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
        TelemetryProjectRegistration owner = new TelemetryProjectRegistration(
                descriptorWithStats("embedded-owner", "Embedded Owner"),
                "Example:Embedded Owner",
                "1.0.0",
                tempDir.resolve("Embedded Owner.jar")
        );
        TelemetryProjectRegistration downstream = new TelemetryProjectRegistration(
                descriptorWithStats("downstream-mod", "Downstream Mod"),
                "Example:Downstream Mod",
                "1.0.0",
                tempDir.resolve("Downstream Mod.jar")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.success(204),
                CrashReportClient.UploadResult.success(204)
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                owner,
                List.of(owner, downstream),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Owner", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Downstream Mod", "1.0.0")
                ),
                client,
                null,
                null,
                new EmbeddedTelemetryPlayerCounter()
        );

        service.emitStatsHeartbeatNow();

        service.shutdown();

        assertEquals(2, client.payloads.size());
        List<String> projectIds = client.payloads.stream()
                .map(JsonParser::parseString)
                .map(element -> element.getAsJsonObject().get("projectId").getAsString())
                .toList();
        assertTrue(projectIds.contains("embedded-owner"));
        assertTrue(projectIds.contains("downstream-mod"));
    }

    @Test
    void shutdownFlushesQueuedStatsHeartbeatWhenAsyncFlushHasNotRun() {
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
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptorWithStats(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        QueuedScheduledExecutor executor = new QueuedScheduledExecutor();
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                executor,
                new EmbeddedTelemetryPlayerCounter()
        );

        service.emitStatsHeartbeatNow();

        assertEquals(1, service.pendingReports());
        assertEquals(1, executor.queuedTasks());
        assertEquals(0, client.calls);

        service.shutdown();

        assertEquals(1, client.calls);
        assertEquals(0, service.pendingReports());
    }

    @Test
    void embeddedProjectEnabledControlPersistsAndBlocksCaptureImmediately() throws Exception {
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
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        assertTrue(service.setProjectEnabled(false));
        assertFalse(service.isEnabled());
        service.captureSetupFailure(testThrowable());
        service.recordError("embedded_event", null, "Embedded runtime event");

        assertEquals(0, service.pendingReports());
        assertEquals(0, service.flushPendingReportsNow("disabled").attempted());
        String overrideRaw = java.nio.file.Files.readString(dataPaths.projectOverrideFile("embedded-mod"));
        JsonObject override = JsonParser.parseString(overrideRaw).getAsJsonObject();
        assertFalse(override.get("enabled").getAsBoolean());
    }

    @Test
    void embeddedBreadcrumbControlPersistsAndSuppressesBreadcrumbPayloads() throws Exception {
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
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        assertTrue(service.setBreadcrumbsEnabled(false));
        service.recordBreadcrumb("bootstrap", "Should not be included.");
        service.captureSetupFailure(testThrowable());

        assertEquals(1, service.flushPendingReportsNow("breadcrumbs-disabled").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals(0, payload.getAsJsonArray("breadcrumbs").size());
        String overrideRaw = java.nio.file.Files.readString(dataPaths.projectOverrideFile("embedded-mod"));
        JsonObject override = JsonParser.parseString(overrideRaw).getAsJsonObject();
        assertFalse(override.getAsJsonObject("events").getAsJsonObject("breadcrumbs").get("enabled").getAsBoolean());
    }

    private static TelemetryProjectDescriptor descriptor() {
        return TelemetryProjectDescriptor.fromJson(
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
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
    }

    private static TelemetryProjectDescriptor descriptorWithStats() {
        return descriptorWithStats("embedded-mod", "Embedded Mod");
    }

    private static TelemetryProjectDescriptor descriptorWithStats(String projectId, String displayName) {
        return TelemetryProjectDescriptor.fromJson(
                String.format(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  },
                  "stats": {
                    "enabled": true
                  }
                }
                """,
                projectId,
                displayName),
                null
        );
    }

    private static RuntimeException testThrowable() {
        RuntimeException throwable = new RuntimeException("embedded setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.embedded.EmbeddedMod", "setup", "EmbeddedMod.java", 12)
        });
        return throwable;
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

    private static final class QueuedScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int queuedTasks() {
            return tasks.size();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("schedule");
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("schedule");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                      long initialDelay,
                                                      long period,
                                                      TimeUnit unit) {
            throw new UnsupportedOperationException("scheduleAtFixedRate");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                         long initialDelay,
                                                         long delay,
                                                         TimeUnit unit) {
            throw new UnsupportedOperationException("scheduleWithFixedDelay");
        }

    }
}
