package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.runtime.profiler.RemoteTelemetryProfilerView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void olderForeignProviderDefaultsToNoProfilerCapability() {
        ForeignBridge legacy = foreignBridge("legacy", TelemetryRuntimeOrigin.EMBEDDED, "9.0.0");

        TelemetryCoordinatorRegistry.register(legacy);

        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        assertTrue(active.capabilities().isEmpty());
        assertTrue(active.profilerLatest("mod-a").isEmpty());
        assertTrue(active.profilerHistory("mod-a").isEmpty());
    }

    @Test
    void capableForeignProviderRoundTripsOnlyJdkPayloads() {
        CapableForeignBridge provider = capableForeignBridge();

        TelemetryCoordinatorRegistry.register(provider);

        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        assertTrue(active.capabilities().contains(TelemetryProfilerView.CAPABILITY));
        assertEquals(7, ((Number) active.profilerLatest("mod-a").get("windowKey")).intValue());
        assertTrue(active.profilerLatest("mod-a").values().stream()
                .noneMatch(value -> value instanceof com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot));
    }

    @Test
    void isolatedForeignProviderRoundTripsBoundedProfilerView() throws Exception {
        URL classes = TelemetryCoordinatorRegistryTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
        try (ProfilerFixtureClassLoader isolated = new ProfilerFixtureClassLoader(
                classes,
                TelemetryCoordinatorRegistryTest.class.getClassLoader()
        )) {
            Class<?> fixtureType = isolated.loadClass(IsolatedForeignProfilerBridge.class.getName());
            assertSame(isolated, fixtureType.getClassLoader());
            assertThrows(
                    ClassNotFoundException.class,
                    () -> isolated.loadClass("com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot")
            );
            Object foreign = fixtureType.getConstructor().newInstance();

            TelemetryCoordinatorRegistry.register(foreign);

            TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
            assertNotNull(active);
            RemoteTelemetryProfilerView view = new RemoteTelemetryProfilerView(active, "mod-a");
            assertEquals(TelemetryProfilerStatus.State.COMPLETE, view.status().state());
            assertEquals(20, view.latest().orElseThrow().windowKey());
            assertEquals(
                    List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
                    view.history().stream().map(TelemetryProfilerSnapshot::windowKey).toList()
            );

            AtomicBoolean callback = new AtomicBoolean();
            TelemetryProfilerSubscription subscription = view.subscribe(snapshot -> {
                callback.set(snapshot.windowKey() == 20);
            });
            assertTrue(callback.get());
            subscription.close();

            Method unsubscribed = fixtureType.getMethod("unsubscribed");
            assertTrue((Boolean) unsubscribed.invoke(foreign));
        }
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
    void isolatesProtocolTwoRegistryFromLegacyProtocolOneElection() {
        ArrayList<String> lifecycle = new ArrayList<>();
        OrderedBridge modern = orderedBridge("modern", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4", lifecycle);
        OrderedBridge legacy = new OrderedBridge(
                candidate("legacy", TelemetryRuntimeOrigin.STANDALONE, "9.0.0", 1),
                lifecycle
        );

        TelemetryCoordinatorRegistry.register(modern);
        assertEquals(List.of("modern:start"), lifecycle);
        assertTrue(modern.isActive());
        assertFalse(System.getProperties().containsKey(TelemetryCoordinatorRegistry.LEGACY_PROTOCOL_ONE_REGISTRY_KEY));

        lifecycle.clear();
        simulateLegacyProtocolOneRegister(legacy);

        assertTrue(modern.isActive());
        assertTrue(legacy.isActive());
        assertEquals(List.of("legacy:start"), lifecycle);
        assertEquals("modern", TelemetryCoordinatorRegistry.activeBridge().providerId());
    }

    @Test
    void clearForTestsRemovesCurrentAndKnownLegacyRegistryMaps() {
        TelemetryCoordinatorRegistry.register(bridge("modern", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4"));
        System.getProperties().put(
                TelemetryCoordinatorRegistry.LEGACY_PROTOCOL_ONE_REGISTRY_KEY,
                new ConcurrentHashMap<String, Object>(Map.of("legacy", bridge(
                        "legacy",
                        TelemetryRuntimeOrigin.STANDALONE,
                        "0.1.3"
                )))
        );

        TelemetryCoordinatorRegistry.clearForTests();

        assertFalse(System.getProperties().containsKey(TelemetryCoordinatorRegistry.REGISTRY_KEY));
        assertFalse(System.getProperties().containsKey(TelemetryCoordinatorRegistry.LEGACY_PROTOCOL_ONE_REGISTRY_KEY));
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

    @Test
    void shutsDownActiveBridgeWhenSameProviderRegistersReplacement() {
        ArrayList<String> lifecycle = new ArrayList<>();
        OrderedBridge original = orderedBridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3", lifecycle);
        OrderedBridge replacement = orderedBridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3", lifecycle);

        TelemetryCoordinatorRegistry.register(original);
        assertEquals(List.of("standalone:start"), lifecycle);
        assertTrue(original.isActive());

        lifecycle.clear();
        TelemetryCoordinatorRegistry.register(replacement);

        assertFalse(original.isActive());
        assertTrue(replacement.isActive());
        assertEquals(List.of("standalone:shutdown", "standalone:start"), lifecycle);
    }

    @Test
    void failedReplayRecoveryActivatesFixedWinnerAndDeactivatesFallback() {
        ArrayList<String> lifecycle = new ArrayList<>();
        OrderedBridge fallback = orderedBridge("fallback", TelemetryRuntimeOrigin.EMBEDDED, "1.0.0", lifecycle);
        ReplayBridge winner = new ReplayBridge(
                candidate("winner", TelemetryRuntimeOrigin.STANDALONE, "2.0.0", TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION),
                lifecycle
        );
        TelemetryCoordinatorRegistry.register(fallback);
        lifecycle.clear();
        TelemetryCoordinatorRegistry.register(winner);

        assertEquals("fallback", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertFalse(winner.isActive());

        lifecycle.clear();
        winner.reconcileResult.set(true);

        assertEquals("winner", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertTrue(winner.isActive());
        assertFalse(fallback.isActive());
        assertEquals(List.of("fallback:shutdown", "winner:start"), lifecycle);
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

    private static CapableForeignBridge capableForeignBridge() {
        return new CapableForeignBridge(candidate(
                "capable",
                TelemetryRuntimeOrigin.EMBEDDED,
                "9.0.0",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION
        ));
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

    @SuppressWarnings("unchecked")
    private static void simulateLegacyProtocolOneRegister(TelemetryCoordinatorBridge bridge) {
        ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<>();
        Object existing = System.getProperties().putIfAbsent(
                TelemetryCoordinatorRegistry.LEGACY_PROTOCOL_ONE_REGISTRY_KEY,
                created
        );
        ConcurrentHashMap<String, Object> registry = existing instanceof ConcurrentHashMap<?, ?> map
                ? (ConcurrentHashMap<String, Object>) map
                : created;
        registry.put(bridge.providerId(), bridge);
        simulateLegacyProtocolOneElection(registry);
    }

    private static void simulateLegacyProtocolOneElection(ConcurrentHashMap<String, Object> registry) {
        ArrayList<TelemetryRuntimeCandidate> candidates = new ArrayList<>();
        for (Object value : registry.values()) {
            if (value instanceof TelemetryCoordinatorBridge bridge) {
                candidates.add(candidate(
                        bridge.providerId(),
                        TelemetryRuntimeOrigin.valueOf(bridge.origin()),
                        bridge.runtimeVersion(),
                        bridge.coordinatorProtocolVersion()
                ));
            }
        }
        TelemetryRuntimeCandidate winner = TelemetryRuntimeCandidate.selectWinner(candidates, 1);
        for (Object value : List.copyOf(registry.values())) {
            if (!(value instanceof TelemetryCoordinatorBridge bridge)) {
                continue;
            }
            boolean shouldBeActive = winner != null && bridge.providerId().equals(winner.providerId());
            if (!shouldBeActive && bridge.isActive()) {
                bridge.shutdown();
                bridge.deactivate();
            }
        }
        if (winner == null) {
            return;
        }
        Object value = registry.get(winner.providerId());
        if (value instanceof TelemetryCoordinatorBridge bridge && !bridge.isActive()) {
            bridge.activate();
            bridge.start();
        }
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

    private static class OrderedBridge extends RecordingBridge {
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

        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return true;
        }

        public Map<String, Object> dispatchProjectContribution(String token,
                                                               String operation,
                                                               Map<String, Object> payload,
                                                               Throwable throwable) {
            return Map.of("accepted", true);
        }
    }

    private static final class ReplayBridge extends OrderedBridge {
        private final AtomicBoolean reconcileResult = new AtomicBoolean(false);

        private ReplayBridge(TelemetryRuntimeCandidate candidate, ArrayList<String> lifecycle) {
            super(candidate, lifecycle);
        }

        @Override
        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return reconcileResult.get();
        }
    }

    @SuppressWarnings("unused")
    private static class ForeignBridge {
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

        protected ForeignBridge(TelemetryRuntimeCandidate candidate) {
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

        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return true;
        }

        public Map<String, Object> dispatchProjectContribution(String token,
                                                               String operation,
                                                               Map<String, Object> payload,
                                                               Throwable throwable) {
            return Map.of("accepted", true);
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

    private static final class CapableForeignBridge extends ForeignBridge {
        private CapableForeignBridge(TelemetryRuntimeCandidate candidate) {
            super(candidate);
        }

        public List<String> capabilities() {
            return List.of(TelemetryProfilerView.CAPABILITY);
        }

        public Map<String, Object> profilerLatest(String projectId) {
            return Map.of("windowKey", 7);
        }
    }

    /** Fixture source uses only JDK types and is loaded child-first by the boundary test. */
    public static final class IsolatedForeignProfilerBridge {
        private boolean unsubscribed;

        public String providerId() {
            return "isolated-profiler";
        }

        public String origin() {
            return "EMBEDDED";
        }

        public String runtimeVersion() {
            return "9.0.0";
        }

        public int coordinatorProtocolVersion() {
            return 3;
        }

        public String providerPluginIdentifier() {
            return "Example:Isolated";
        }

        public String providerPluginVersion() {
            return "1.0.0";
        }

        public String sourcePath() {
            return "isolated.jar";
        }

        public String sharedDataRoot() {
            return "Telemetry";
        }

        public void activate() {
        }

        public void deactivate() {
        }

        public boolean isActive() {
            return true;
        }

        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return true;
        }

        public Map<String, Object> dispatchProjectContribution(String token,
                                                               String operation,
                                                               Map<String, Object> payload,
                                                               Throwable throwable) {
            return Map.of("accepted", true);
        }

        public List<String> capabilities() {
            return List.of("profiler-api-v1");
        }

        public Map<String, Object> profilerStatus(String projectId) {
            return Map.of(
                    "state", "COMPLETE",
                    "detail", "isolated",
                    "active", true,
                    "circuitOpen", false,
                    "sparkVersion", "spark-1",
                    "runtimeVersion", "9.0.0",
                    "nextCaptureAtMillis", 0L,
                    "analysisDurationMillis", 2L,
                    "omittedPathCount", 0,
                    "truncatedPrefixCount", 0
            );
        }

        public Map<String, Object> profilerLatest(String projectId) {
            return snapshot(20);
        }

        public List<Map<String, Object>> profilerHistory(String projectId) {
            ArrayList<Map<String, Object>> history = new ArrayList<>();
            for (int window = 1; window <= 20; window++) {
                history.add(snapshot(window));
            }
            return history;
        }

        public String subscribeProfiler(String projectId,
                                        java.util.function.Consumer<Map<String, Object>> listener) {
            listener.accept(snapshot(20));
            return "isolated-subscription";
        }

        public boolean unsubscribeProfiler(String subscriptionId) {
            unsubscribed = "isolated-subscription".equals(subscriptionId);
            return unsubscribed;
        }

        public boolean unsubscribed() {
            return unsubscribed;
        }

        private static Map<String, Object> snapshot(int window) {
            return Map.of(
                    "projectId", "mod-a",
                    "projectVersion", "1.0.0",
                    "sparkVersion", "spark-1",
                    "windowKey", window,
                    "observedAtMillis", (long) window,
                    "selectedWorldThreadSelfMilliseconds", 1.0d,
                    "qualificationRuleSet", "unqualified-v1",
                    "paths", List.of(),
                    "context", Map.of(
                            "observedAtMillis", (long) window,
                            "breadcrumbCategoryCounts", Map.of()
                    )
            );
        }
    }

    private static final class ProfilerFixtureClassLoader extends URLClassLoader {
        private final String fixtureName = IsolatedForeignProfilerBridge.class.getName();

        private ProfilerFixtureClassLoader(URL classes, ClassLoader parent) {
            super(new URL[]{classes}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.alechilles.alecstelemetry.api.TelemetryProfiler")) {
                throw new ClassNotFoundException(name);
            }
            if (!fixtureName.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
