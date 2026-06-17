package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryStatsHeartbeatServiceTest {

    @Test
    void usesFiveMinuteFixedHeartbeatInterval() {
        assertEquals(300, TelemetryStatsHeartbeatService.HEARTBEAT_INTERVAL_SECONDS);
    }

    @Test
    void emitsHeartbeatForEveryStatsRuntimeProject() {
        FakeStatsRuntime runtime = new FakeStatsRuntime(List.of(
                registration("example-mod", "Example Mod"),
                registration("second-mod", "Second Mod")
        ));
        TelemetryPlayerCounter players = new TelemetryPlayerCounter();
        players.markReady(UUID.randomUUID());

        new TelemetryStatsHeartbeatService(runtime, players, null, null).emitHeartbeatNow();

        assertEquals(List.of("example-mod", "second-mod"), runtime.projectIds);
        assertEquals(List.of(1, 1), runtime.playersOnlineValues);
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
        private final ArrayList<Integer> playersOnlineValues = new ArrayList<>();

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
        }
    }
}
