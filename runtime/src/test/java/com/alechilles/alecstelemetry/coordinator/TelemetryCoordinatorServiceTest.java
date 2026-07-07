package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerIntervalSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        IntSupplier onlinePlayers = () -> 7;
        TelemetryCoordinatorService service = TelemetryCoordinatorService.fromDiscovery(
                settings,
                dataPaths,
                discovery,
                client,
                null,
                executor,
                onlinePlayers
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

        service.start();
        executor.runNext();
        executor.runNext();

        assertEquals(1, service.flushPendingReportsNow("legacy-discovery-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        JsonObject details = payload.getAsJsonObject("details");
        assertEquals(7, details.get("playersOnline").getAsInt());
        assertEquals(7, details.get("maxPlayersSinceLastReport").getAsInt());
        assertEquals(7.0, details.get("avgPlayersSinceLastReport").getAsDouble(), 0.01);
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
    void rateLimitedEventUploadStopsCurrentFlushPassAndLeavesQueueIntact() {
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
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(429, "rate_limited", 42, "events", null),
                CrashReportClient.UploadResult.success(204)
        );
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

        assertTrue(service.recordUsage("embedded-mod", "settings_opened", Map.of("source", "one")));
        assertTrue(service.recordUsage("embedded-mod", "settings_opened", Map.of("source", "two")));

        TelemetryCoreEngine.FlushSummary summary = service.flushPendingReportsNow("test", "embedded-mod");

        assertEquals(1, summary.attempted());
        assertEquals(0, summary.uploaded());
        assertEquals(2, summary.pendingAfter());
        assertTrue(summary.lastFailure().contains("rate limited"));
        assertEquals(1, client.calls);
    }

    @Test
    void statsUploadHintUpdatesRecommendedHeartbeatInterval() {
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
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.success(202, "stats", 180)
        );
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );

        engine.recordAggregateStatsHeartbeat(List.of(embedded), 3);
        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow("test");

        assertEquals(1, summary.uploaded());
        assertEquals(300, engine.statsHeartbeatIntervalSeconds());
    }

    @Test
    void failedUploadStopsPassAndThrottlesAutomaticRetries() {
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
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(500, "server timeout"),
                CrashReportClient.UploadResult.success(204)
        );
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

        assertTrue(service.recordUsage("embedded-mod", "settings_opened", Map.of("source", "one")));
        assertTrue(service.recordUsage("embedded-mod", "settings_opened", Map.of("source", "two")));

        TelemetryCoreEngine.FlushSummary failed = service.flushPendingReportsNow("event", "embedded-mod");
        TelemetryCoreEngine.FlushSummary throttled = service.flushPendingReportsNow("periodic", "embedded-mod");
        TelemetryCoreEngine.FlushSummary manual = service.flushPendingReportsNow("manual", "embedded-mod");

        assertEquals(1, failed.attempted());
        assertEquals(0, failed.uploaded());
        assertEquals(2, failed.pendingAfter());
        assertTrue(failed.lastFailure().contains("server timeout"));
        assertEquals(0, throttled.attempted());
        assertEquals(2, throttled.pendingAfter());
        assertTrue(throttled.lastFailure().contains("Upload retry cooldown active"));
        assertEquals(2, manual.attempted());
        assertEquals(2, manual.uploaded());
        assertEquals(3, client.calls);
    }

    @Test
    void coordinatorStatsHeartbeatSchedulesWithCurrentEngineInterval() {
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
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );
        engine.applyStatsHeartbeatIntervalHint(180);
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        TelemetryCoordinatorStatsHeartbeat heartbeat = new TelemetryCoordinatorStatsHeartbeat(engine, () -> 3, executor, null);

        heartbeat.start();

        assertTrue(executor.lastScheduledDelaySeconds() >= 120);
        assertTrue(executor.lastScheduledDelaySeconds() <= 300);
    }

    @Test
    void coordinatorStatsHeartbeatPreservesIntervalPeakAndAverage() {
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
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(embedded),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );
        TelemetryCoordinatorStatsHeartbeat heartbeat = new TelemetryCoordinatorStatsHeartbeat(
                engine,
                () -> new TelemetryPlayerIntervalSnapshot(1, 5, 2.75),
                null,
                null
        );

        heartbeat.emitHeartbeatNow();
        engine.flushPendingReportsNow("test-coordinator-interval-stats");

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        JsonObject details = payload.getAsJsonObject("details");
        assertEquals(1, details.get("playersOnline").getAsInt());
        assertEquals(5, details.get("maxPlayersSinceLastReport").getAsInt());
        assertEquals(2.75, details.get("avgPlayersSinceLastReport").getAsDouble(), 0.01);
    }

    @Test
    void concurrentManualReportReviewAccessInitializesStoresSafely() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryDataPaths dataPaths = dataPaths(settings);
        ArrayList<TelemetryProjectRegistration> reportProjects = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            reportProjects.add(new TelemetryProjectRegistration(
                    reportDescriptor("report-mod-" + i, "Report Mod " + i, "standalone"),
                    "Example:Report Mod " + i,
                    "1.0.0",
                    tempDir.resolve("Report-" + i + ".jar")
            ));
        }
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(),
                reportProjects,
                List.of(),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );
        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int pass = 0; pass < 25; pass++) {
                            engine.manualReportsForReview(1);
                        }
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertNull(failure.get());
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
    void startSchedulesStatsHeartbeatAfterStartupDelay() {
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

        assertEquals(0, service.pendingReports(null));
        assertEquals(2, executor.queuedTasks());
        assertTrue(executor.lastScheduledDelaySeconds() >= 120);
        assertTrue(executor.lastScheduledDelaySeconds() <= 300);

        executor.runNext();
        executor.runNext();

        assertEquals(1, service.flushPendingReportsNow("startup-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
    }

    @Test
    void shutdownQueuesBestEffortStatsHeartbeatAfterStart() {
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
        service.shutdown();

        assertEquals(1, client.payloads.size());
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
    void serverVerificationPersistsSuppliedClaimTokenAndQueuesHeartbeat() throws Exception {
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

        TelemetryServerVerificationResult result = service.requestServerVerification("ms_claim_from_command");

        assertEquals(TelemetryServerVerificationResult.Status.QUEUED, result.status());
        JsonObject identity = JsonParser.parseString(Files.readString(dataPaths.serverIdentityFile())).getAsJsonObject();
        assertEquals("ms_claim_from_command", identity.get("serverClaimToken").getAsString());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("ms_claim_from_command", payload.getAsJsonObject("details").get("serverClaimToken").getAsString());
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
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "events": {
                    "errors": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
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

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private long lastScheduledDelaySeconds = -1;

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

        void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }

        long lastScheduledDelaySeconds() {
            return lastScheduledDelaySeconds;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastScheduledDelaySeconds = TimeUnit.SECONDS.convert(delay, unit);
            tasks.add(command);
            return new NoopScheduledFuture<>();
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
