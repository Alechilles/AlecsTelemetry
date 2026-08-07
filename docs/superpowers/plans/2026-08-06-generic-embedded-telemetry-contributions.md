# Generic Embedded Telemetry Contributions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let any embeddable mod register its own independently consented Alec's Telemetry project, while every physical host and isolated classloader still converges on one runtime provider and one writable candidate per logical project.

**Architecture:** Add a public anchored-descriptor contribution API, a JDK-only process-wide contribution registry with deterministic per-project election and opaque write tokens, and a transactional live project catalog in the elected coordinator. Contributor services share a reference-counted host-local provider lease, replay the full contribution snapshot across provider handoff, and retain queued envelopes when a project retires. Patchwork is the generic reference consumer; Tamework verifies the shaded two-project/one-provider result.

**Tech Stack:** Java 25, Gradle composite build, Maven multi-module builds, JUnit 5, Gson, Hytale `JavaPlugin`, shaded JAR integration tests, JDK reflection and collections for cross-classloader protocols.

## Global Constraints

- Preserve `EmbeddedTelemetryBootstrap.bootstrap(JavaPlugin)` source and binary behavior.
- Keep Telemetry core generic: no Patchwork identifiers or signal names outside Patchwork.
- Cross-classloader state is limited to JDK types, `Throwable`, and reflective bridge objects.
- Contribution ABI starts at `1`; coordinator protocol advances from `2` to `3`.
- Registration alone emits nothing. Every write passes active-token, descriptor, consent, allowlist, sampling, and destination gates.
- A Telemetry failure returns a disabled service or a no-op result and never blocks the embedding mod's lifecycle.
- Do not delete queued envelopes when a contribution retires; immutable envelope destination metadata remains authoritative for retry.
- Do not add source-text, declaration-order, class-presence, descriptor-presence, or exact-JAR-inventory tests. Every new test must invoke production behavior and assert an observable result.
- Preserve unrelated dirty files, including Alec's Telemetry release artifacts and Tamework's modified `TameworkCommandSelectionPageRefreshTest.java`.
- Use Git Bash for every command. Stage and commit only the current repository's intended files.
- Update `docs/privacy-policy.md` in the same Telemetry implementation commit because this changes project identity, consent, queue, and destination behavior.

## 1.1.0 MVP amendment

The implementation plan's broader live-replacement design is deferred for this
release. The shipped MVP must enforce the following acceptance boundaries:

- anchored contributions are hosted-only; conventional descriptors retain
  custom-endpoint behavior;
- an established same-ID winner is fenced on retirement, with registered
  fallbacks passive until restart or explicit re-registration (no hot failover);
- only setup breadcrumbs, lifecycle events, and setup-failure captures may be
  buffered before `start()`; other operations return `not_started` and are not
  replayed;
- heartbeat aggregates are split by resolved event destination;
- provider-pool start/close lifecycle calls remain serialized through the pool
  lock, with a latch-backed behavior test for last-lease shutdown versus a new
  acquire.

The unchecked full-replacement and custom-contributed-destination items below
remain future work until immutable queued routing and consent semantics are
specified and tested.

## Public and Protocol Contracts

The public contributor API is fixed as:

```java
TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
        .descriptorResource(
                PatchworkTelemetry.class,
                "META-INF/alecs-telemetry/projects/patchwork.json"
        )
        .logicalPluginIdentifier("Alechilles:Patchwork")
        .logicalPluginVersion(PatchworkVersion.current())
        .build();

EmbeddedTelemetryService telemetry =
        EmbeddedTelemetryBootstrap.contribute(hostPlugin, contribution);
```

`TelemetryProjectContribution` is an immutable value object with these accessors:

```java
public final class TelemetryProjectContribution {
    public static Builder builder();
    public Class<?> resourceAnchor();
    public String descriptorResource();
    public String logicalPluginIdentifier();
    public String logicalPluginVersion();
}
```

Each registered candidate supplies a JDK-only bridge. The process-wide registry uses it only after atomically validating that the caller token is still the elected winner:

```java
public interface TelemetryProjectContributionBridge {
    int contributionAbiVersion();
    Map<String, Object> candidate();
    Map<String, Object> dispatch(
            String token,
            String operation,
            Map<String, Object> payload,
            Throwable throwable
    );
}
```

The runtime coordinator bridge gains snapshot reconciliation:

```java
default boolean reconcileProjectContributions(
        long revision,
        List<Map<String, Object>> contributions
) {
    return false;
}

default Map<String, Object> dispatchProjectContribution(
        String token,
        String operation,
        Map<String, Object> payload,
        Throwable throwable
) {
    return Map.of("accepted", false, "reason", "contribution_dispatch_unavailable");
}
```

Operation names are constants owned by Telemetry: `breadcrumb`, `error`, `lifecycle`, `performance`, `usage`, `stats`, `capture_setup`, `capture_start`, `capture_world_removal`, `test_report`, `manual_report`, and `flush`. Payloads contain only the existing bounded fields for that operation; logical project identity comes from the validated token, never from caller payload.

The contribution registry entry is a `Map<String,Object>` with these exact keys: `abiVersion`, `projectId`, `logicalPluginIdentifier`, `logicalPluginVersion`, `origin`, `hostPluginIdentifier`, `hostPluginVersion`, `sourcePath`, `descriptorJson`, `descriptorHash`, `token`, and `bridge`. The system-property registry key is `com.alechilles.alecstelemetry.project-contribution.registry.v1`.

---

## Task 1: Add the Anchored Contribution Model and Compatible Bootstrap

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/TelemetryProjectContribution.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`

- [ ] **Step 1: Write failing production-behavior tests**

Add tests that build an anchored contribution, load a descriptor from the anchor's classloader, and assert the returned service reports the descriptor project ID and logical version rather than the host identity. Add a malformed/missing-resource case that returns a disabled service with a diagnostic reason. Re-run the existing conventional bootstrap test unchanged to pin compatibility.

```java
@Test
void anchoredContributionUsesLogicalIdentityInsteadOfPhysicalHost() {
    TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
            .descriptorResource(TestAnchor.class, "fixtures/contributed-project.json")
            .logicalPluginIdentifier("Example:Library")
            .logicalPluginVersion("2.4.0")
            .build();

    EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

    assertEquals("example-library", service.projectId());
    assertNull(service.disabledReason());
}
```

- [ ] **Step 2: Verify the test fails for the missing API**

Run:

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*EmbeddedTelemetryServiceTest'
```

Expected: compilation fails because `TelemetryProjectContribution` and `contribute(...)` do not exist.

- [ ] **Step 3: Implement the immutable builder and anchored loading**

Validate non-null/non-blank builder inputs immediately. Normalize the resource path by removing one leading slash. Load with `resourceAnchor().getClassLoader().getResourceAsStream(...)`; do not use the host plugin classloader. Parse using existing `TelemetryProjectDescriptor.fromJson(...)`, require the logical identifier in `ownerPluginIdentifiers`, and require a valid Hytale `Semver` before entering runtime machinery.

Refactor conventional bootstrap through one private method that accepts descriptor bytes and derived identity. Keep the public descriptor lookup fallback paths and disabled-service messages compatible.

- [ ] **Step 4: Run the focused bootstrap tests**

Run the command from Step 2. Expected: all `EmbeddedTelemetryServiceTest` cases pass.

- [ ] **Step 5: Commit the public API slice**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded/TelemetryProjectContribution.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java
git commit -m "Feat: add embedded telemetry contributions"
```

## Task 2: Implement Deterministic Contribution Election and Token Fencing

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryProjectContributionBridge.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryProjectContributionCandidate.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryProjectContributionRegistry.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryProjectContributionRegistryTest.java`

- [ ] **Step 1: Write election and handoff tests**

Cover these observable outcomes in one focused test class:

1. Higher logical semantic version wins.
2. Standalone wins an equal-version tie over embedded.
3. Host ID, source path, and descriptor hash make equal candidates deterministic.
4. Retiring the winner fences its token before the fallback token becomes writable.
5. A different logical owner for an established project ID is quarantined.
6. Same-version descriptor drift elects one whole snapshot and reports a warning.
7. An incompatible ABI and invalid semantic version remain passive.

```java
assertFalse((boolean) registry.dispatch(oldToken, "usage", Map.of("eventName", "reload"), null).get("accepted"));
assertTrue((boolean) registry.dispatch(newToken, "usage", Map.of("eventName", "reload"), null).get("accepted"));
assertEquals(List.of(newToken), recordingBridge.acceptedTokens());
```

- [ ] **Step 2: Run the registry test and verify failure**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*TelemetryProjectContributionRegistryTest'
```

- [ ] **Step 3: Implement the JDK-only process registry**

Store raw bridge objects and maps in a system-property-owned `ConcurrentHashMap<String,Object>`. Generate tokens with `UUID.randomUUID().toString()`. Parse versions with Hytale `Semver`; reject invalid versions before comparison. Group project IDs case-insensitively and compare compatible candidates in the approved order. Publish an immutable winners snapshot and increment a monotonic revision only after the complete election succeeds.

Make `register(Object bridge)` return the token, `unregister(String token)` re-elect atomically, `isActive(String token)` read the same immutable snapshot, `activeContributions()` return defensive JDK-only maps, and `dispatch(...)` verify the current token then invoke the winner bridge reflectively. Return JDK-only result maps so report submission, flush, and diagnostics can preserve their existing return values; every result includes `accepted` and a bounded `reason` when rejected.

- [ ] **Step 4: Run registry and existing runtime-election tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*TelemetryProjectContributionRegistryTest' --tests '*TelemetryRuntimeCandidateTest' --tests '*TelemetryCoordinatorRegistryTest'
```

- [ ] **Step 5: Commit the contribution protocol**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryProjectContributionRegistryTest.java
git commit -m "Feat: elect telemetry project contributions"
```

## Task 3: Add a Transactional Live Project Catalog

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectCatalog.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorStatsHeartbeat.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetector.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/crash/CrashAttribution.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectCatalogTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectCollisionDetectorTest.java`

- [ ] **Step 1: Write live-catalog behavior tests**

Exercise production registration and engine APIs, not collection internals:

- adding a valid contribution makes a project handle and independent consent entry visible without restarting;
- a deliberately invalid proposed replacement leaves the previous project writable;
- removing an active contribution removes it from new consent and heartbeat snapshots;
- a queued event remains flushable after retirement;
- overlapping package prefixes suppress automatic crash attribution for both ambiguous projects while explicit project errors still persist.

```java
service.recordError("generation_failed", failure, null);
assertEquals(1L, pendingEventFileCount("library"));

catalog.reconcile(List.of());
assertEquals(1L, pendingEventFileCount("library"));
assertTrue(service.requestFlush());
```

- [ ] **Step 2: Run the focused tests and verify failure**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*TelemetryProjectCatalogTest' --tests '*TelemetryCoordinatorServiceTest' --tests '*TelemetryProjectCollisionDetectorTest'
```

- [ ] **Step 3: Implement immutable snapshots and transactional reconcile**

`TelemetryProjectCatalog` owns `AtomicReference<Snapshot>` and serializes reconciliation under one lock. Construct registrations, overrides, manual-report eligibility, collision diagnostics, and ambiguous prefix sets off to the side. Publish only a fully valid immutable snapshot. Expose `snapshot()`, `find(projectId)`, `reconcile(List<TelemetryProjectRegistration>)`, and `retire(projectId)`.

Replace `TelemetryCoreEngine`'s fixed project list with catalog snapshot reads at operation boundaries. Preserve per-project event/crash/report stores after retirement. Update consent, commands/runtime API consumers, report project selection, and the aggregate heartbeat to read the current snapshot instead of constructor-captured lists.

Filter ambiguous package prefixes in `CrashAttribution` before selecting any automatic owner; do not block explicit project-scoped writes.

- [ ] **Step 4: Run the project, consent, report, stats, and crash suites**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*TelemetryProjectCatalogTest' --tests '*TelemetryConsentCoordinatorTest' --tests '*TelemetryCoordinatorServiceTest' --tests '*TelemetryProjectCollisionDetectorTest' --tests '*Stats*' --tests '*Report*'
```

- [ ] **Step 5: Commit the live catalog**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry runtime/src/test/java/com/alechilles/alecstelemetry/project runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java
git commit -m "Feat: reconcile live telemetry projects"
```

## Task 4: Advance Coordinator Protocol and Replay Contributions Across Provider Handoff

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorBridge.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistryTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderParityTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryContributionProviderHandoffIT.java`

- [ ] **Step 1: Write provider replay tests**

Register a contribution before any provider, activate provider A, obtain a stable project handle, then introduce a newer isolated-classloader provider B. Assert B receives the complete revision/snapshot before it becomes active, the stable handle writes through B, and only one provider reports active. Also assert a provider that rejects reconciliation never replaces the last valid provider.

- [ ] **Step 2: Verify failure**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*TelemetryContributionProviderHandoffIT' --tests '*TelemetryCoordinatorRegistryTest' --tests '*TelemetryRuntimeProviderParityTest'
```

- [ ] **Step 3: Implement protocol 3 activation**

Set:

```java
public static final int COORDINATOR_PROTOCOL_VERSION = 3;
static final String REGISTRY_KEY =
        "com.alechilles.alecstelemetry.coordinator.registry.protocol.v3";
```

Teach `ReflectiveBridge` to call `reconcileProjectContributions(long,List)` and `dispatchProjectContribution(String,String,Map,Throwable)`, and reject bridges lacking protocol-3 behavior. During election, reconcile the complete immutable contribution snapshot before `activate()` and `start()`. On registry revision changes, reconcile the active provider transactionally. If replay fails, leave the old active provider/catalog intact and continue normal provider fallback.

- [ ] **Step 4: Run handoff and compatibility tests**

Use the command from Step 2. Expected: all pass, including existing direct-bootstrap parity.

- [ ] **Step 5: Commit protocol 3**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderParityTest.java
git commit -m "Feat: replay contributions across provider handoff"
```

## Task 5: Share Host-Local Provider Leases and Route the Full Token-Bound Service

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryEmbeddedProviderPool.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHost.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryHandle.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryContributionIT.java`

- [ ] **Step 1: Write lease, token, consent, and pre-start tests**

Cover the complete observable surface:

- conventional host plus contributed project yields two handles and one host-local provider;
- independent consent rejects a host event while accepting a component event;
- every existing operation category reaches the active contributed project;
- passive and retired tokens no-op;
- duplicate candidates buffer setup events before start, but only the elected candidate drains its buffer;
- shutdown is idempotent and the last started lease alone unregisters the local provider.

Use real `EmbeddedTelemetryService` calls such as `recordBreadcrumb`, `recordError`, `recordLifecycle`, `recordPerformance`, `recordUsage`, `recordStats`, setup/start capture, manual-report submission, and flush. Assert persisted/enqueued outcomes through existing engine diagnostics or stores.

- [ ] **Step 2: Run the focused service and host tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests '*EmbeddedTelemetryServiceTest' --tests '*EmbeddedTelemetryContributionIT' --tests '*TelemetryRuntimeHostTest'
```

- [ ] **Step 3: Implement the provider pool and token route**

Key leases by physical host identifier, normalized source path, runtime version, and Telemetry classloader identity. `acquire(...)` returns a prepared lease. The first `start()` registers/starts `TelemetryRuntimeHost`; later leases increment a started refcount. `close()` unregisters only when the count reaches zero.

Add a contributed-service mode to `EmbeddedTelemetryService`. Its state contains contribution token, registry bridge, provider lease, and a bounded pre-start operation buffer. Route every contributor operation through `dispatch(token, operation, payload, throwable)`. Existing conventional/coordinator/local modes remain unchanged. Fence the token before releasing the lease on shutdown.

- [ ] **Step 4: Run the full runtime test suite**

```bash
bash ../gradlew :alecstelemetry:runtime:test
```

- [ ] **Step 5: Commit service integration**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host runtime/src/test/java/com/alechilles/alecstelemetry/embedded runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host
git commit -m "Feat: route token-bound embedded telemetry"
```

## Task 6: Document, Version, and Verify Alec's Telemetry 1.1.0

**Files:**
- Modify: `pom.xml`
- Modify: `runtime/pom.xml`
- Modify: `standalone/pom.xml`
- Modify: `gradle.properties`
- Modify: `CHANGELOG.md`
- Modify: `docs/embedded-mode.md`
- Create: `docs/embedded-contributions.md`
- Modify: `docs/project-descriptor.md`
- Modify: `docs/usage-stats.md`
- Modify: `docs/command-reference.md`
- Modify: `docs/privacy-policy.md`

- [ ] **Step 1: Update current-state documentation and privacy language**

Document the generic API, unique resource path, independent consent/destinations, logical version attribution, local-only physical host provenance except existing `loadedMods`, candidate diagnostics, active heartbeat eligibility, queued-data retention after retirement, custom endpoint responsibility, protocol-3 compatibility, and graceful failure behavior.

Set Maven and Gradle versions to `1.1.0`. Add release notes for contribution ABI 1, coordinator protocol 3, existing-bootstrap compatibility, and the new generic contributor guide.

- [ ] **Step 2: Scan for stale versions and prohibited placeholders**

```bash
rg -n '1\.0\.[45]|protocol\.v2|COORDINATOR_PROTOCOL_VERSION = 2' pom.xml runtime standalone gradle.properties docs README.md CHANGELOG.md
rg -n 'TODO|TBD|FIXME' docs/embedded-contributions.md docs/privacy-policy.md
```

Expected: no stale release/protocol references in the edited current-state surfaces and no placeholders.

- [ ] **Step 3: Run the canonical build**

```bash
bash ../gradlew :alecstelemetry:standalone:build
```

- [ ] **Step 4: Commit the Telemetry release line**

```bash
git add pom.xml runtime/pom.xml standalone/pom.xml gradle.properties CHANGELOG.md docs
git commit -m "Docs: release embedded contribution support"
```

## Task 7: Add Generic Telemetry Contribution Support to Patchwork 1.3.0

**Repository:** `C:\Users\22ale\AppData\Roaming\Hytale\Modding\Patchwork`

**Files:**
- Modify: `pom.xml`
- Modify: `runtime/pom.xml`
- Modify: `standalone/pom.xml`
- Modify: `gradle.properties`
- Modify: `runtime/build.gradle`
- Modify: `standalone/build.gradle`
- Create: `runtime/src/main/java/com/alechilles/patchwork/telemetry/PatchworkTelemetry.java`
- Create: `runtime/src/main/resources/META-INF/alecs-telemetry/projects/patchwork.json`
- Modify: `runtime/src/main/java/com/alechilles/patchwork/embedded/EmbeddedPatchworkBootstrap.java`
- Modify: `runtime/src/main/java/com/alechilles/patchwork/embedded/StandalonePatchworkBootstrap.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/embedded/EmbeddedPatchworkBootstrapTest.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/embedded/StandalonePatchworkBootstrapTest.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/embedded/EmbeddedForeignClassLoaderIT.java`
- Modify: `standalone/src/test/java/com/alechilles/patchwork/standalone/PatchworkPluginLifecycleTest.java`

- [ ] **Step 1: Write lifecycle and isolated-classloader tests**

Assert Patchwork start registers one logical `Alechilles:Patchwork` contribution with version `1.3.0`, standalone and embedded origins are derived correctly, close retires it, duplicate isolated copies elect one active token, and a disabled Telemetry contribution does not prevent Patchwork activation or shutdown.

- [ ] **Step 2: Verify failure against the current Patchwork runtime**

```bash
cd ../Patchwork
bash ../gradlew :patchwork:runtime:test :patchwork:standalone:test
```

- [ ] **Step 3: Add the dependency, descriptor, and lifecycle wrapper**

Add `com.alechilles:alecstelemetry-runtime:1.1.0` as a normal transitive runtime API dependency in Maven and Gradle. Generate Maven `pom.properties` or an equivalent version resource in both build systems so `PatchworkVersion.current()` is `1.3.0` in packaged runtime artifacts.

`PatchworkTelemetry.prepare(hostPlugin)` builds the public generic contribution, catches environmental failures once, and exposes idempotent `start()`, `close()`, and project-scoped signal methods. Start it with Patchwork's service and close it before the Patchwork service releases runtime state. The standalone path uses the same wrapper and descriptor.

The descriptor must use project ID `patchwork`, owner `Alechilles:Patchwork`, package prefix `com.alechilles.patchwork`, and explicit `supported: true` / `defaultEnabled: true` for crash, errors, lifecycle, breadcrumbs, performance, usage, stats, and reports. Define the approved low-cardinality signal names and detail allowlists in the descriptor only.

- [ ] **Step 4: Run Patchwork lifecycle and classloader tests**

```bash
bash ../gradlew :patchwork:runtime:test :patchwork:standalone:test
```

- [ ] **Step 5: Commit the Patchwork contribution base**

```bash
git add pom.xml runtime/pom.xml standalone/pom.xml gradle.properties runtime/build.gradle standalone/build.gradle runtime/src/main runtime/src/test/java/com/alechilles/patchwork/embedded standalone/src/test/java/com/alechilles/patchwork/standalone/PatchworkPluginLifecycleTest.java
git commit -m "Feat: contribute Patchwork telemetry"
```

## Task 8: Emit Patchwork's Approved Operational Signals and Update Its Docs

**Repository:** `C:\Users\22ale\AppData\Roaming\Hytale\Modding\Patchwork`

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/patchwork/embedded/HytaleEarlyLoadComposition.java`
- Modify: `runtime/src/main/java/com/alechilles/patchwork/embedded/PatchworkAdministrationService.java`
- Modify: `runtime/src/main/java/com/alechilles/patchwork/embedded/PatchworkRuntimeHost.java`
- Modify: `runtime/src/main/java/com/alechilles/patchwork/reload/PatchReloadCoordinator.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/embedded/HytaleEarlyLoadCompositionTest.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/embedded/PatchworkAdministrationServiceTest.java`
- Modify: `runtime/src/test/java/com/alechilles/patchwork/reload/PatchReloadCoordinatorTest.java`
- Modify: `standalone/src/test/java/com/alechilles/patchwork/standalone/StandalonePackagingIT.java`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/Embedding.md`
- Modify: `docs/Operations.md`
- Modify: `wiki/Developer-Integration/Embed-Patchwork.md`
- Modify: `wiki/Reference-Library/Compatibility-and-Versions.md`
- Modify: `wiki/Server-Administration/Commands-and-Status.md`

- [ ] **Step 1: Add behavior tests for signals and graceful failure**

Use a recording project-scoped Telemetry adapter and assert the production paths emit:

- lifecycle `runtime_activated`, `runtime_deactivated`, `generation_completed`, `reload_completed`;
- errors `generation_failed`, `publish_failed`, `reload_failed`;
- performance `generation_duration`, `reload_duration`;
- bounded administrative/feature usage and high-level breadcrumbs.

Assert telemetry exceptions are swallowed after a bounded warning and do not change Patchwork's generation, publish, reload, or command outcome.

- [ ] **Step 2: Implement instrumentation at existing outcome boundaries**

Record once where the production service already knows success/failure and elapsed time. Do not add duplicate measurements in callers. Do not send player identity, asset contents, paths, exception messages outside existing Telemetry redaction, or unbounded values.

- [ ] **Step 3: Replace packaging-presence checks with packaged behavior**

Make `StandalonePackagingIT` load the built Patchwork jar in an isolated classloader, invoke the production bootstrap with the existing fake host fixture, and assert the registered logical project, version, origin, and token election. Do not assert ZIP entries merely exist.

- [ ] **Step 4: Update Patchwork user/developer documentation**

Explain automatic transitive Telemetry availability, independent Patchwork consent, the namespaced descriptor contract for embedders, non-fatal failures, and the `1.3.x` / Telemetry `1.1.x` compatibility line.

- [ ] **Step 5: Run Maven and Gradle verification**

```bash
./mvnw verify
bash ../gradlew :patchwork:runtime:test :patchwork:standalone:build
```

- [ ] **Step 6: Commit Patchwork signals and docs**

```bash
git add runtime/src standalone/src/test README.md CHANGELOG.md docs wiki
git commit -m "Feat: report Patchwork operations"
```

## Task 9: Align Tamework and Verify the Shaded Two-Project Result

**Repository:** `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecstamework`

**Files:**
- Modify: `gradle.properties`
- Modify: `src/test/java/com/alechilles/alecstamework/integration/patchwork/TameworkPatchworkContributionTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/integration/patchwork/PatchworkPackagingIT.java`
- Modify: `README.md`
- Modify: `docs/Build-and-Packaging.md`
- Modify: `wiki/Developer-Documentation/Tooling-and-Contribution/Integrations-Telemetry-and-Build-Workflow.md`

- [ ] **Step 1: Write the packaged behavior assertion**

Replace `PatchworkPackagingIT` ZIP/class/resource presence assertions with an isolated packaged bootstrap that loads Tamework's shaded jar, starts Tamework's existing conventional Telemetry service and its production `TameworkPatchworkRuntime`, then asserts:

```java
assertEquals(Set.of("tamework", "patchwork"), activeProjectIds());
assertEquals(1, activeLocalProviderCount());
assertTrue(recordProjectEvent("tamework", "test"));
assertTrue(recordProjectEvent("patchwork", "runtime_activated"));
```

Also add a dependency-backed integration case showing Tamework consent can be disabled while Patchwork remains writable.

- [ ] **Step 2: Align direct and transitive versions**

Set `patchwork_version=1.3.0` and `alecstelemetry_version=1.1.0`. Keep Tamework's direct Telemetry dependency because `CrashTelemetryService` directly uses the API. Do not change its conventional bootstrap.

- [ ] **Step 3: Verify dependency convergence**

```bash
cd ../alecstamework
bash ../gradlew :alecstamework:dependencyInsight --dependency alecstelemetry-runtime --configuration runtimeClasspath
```

Expected: the selected runtime is `1.1.0` for both the direct dependency and Patchwork's transitive edge.

- [ ] **Step 4: Run unit and packaged integration tests**

```bash
bash ../gradlew :alecstamework:test
bash ../gradlew :alecstamework:packagingTest
```

- [ ] **Step 5: Update dependency and consent documentation**

Describe the direct-plus-transitive dependency relationship, two independent consent projects, one shared provider, and the required version alignment. Preserve the unrelated modified UI refresh test by staging only the listed files.

- [ ] **Step 6: Commit Tamework alignment**

```bash
git add gradle.properties src/test/java/com/alechilles/alecstamework/integration/patchwork/TameworkPatchworkContributionTest.java src/test/java/com/alechilles/alecstamework/integration/patchwork/PatchworkPackagingIT.java README.md docs/Build-and-Packaging.md wiki/Developer-Documentation/Tooling-and-Contribution/Integrations-Telemetry-and-Build-Workflow.md
git commit -m "Build: align embedded telemetry dependencies"
```

## Task 10: Cross-Repository Acceptance Verification

**Files:**
- No production files expected.
- Modify only a repository's tests or docs if verification exposes an actual behavior defect; return to the owning task and use a new scoped commit.

- [ ] **Step 1: Run all canonical builds from the shared workspace**

```bash
cd ../AlecsTelemetry
bash ../gradlew :alecstelemetry:standalone:build
cd ../Patchwork
./mvnw verify
bash ../gradlew :patchwork:standalone:build
cd ../alecstamework
bash ../gradlew :alecstamework:test :alecstamework:packagingTest
```

- [ ] **Step 2: Run the multi-candidate compatibility fixture**

Combine packaged Tamework, standalone Patchwork, and standalone Telemetry candidates in isolated loaders. Assert provider election and project election are independent: protocol-3 Telemetry owns runtime work; the highest Patchwork logical version wins; Tamework remains a separate project; retiring either winner activates its fallback without duplicate writes.

- [ ] **Step 3: Review privacy and data behavior against the implementation**

Confirm no new physical-host field is uploaded, `loadedMods` retains its existing meaning, contributed logical version is the normal project version, consent is independent, custom endpoints remain project-specific, and retired queues retain existing policy coverage. If the implementation differs, update `docs/privacy-policy.md` before completion.

- [ ] **Step 4: Inspect repository status and commit history**

```bash
cd ../AlecsTelemetry && git status --short && git log --oneline -8
cd ../Patchwork && git status --short && git log --oneline -8
cd ../alecstamework && git status --short && git log --oneline -8
```

Expected: only the pre-existing unrelated artifacts/UI test remain dirty; all implementation work is committed in scoped commits.

- [ ] **Step 5: Request independent review**

Use `superpowers:requesting-code-review` with the approved design spec and this plan. Resolve only evidence-backed issues, rerun the affected focused test, then rerun the three canonical builds before declaring completion.

## Completion Checklist

- [ ] Generic anchored contributions work without conventional resource collisions.
- [ ] Existing direct embedded bootstrap remains compatible.
- [ ] Contribution ABI 1 and coordinator protocol 3 work across isolated classloaders.
- [ ] Provider and per-project elections are independent and deterministic.
- [ ] Only the active token can write; handoff has no duplicate interval.
- [ ] Live catalog reconciliation is transactional and queued data survives retirement.
- [ ] All existing telemetry categories obey independent project consent.
- [ ] Ambiguous automatic crash attribution is suppressed without blocking explicit events.
- [ ] Patchwork `1.3.0` supplies Telemetry `1.1.0` transitively and reports only approved bounded signals.
- [ ] Tamework's shaded jar exposes Tamework plus Patchwork with one local provider.
- [ ] Privacy, contributor, command/status, release, and integration documentation match shipped behavior.
- [ ] Alec's Telemetry, Patchwork, and Tamework canonical builds pass.
