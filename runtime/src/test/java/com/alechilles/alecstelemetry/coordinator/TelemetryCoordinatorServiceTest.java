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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
        TelemetryProjectRegistration reportProject = new TelemetryProjectRegistration(
                reportDescriptor("report-mod", "Report Mod", "standalone"),
                "Example:Report Mod",
                "1.0.0",
                tempDir.resolve("Report.jar")
        );
        CrashReportEnvelope.LoadedModMetadata loadedMod =
                new CrashReportEnvelope.LoadedModMetadata("Example:Stats Mod", "7.8.9");
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscoveryResult(
                List.of(statsProject),
                List.of(reportProject),
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
        assertEquals(2, service.manualReportProjects().size());
        assertEquals("stats-mod", service.manualReportProjects().get(0).projectId());
        assertEquals("report-mod", service.manualReportProjects().get(1).projectId());
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

        assertEquals(1, service.flushPendingReportsNow("test").attempted());
        assertEquals(1, client.payloads.size());

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", payload.get("projectId").getAsString());
        assertEquals("runtime_stats", payload.get("source").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals(3, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
        var projects = payload.getAsJsonObject("details").getAsJsonArray("projects");
        assertEquals(2, projects.size());
        assertEquals("embedded-mod", projects.get(0).getAsJsonObject().get("projectId").getAsString());
        assertEquals("dependency-mod", projects.get(1).getAsJsonObject().get("projectId").getAsString());
        assertEquals("Example:Dependency Mod", projects.get(1).getAsJsonObject().get("pluginIdentifier").getAsString());
    }

    @Test
    void startQueuesStatsHeartbeatImmediately() {
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
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                executor,
                () -> 2
        );

        service.start();

        assertEquals(1, service.pendingReports(null));
        assertEquals(1, executor.queuedTasks());
        assertEquals(1, service.flushPendingReportsNow("startup-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
    }

    @Test
    void serverVerificationRequiresClaimToken() {
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
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null,
                () -> 2
        );

        TelemetryServerVerificationResult result = service.requestServerVerification();

        assertEquals(TelemetryServerVerificationResult.Status.MISSING_CLAIM_TOKEN, result.status());
        assertEquals(0, result.queuedHeartbeats());
        assertEquals(0, client.payloads.size());
    }

    @Test
    void serverVerificationQueuesClaimHeartbeatAndFlushes() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        Files.createDirectories(dataPaths.serverIdentityFile().getParent());
        Files.writeString(dataPaths.serverIdentityFile(), """
                {
                  "serverId": "550e8400-e29b-41d4-a716-446655440000",
                  "serverClaimToken": "ms_claim_test"
                }
                """);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                statsDescriptor("embedded-mod", "Embedded Mod", "embedded"),
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
                null,
                () -> 2
        );

        TelemetryServerVerificationResult result = service.requestServerVerification();

        assertEquals(TelemetryServerVerificationResult.Status.QUEUED, result.status());
        assertEquals(1, result.queuedHeartbeats());
        assertEquals(1, result.flushSummary().attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals("ms_claim_test", payload.getAsJsonObject("details").get("serverClaimToken").getAsString());
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

    private static TelemetryProjectDescriptor reportDescriptor(String projectId, String displayName, String runtimeMode) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "runtimeMode": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "reports": {
                    "enabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event",
                    "manualReportUrl": "https://example.invalid/telemetry/report"
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

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
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
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException("scheduleAtFixedRate");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new NoopScheduledFuture<>();
        }
    }

    private static final class NoopScheduledFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public V get() {
            return null;
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
