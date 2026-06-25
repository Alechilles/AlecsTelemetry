package com.alechilles.alecstelemetry.runtime.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeStatsHeartbeatServiceTest {

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
                    "enabled": true
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
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
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

        new TelemetryStatsHeartbeatService(TelemetryStatsRuntime.from(new CoreRuntimeOperations(engine)), counter, null, null).emitHeartbeatNow();

        assertEquals(1, engine.flushPendingReportsNow("test-stats-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals("stats", payload.get("featureKey").getAsString());
        assertEquals("heartbeat", payload.get("entryPoint").getAsString());
        assertEquals("server", payload.get("runtimeSide").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
    }

    @Test
    void heartbeatQueuesOneAggregateStatsEventForAllProjects() {
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
        TelemetryProjectRegistration first = statsRegistration("first-mod", "First Mod");
        TelemetryProjectRegistration second = statsRegistration("second-mod", "Second Mod");
        CapturingClient client = new CapturingClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(first, second),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:First Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Second Mod", "1.0.0")
                ),
                client,
                null,
                null
        );
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        counter.markReady(UUID.randomUUID());

        new TelemetryStatsHeartbeatService(TelemetryStatsRuntime.from(new CoreRuntimeOperations(engine)), counter, null, null).emitHeartbeatNow();

        assertEquals(1, engine.flushPendingReportsNow("test-aggregate-stats-heartbeat").attempted());
        assertEquals(1, client.payloads.size());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("first-mod", payload.get("projectId").getAsString());
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals(1, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
        var projects = payload.getAsJsonObject("details").getAsJsonArray("projects");
        assertEquals(2, projects.size());
        assertEquals("first-mod", projects.get(0).getAsJsonObject().get("projectId").getAsString());
        assertEquals("second-mod", projects.get(1).getAsJsonObject().get("projectId").getAsString());
        assertEquals("Example:Second Mod", projects.get(1).getAsJsonObject().get("pluginIdentifier").getAsString());
    }

    @Test
    void heartbeatSkipsStatsEmissionWhenRuntimeDisallowsHeartbeat() {
        TelemetryProjectRegistration registration = statsRegistration("passive-mod", "Passive Mod");
        java.util.ArrayList<String> emittedProjectIds = new java.util.ArrayList<>();
        TelemetryStatsRuntime runtime = new TelemetryStatsRuntime() {
            @Nonnull
            @Override
            public List<TelemetryProjectRegistration> projects() {
                return List.of(registration);
            }

            @Override
            public boolean canEmitHeartbeat() {
                return false;
            }

            @Override
            public void recordStatsWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               @Nonnull TelemetryEventContext context) {
                emittedProjectIds.add(projectId);
            }
        };

        new TelemetryStatsHeartbeatService(runtime, new TelemetryPlayerCounter(), null, null).emitHeartbeatNow();

        assertEquals(List.of(), emittedProjectIds);
    }

    @Test
    void startSchedulesFirstHeartbeatAfterStartupDelay() {
        TelemetryProjectRegistration registration = statsRegistration("startup-mod", "Startup Mod");
        java.util.ArrayList<String> emittedProjectIds = new java.util.ArrayList<>();
        TelemetryStatsRuntime runtime = new TelemetryStatsRuntime() {
            @Nonnull
            @Override
            public List<TelemetryProjectRegistration> projects() {
                return List.of(registration);
            }

            @Override
            public boolean canEmitHeartbeat() {
                return true;
            }

            @Override
            public void recordStatsWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               @Nonnull TelemetryEventContext context) {
                emittedProjectIds.add(projectId + ":" + eventName);
            }
        };

        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        new TelemetryStatsHeartbeatService(runtime, new TelemetryPlayerCounter(), executor, null).start();

        assertEquals(List.of(), emittedProjectIds);
        assertEquals(List.of(120L), executor.delays);
        assertEquals(List.of(TimeUnit.SECONDS), executor.units);
    }

    @Test
    void projectHandleDoesNotQueueCustomChartSamples() {
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
                    "enabled": true
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
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = new TelemetryRuntimeApiImpl(new CoreRuntimeOperations(engine)).findProject("example-mod");
        handle.recordStatsWithContext(
                "chart_sample",
                com.alechilles.alecstelemetry.api.TelemetryEventContext.stats()
                        .featureKey("stats")
                        .entryPoint("custom_chart")
                        .runtimeSide("server")
                        .detail("chartId", "language")
                        .detail("value", "English")
                        .build()
        );

        assertEquals(0, engine.flushPendingReportsNow("test-chart-sample").attempted());
        assertEquals(0, client.payloads.size());
    }

    private static final class CoreRuntimeOperations implements TelemetryRuntimeOperations {
        private final TelemetryCoreEngine engine;

        private CoreRuntimeOperations(TelemetryCoreEngine engine) {
            this.engine = engine;
        }

        @Override
        public boolean isEnabled() {
            return engine.isEnabled();
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> projects() {
            return engine.projects();
        }

        @Nullable
        @Override
        public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
            return engine.findProject(projectId);
        }

        @Override
        public boolean isProjectEnabled(@Nonnull String projectId) {
            return engine.isProjectEnabled(projectId);
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            return engine.triggerFlushAsync(projectId);
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return false;
        }

        @Override
        public boolean openReportPage(@Nonnull String projectId,
                                      @Nonnull Ref<EntityStore> playerEntityRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull TelemetryReportOpenRequest request) {
            return false;
        }

        @Override
        public void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
            engine.recordBreadcrumb(projectId, category, detail);
        }

        @Override
        public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            engine.captureSetupFailure(projectId, throwable);
        }

        @Override
        public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            engine.captureStartFailure(projectId, throwable);
        }

        @Override
        public void recordErrorWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable Throwable throwable,
                                           @Nullable TelemetryEventContext context) {
            engine.recordErrorWithContext(projectId, eventName, throwable, context);
        }

        @Override
        public void recordLifecycleWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               int durationMs,
                                               boolean success,
                                               @Nullable TelemetryEventContext context) {
            engine.recordLifecycleWithContext(projectId, eventName, durationMs, success, context);
        }

        @Override
        public void recordPerformanceWithContext(@Nonnull String projectId,
                                                 @Nonnull String eventName,
                                                 int durationMs,
                                                 @Nullable Double metricValue,
                                                 @Nullable TelemetryEventContext context) {
            engine.recordPerformanceWithContext(projectId, eventName, durationMs, metricValue, context);
        }

        @Override
        public void recordUsageWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable TelemetryEventContext context) {
            engine.recordUsageWithContext(projectId, eventName, context);
        }

        @Override
        public void recordStatsWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable TelemetryEventContext context) {
            engine.recordStatsWithContext(projectId, eventName, context);
        }

        @Override
        public void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                                  @Nonnull TelemetryPlayerIntervalSnapshot players) {
            engine.recordAggregateStatsHeartbeat(projects, players);
        }

        @Override
        public void recordAggregateStatsHeartbeat(@Nonnull List<TelemetryProjectRegistration> projects,
                                                  int playersOnline) {
            engine.recordAggregateStatsHeartbeat(projects, playersOnline);
        }
    }

    private static TelemetryProjectRegistration statsRegistration(String projectId, String displayName) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "stats": {
                    "enabled": true
                  }
                }
                """.formatted(projectId, displayName, displayName),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:" + displayName, "1.0.0", null);
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

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final java.util.ArrayList<Long> delays = new java.util.ArrayList<>();
        private final java.util.ArrayList<TimeUnit> units = new java.util.ArrayList<>();

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
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            delays.add(delay);
            units.add(unit);
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
