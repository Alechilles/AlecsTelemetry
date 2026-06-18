package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoordinatorRegistryTest {

    @AfterEach
    void clearRegistry() {
        TelemetryCoordinatorRegistry.clearForTests();
    }

    @Test
    void electsLatestCompatibleCandidate() {
        RecordingBridge standalone = bridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");
        ForeignBridge embedded = foreignBridge("embedded", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");

        TelemetryCoordinatorRegistry.register(standalone);
        TelemetryCoordinatorRegistry.register(embedded);

        assertEquals("embedded", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertFalse(standalone.active);
        assertTrue(embedded.active);
    }

    @Test
    void forwardsToActiveBridgeWithJdkOnlyArguments() {
        ForeignBridge embedded = foreignBridge("embedded", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");
        TelemetryCoordinatorRegistry.register(embedded);

        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        assertTrue(active.recordUsage("alecs-tamework", "settings_page_opened", Map.of("source", "api")));
        assertTrue(active.setProjectEnabled("alecs-tamework", false));
        assertTrue(active.setBreadcrumbsEnabled("alecs-tamework", false));
        RuntimeException throwable = new RuntimeException("world crashed");
        assertTrue(active.captureExceptionalWorldRemoval(
                throwable,
                "Default",
                "EXCEPTIONAL",
                "Alechilles:Alec's Tamework!"
        ));

        assertEquals("alecs-tamework", embedded.lastProjectId);
        assertEquals("settings_page_opened", embedded.lastEventName);
        assertEquals("api", embedded.lastDetails.get("source"));
        assertEquals(throwable, embedded.lastWorldRemovalThrowable);
        assertEquals("Default", embedded.lastWorldName);
        assertEquals("EXCEPTIONAL", embedded.lastWorldRemovalReason);
        assertEquals("Alechilles:Alec's Tamework!", embedded.lastWorldFailurePluginIdentifier);
        assertFalse(embedded.projectEnabled);
        assertFalse(embedded.breadcrumbsEnabled);
    }

    @Test
    void ignoresIncompatibleNewerCandidate() {
        RecordingBridge standalone = bridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");
        ForeignBridge incompatible = foreignBridge(
                "embedded",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION + 1
        );

        TelemetryCoordinatorRegistry.register(standalone);
        TelemetryCoordinatorRegistry.register(incompatible);

        assertEquals("standalone", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertTrue(standalone.active);
        assertFalse(incompatible.active);
    }

    @Test
    void ignoresLegacyProtocolOneCandidateAfterConsentBridgeProtocolBump() {
        ForeignBridge legacy = foreignBridge("legacy", TelemetryRuntimeOrigin.EMBEDDED, "9.0.0", 1);

        TelemetryCoordinatorRegistry.register(legacy);

        assertNull(TelemetryCoordinatorRegistry.activeBridge());
        assertFalse(legacy.active);
    }

    @Test
    void shutsDownOldCoordinatorBeforeStartingNewWinner() {
        ArrayList<String> lifecycle = new ArrayList<>();
        OrderedBridge standalone = orderedBridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3", lifecycle);
        OrderedBridge embedded = orderedBridge("embedded", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4", lifecycle);

        TelemetryCoordinatorRegistry.register(standalone);
        assertEquals(List.of("standalone:start"), lifecycle);

        lifecycle.clear();
        TelemetryCoordinatorRegistry.register(embedded);

        assertEquals(List.of("standalone:shutdown", "embedded:start"), lifecycle);
    }

    private static RecordingBridge bridge(String providerId, TelemetryRuntimeOrigin origin, String runtimeVersion) {
        return new RecordingBridge(candidate(providerId, origin, runtimeVersion, TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION));
    }

    private static OrderedBridge orderedBridge(String providerId,
                                               TelemetryRuntimeOrigin origin,
                                               String runtimeVersion,
                                               ArrayList<String> lifecycle) {
        return new OrderedBridge(
                candidate(providerId, origin, runtimeVersion, TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION),
                lifecycle
        );
    }

    private static ForeignBridge foreignBridge(String providerId, TelemetryRuntimeOrigin origin, String runtimeVersion) {
        return foreignBridge(providerId, origin, runtimeVersion, TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION);
    }

    private static ForeignBridge foreignBridge(String providerId,
                                               TelemetryRuntimeOrigin origin,
                                               String runtimeVersion,
                                               int protocolVersion) {
        return new ForeignBridge(candidate(providerId, origin, runtimeVersion, protocolVersion));
    }

    private static TelemetryRuntimeCandidate candidate(String providerId,
                                                       TelemetryRuntimeOrigin origin,
                                                       String runtimeVersion,
                                                       int protocolVersion) {
        return new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                protocolVersion,
                "Alechilles:" + providerId,
                "1.0.0",
                Path.of(providerId + ".jar"),
                Path.of("Telemetry")
        );
    }

    private static class RecordingBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private boolean active;

        private RecordingBridge(TelemetryRuntimeCandidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public String providerId() {
            return candidate.providerId();
        }

        @Override
        public String origin() {
            return candidate.origin().name();
        }

        @Override
        public String runtimeVersion() {
            return candidate.runtimeVersion();
        }

        @Override
        public int coordinatorProtocolVersion() {
            return candidate.coordinatorProtocolVersion();
        }

        @Override
        public String providerPluginIdentifier() {
            return candidate.providerPluginIdentifier();
        }

        @Override
        public String providerPluginVersion() {
            return candidate.providerPluginVersion();
        }

        @Override
        public String sourcePath() {
            return candidate.sourcePath().toString();
        }

        @Override
        public String sharedDataRoot() {
            return candidate.sharedDataRoot().toString();
        }

        @Override
        public void activate() {
            active = true;
        }

        @Override
        public void deactivate() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    private static final class OrderedBridge extends RecordingBridge {
        private final ArrayList<String> lifecycle;

        private OrderedBridge(TelemetryRuntimeCandidate candidate, ArrayList<String> lifecycle) {
            super(candidate);
            this.lifecycle = lifecycle;
        }

        @Override
        public void start() {
            lifecycle.add(providerId() + ":start");
        }

        @Override
        public void shutdown() {
            lifecycle.add(providerId() + ":shutdown");
        }
    }

    @SuppressWarnings("unused")
    private static final class ForeignBridge {
        private final TelemetryRuntimeCandidate candidate;
        private boolean active;
        private String lastProjectId;
        private String lastEventName;
        private boolean projectEnabled = true;
        private boolean breadcrumbsEnabled = true;
        private Map<String, Object> lastDetails = Map.of();
        private Throwable lastWorldRemovalThrowable;
        private String lastWorldName;
        private String lastWorldRemovalReason;
        private String lastWorldFailurePluginIdentifier;

        private ForeignBridge(TelemetryRuntimeCandidate candidate) {
            this.candidate = candidate;
        }

        public String providerId() {
            return candidate.providerId();
        }

        public String origin() {
            return candidate.origin().name();
        }

        public String runtimeVersion() {
            return candidate.runtimeVersion();
        }

        public int coordinatorProtocolVersion() {
            return candidate.coordinatorProtocolVersion();
        }

        public String providerPluginIdentifier() {
            return candidate.providerPluginIdentifier();
        }

        public String providerPluginVersion() {
            return candidate.providerPluginVersion();
        }

        public String sourcePath() {
            return candidate.sourcePath().toString();
        }

        public String sharedDataRoot() {
            return candidate.sharedDataRoot().toString();
        }

        public void activate() {
            active = true;
        }

        public void deactivate() {
            active = false;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isProjectEnabled(String projectId) {
            return projectEnabled;
        }

        public boolean setProjectEnabled(String projectId, boolean enabled) {
            projectEnabled = enabled;
            return true;
        }

        public boolean setBreadcrumbsEnabled(String projectId, boolean enabled) {
            breadcrumbsEnabled = enabled;
            return true;
        }

        public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
            lastProjectId = projectId;
            lastEventName = eventName;
            lastDetails = details;
            return true;
        }

        public boolean captureExceptionalWorldRemoval(Throwable throwable,
                                                      String worldName,
                                                      String removalReason,
                                                      String possibleFailureCause) {
            lastWorldRemovalThrowable = throwable;
            lastWorldName = worldName;
            lastWorldRemovalReason = removalReason;
            lastWorldFailurePluginIdentifier = possibleFailureCause;
            return true;
        }
    }
}
