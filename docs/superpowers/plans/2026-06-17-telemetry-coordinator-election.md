# Telemetry Coordinator Election Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make standalone and embedded Alec's Telemetry copies converge on one active, latest-compatible runtime coordinator that handles telemetry for every installed enabled telemetry project.

**Architecture:** Introduce a runtime-level coordinator candidate/election layer in `alecstelemetry-runtime` so both standalone and embedded boot paths participate in the same arbitration flow. Runtime precedence is `highest compatible runtime version wins`; standalone is only a tie-breaker and optional admin/UI shell, not an unconditional runtime winner.

**Tech Stack:** Java 25, Maven multi-module project, Hytale `JavaPlugin`/`PluginClassLoader`, JUnit 5, Gson, existing `TelemetryCoreEngine`.

---

## Scope Check

This plan covers one subsystem: in-process telemetry runtime coordination across standalone and embedded copies. It intentionally does not redesign hosted ingest, report envelope schemas, Tamework telemetry event names, or player-facing UI layout.

## Current Evidence

- Standalone boot path: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\AlecsTelemetry.java`
- Embedded boot path: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryBootstrap.java`
- Embedded runtime currently owns exactly one project: `EmbeddedTelemetryService` builds `TelemetryCoreEngine(..., List.of(project), ...)`.
- Standalone discovery currently skips embedded projects for capture/upload but keeps them consent-visible.
- Hytale plugin loading uses separate `PluginClassLoader` instances, so Java static fields inside shaded telemetry classes are not enough for global coordination.

## Target Rules

1. Every standalone or embedded telemetry copy registers as a coordinator candidate.
2. Candidates declare:
   - runtime version, from Maven/project manifest
   - coordinator protocol version
   - origin: `standalone` or `embedded`
   - provider plugin identifier
   - provider plugin version
   - shared data root
3. Only candidates with the current coordinator protocol are eligible.
4. The active coordinator is selected by:
   - highest semantic runtime version
   - if equal, standalone origin before embedded
   - if still equal, lexicographic provider plugin identifier
   - if still equal, lexicographic source path
5. Losers remain passive clients. They must not install uncaught handlers, schedule flush loops, emit stats heartbeats, or upload telemetry.
6. The active coordinator scans all installed mod descriptors and captures/uploads for every enabled telemetry project, including projects marked `runtimeMode=embedded`.
7. Standalone, if installed but not active, may still register commands and UI; those surfaces proxy to the active coordinator when possible.
8. Passive embedded handles continue to satisfy existing consumer API calls by forwarding to the active coordinator bridge.

## File Structure

Create:

- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeOrigin.java`
  - Runtime origin enum.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeCandidate.java`
  - Immutable candidate metadata plus semver comparison.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorBridge.java`
  - Classloader-neutral active-runtime bridge contract using only JDK types.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorRegistry.java`
  - Shared JVM registry stored in `System.getProperties()` and accessed with reflection-safe JDK objects.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorService.java`
  - Multi-project runtime coordinator built on `TelemetryCoreEngine`.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeCandidateTest.java`
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorRegistryTest.java`
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorServiceTest.java`

Move:

- From `standalone/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscovery.java`
- To `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscovery.java`
- From `standalone/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetector.java`
- To `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetector.java`
- From `standalone/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscoveryTest.java`
- To `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscoveryTest.java`
- From `standalone/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetectorTest.java`
- To `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetectorTest.java`

Modify:

- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryBootstrap.java`
  - Register embedded candidate and return a handle backed by the active coordinator.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryService.java`
  - Convert from always-local runtime to owner-project handle/proxy.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\AlecsTelemetry.java`
  - Register standalone candidate and proxy command/UI surfaces to active coordinator.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryRuntimeService.java`
  - Wrap `TelemetryCoordinatorService` instead of directly owning `TelemetryCoreEngine`.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryDataPaths.java`
  - Add shared coordinator data root for embedded winner.
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\embedded-mode.md`
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\embedded-mode-refactor-plan.md`
- `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\wiki\Integration-Guides\Embedded-Mode.md`

## Shared Registry Design

Do not use only `TelemetryRuntimeLocator`; it is loaded per classloader when the runtime library is shaded into multiple mods.

Use one JVM-wide JDK object:

```java
private static final String REGISTRY_KEY = "com.alechilles.alecstelemetry.coordinator.registry.v1";
```

Store a `java.util.concurrent.ConcurrentHashMap<String, Object>` under `System.getProperties().putIfAbsent(REGISTRY_KEY, map)`. The key is a provider id string. The value is a local bridge object from that candidate's classloader. The registry must use reflection for candidate metadata and operations from the first implementation, because `instanceof TelemetryCoordinatorBridge` fails when the same class name is loaded by a different plugin classloader.

Bridge methods must not expose telemetry runtime classes such as `TelemetryEventContext`, `TelemetryProjectRegistration`, `TelemetryRuntimeDiagnostics`, or `TelemetryRuntimeCandidate`. Bridge candidate metadata uses only `String`, `int`, `boolean`, `Throwable`, and `Map<String, Object>`.

## Task 1: Candidate Model

**Files:**
- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeOrigin.java`
- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeCandidate.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryRuntimeCandidateTest.java`

- [ ] **Step 1: Write the failing candidate precedence tests**

Create `TelemetryRuntimeCandidateTest.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeCandidateTest {

    @Test
    void highestCompatibleRuntimeVersionWinsOverStandalone() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate embedded = candidate(
                "embedded-tamework",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "Alechilles:Alec's Tamework!"
        );

        assertEquals(embedded, TelemetryRuntimeCandidate.selectWinner(List.of(standalone, embedded), 1));
    }

    @Test
    void incompatibleNewerRuntimeIsIgnored() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate incompatible = new TelemetryRuntimeCandidate(
                "embedded-new",
                TelemetryRuntimeOrigin.EMBEDDED,
                "9.0.0",
                2,
                "Alechilles:Future Mod",
                "1.0.0",
                Path.of("Future.jar"),
                Path.of("UserData").resolve("Telemetry")
        );

        assertEquals(standalone, TelemetryRuntimeCandidate.selectWinner(List.of(standalone, incompatible), 1));
    }

    @Test
    void standaloneWinsTieOnEqualRuntimeVersion() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.4",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate embedded = candidate(
                "embedded-tamework",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "Alechilles:Alec's Tamework!"
        );

        assertEquals(standalone, TelemetryRuntimeCandidate.selectWinner(List.of(embedded, standalone), 1));
    }

    private static TelemetryRuntimeCandidate candidate(String providerId,
                                                       TelemetryRuntimeOrigin origin,
                                                       String runtimeVersion,
                                                       String pluginIdentifier) {
        return new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                1,
                pluginIdentifier,
                "1.0.0",
                Path.of(providerId + ".jar"),
                Path.of("UserData").resolve("Telemetry")
        );
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeCandidateTest test
```

Expected: compilation fails because `TelemetryRuntimeCandidate` and `TelemetryRuntimeOrigin` do not exist.

- [ ] **Step 3: Add `TelemetryRuntimeOrigin`**

Create `TelemetryRuntimeOrigin.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

public enum TelemetryRuntimeOrigin {
    STANDALONE,
    EMBEDDED
}
```

- [ ] **Step 4: Add `TelemetryRuntimeCandidate`**

Create `TelemetryRuntimeCandidate.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record TelemetryRuntimeCandidate(@Nonnull String providerId,
                                        @Nonnull TelemetryRuntimeOrigin origin,
                                        @Nonnull String runtimeVersion,
                                        int coordinatorProtocolVersion,
                                        @Nonnull String providerPluginIdentifier,
                                        @Nonnull String providerPluginVersion,
                                        @Nonnull Path sourcePath,
                                        @Nonnull Path sharedDataRoot) {

    @Nullable
    public static TelemetryRuntimeCandidate selectWinner(@Nonnull List<TelemetryRuntimeCandidate> candidates,
                                                         int requiredProtocolVersion) {
        return candidates.stream()
                .filter(candidate -> candidate.coordinatorProtocolVersion() == requiredProtocolVersion)
                .max(WINNER_ORDER)
                .orElse(null);
    }

    private static final Comparator<TelemetryRuntimeCandidate> WINNER_ORDER =
            Comparator.comparing(TelemetryRuntimeCandidate::versionKey)
                    .thenComparing(candidate -> candidate.origin() == TelemetryRuntimeOrigin.STANDALONE ? 1 : 0)
                    .thenComparing(candidate -> candidate.providerPluginIdentifier().toLowerCase(Locale.ROOT), Comparator.reverseOrder())
                    .thenComparing(candidate -> candidate.sourcePath().toString().toLowerCase(Locale.ROOT), Comparator.reverseOrder());

    @Nonnull
    private VersionKey versionKey() {
        return VersionKey.parse(runtimeVersion);
    }

    private record VersionKey(int major, int minor, int patch, @Nonnull String suffix) implements Comparable<VersionKey> {
        @Nonnull
        static VersionKey parse(@Nonnull String raw) {
            String trimmed = raw.trim();
            String[] versionAndSuffix = trimmed.split("-", 2);
            String[] parts = versionAndSuffix[0].split("\\.");
            return new VersionKey(
                    parsePart(parts, 0),
                    parsePart(parts, 1),
                    parsePart(parts, 2),
                    versionAndSuffix.length > 1 ? versionAndSuffix[1] : ""
            );
        }

        private static int parsePart(@Nonnull String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            try {
                return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        @Override
        public int compareTo(@Nonnull VersionKey other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            int patchCompare = Integer.compare(patch, other.patch);
            if (patchCompare != 0) {
                return patchCompare;
            }
            if (suffix.isBlank() && !other.suffix.isBlank()) {
                return 1;
            }
            if (!suffix.isBlank() && other.suffix.isBlank()) {
                return -1;
            }
            return other.suffix.compareToIgnoreCase(suffix);
        }
    }
}
```

- [ ] **Step 5: Run candidate tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeCandidateTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryRuntimeOrigin.java runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryRuntimeCandidate.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryRuntimeCandidateTest.java
git commit -m "Feat: add telemetry runtime candidate precedence"
```

## Task 2: Classloader-Neutral Registry

**Files:**
- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorBridge.java`
- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorRegistry.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorRegistryTest.java`

- [ ] **Step 1: Write registry tests**

Create `TelemetryCoordinatorRegistryTest.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoordinatorRegistryTest {

    @AfterEach
    void clearRegistry() {
        TelemetryCoordinatorRegistry.clearForTests();
    }

    @Test
    void electsLatestCompatibleCandidate() {
        RecordingBridge standalone = bridge("standalone", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");
        RecordingBridge embedded = bridge("embedded", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");

        TelemetryCoordinatorRegistry.register(standalone);
        TelemetryCoordinatorRegistry.register(embedded);

        assertEquals("embedded", TelemetryCoordinatorRegistry.activeBridge().providerId());
        assertFalse(standalone.active);
        assertTrue(embedded.active);
    }

    @Test
    void forwardsToActiveBridgeWithJdkOnlyArguments() {
        RecordingBridge embedded = bridge("embedded", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");
        TelemetryCoordinatorRegistry.register(embedded);

        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        assertTrue(active.recordUsage("alecs-tamework", "settings_page_opened", Map.of("source", "api")));

        assertEquals("alecs-tamework", embedded.lastProjectId);
        assertEquals("settings_page_opened", embedded.lastEventName);
        assertEquals("api", embedded.lastDetails.get("source"));
    }

    private static RecordingBridge bridge(String providerId, TelemetryRuntimeOrigin origin, String runtimeVersion) {
        return new RecordingBridge(new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Alechilles:" + providerId,
                "1.0.0",
                Path.of(providerId + ".jar"),
                Path.of("Telemetry")
        ));
    }

    private static final class RecordingBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private boolean active;
        private String lastProjectId;
        private String lastEventName;
        private Map<String, Object> lastDetails = Map.of();

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

        @Override
        public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
            lastProjectId = projectId;
            lastEventName = eventName;
            lastDetails = details;
            return true;
        }
    }
}
```

- [ ] **Step 2: Run registry tests to verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCoordinatorRegistryTest test
```

Expected: compilation fails because `TelemetryCoordinatorBridge` and `TelemetryCoordinatorRegistry` do not exist.

- [ ] **Step 3: Add bridge interface**

Create `TelemetryCoordinatorBridge.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public interface TelemetryCoordinatorBridge {

    @Nonnull
    String providerId();

    @Nonnull
    String origin();

    @Nonnull
    String runtimeVersion();

    int coordinatorProtocolVersion();

    @Nonnull
    String providerPluginIdentifier();

    @Nonnull
    String providerPluginVersion();

    @Nonnull
    String sourcePath();

    @Nonnull
    String sharedDataRoot();

    void activate();

    void deactivate();

    boolean isActive();

    default void start() {
    }

    default void shutdown() {
    }

    default boolean recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
        return false;
    }

    default boolean recordError(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable Throwable throwable,
                                @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordLifecycle(@Nonnull String projectId,
                                    @Nonnull String eventName,
                                    int durationMs,
                                    boolean success,
                                    @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordPerformance(@Nonnull String projectId,
                                      @Nonnull String eventName,
                                      int durationMs,
                                      @Nullable Double metricValue,
                                      @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordUsage(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean requestFlush(@Nullable String projectId) {
        return false;
    }
}
```

- [ ] **Step 4: Add registry implementation**

Create `TelemetryCoordinatorRegistry.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TelemetryCoordinatorRegistry {

    public static final int COORDINATOR_PROTOCOL_VERSION = 1;
    private static final String REGISTRY_KEY = "com.alechilles.alecstelemetry.coordinator.registry.v1";

    private TelemetryCoordinatorRegistry() {
    }

    public static void register(@Nonnull TelemetryCoordinatorBridge bridge) {
        registry().put(bridge.providerId(), bridge);
        elect();
    }

    public static void unregister(@Nonnull String providerId) {
        Object removed = registry().remove(providerId);
        if (removed instanceof TelemetryCoordinatorBridge bridge && bridge.isActive()) {
            bridge.shutdown();
            bridge.deactivate();
        }
        elect();
    }

    @Nullable
    public static TelemetryCoordinatorBridge activeBridge() {
        TelemetryRuntimeCandidate winner = TelemetryRuntimeCandidate.selectWinner(localCandidates(), COORDINATOR_PROTOCOL_VERSION);
        if (winner == null) {
            return null;
        }
        Object bridge = registry().get(winner.providerId());
        return bridge == null ? null : new ReflectiveBridge(bridge, winner);
    }

    static void clearForTests() {
        Object existing = System.getProperties().remove(REGISTRY_KEY);
        if (existing instanceof ConcurrentHashMap<?, ?> map) {
            map.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> registry() {
        Object existing = System.getProperties().get(REGISTRY_KEY);
        if (existing instanceof ConcurrentHashMap<?, ?> map) {
            return (ConcurrentHashMap<String, Object>) map;
        }
        ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<>();
        Object raced = System.getProperties().putIfAbsent(REGISTRY_KEY, created);
        return raced instanceof ConcurrentHashMap<?, ?> map ? (ConcurrentHashMap<String, Object>) map : created;
    }

    private static void elect() {
        TelemetryRuntimeCandidate winner = TelemetryRuntimeCandidate.selectWinner(localCandidates(), COORDINATOR_PROTOCOL_VERSION);
        for (Object value : registry().values()) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate == null) {
                continue;
            }
            ReflectiveBridge bridge = new ReflectiveBridge(value, candidate);
            boolean shouldBeActive = winner != null && candidate.providerId().equals(winner.providerId());
            if (shouldBeActive && !bridge.isActive()) {
                bridge.activate();
                bridge.start();
            } else if (!shouldBeActive && bridge.isActive()) {
                bridge.shutdown();
                bridge.deactivate();
            }
        }
    }

    @Nonnull
    private static List<TelemetryRuntimeCandidate> localCandidates() {
        ArrayList<TelemetryRuntimeCandidate> candidates = new ArrayList<>();
        for (Object value : registry().values()) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    @Nullable
    private static TelemetryRuntimeCandidate candidateFrom(@Nonnull Object bridge) {
        try {
            return new TelemetryRuntimeCandidate(
                    invokeString(bridge, "providerId"),
                    TelemetryRuntimeOrigin.valueOf(invokeString(bridge, "origin")),
                    invokeString(bridge, "runtimeVersion"),
                    invokeInt(bridge, "coordinatorProtocolVersion"),
                    invokeString(bridge, "providerPluginIdentifier"),
                    invokeString(bridge, "providerPluginVersion"),
                    Path.of(invokeString(bridge, "sourcePath")),
                    Path.of(invokeString(bridge, "sharedDataRoot"))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String invokeString(@Nonnull Object target, @Nonnull String methodName) throws Exception {
        Object value = invoke(target, methodName);
        return value == null ? "" : value.toString();
    }

    private static int invokeInt(@Nonnull Object target, @Nonnull String methodName) throws Exception {
        Object value = invoke(target, methodName);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static Object invoke(@Nonnull Object target, @Nonnull String methodName, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] instanceof Map ? Map.class : args[i].getClass();
        }
        Method method = target.getClass().getMethod(methodName, types);
        return method.invoke(target, args);
    }

    private record ReflectiveBridge(@Nonnull Object delegate,
                                    @Nonnull TelemetryRuntimeCandidate candidate) implements TelemetryCoordinatorBridge {

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
            invokeQuiet("activate");
        }

        @Override
        public void deactivate() {
            invokeQuiet("deactivate");
        }

        @Override
        public boolean isActive() {
            try {
                Object value = invoke(delegate, "isActive");
                return value instanceof Boolean active && active;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public void start() {
            invokeQuiet("start");
        }

        @Override
        public void shutdown() {
            invokeQuiet("shutdown");
        }

        @Override
        public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
            try {
                Object value = invoke(delegate, "recordUsage", projectId, eventName, details);
                return value instanceof Boolean result && result;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public boolean requestFlush(String projectId) {
            try {
                Object value = invoke(delegate, "requestFlush", projectId);
                return value instanceof Boolean result && result;
            } catch (Exception ex) {
                return false;
            }
        }

        private void invokeQuiet(String methodName) {
            try {
                invoke(delegate, methodName);
            } catch (Exception ignored) {
            }
        }
    }
}
```

- [ ] **Step 5: Run registry tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCoordinatorRegistryTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorBridge.java runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistryTest.java
git commit -m "Feat: add telemetry coordinator registry"
```

## Task 3: Move Discovery Into Runtime

**Files:**
- Move: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectDiscovery.java`
- Move: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectCollisionDetector.java`
- Move tests from `standalone/src/test/java/.../project/` to `runtime/src/test/java/.../project/`

- [ ] **Step 1: Move source and test files**

Run:

```powershell
New-Item -ItemType Directory -Force runtime\src\main\java\com\alechilles\alecstelemetry\project
New-Item -ItemType Directory -Force runtime\src\test\java\com\alechilles\alecstelemetry\project
git mv standalone\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectDiscovery.java runtime\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectDiscovery.java
git mv standalone\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectCollisionDetector.java runtime\src\main\java\com\alechilles\alecstelemetry\project\TelemetryProjectCollisionDetector.java
git mv standalone\src\test\java\com\alechilles\alecstelemetry\project\TelemetryProjectDiscoveryTest.java runtime\src\test\java\com\alechilles\alecstelemetry\project\TelemetryProjectDiscoveryTest.java
git mv standalone\src\test\java\com\alechilles\alecstelemetry\project\TelemetryProjectCollisionDetectorTest.java runtime\src\test\java\com\alechilles\alecstelemetry\project\TelemetryProjectCollisionDetectorTest.java
```

- [ ] **Step 2: Run moved tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDiscoveryTest,TelemetryProjectCollisionDetectorTest test
```

Expected: tests pass in the runtime module.

- [ ] **Step 3: Run standalone compile**

Run:

```powershell
.\mvnw.cmd -pl standalone -am -DskipTests compile
```

Expected: standalone compiles because it depends on `alecstelemetry-runtime`.

- [ ] **Step 4: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/project runtime/src/test/java/com/alechilles/alecstelemetry/project standalone/src/main/java/com/alechilles/alecstelemetry/project standalone/src/test/java/com/alechilles/alecstelemetry/project
git commit -m "Refactor: move telemetry project discovery into runtime"
```

## Task 4: Shared Coordinator Data Paths

**Files:**
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryDataPaths.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\runtime\TelemetryDataPathsTest.java`

- [ ] **Step 1: Add tests for shared embedded coordinator root**

Add this test to `TelemetryDataPathsTest.java`:

```java
@Test
void sharedCoordinatorRootUsesGlobalUserDataTelemetryDirectory() {
    Path hytaleRoot = tempDir.resolve("Hytale");
    Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Mods").resolve("Alechilles_Alec's Tamework!");
    TestPlugin plugin = new TestPlugin(pluginDataDirectory);

    TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinator(plugin);

    assertEquals(
            hytaleRoot.resolve("UserData").resolve("Telemetry").toAbsolutePath().normalize(),
            paths.runtimeRoot()
    );
    assertEquals(paths.runtimeRoot().resolve("Settings").resolve("runtime.json"), paths.settingsFile());
    assertEquals(paths.runtimeRoot().resolve("Telemetry"), paths.telemetryRoot());
    assertEquals(hytaleRoot.resolve("UserData").resolve("Mods").toAbsolutePath().normalize(), paths.modsDirectory());
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: compilation fails because `TelemetryDataPaths.forSharedCoordinator(...)` does not exist.

- [ ] **Step 3: Add shared data path factory**

Add this method to `TelemetryDataPaths.java`:

```java
@Nonnull
public static TelemetryDataPaths forSharedCoordinator(@Nonnull JavaPlugin plugin) {
    Path dataDirectory = plugin.getDataDirectory().toAbsolutePath().normalize();
    Path modsDirectory = resolveModsDirectory(dataDirectory);
    Path userDataDirectory = modsDirectory == null ? null : modsDirectory.getParent();
    Path runtimeRoot = userDataDirectory == null
            ? dataDirectory.resolve("TelemetryCoordinator").toAbsolutePath().normalize()
            : userDataDirectory.resolve("Telemetry").toAbsolutePath().normalize();
    Path telemetryRoot = runtimeRoot.resolve("Telemetry");
    Path settingsRoot = runtimeRoot.resolve("Settings");
    return new TelemetryDataPaths(
            runtimeRoot,
            settingsRoot.resolve("runtime.json"),
            settingsRoot.resolve("projects"),
            telemetryRoot,
            telemetryRoot.resolve("crash-reports"),
            telemetryRoot.resolve("events"),
            modsDirectory
    );
}
```

- [ ] **Step 4: Run data path tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: all `TelemetryDataPathsTest` tests pass.

- [ ] **Step 5: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java
git commit -m "Feat: add shared telemetry coordinator data paths"
```

## Task 5: Multi-Project Coordinator Service

**Files:**
- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorService.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\coordinator\TelemetryCoordinatorServiceTest.java`

- [ ] **Step 1: Write coordinator service test for embedded project capture**

Create `TelemetryCoordinatorServiceTest.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoordinatorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void activeCoordinatorCapturesForEmbeddedDescriptorDiscoveredInModsDirectory() throws Exception {
        Path modsDirectory = tempDir.resolve("UserData").resolve("Mods");
        Path runtimeRoot = tempDir.resolve("UserData").resolve("Telemetry");
        Path modDirectory = modsDirectory.resolve("Alec's Tamework!");
        Files.createDirectories(modDirectory.resolve("telemetry"));
        Files.writeString(modDirectory.resolve("manifest.json"), """
                {
                  "Group": "Alechilles",
                  "Name": "Alec's Tamework!",
                  "Version": "2.14.1",
                  "Main": "com.alechilles.alecstamework.Tamework"
                }
                """);
        Files.writeString(modDirectory.resolve("telemetry").resolve("project.json"), """
                {
                  "projectId": "alecs-tamework",
                  "displayName": "Alec's Tamework!",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Alechilles:Alec's Tamework!"],
                  "packagePrefixes": ["com.alechilles.alecstamework"],
                  "events": {
                    "errors": {
                      "enabled": true,
                      "details": {
                        "settings_failed": {
                          "allowedFields": {
                            "source": { "type": "enum", "values": ["api"] }
                          }
                        }
                      }
                    }
                  },
                  "defaults": { "destinationMode": "custom" },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """);
        TelemetryDataPaths paths = new TelemetryDataPaths(
                runtimeRoot,
                runtimeRoot.resolve("Settings").resolve("runtime.json"),
                runtimeRoot.resolve("Settings").resolve("projects"),
                runtimeRoot.resolve("Telemetry"),
                runtimeRoot.resolve("Telemetry").resolve("crash-reports"),
                runtimeRoot.resolve("Telemetry").resolve("events"),
                modsDirectory
        );
        RecordingClient client = new RecordingClient();
        TelemetryCoordinatorService service = TelemetryCoordinatorService.create(paths, client, null, null);

        assertTrue(service.recordError("alecs-tamework", "settings_failed", null, Map.of("source", "api")));
        assertEquals(1, service.flushPendingReportsNow("test", "alecs-tamework").attempted());

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("alecs-tamework", payload.get("projectId").getAsString());
        assertEquals("settings_failed", payload.get("eventName").getAsString());
        assertEquals("embedded", payload.getAsJsonObject("environment").get("runtimeMode").getAsString());
    }

    private static final class RecordingClient implements CrashReportClient {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            payloads.add(payloadJson);
            return UploadResult.success(204);
        }
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCoordinatorServiceTest test
```

Expected: compilation fails because `TelemetryCoordinatorService` does not exist.

- [ ] **Step 3: Implement coordinator service**

Create `TelemetryCoordinatorService.java`:

```java
package com.alechilles.alecstelemetry.coordinator;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public final class TelemetryCoordinatorService {

    private final TelemetryDataPaths dataPaths;
    private final TelemetryRuntimeSettings settings;
    private final TelemetryCoreEngine engine;
    private final List<TelemetryProjectRegistration> projects;

    @Nonnull
    public static TelemetryCoordinatorService create(@Nonnull TelemetryDataPaths dataPaths,
                                                     @Nullable HytaleLogger logger,
                                                     @Nullable ScheduledExecutorService executor) {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        return create(
                dataPaths,
                new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger),
                logger,
                executor
        );
    }

    @Nonnull
    static TelemetryCoordinatorService create(@Nonnull TelemetryDataPaths dataPaths,
                                              @Nonnull CrashReportClient client,
                                              @Nullable HytaleLogger logger,
                                              @Nullable ScheduledExecutorService executor) {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryProjectDiscovery.DiscoveryResult discovery = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
                .loadAll(dataPaths.projectSettingsDirectory());
        List<TelemetryProjectRegistration> projects = applyOverrides(discovery.consentProjects(), overrides);
        return new TelemetryCoordinatorService(settings, dataPaths, projects, discovery.loadedMods(), client, logger, executor);
    }

    private TelemetryCoordinatorService(@Nonnull TelemetryRuntimeSettings settings,
                                        @Nonnull TelemetryDataPaths dataPaths,
                                        @Nonnull List<TelemetryProjectRegistration> projects,
                                        @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                        @Nonnull CrashReportClient client,
                                        @Nullable HytaleLogger logger,
                                        @Nullable ScheduledExecutorService executor) {
        this.settings = settings;
        this.dataPaths = dataPaths;
        this.projects = List.copyOf(projects);
        this.engine = new TelemetryCoreEngine(settings, dataPaths, projects, projects, loadedMods, client, logger, executor);
    }

    public void start() {
        engine.start();
    }

    public void shutdown() {
        engine.shutdown();
    }

    public boolean recordError(@Nonnull String projectId,
                               @Nonnull String eventName,
                               @Nullable Throwable throwable,
                               @Nonnull Map<String, Object> details) {
        TelemetryEventContext context = contextFrom(details);
        engine.recordErrorWithContext(projectId, eventName, throwable, context);
        return engine.findProject(projectId) != null;
    }

    public boolean recordUsage(@Nonnull String projectId,
                               @Nonnull String eventName,
                               @Nonnull Map<String, Object> details) {
        engine.recordUsageWithContext(projectId, eventName, contextFrom(details));
        return engine.findProject(projectId) != null;
    }

    public boolean requestFlush(@Nullable String projectId) {
        return engine.triggerFlushAsync(projectId);
    }

    @Nonnull
    public TelemetryCoreEngine.FlushSummary flushPendingReportsNow(@Nonnull String reason, @Nullable String projectId) {
        return engine.flushPendingReportsNow(reason, projectId);
    }

    @Nonnull
    public List<TelemetryProjectRegistration> projects() {
        return projects;
    }

    @Nonnull
    private static TelemetryEventContext contextFrom(@Nonnull Map<String, Object> details) {
        TelemetryEventContext.Builder builder = TelemetryEventContext.builder();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                builder.detail(entry.getKey(), text);
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.detail(entry.getKey(), value);
            }
        }
        return builder.build();
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> applyOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides) {
        ArrayList<TelemetryProjectRegistration> resolved = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            resolved.add(project.withOverride(overrides.get(project.projectId().toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(resolved);
    }
}
```

- [ ] **Step 4: Add missing bridge methods if compile reveals `TelemetryEventContext.Builder.detail(String, Object)` does not exist**

If the runtime compile fails because `TelemetryEventContext.Builder` lacks `detail(String, Object)`, add a builder method in `TelemetryEventContext.java`:

```java
@Nonnull
public Builder detail(@Nonnull String key, @Nonnull Object value) {
    if (details == null) {
        details = new LinkedHashMap<>();
    }
    details.put(key, value);
    return this;
}
```

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCoordinatorServiceTest test
```

Expected: test compiles and passes.

- [ ] **Step 5: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryEventContext.java
git commit -m "Feat: add shared telemetry coordinator service"
```

## Task 6: Embedded Bootstrap Registers Candidate And Proxies

**Files:**
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryBootstrap.java`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryService.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\embedded\EmbeddedTelemetryServiceTest.java`

- [ ] **Step 1: Add embedded passive forwarding test**

Add this test to `EmbeddedTelemetryServiceTest.java`:

```java
@Test
void embeddedServiceForwardsToNewerActiveCoordinatorWhenItLosesElection() {
    TelemetryCoordinatorRegistry.clearForTests();
    RecordingCoordinatorBridge active = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
            "newer-embedded",
            TelemetryRuntimeOrigin.EMBEDDED,
            "0.1.4",
            TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
            "Alechilles:Newer Mod",
            "1.0.0",
            tempDir.resolve("Newer.jar"),
            tempDir.resolve("UserData").resolve("Telemetry")
    ));
    TelemetryCoordinatorRegistry.register(active);

    EmbeddedTelemetryService service = EmbeddedTelemetryService.passiveProxy(
            "alecs-tamework",
            "Alec's Tamework!",
            null
    );
    service.recordUsage("settings_page_opened", TelemetryEventContext.usage()
            .detail("source", "api")
            .build());

    assertEquals("alecs-tamework", active.lastProjectId);
    assertEquals("settings_page_opened", active.lastEventName);
    assertEquals("api", active.lastDetails.get("source"));
}
```

Add this nested helper to the test class:

```java
private static final class RecordingCoordinatorBridge implements TelemetryCoordinatorBridge {
    private final TelemetryRuntimeCandidate candidate;
    private boolean active;
    private String lastProjectId;
    private String lastEventName;
    private Map<String, Object> lastDetails = Map.of();

    private RecordingCoordinatorBridge(TelemetryRuntimeCandidate candidate) {
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

    @Override
    public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
        lastProjectId = projectId;
        lastEventName = eventName;
        lastDetails = details;
        return true;
    }
}
```

- [ ] **Step 2: Run embedded tests to verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest test
```

Expected: compilation fails because `EmbeddedTelemetryService.passiveProxy(...)` does not exist.

- [ ] **Step 3: Add passive proxy mode to `EmbeddedTelemetryService`**

Add fields:

```java
private final boolean passiveProxy;
```

Update active constructors to set:

```java
this.passiveProxy = false;
```

Update disabled constructor to set:

```java
this.passiveProxy = false;
```

Add factory:

```java
@Nonnull
public static EmbeddedTelemetryService passiveProxy(@Nonnull String projectId,
                                                    @Nonnull String displayName,
                                                    @Nullable HytaleLogger logger) {
    EmbeddedTelemetryService service = disabled(projectId, displayName, logger, "Forwarding to active telemetry coordinator.");
    service.passiveProxy = true;
    return service;
}
```

If `passiveProxy` cannot be final after adding this factory, remove `final` from the field and keep all writes inside constructors/factory methods.

Add helper methods:

```java
@Nullable
private TelemetryCoordinatorBridge activeBridge() {
    return TelemetryCoordinatorRegistry.activeBridge();
}

@Nonnull
private static Map<String, Object> detailsFrom(@Nullable TelemetryEventContext context) {
    return context == null ? Map.of() : context.normalize().details();
}
```

Update `recordUsageWithContext` before local engine use:

```java
if (passiveProxy) {
    TelemetryCoordinatorBridge bridge = activeBridge();
    if (bridge != null) {
        bridge.recordUsage(project.projectId(), eventName, detailsFrom(context));
    }
    return;
}
```

Repeat the same passive-bridge pattern for `recordErrorWithContext`, `recordLifecycleWithContext`, `recordPerformanceWithContext`, `recordBreadcrumb`, and `requestFlush`.

- [ ] **Step 4: Update embedded bootstrap to register a candidate**

In `EmbeddedTelemetryBootstrap.bootstrap(...)`, after descriptor validation and before returning the service, create shared paths:

```java
TelemetryDataPaths coordinatorPaths = TelemetryDataPaths.forSharedCoordinator(plugin);
TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
        "embedded:" + pluginIdentifier,
        TelemetryRuntimeOrigin.EMBEDDED,
        EmbeddedTelemetryBootstrap.class.getPackage().getImplementationVersion() == null
                ? "0.0.0"
                : EmbeddedTelemetryBootstrap.class.getPackage().getImplementationVersion(),
        TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
        pluginIdentifier,
        pluginVersion,
        resolvePluginSourcePath(plugin) == null ? Path.of(pluginIdentifier.replace(':', '_')) : resolvePluginSourcePath(plugin),
        coordinatorPaths.runtimeRoot()
);
TelemetryCoordinatorService coordinator = TelemetryCoordinatorService.create(
        coordinatorPaths,
        logger,
        HytaleServer.SCHEDULED_EXECUTOR
);
TelemetryCoordinatorRegistry.register(new LocalCoordinatorBridge(candidate, coordinator));
TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
if (active == null || !candidate.providerId().equals(active.providerId())) {
    return EmbeddedTelemetryService.passiveProxy(descriptor.projectId(), descriptor.displayName(), logger);
}
```

Add a private nested bridge in `EmbeddedTelemetryBootstrap`:

```java
private record LocalCoordinatorBridge(@Nonnull TelemetryRuntimeCandidate candidate,
                                      @Nonnull TelemetryCoordinatorService service) implements TelemetryCoordinatorBridge {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

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
        ACTIVE.set(true);
    }

    @Override
    public void deactivate() {
        ACTIVE.set(false);
    }

    @Override
    public boolean isActive() {
        return ACTIVE.get();
    }

    @Override
    public void start() {
        service.start();
    }

    @Override
    public void shutdown() {
        service.shutdown();
    }

    @Override
    public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
        return service.recordUsage(projectId, eventName, details);
    }

    @Override
    public boolean recordError(String projectId, String eventName, Throwable throwable, Map<String, Object> details) {
        return service.recordError(projectId, eventName, throwable, details);
    }

    @Override
    public boolean requestFlush(String projectId) {
        return service.requestFlush(projectId);
    }
}
```

Use an instance `AtomicBoolean`, not static, if Java rejects the record static field or multiple bridges are needed in the same classloader.

- [ ] **Step 5: Run embedded tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest test
```

Expected: all embedded tests pass.

- [ ] **Step 6: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java
git commit -m "Feat: register embedded telemetry coordinator candidates"
```

## Task 7: Standalone Uses Coordinator Registry

**Files:**
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\AlecsTelemetry.java`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryRuntimeService.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\test\java\com\alechilles\alecstelemetry\runtime\TelemetryRuntimeServiceTest.java`

- [ ] **Step 1: Add standalone loses-to-newer-embedded test**

Add this test to `TelemetryRuntimeServiceTest.java`:

```java
@Test
void standaloneRuntimeServiceReportsPassiveWhenNewerEmbeddedCoordinatorWins() {
    TelemetryCoordinatorRegistry.clearForTests();
    RecordingCoordinatorBridge embedded = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
            "embedded:tamework",
            TelemetryRuntimeOrigin.EMBEDDED,
            "0.1.4",
            TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
            "Alechilles:Alec's Tamework!",
            "2.14.1",
            tempDir.resolve("Alec's Tamework! v2.14.1.jar"),
            tempDir.resolve("UserData").resolve("Telemetry")
    ));
    TelemetryCoordinatorRegistry.register(embedded);

    TelemetryRuntimeService service = TelemetryRuntimeService.createForTests(
            settings(),
            dataPaths(),
            "0.1.3",
            List.of(project("alecs-tamework", "Alec's Tamework!", "embedded")),
            List.of(),
            new RecordingClient(),
            null,
            null
    );

    service.start();

    assertFalse(service.ownsActiveCoordinator());
    assertEquals("embedded:tamework", service.activeCoordinatorProviderId());
}
```

Add a local `RecordingCoordinatorBridge` helper matching the one from Task 6, or move it into a package-private test fixture if the file already has helper fixtures.

- [ ] **Step 2: Run standalone service test to verify failure**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: compilation fails because `ownsActiveCoordinator()`, `activeCoordinatorProviderId()`, and the test factory do not exist.

- [ ] **Step 3: Refactor `TelemetryRuntimeService` around coordinator**

Add fields:

```java
private final TelemetryRuntimeCandidate candidate;
private final TelemetryCoordinatorService coordinatorService;
```

Add methods:

```java
public boolean ownsActiveCoordinator() {
    TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
    return active != null && active.providerId().equals(candidate.providerId());
}

@Nullable
public String activeCoordinatorProviderId() {
    TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
    return active == null ? null : active.providerId();
}
```

Update `start()`:

```java
public void start() {
    TelemetryCoordinatorRegistry.register(new StandaloneCoordinatorBridge(candidate, coordinatorService));
}
```

Update `shutdown()`:

```java
public void shutdown() {
    TelemetryCoordinatorRegistry.unregister(candidate.providerId());
}
```

Add nested bridge:

```java
private static final class StandaloneCoordinatorBridge implements TelemetryCoordinatorBridge {
    private final TelemetryRuntimeCandidate candidate;
    private final TelemetryCoordinatorService service;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private StandaloneCoordinatorBridge(TelemetryRuntimeCandidate candidate, TelemetryCoordinatorService service) {
        this.candidate = candidate;
        this.service = service;
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
        active.set(true);
    }

    @Override
    public void deactivate() {
        active.set(false);
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public void start() {
        service.start();
    }

    @Override
    public void shutdown() {
        service.shutdown();
    }

    @Override
    public boolean recordUsage(String projectId, String eventName, Map<String, Object> details) {
        return service.recordUsage(projectId, eventName, details);
    }

    @Override
    public boolean recordError(String projectId, String eventName, Throwable throwable, Map<String, Object> details) {
        return service.recordError(projectId, eventName, throwable, details);
    }

    @Override
    public boolean requestFlush(String projectId) {
        return service.requestFlush(projectId);
    }
}
```

- [ ] **Step 4: Update `AlecsTelemetry` startup logging**

Change the start log branch to:

```java
String activeProvider = runtimeService.activeCoordinatorProviderId();
getLogger().at(Level.INFO).log(
        "Alec's Telemetry enabled. Active coordinator="
                + (activeProvider == null ? "<none>" : activeProvider)
                + ", standaloneOwnsRuntime=" + runtimeService.ownsActiveCoordinator()
                + ", registered projects=" + runtimeService.registeredProjectCount()
);
```

- [ ] **Step 5: Run standalone tests**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: `TelemetryRuntimeServiceTest` passes.

- [ ] **Step 6: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java
git commit -m "Feat: let standalone participate in telemetry coordinator election"
```

## Task 8: Command/API Proxy Behavior

**Files:**
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\api\internal\TelemetryProjectHandleImpl.java`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\api\internal\TelemetryRuntimeApiImpl.java`
- Modify command classes under `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\main\java\com\alechilles\alecstelemetry\commands`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\test\java\com\alechilles\alecstelemetry\api\TelemetryProjectHandleCompatibilityTest.java`
- Test: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\standalone\src\test\java\com\alechilles\alecstelemetry\commands\TelemetryCommandRootTest.java`

- [ ] **Step 1: Add API test that flush proxies to active coordinator**

Add to `TelemetryProjectHandleCompatibilityTest.java`:

```java
@Test
void dependencyApiFlushUsesActiveCoordinatorWhenStandaloneIsPassive() {
    TelemetryCoordinatorRegistry.clearForTests();
    RecordingCoordinatorBridge embedded = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
            "embedded:tamework",
            TelemetryRuntimeOrigin.EMBEDDED,
            "0.1.4",
            TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
            "Alechilles:Alec's Tamework!",
            "2.14.1",
            tempDir.resolve("Alec's Tamework! v2.14.1.jar"),
            tempDir.resolve("UserData").resolve("Telemetry")
    ));
    TelemetryCoordinatorRegistry.register(embedded);

    TelemetryProjectHandle handle = handleForProject("alecs-tamework");

    assertTrue(handle.requestFlush());
    assertEquals("alecs-tamework", embedded.lastFlushProjectId);
}
```

- [ ] **Step 2: Run API test to verify failure**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryProjectHandleCompatibilityTest test
```

Expected: test fails because the handle still calls the local standalone runtime directly.

- [ ] **Step 3: Update project handle flush implementation**

In `TelemetryProjectHandleImpl.requestFlush()`, use:

```java
TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
if (active != null) {
    return active.requestFlush(project.projectId());
}
return runtime.triggerFlushAsync(project.projectId());
```

- [ ] **Step 4: Update command flush implementation**

In `TelemetryFlushCommand`, route the actual flush trigger through `TelemetryRuntimeService.triggerFlushAsync(projectId)` after updating that service method to:

```java
public boolean triggerFlushAsync(@Nullable String projectId) {
    TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
    if (active != null) {
        return active.requestFlush(projectId);
    }
    return coordinatorService.requestFlush(projectId);
}
```

- [ ] **Step 5: Run API and command tests**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryProjectHandleCompatibilityTest,TelemetryCommandRootTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/api/internal standalone/src/main/java/com/alechilles/alecstelemetry/commands standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java standalone/src/test/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRootTest.java
git commit -m "Feat: proxy standalone telemetry API to active coordinator"
```

## Task 9: Docs Update

**Files:**
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\embedded-mode.md`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\embedded-mode-refactor-plan.md`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\wiki\Integration-Guides\Embedded-Mode.md`
- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\README.md`

- [ ] **Step 1: Replace old coexistence rule**

In each docs file, replace text equivalent to:

```markdown
If a mod declares embedded mode, the standalone runtime ignores that mod.
```

with:

```markdown
Every installed copy of Alec's Telemetry, standalone or embedded, registers as a runtime coordinator candidate. The latest compatible runtime version wins. Non-winning copies become passive clients and forward telemetry operations to the active coordinator. If standalone is installed but an embedded copy is newer, standalone can still provide commands and UI while the newer embedded runtime owns capture, queueing, and upload.
```

- [ ] **Step 2: Add precedence section**

Add this section to `docs/embedded-mode.md` and mirror it into the wiki page:

```markdown
## Runtime Precedence

Runtime ownership is selected per server process:

1. Only coordinator candidates with the current coordinator protocol are eligible.
2. The highest telemetry runtime version wins.
3. If versions match, standalone wins over embedded.
4. If versions and origin match, provider plugin identifier and source path provide a stable tie-breaker.

The active coordinator handles descriptors from all installed enabled mods, including descriptors marked `runtimeMode: "embedded"`. Passive embedded copies do not install their own uncaught exception handlers, stats heartbeats, queues, or upload loops.
```

- [ ] **Step 3: Run docs grep**

Run:

```powershell
rg -n "standalone runtime ignores|owning mod only|global project registry|local ownership|Default: no shared commands" README.md docs wiki
```

Expected: no stale statements remain unless they are explicitly marked as historical notes.

- [ ] **Step 4: Commit**

```powershell
git add README.md docs/embedded-mode.md docs/embedded-mode-refactor-plan.md wiki/Integration-Guides/Embedded-Mode.md
git commit -m "Docs: describe telemetry coordinator precedence"
```

## Task 10: End-To-End Verification

**Files:**
- No planned source changes.

- [ ] **Step 1: Run runtime tests**

Run:

```powershell
.\mvnw.cmd -pl runtime test
```

Expected: all runtime tests pass.

- [ ] **Step 2: Run standalone tests with runtime module**

Run:

```powershell
.\mvnw.cmd -pl standalone -am test
```

Expected: all runtime and standalone tests pass.

- [ ] **Step 3: Package standalone jar**

Run:

```powershell
.\mvnw.cmd -pl standalone -am package
```

Expected: package succeeds and produces `standalone\target\Alec's Telemetry v0.1.3.jar` or the current project version.

- [ ] **Step 4: Inspect packaged standalone classes**

Run:

```powershell
jar tf "standalone\target\Alec's Telemetry v0.1.3.jar" | Select-String -Pattern "TelemetryCoordinatorRegistry|TelemetryCoordinatorService|EmbeddedTelemetryBootstrap|AlecsTelemetry.class"
```

Expected: standalone jar contains coordinator classes and `AlecsTelemetry.class`.

- [ ] **Step 5: Inspect installed Tamework jar before manual server run**

Run:

```powershell
jar tf "C:\Users\22ale\AppData\Roaming\Hytale\UserData\Mods\Alec's Tamework! v2.14.1.jar" | Select-String -Pattern "telemetry/project.json|EmbeddedTelemetryBootstrap.class"
tar -xOf "C:\Users\22ale\AppData\Roaming\Hytale\UserData\Mods\Alec's Tamework! v2.14.1.jar" telemetry/project.json | Select-String -Pattern '"projectId"|"runtimeMode"'
```

Expected:

```text
telemetry/project.json
com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class
"projectId": "alecs-tamework"
"runtimeMode": "embedded"
```

- [ ] **Step 6: Manual server verification**

Install a test standalone jar with version lower than the embedded runtime candidate, then start the Hytale server.

Expected log signal:

```text
Alec's Telemetry enabled. Active coordinator=embedded:Alechilles:Alec's Tamework!, standaloneOwnsRuntime=false
```

Run `/telemetry status`.

Expected behavior:

- The command is available if standalone is installed.
- Status names the active coordinator provider.
- Tamework appears as a registered telemetry project.
- Flush requests are routed to the active coordinator.

- [ ] **Step 7: Commit manual verification notes**

Create `docs/verification/2026-06-17-telemetry-coordinator-election.md` with:

```markdown
# Telemetry Coordinator Election Verification

Date: 2026-06-17

## Automated

- `.\mvnw.cmd -pl runtime test`: passed
- `.\mvnw.cmd -pl standalone -am test`: passed
- `.\mvnw.cmd -pl standalone -am package`: passed

## Package Inspection

- Standalone jar contains coordinator classes.
- Tamework jar contains `telemetry/project.json`.
- Tamework descriptor declares `runtimeMode: "embedded"`.

## Manual Server

- Active coordinator:
- Standalone owns runtime:
- Registered projects:
- `/telemetry status` result:
- Manual flush result:
```

Then run:

```powershell
git add docs/verification/2026-06-17-telemetry-coordinator-election.md
git commit -m "Test: document telemetry coordinator verification"
```

## Risks And Decisions To Recheck During Execution

- The registry must stay reflection-first. Do not replace reflective bridge access with direct `instanceof TelemetryCoordinatorBridge` checks, because shaded runtime copies can load the same class name through different plugin classloaders.
- `System.getProperties()` stores object values even though `setProperty` is string-only. Use `putIfAbsent`, not `setProperty`.
- Do not let passive candidates call `TelemetryCoreEngine.start()`.
- Do not let old standalone skip embedded descriptors for runtime capture after `TelemetryCoordinatorService` is introduced.
- Keep coordinator protocol version explicit so a future breaking embedded runtime cannot accidentally be selected by older code.

## Self-Review

Spec coverage:

- Latest compatible runtime wins: Task 1 and Task 2.
- Embedded can beat standalone when newer: Task 1 and Task 7.
- Only one active runtime handles telemetry: Task 2, Task 6, Task 7.
- Active embedded can handle all installed telemetry projects: Task 3, Task 4, Task 5.
- Standalone remains useful as command/UI shell: Task 7 and Task 8.
- Docs no longer describe embedded as owning only itself: Task 9.

Placeholder scan:

- No `TBD`, `TODO`, or undefined feature placeholders are intentionally left in this plan.

Type consistency:

- Coordinator candidate types are introduced before registry/service tasks.
- Registry and bridge use JDK-only argument types for passive forwarding.
- Existing `TelemetryEventContext` remains local to runtime implementations and is converted at bridge boundaries.
