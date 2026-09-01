package com.alechilles.beacon.runtime.stats;

import com.alechilles.beacon.api.TelemetryEventContext;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryStatsHeartbeatServiceTest {

    @Test
    void usesThirtyMinuteStatsHeartbeatInterval() {
        assertEquals(1800, TelemetryStatsHeartbeatService.HEARTBEAT_INTERVAL_SECONDS);
    }

    @Test
    void schedulesFirstHeartbeatAfterStartupDelayAndThenThirtyMinuteJitteredCadence() {
        FakeStatsRuntime runtime = new FakeStatsRuntime(List.of(registration("example-mod", "Example Mod")));
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        TelemetryStatsHeartbeatService service = new TelemetryStatsHeartbeatService(runtime, new TelemetryPlayerCounter(), executor, null);

        service.start();

        assertEquals(1, executor.delaysSeconds.size());
        assertTrue(executor.delaysSeconds.get(0) >= 120);
        assertTrue(executor.delaysSeconds.get(0) <= 300);

        executor.runNext();

        assertEquals(2, executor.delaysSeconds.size());
        assertTrue(executor.delaysSeconds.get(1) >= 1500);
        assertTrue(executor.delaysSeconds.get(1) <= 2100);
    }

    @Test
    void emitsHeartbeatForEveryStatsRuntimeProject() {
        FakeStatsRuntime runtime = new FakeStatsRuntime(List.of(
                registration("example-mod", "Example Mod"),
                registration("second-mod", "Second Mod")
        ));
        TelemetryPlayerCounter players = new TelemetryPlayerCounter();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        players.markReady(first);
        players.markReady(second);
        players.markDisconnected(second);

        new TelemetryStatsHeartbeatService(runtime, players, null, null).emitHeartbeatNow();

        assertEquals(List.of("example-mod"), runtime.projectIds);
        assertEquals(List.of(1), runtime.playersOnlineValues);
        assertEquals(List.of(2), runtime.maxPlayersSinceLastReportValues);
        assertEquals(1.25, runtime.avgPlayersSinceLastReportValues.getFirst(), 0.01);
        assertEquals(List.of("example-mod", "second-mod"), runtime.aggregateProjectIds);
    }

    private static TelemetryProjectRegistration registration(String projectId, String displayName) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example"],
                  "stats": {
                    "enabled": true
                  }
                }
                """.formatted(projectId, displayName, displayName),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:" + displayName, "1.0.0", null);
    }

    private static final class FakeStatsRuntime implements TelemetryStatsRuntime {
        private final List<TelemetryProjectRegistration> projects;
        private final ArrayList<String> projectIds = new ArrayList<>();
        private final ArrayList<String> aggregateProjectIds = new ArrayList<>();
        private final ArrayList<Integer> playersOnlineValues = new ArrayList<>();
        private final ArrayList<Integer> maxPlayersSinceLastReportValues = new ArrayList<>();
        private final ArrayList<Double> avgPlayersSinceLastReportValues = new ArrayList<>();

        private FakeStatsRuntime(List<TelemetryProjectRegistration> projects) {
            this.projects = projects;
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> projects() {
            return projects;
        }

        @Override
        public void recordStatsWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nonnull TelemetryEventContext context) {
            projectIds.add(projectId);
            playersOnlineValues.add(((Number) context.details().get("playersOnline")).intValue());
            maxPlayersSinceLastReportValues.add(((Number) context.details().get("maxPlayersSinceLastReport")).intValue());
            avgPlayersSinceLastReportValues.add(((Number) context.details().get("avgPlayersSinceLastReport")).doubleValue());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> projects = (List<Map<String, Object>>) context.details().get("projects");
            projects.stream()
                    .map(project -> (String) project.get("projectId"))
                    .forEach(aggregateProjectIds::add);
        }

        @Override
        public int statsHeartbeatDelaySeconds(boolean firstHeartbeat) {
            return firstHeartbeat ? 180 : 1740;
        }
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final ArrayList<Long> delaysSeconds = new ArrayList<>();

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

        void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            delaysSeconds.add(TimeUnit.SECONDS.convert(delay, unit));
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
            throw new UnsupportedOperationException("scheduleWithFixedDelay");
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
