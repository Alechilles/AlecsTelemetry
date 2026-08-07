package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectContributionRegistryTest {

    @AfterEach
    void clearRegistry() {
        TelemetryProjectContributionRegistry.clearForTests();
    }

    @Test
    void higherLogicalSemanticVersionWins() {
        RecordingBridge old = bridge("library", "1.9.0", "host-a", "old.jar", "hash-a");
        RecordingBridge current = bridge("library", "2.0.0", "host-b", "new.jar", "hash-b");

        String oldToken = TelemetryProjectContributionRegistry.register(old);
        String currentToken = TelemetryProjectContributionRegistry.register(current);

        assertFalse(TelemetryProjectContributionRegistry.isActive(oldToken));
        assertTrue(TelemetryProjectContributionRegistry.isActive(currentToken));
        assertEquals(currentToken, activeToken("library"));
    }

    @Test
    void standaloneWinsEqualVersionTieOverEmbedded() {
        RecordingBridge embedded = bridge("library", "2.0.0", "host-a", "embedded.jar", "hash-a", "EMBEDDED");
        RecordingBridge standalone = bridge("library", "2.0.0", "host-z", "standalone.jar", "hash-z", "STANDALONE");

        String embeddedToken = TelemetryProjectContributionRegistry.register(embedded);
        String standaloneToken = TelemetryProjectContributionRegistry.register(standalone);

        assertFalse(TelemetryProjectContributionRegistry.isActive(embeddedToken));
        assertTrue(TelemetryProjectContributionRegistry.isActive(standaloneToken));
        assertEquals(standaloneToken, activeToken("library"));
    }

    @Test
    void hostSourceAndDescriptorHashMakeEqualCandidatesDeterministic() {
        RecordingBridge hostB = bridge("library", "2.0.0", "Host-B", "z.jar", "zzz");
        RecordingBridge hostA = bridge("library", "2.0.0", "host-a", "z.jar", "zzz");
        RecordingBridge sourceA = bridge("library", "2.0.0", "host-a", "a.jar", "zzz");
        RecordingBridge hashA = bridge("library", "2.0.0", "host-a", "a.jar", "aaa");

        TelemetryProjectContributionRegistry.register(hostB);
        TelemetryProjectContributionRegistry.register(hostA);
        TelemetryProjectContributionRegistry.register(sourceA);
        String hashAToken = TelemetryProjectContributionRegistry.register(hashA);

        assertEquals(hashAToken, activeToken("LIBRARY"));
    }

    @Test
    void retiringWinnerFencesItsTokenBeforeFallbackBecomesWritable() {
        RecordingBridge fallback = bridge("library", "1.0.0", "host-a", "fallback.jar", "hash-a");
        RecordingBridge winner = bridge("library", "2.0.0", "host-a", "winner.jar", "hash-b");

        String fallbackToken = TelemetryProjectContributionRegistry.register(fallback);
        String winnerToken = TelemetryProjectContributionRegistry.register(winner);
        TelemetryProjectContributionRegistry.unregister(winnerToken);

        assertFalse((boolean) TelemetryProjectContributionRegistry
                .dispatch(winnerToken, "usage", Map.of("eventName", "reload"), null)
                .get("accepted"));
        assertTrue((boolean) TelemetryProjectContributionRegistry
                .dispatch(fallbackToken, "usage", Map.of("eventName", "reload"), null)
                .get("accepted"));
        assertEquals(List.of(fallbackToken), fallback.acceptedTokens());
        assertEquals(List.of(), winner.acceptedTokens());
    }

    @Test
    void differentLogicalOwnerIsQuarantinedAfterOwnerLineageIsEstablished() {
        RecordingBridge firstOwner = bridge("shared-project", "1.0.0", "owner-a", "a.jar", "hash-a", "EMBEDDED", 1, "owner-a");
        RecordingBridge differentOwner = bridge("SHARED-PROJECT", "9.0.0", "owner-b", "b.jar", "hash-b", "EMBEDDED", 1, "owner-b");

        String firstToken = TelemetryProjectContributionRegistry.register(firstOwner);
        String quarantinedToken = TelemetryProjectContributionRegistry.register(differentOwner);

        assertTrue(TelemetryProjectContributionRegistry.isActive(firstToken));
        assertFalse(TelemetryProjectContributionRegistry.isActive(quarantinedToken));
        assertEquals(firstToken, activeToken("shared-project"));
        assertTrue(TelemetryProjectContributionRegistry.diagnostics().stream()
                .anyMatch(diagnostic -> "owner_conflict".equals(diagnostic.get("kind"))));
    }

    @Test
    void sameVersionDescriptorDriftElectsOneSnapshotAndReportsWarning() {
        RecordingBridge first = bridge("library", "2.0.0", "owner", "a.jar", "hash-a");
        RecordingBridge drift = bridge("library", "2.0.0", "owner", "b.jar", "hash-b");

        String firstToken = TelemetryProjectContributionRegistry.register(first);
        String driftToken = TelemetryProjectContributionRegistry.register(drift);

        assertEquals(firstToken, activeToken("library"));
        assertFalse(TelemetryProjectContributionRegistry.isActive(driftToken));
        assertTrue(TelemetryProjectContributionRegistry.diagnostics().stream()
                .anyMatch(diagnostic -> "descriptor_drift".equals(diagnostic.get("kind"))
                        && "library".equals(diagnostic.get("projectId"))));
        Map<String, Object> active = TelemetryProjectContributionRegistry.activeContributions().get(0);
        assertEquals("hash-a", active.get("descriptorHash"));
        assertEquals("a.jar", active.get("sourcePath"));
    }

    @Test
    void incompatibleAbiAndInvalidSemanticVersionRemainPassive() {
        RecordingBridge compatible = bridge("library", "1.0.0", "owner", "good.jar", "hash-a");
        RecordingBridge incompatible = bridge("future", "99.0.0", "owner", "future.jar", "hash-b", "EMBEDDED", 2);
        RecordingBridge invalidVersion = bridge("invalid", "not-semver", "owner", "invalid.jar", "hash-c");

        String compatibleToken = TelemetryProjectContributionRegistry.register(compatible);
        String incompatibleToken = TelemetryProjectContributionRegistry.register(incompatible);
        String invalidToken = TelemetryProjectContributionRegistry.register(invalidVersion);

        assertTrue(TelemetryProjectContributionRegistry.isActive(compatibleToken));
        assertFalse(TelemetryProjectContributionRegistry.isActive(incompatibleToken));
        assertFalse(TelemetryProjectContributionRegistry.isActive(invalidToken));
        assertEquals(List.of("library"), TelemetryProjectContributionRegistry.activeContributions().stream()
                .map(candidate -> candidate.get("projectId").toString())
                .toList());
    }

    @Test
    void activeSnapshotIsDefensiveAndDoesNotExposeRawBridge() {
        TelemetryProjectContributionRegistry.register(
                bridge("library", "1.0.0", "owner", "library.jar", "hash-a")
        );

        List<Map<String, Object>> active = TelemetryProjectContributionRegistry.activeContributions();

        assertFalse(active.get(0).containsKey("bridge"));
        assertThrows(UnsupportedOperationException.class,
                () -> active.get(0).put("projectId", "mutated"));
        assertThrows(UnsupportedOperationException.class, () -> active.clear());
    }

    private static String activeToken(String projectId) {
        return TelemetryProjectContributionRegistry.activeContributions().stream()
                .filter(candidate -> projectId.equalsIgnoreCase(candidate.get("projectId").toString()))
                .findFirst()
                .map(candidate -> candidate.get("token").toString())
                .orElseThrow();
    }

    private static RecordingBridge bridge(String projectId,
                                          String version,
                                          String hostIdentifier,
                                          String sourcePath,
                                          String descriptorHash) {
        return bridge(projectId, version, hostIdentifier, sourcePath, descriptorHash, "EMBEDDED", 1);
    }

    private static RecordingBridge bridge(String projectId,
                                          String version,
                                          String hostIdentifier,
                                          String sourcePath,
                                          String descriptorHash,
                                          String origin) {
        return bridge(projectId, version, hostIdentifier, sourcePath, descriptorHash, origin, 1);
    }

    private static RecordingBridge bridge(String projectId,
                                          String version,
                                          String hostIdentifier,
                                          String sourcePath,
                                          String descriptorHash,
                                          String origin,
                                          int abiVersion) {
        return bridge(projectId, version, hostIdentifier, sourcePath, descriptorHash, origin, abiVersion, "owner");
    }

    private static RecordingBridge bridge(String projectId,
                                          String version,
                                          String hostIdentifier,
                                          String sourcePath,
                                          String descriptorHash,
                                          String origin,
                                          int abiVersion,
                                          String logicalOwner) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("abiVersion", abiVersion);
        candidate.put("projectId", projectId);
        candidate.put("logicalPluginIdentifier", logicalOwner);
        candidate.put("logicalPluginVersion", version);
        candidate.put("origin", origin);
        candidate.put("hostPluginIdentifier", hostIdentifier);
        candidate.put("hostPluginVersion", "1.0.0");
        candidate.put("sourcePath", sourcePath);
        candidate.put("descriptorJson", "{\"projectId\":\"" + projectId + "\"}");
        candidate.put("descriptorHash", descriptorHash);
        return new RecordingBridge(candidate);
    }

    private static final class RecordingBridge implements TelemetryProjectContributionBridge {
        private final Map<String, Object> candidate;
        private final List<String> acceptedTokens = new ArrayList<>();

        private RecordingBridge(Map<String, Object> candidate) {
            this.candidate = Map.copyOf(candidate);
        }

        @Override
        public int contributionAbiVersion() {
            return ((Number) candidate.get("abiVersion")).intValue();
        }

        @Override
        public Map<String, Object> candidate() {
            return candidate;
        }

        @Override
        public Map<String, Object> dispatch(String token,
                                             String operation,
                                             Map<String, Object> payload,
                                             Throwable throwable) {
            acceptedTokens.add(token);
            return Map.of("accepted", true, "operation", operation);
        }

        private List<String> acceptedTokens() {
            return List.copyOf(acceptedTokens);
        }
    }
}
