package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryContributionProviderHandoffIT {

    @AfterEach
    void clearRegistries() {
        TelemetryCoordinatorRegistry.clearForTests();
        TelemetryProjectContributionRegistry.clearForTests();
    }

    @Test
    void replaysCompleteContributionSnapshotBeforeActivatingNewProvider() {
        RecordingContribution contribution = new RecordingContribution("library", "1.0.0");
        String token = TelemetryProjectContributionRegistry.register(contribution);
        assertTrue(TelemetryProjectContributionRegistry.isActive(token));

        RecordingProvider providerA = new RecordingProvider("provider-a", "1.0.0");
        TelemetryCoordinatorRegistry.register(providerA);
        assertEquals("provider-a", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertEquals(List.of("library"), providerA.replayedProjectIds());

        RecordingProvider providerB = new RecordingProvider("provider-b", "2.0.0");
        TelemetryCoordinatorRegistry.register(providerB);

        assertEquals("provider-b", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertFalse(providerA.active);
        assertTrue(providerB.active);
        assertEquals(List.of("library"), providerB.replayedProjectIds());
        assertTrue(providerB.replayedBeforeStart);
    }

    @Test
    void failedReplayLeavesPreviousProviderActive() {
        RecordingContribution contribution = new RecordingContribution("library", "1.0.0");
        TelemetryProjectContributionRegistry.register(contribution);

        RecordingProvider providerA = new RecordingProvider("provider-a", "1.0.0");
        TelemetryCoordinatorRegistry.register(providerA);
        RecordingProvider rejecting = new RecordingProvider("provider-b", "2.0.0");
        rejecting.rejectReplay = true;
        TelemetryCoordinatorRegistry.register(rejecting);

        assertEquals("provider-a", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertTrue(providerA.active);
        assertFalse(rejecting.active);
    }

    private static Map<String, Object> candidate(String projectId,
                                                 String version,
                                                 String origin,
                                                 String hostIdentifier,
                                                 String sourcePath) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("abiVersion", TelemetryProjectContributionRegistry.ABI_VERSION);
        values.put("projectId", projectId);
        values.put("logicalPluginIdentifier", "Example:" + projectId);
        values.put("logicalPluginVersion", version);
        values.put("origin", origin);
        values.put("hostPluginIdentifier", hostIdentifier);
        values.put("hostPluginVersion", "1.0.0");
        values.put("sourcePath", sourcePath);
        values.put("descriptorJson", "{\"projectId\":\"" + projectId + "\",\"displayName\":\"Library\",\"ownerPluginIdentifiers\":[\"Example:" + projectId + "\"]}");
        values.put("descriptorHash", projectId + "-hash");
        return Map.copyOf(values);
    }

    private static final class RecordingContribution implements TelemetryProjectContributionBridge {
        private final Map<String, Object> candidate;

        private RecordingContribution(String projectId, String version) {
            this.candidate = TelemetryContributionProviderHandoffIT.candidate(
                    projectId,
                    version,
                    "EMBEDDED",
                    "Example:Host",
                    projectId + ".jar"
            );
        }

        @Override
        public int contributionAbiVersion() {
            return TelemetryProjectContributionRegistry.ABI_VERSION;
        }

        @Override
        public Map<String, Object> candidate() {
            return candidate;
        }

        @Override
        public Map<String, Object> dispatch(String token, String operation, Map<String, Object> payload, Throwable throwable) {
            return Map.of("accepted", true);
        }
    }

    private static final class RecordingProvider implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final ArrayList<String> replayed = new ArrayList<>();
        private boolean active;
        private boolean started;
        private boolean replayedBeforeStart;
        private boolean rejectReplay;

        private RecordingProvider(String providerId, String version) {
            this.candidate = new TelemetryRuntimeCandidate(
                    providerId,
                    TelemetryRuntimeOrigin.EMBEDDED,
                    version,
                    TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                    "Example:" + providerId,
                    version,
                    Path.of(providerId + ".jar"),
                    Path.of("Telemetry")
            );
        }

        @Override public String providerId() { return candidate.providerId(); }
        @Override public String origin() { return candidate.origin().name(); }
        @Override public String runtimeVersion() { return candidate.runtimeVersion(); }
        @Override public int coordinatorProtocolVersion() { return candidate.coordinatorProtocolVersion(); }
        @Override public String providerPluginIdentifier() { return candidate.providerPluginIdentifier(); }
        @Override public String providerPluginVersion() { return candidate.providerPluginVersion(); }
        @Override public String sourcePath() { return candidate.sourcePath().toString(); }
        @Override public String sharedDataRoot() { return candidate.sharedDataRoot().toString(); }
        @Override public void activate() { active = true; }
        @Override public void deactivate() { active = false; }
        @Override public boolean isActive() { return active; }
        @Override public void start() { started = true; }
        @Override public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            if (started) {
                replayedBeforeStart = false;
            } else {
                replayedBeforeStart = true;
            }
            if (rejectReplay) {
                return false;
            }
            replayed.clear();
            for (Map<String, Object> contribution : contributions) {
                Object projectId = contribution.get("projectId");
                if (projectId != null) {
                    replayed.add(projectId.toString());
                }
            }
            return true;
        }

        private List<String> replayedProjectIds() {
            return List.copyOf(replayed);
        }
    }
}
