package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistrationSource;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import org.junit.jupiter.api.io.TempDir;
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

    @TempDir
    Path tempDir;

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
        assertEquals(
                List.of("activate", "start", "shutdown", "deactivate"),
                providerA.lifecycleEvents()
        );
        assertEquals(List.of("activate", "start"), providerB.lifecycleEvents());
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

    @Test
    void coordinatorHandoffReconstructsPassiveBaseAroundActiveContribution() {
        TelemetryProjectDescriptor declared = creditorDescriptor();
        TelemetryProjectRegistration passive = TelemetryProjectRegistration.passiveDescriptor(
                declared,
                tempDir.resolve("Creditor.jar"),
                "Example:Host",
                "3.0.0"
        );
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscoveryResult(
                List.of(passive),
                List.of(passive),
                List.of(),
                List.of(),
                List.of()
        );
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        ServiceProvider providerA = new ServiceProvider(
                "provider-a",
                "2.0.0",
                TelemetryCoordinatorService.fromDiscovery(
                        settings,
                        dataPaths(tempDir.resolve("provider-a"), settings),
                        discovery,
                        new NoopClient(),
                        null,
                        null,
                        null
                )
        );
        ServiceProvider providerB = new ServiceProvider(
                "provider-b",
                "1.0.0",
                TelemetryCoordinatorService.fromDiscovery(
                        settings,
                        dataPaths(tempDir.resolve("provider-b"), settings),
                        discovery,
                        new NoopClient(),
                        null,
                        null,
                        null
                )
        );

        TelemetryCoordinatorRegistry.register(providerA);
        assertEquals("provider-a", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertEquals(TelemetryProjectRegistrationSource.PASSIVE_DESCRIPTOR,
                providerA.service.projects().getFirst().source());

        String token = TelemetryProjectContributionRegistry.register(
                new RecordingContribution(creditorCandidate(declared))
        );
        assertTrue(providerA.reconcileCurrentContributions());
        assertEquals(TelemetryProjectRegistrationSource.CONTRIBUTION,
                providerA.service.projects().getFirst().source());
        assertTrue(providerA.service.projects().getFirst().usage().supported());

        TelemetryCoordinatorRegistry.register(providerB);
        TelemetryCoordinatorRegistry.unregister("provider-a");

        assertEquals("provider-b", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertEquals(1, providerB.service.projects().size());
        assertEquals(TelemetryProjectRegistrationSource.CONTRIBUTION,
                providerB.service.projects().getFirst().source());
        assertTrue(providerB.service.projects().getFirst().usage().supported());

        TelemetryProjectContributionRegistry.unregister(token);
        assertTrue(providerB.reconcileCurrentContributions());
        assertEquals(1, providerB.service.projects().size());
        assertEquals(TelemetryProjectRegistrationSource.PASSIVE_DESCRIPTOR,
                providerB.service.projects().getFirst().source());
        assertFalse(providerB.service.projects().getFirst().usage().supported());
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

    private static Map<String, Object> creditorCandidate(TelemetryProjectDescriptor descriptor) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("abiVersion", TelemetryProjectContributionRegistry.ABI_VERSION);
        values.put("projectId", descriptor.projectId());
        values.put("logicalPluginIdentifier", "Author:Creditor");
        values.put("logicalPluginVersion", "1.4.0");
        values.put("origin", "EMBEDDED");
        values.put("hostPluginIdentifier", "Example:Host");
        values.put("hostPluginVersion", "3.0.0");
        values.put("sourcePath", "Creditor.jar");
        values.put("descriptorJson", descriptor.toJson());
        values.put("descriptorHash", descriptor.canonicalHash());
        return Map.copyOf(values);
    }

    private static TelemetryProjectDescriptor creditorDescriptor() {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "creditor",
                  "projectVersion": "1.4.0",
                  "displayName": "Creditor",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Author:Creditor"],
                  "packagePrefixes": ["com.example.creditor"],
                  "usage": {
                    "supported": true,
                    "defaultEnabled": true,
                    "allowedEvents": ["credit_applied"]
                  },
                  "stats": {
                    "supported": true,
                    "defaultEnabled": true,
                    "allowedEvents": ["heartbeat"]
                  },
                  "defaults": {
                    "destinationMode": "hosted"
                  },
                  "hosted": {
                    "endpoint": "https://creditor.example/ingest/crash",
                    "eventEndpoint": "https://creditor.example/ingest/event"
                  }
                }
                """,
                null
        );
    }

    private static TelemetryDataPaths dataPaths(Path root, TelemetryRuntimeSettings settings) {
        return new TelemetryDataPaths(
                root,
                settings.filePath(),
                root.resolve("Settings").resolve("projects"),
                root.resolve("Telemetry"),
                root.resolve("Telemetry").resolve("crash-reports"),
                root.resolve("Telemetry").resolve("events"),
                root.resolve("Mods")
        );
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

        private RecordingContribution(Map<String, Object> candidate) {
            this.candidate = Map.copyOf(candidate);
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
        private final ArrayList<String> lifecycle = new ArrayList<>();
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
        @Override public void activate() { lifecycle.add("activate"); active = true; }
        @Override public void deactivate() { lifecycle.add("deactivate"); active = false; }
        @Override public boolean isActive() { return active; }
        @Override public void start() { lifecycle.add("start"); started = true; }
        @Override public void shutdown() { lifecycle.add("shutdown"); }
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

        private List<String> lifecycleEvents() {
            return List.copyOf(lifecycle);
        }
    }

    private static final class ServiceProvider implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final TelemetryCoordinatorService service;
        private boolean active;

        private ServiceProvider(String providerId,
                                String version,
                                TelemetryCoordinatorService service) {
            this.candidate = new TelemetryRuntimeCandidate(
                    providerId,
                    TelemetryRuntimeOrigin.EMBEDDED,
                    version,
                    TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                    "Example:" + providerId,
                    version,
                    Path.of(providerId + ".jar"),
                    Path.of(providerId, "Telemetry")
            );
            this.service = service;
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
        @Override public void start() { service.start(); }
        @Override public void shutdown() { service.shutdown(); }

        @Override
        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return service.reconcileProjectContributions(revision, contributions);
        }

        private boolean reconcileCurrentContributions() {
            return reconcileProjectContributions(
                    TelemetryProjectContributionRegistry.revision(),
                    TelemetryProjectContributionRegistry.activeContributions()
            );
        }
    }

    private static final class NoopClient implements CrashReportClient {
        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            return UploadResult.success(204);
        }
    }
}
