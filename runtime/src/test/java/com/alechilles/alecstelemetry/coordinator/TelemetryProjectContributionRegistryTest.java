package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    void linkageErrorsAtBridgeBoundariesRemainPassiveAndRejected() {
        RecordingBridge candidateFailure = new LinkageBridge(
                candidate("candidate-linkage", "1.0.0", "owner", "candidate.jar", "hash-a"),
                false,
                true,
                false
        );
        String passiveToken = TelemetryProjectContributionRegistry.register(candidateFailure);

        assertFalse(TelemetryProjectContributionRegistry.isActive(passiveToken));

        RecordingBridge dispatchFailure = new LinkageBridge(
                candidate("dispatch-linkage", "1.0.0", "owner", "dispatch.jar", "hash-b"),
                false,
                false,
                true
        );
        String dispatchToken = TelemetryProjectContributionRegistry.register(dispatchFailure);

        Map<String, Object> result = TelemetryProjectContributionRegistry.dispatch(
                dispatchToken, "usage", Map.of("eventName", "linkage"), null
        );
        assertFalse((boolean) result.get("accepted"));
        assertEquals("bridge_dispatch_failed", result.get("reason"));
    }

    @Test
    void forgedBridgeAbiCannotBecomeActive() {
        Map<String, Object> candidate = candidate("forged-abi", "1.0.0", "owner", "forged.jar", "hash-a");
        String token = TelemetryProjectContributionRegistry.register(new RecordingBridge(candidate, 2));

        assertFalse(TelemetryProjectContributionRegistry.isActive(token));
        assertTrue(TelemetryProjectContributionRegistry.diagnostics().stream()
                .anyMatch(diagnostic -> "abi_mismatch".equals(diagnostic.get("reason"))));
    }

    @Test
    void sourcePathAndDescriptorHashTieBreakersRemainCaseSensitive() {
        TelemetryProjectContributionRegistry.register(
                bridge("path-case", "1.0.0", "host", "a.jar", "hash")
        );
        TelemetryProjectContributionRegistry.register(
                bridge("path-case", "1.0.0", "host", "A.jar", "hash")
        );
        Map<String, Object> pathWinner = TelemetryProjectContributionRegistry.activeContributions().get(0);
        assertEquals("A.jar", pathWinner.get("sourcePath"));

        TelemetryProjectContributionRegistry.clearForTests();
        TelemetryProjectContributionRegistry.register(
                bridge("hash-case", "1.0.0", "host", "same.jar", "a-hash")
        );
        TelemetryProjectContributionRegistry.register(
                bridge("hash-case", "1.0.0", "host", "same.jar", "A-hash")
        );
        Map<String, Object> hashWinner = TelemetryProjectContributionRegistry.activeContributions().get(0);
        assertEquals("A-hash", hashWinner.get("descriptorHash"));
    }

    @Test
    void exactDuplicateRegistrationSharesTokenAndUnregisterKeepsItActiveUntilLastReference() {
        RecordingBridge first = bridge("duplicate", "1.0.0", "host", "same.jar", "same-hash");
        RecordingBridge duplicate = bridge("duplicate", "1.0.0", "host", "same.jar", "same-hash");

        String firstToken = TelemetryProjectContributionRegistry.register(first);
        String duplicateToken = TelemetryProjectContributionRegistry.register(duplicate);

        assertEquals(firstToken, duplicateToken);
        assertEquals(1, TelemetryProjectContributionRegistry.activeContributions().size());

        TelemetryProjectContributionRegistry.unregister(firstToken);

        assertTrue(TelemetryProjectContributionRegistry.isActive(duplicateToken));
        assertTrue((boolean) TelemetryProjectContributionRegistry
                .dispatch(duplicateToken, "usage", Map.of("eventName", "duplicate"), null)
                .get("accepted"));

        TelemetryProjectContributionRegistry.unregister(duplicateToken);
        assertFalse(TelemetryProjectContributionRegistry.isActive(duplicateToken));
    }

    @Test
    void malformedBridgeResultIsRejectedAndCopiedValuesStayJdkOnly() {
        Map<String, Object> nullCandidate = candidate("null-result", "1.0.0", "owner", "null.jar", "hash-a");
        String nullToken = TelemetryProjectContributionRegistry.register(new ResultBridge(nullCandidate, null));
        Map<String, Object> nullResult = TelemetryProjectContributionRegistry.dispatch(
                nullToken, "usage", Map.of(), null
        );
        assertFalse((boolean) nullResult.get("accepted"));
        assertEquals("bridge_invalid_result", nullResult.get("reason"));

        Map<String, Object> valueCandidate = candidate("value-copy", "1.0.0", "owner", "value.jar", "hash-b");
        MutableNumber mutableNumber = new MutableNumber(7);
        String valueToken = TelemetryProjectContributionRegistry.register(new ResultBridge(
                valueCandidate,
                Map.of("accepted", true, "character", 'x', "number", mutableNumber)
        ));
        Map<String, Object> valueResult = TelemetryProjectContributionRegistry.dispatch(
                valueToken, "usage", Map.of(), null
        );
        assertEquals("x", valueResult.get("character"));
        assertFalse(valueResult.get("number") instanceof MutableNumber);
        assertTrue(valueResult.get("number") instanceof Number || valueResult.get("number") instanceof String);
    }

    @Test
    void handoffFencesOldDispatchWithoutBlockingFallbackOrOverlappingBridgeWork() throws Exception {
        BlockingBridge old = new BlockingBridge(
                candidate("handoff", "2.0.0", "owner", "old.jar", "hash-old")
        );
        RecordingBridge fallback = bridge("handoff", "1.0.0", "owner", "fallback.jar", "hash-fallback");
        String oldToken = TelemetryProjectContributionRegistry.register(old);
        String fallbackToken = TelemetryProjectContributionRegistry.register(fallback);

        Thread oldDispatch = new Thread(() -> TelemetryProjectContributionRegistry.dispatch(
                oldToken, "usage", Map.of("eventName", "old"), null
        ));
        oldDispatch.start();
        assertTrue(old.entered.await(1, TimeUnit.SECONDS));

        CountDownLatch handoffComplete = new CountDownLatch(1);
        Thread handoff = new Thread(() -> {
            TelemetryProjectContributionRegistry.unregister(oldToken);
            handoffComplete.countDown();
        });
        handoff.start();

        CountDownLatch fallbackAttemptComplete = new CountDownLatch(1);
        List<Map<String, Object>> fallbackBeforeRelease = new ArrayList<>();
        Thread fallbackAttempt = new Thread(() -> {
            fallbackBeforeRelease.add(TelemetryProjectContributionRegistry.dispatch(
                    fallbackToken, "usage", Map.of("eventName", "before-release"), null
            ));
            fallbackAttemptComplete.countDown();
        });
        fallbackAttempt.start();

        assertTrue(fallbackAttemptComplete.await(1, TimeUnit.SECONDS));
        assertFalse((boolean) fallbackBeforeRelease.get(0).get("accepted"));
        assertFalse(handoffComplete.await(150, TimeUnit.MILLISECONDS));

        old.release.countDown();
        assertTrue(handoffComplete.await(2, TimeUnit.SECONDS));
        oldDispatch.join(1_000);
        handoff.join(1_000);
        fallbackAttempt.join(1_000);

        assertFalse((boolean) TelemetryProjectContributionRegistry
                .dispatch(oldToken, "usage", Map.of("eventName", "after"), null)
                .get("accepted"));
        assertTrue((boolean) TelemetryProjectContributionRegistry
                .dispatch(fallbackToken, "usage", Map.of("eventName", "after"), null)
                .get("accepted"));
        assertFalse(old.overlapped);
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

    private static Map<String, Object> candidate(String projectId,
                                                 String version,
                                                 String hostIdentifier,
                                                 String sourcePath,
                                                 String descriptorHash) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("abiVersion", 1);
        candidate.put("projectId", projectId);
        candidate.put("logicalPluginIdentifier", "owner");
        candidate.put("logicalPluginVersion", version);
        candidate.put("origin", "EMBEDDED");
        candidate.put("hostPluginIdentifier", hostIdentifier);
        candidate.put("hostPluginVersion", "1.0.0");
        candidate.put("sourcePath", sourcePath);
        candidate.put("descriptorJson", "{\"projectId\":\"" + projectId + "\"}");
        candidate.put("descriptorHash", descriptorHash);
        return candidate;
    }

    private static class RecordingBridge implements TelemetryProjectContributionBridge {
        private final Map<String, Object> candidate;
        private final List<String> acceptedTokens = new ArrayList<>();
        private final int declaredAbiVersion;

        private RecordingBridge(Map<String, Object> candidate) {
            this(candidate, ((Number) candidate.get("abiVersion")).intValue());
        }

        private RecordingBridge(Map<String, Object> candidate, int declaredAbiVersion) {
            this.candidate = Map.copyOf(candidate);
            this.declaredAbiVersion = declaredAbiVersion;
        }

        @Override
        public int contributionAbiVersion() {
            return declaredAbiVersion;
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

    private static final class LinkageBridge extends RecordingBridge {
        private final boolean abiLinkageError;
        private final boolean candidateLinkageError;
        private final boolean dispatchLinkageError;

        private LinkageBridge(Map<String, Object> candidate,
                              boolean abiLinkageError,
                              boolean candidateLinkageError,
                              boolean dispatchLinkageError) {
            super(candidate);
            this.abiLinkageError = abiLinkageError;
            this.candidateLinkageError = candidateLinkageError;
            this.dispatchLinkageError = dispatchLinkageError;
        }

        @Override
        public int contributionAbiVersion() {
            if (abiLinkageError) {
                throw new NoClassDefFoundError("missing ABI type");
            }
            return super.contributionAbiVersion();
        }

        @Override
        public Map<String, Object> candidate() {
            if (candidateLinkageError) {
                throw new NoSuchMethodError("missing candidate method");
            }
            return super.candidate();
        }

        @Override
        public Map<String, Object> dispatch(String token,
                                             String operation,
                                             Map<String, Object> payload,
                                             Throwable throwable) {
            if (dispatchLinkageError) {
                throw new NoSuchMethodError("missing dispatch method");
            }
            return super.dispatch(token, operation, payload, throwable);
        }
    }

    private static final class ResultBridge extends RecordingBridge {
        private final Object result;

        private ResultBridge(Map<String, Object> candidate, Object result) {
            super(candidate);
            this.result = result;
        }

        @Override
        public Map<String, Object> dispatch(String token,
                                             String operation,
                                             Map<String, Object> payload,
                                             Throwable throwable) {
            if (result == null) {
                return null;
            }
            return (Map<String, Object>) result;
        }
    }

    private static final class MutableNumber extends Number {
        private int value;

        private MutableNumber(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value;
        }

        @Override
        public long longValue() {
            return value;
        }

        @Override
        public float floatValue() {
            return value;
        }

        @Override
        public double doubleValue() {
            return value;
        }
    }

    private static final class BlockingBridge extends RecordingBridge {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean inDispatch;
        private volatile boolean overlapped;

        private BlockingBridge(Map<String, Object> candidate) {
            super(candidate);
        }

        @Override
        public Map<String, Object> dispatch(String token,
                                             String operation,
                                             Map<String, Object> payload,
                                             Throwable throwable) {
            inDispatch = true;
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                return Map.of("accepted", true);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Map.of("accepted", false, "reason", "interrupted");
            } finally {
                inDispatch = false;
            }
        }
    }
}
