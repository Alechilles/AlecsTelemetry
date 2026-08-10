# Passive Embedded Project Descriptors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Discover data-only telemetry project descriptors shaded into installed mods, report one independently consented Stats heartbeat per logical project, and safely upgrade the same project when executable integration registers richer categories.

**Architecture:** Extend normalized descriptor and registration models with logical project version, source kind, physical-host provenance, and canonical descriptor hash. Installed-mod discovery enumerates the exact namespaced resource directory and elects one passive Stats-only registration per project; the coordinator may replace that passive base with a matching explicit contribution. Persist reviewed capability sets so newly exposed categories remain disabled and generate a one-time operator review notice.

**Tech Stack:** Java 25, Gson, Hytale server API, JUnit 5, Gradle

## Global Constraints

- Presence of a valid direct `.json` resource under `META-INF/alecs-telemetry/projects/` is the installation signal.
- Passive discovery executes no contributor code and exposes only the standard Stats `heartbeat` capability.
- `projectId`, `projectVersion`, `displayName`, at least one owner identifier, and Stats heartbeat support are required for passive activation.
- The first owner identifier is the passive project's canonical logical plugin identifier.
- Logical `projectVersion`, not the physical host manifest version, is used for attribution and election.
- Duplicate candidates are elected by highest semantic version, then source/host/path/hash tie-breakers; descriptors are never field-merged.
- Existing conventional descriptors and explicit contributions without `projectVersion` remain compatible.
- Newly added telemetry categories remain disabled until an operator saves reviewed consent.
- Expansion notices go only to `telemetry.command.telemetry` holders or the local singleplayer owner, once per operator and capability fingerprint.
- Invalid passive descriptors must not prevent their host mod, other projects, or the runtime from operating.
- Update `docs/privacy-policy.md` in the implementation change.
- Preserve unrelated untracked release artifacts and stage only files named by each task.
- Add only production-behavior tests that protect discovery, election, upgrade, consent, notification, or heartbeat regressions.

---

### Task 1: Model Passive Identity and Stats-Only Capabilities

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationSource.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCapabilities.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java`

**Interfaces:**
- Produces: nullable `TelemetryProjectDescriptor.projectVersion()` parsed from top-level `projectVersion` and emitted only when nonblank.
- Produces: `TelemetryProjectDescriptor.statsOnly()`, preserving Stats plus identity/destination/UI metadata and masking every executable category and Reports.
- Produces: `TelemetryProjectDescriptor.canonicalHash()`, lowercase SHA-256 of `toJson()`.
- Produces: `TelemetryProjectRegistrationSource { CONVENTIONAL, PASSIVE_DESCRIPTOR, CONTRIBUTION }`.
- Produces: `TelemetryProjectRegistration.passiveDescriptor(TelemetryProjectDescriptor, Path, String, String)`.
- Produces: `TelemetryProjectRegistration.contribution(TelemetryProjectDescriptor, String, String, Path, String, String, String)`.
- Produces: registration accessors `source()`, `hostPluginIdentifier()`, `hostPluginVersion()`, `declaredDescriptorHash()`, and `isPassiveDescriptor()`.
- Produces: `TelemetryConsentCapabilities.supportedSnapshot(...)`, `supportedCategoryNames(...)`, and `fingerprint(...)`.

- [ ] **Step 1: Add failing descriptor and registration behavior tests**

Add a descriptor test that parses and round-trips `"projectVersion":"1.4.0"`. Add this registration test:

```java
@Test
void passiveDescriptorUsesLogicalVersionAndMasksExecutableCategories() {
    TelemetryProjectDescriptor declared = TelemetryProjectDescriptor.fromJson(
            """
            {
              "projectId": "creditor",
              "projectVersion": "1.4.0",
              "displayName": "Creditor",
              "ownerPluginIdentifiers": ["Author:Creditor"],
              "telemetry": {
                "usage": { "supported": true, "allowedEvents": ["credit_applied"] },
                "stats": { "supported": true, "allowedEvents": ["heartbeat"] }
              }
            }
            """,
            null
    );

    TelemetryProjectRegistration registration = TelemetryProjectRegistration.passiveDescriptor(
            declared, Path.of("Host.jar"), "Example:Host", "3.0.0"
    );

    assertEquals("1.4.0", registration.pluginVersion());
    assertEquals("Example:Host", registration.hostPluginIdentifier());
    assertTrue(registration.isPassiveDescriptor());
    assertTrue(registration.stats().supported());
    assertFalse(registration.usage().supported());
    assertEquals(List.of("stats"),
            TelemetryConsentCapabilities.supportedCategoryNames(registration));
}
```

- [ ] **Step 2: Run the focused tests and verify the new API is missing**

Run:

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.project.TelemetryProjectDescriptorTest --tests com.alechilles.alecstelemetry.project.TelemetryProjectRegistrationTest
```

Expected: compilation fails because `projectVersion`, `passiveDescriptor`, and `TelemetryConsentCapabilities` do not exist.

- [ ] **Step 3: Implement descriptor versioning, masking, hashing, and provenance**

Add `projectVersion` after `projectId` in `TelemetryProjectDescriptor`, add the nullable field to `Document`, normalize it with `normalizeNullable`, and serialize only a nonblank value:

```java
if (projectVersion != null && !projectVersion.isBlank()) {
    document.put("projectVersion", projectVersion);
}
```

Implement `statsOnly()` by constructing one descriptor copy. Preserve `stats`, identity, destination, defaults, UI, package prefixes, and hosted/custom endpoint data. Replace Crash, Errors, Lifecycle, Breadcrumbs, Performance, Usage, and Reports with their existing disabled/default value objects. Implement `canonicalHash()` with SHA-256 over UTF-8 `toJson()`.

Extend `TelemetryProjectRegistration` with source, physical host, and declared hash fields while preserving the existing four- and five-argument constructors as `CONVENTIONAL` overloads. The passive factory must require a nonblank descriptor version and owner, retain the full descriptor hash, and store `descriptor.statsOnly()` as the effective descriptor:

```java
return new TelemetryProjectRegistration(
        declared.statsOnly(),
        declared.ownerPluginIdentifiers().getFirst(),
        declared.projectVersion(),
        sourcePath,
        null,
        TelemetryProjectRegistrationSource.PASSIVE_DESCRIPTOR,
        hostPluginIdentifier,
        hostPluginVersion,
        declared.canonicalHash()
);
```

The contribution factory stores the full active descriptor and supplied canonical hash. `withOverride(...)` must preserve all provenance fields.

Implement `TelemetryConsentCapabilities` with canonical category order:

```java
List.of("crash", "error", "lifecycle", "performance", "usage", "stats", "breadcrumbs")
```

Its fingerprint is the semicolon-joined supported category names; enabled choices are not part of the fingerprint.

- [ ] **Step 4: Run the focused model tests**

Run the Task 1 test command again. Expected: PASS.

- [ ] **Step 5: Commit the model changes**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationSource.java runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCapabilities.java runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java
git commit -m "Feat: model passive telemetry projects"
```

### Task 2: Discover and Elect Namespaced Descriptors

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscovery.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscoveryTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java`

**Interfaces:**
- Consumes: Task 1 passive factory, source kind, logical version, host provenance, and hash.
- Produces: `TelemetryProjectDiscovery.NAMESPACED_DESCRIPTOR_DIRECTORY = "META-INF/alecs-telemetry/projects/"`.
- Produces: one elected conventional or passive registration per normalized project ID.
- Produces: loaded-mod filtering that matches passive registrations against `hostPluginIdentifier()`.

- [ ] **Step 1: Add failing folder/JAR discovery and election tests**

Extend the JAR helper to accept a map of entry name to content. Add behavior tests that:

1. put `META-INF/alecs-telemetry/projects/creditor.json` in a host JAR whose manifest is `Example:Tamework`;
2. assert one project named `creditor`, logical version `1.4.0`, physical host `Example:Tamework`, and only Stats support;
3. put Creditor `1.3.0` and `1.4.0` in two host JARs and assert one `1.4.0` registration; and
4. put an incomplete descriptor beside a valid one and assert the valid project survives while a bounded warning is returned.

Add a runtime-discovery test with only the physical host in the loaded-mod snapshot:

```java
TelemetryRuntimeDiscoveryResult active = discovery.discoverActive(
        dataPaths,
        discovered,
        TelemetryLoadedModSnapshotProvider.fixed(List.of(
                new CrashReportEnvelope.LoadedModMetadata("Example:Tamework", "3.0.0")
        ))
);
assertEquals(List.of("creditor"), active.projects().stream()
        .map(TelemetryProjectRegistration::projectId)
        .toList());
```

- [ ] **Step 2: Run discovery tests and verify the passive project is absent**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.project.TelemetryProjectDiscoveryTest --tests com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryTest
```

Expected: the namespaced descriptor is not discovered.

- [ ] **Step 3: Enumerate direct namespaced descriptors**

Change `EntryData` to carry a list of registrations. Preserve conventional lookup, then append passive candidates:

- folder: list regular direct children of `<mod>/META-INF/alecs-telemetry/projects` ending in `.json`;
- archive: select entries with the exact prefix, a nonblank remainder containing no `/`, and a case-insensitive `.json` suffix;
- sort resource names before parsing.

Before normalization, parse the root object and require explicit nonblank `projectId`, `projectVersion`, `displayName`, and a nonempty owner list. Validate version with `Semver.fromString(...)`. After normalization, require `stats.supported()` and an `allowedEvents` entry equal to `heartbeat` ignoring case. Build the passive registration with the containing manifest identifier/version and archive/folder path. Skip a passive descriptor without a valid host manifest because provenance cannot be established.

- [ ] **Step 4: Elect one candidate per project**

Collect conventional and passive candidates before building result maps. Select by:

```text
highest valid logical semantic version
CONVENTIONAL before PASSIVE_DESCRIPTOR at the same version
lexically smaller physical host identifier
lexically smaller normalized source path
lexically smaller declared descriptor hash
```

Exact copies deduplicate. Same-version differing hashes add one descriptor-drift warning. Never merge fields.

- [ ] **Step 5: Keep passive projects active through loaded-mod filtering**

Add this first branch to `TelemetryRuntimeDiscovery.matchesLoadedMod(...)`:

```java
if (project.isPassiveDescriptor()) {
    return containsIdentifierVariant(loadedIdentifiers, project.hostPluginIdentifier());
}
```

Use `TelemetryConsentCapabilities.supportedSnapshot(project)` instead of the duplicated private supported-snapshot builder.

- [ ] **Step 6: Run discovery tests**

Run the Task 2 test command again. Expected: PASS.

- [ ] **Step 7: Commit discovery**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscovery.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscoveryTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java
git commit -m "Feat: discover passive telemetry descriptors"
```

### Task 3: Upgrade Passive Projects with Active Contributions

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryContributionProviderHandoffIT.java`

**Interfaces:**
- Consumes: passive source/hash fields and contribution factory from Task 1.
- Produces: exact matching active registration replacement of a passive base project.
- Produces: restoration of the passive base when the contribution retires.
- Produces: optional `projectVersion` validation in active bootstrap and coordinator ingestion.

- [ ] **Step 1: Add failing active-upgrade and mismatch tests**

In `TelemetryCoordinatorServiceTest`, construct a passive base registration supporting Stats and Usage, reconcile a contribution map for the same project/version/hash, and assert:

```java
assertTrue(service.reconcileProjectContributions(1L, List.of(activeContribution)));
assertEquals(1, service.projects().size());
assertEquals(TelemetryProjectRegistrationSource.CONTRIBUTION,
        service.projects().getFirst().source());
assertTrue(service.projects().getFirst().usage().supported());
```

Reconcile an empty contribution snapshot at revision `2L` and assert the same single project returns to `PASSIVE_DESCRIPTOR` with Usage unsupported.

Add an embedded-bootstrap test where descriptor `projectVersion` is `2.0.0` but the builder supplies `1.0.0`. Assert the returned service is disabled with `descriptor_version_mismatch` instead of throwing.

- [ ] **Step 2: Run coordinator/bootstrap tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorServiceTest --tests com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryServiceTest
```

Expected: the coordinator rejects the base-ID collision and bootstrap accepts the mismatch.

- [ ] **Step 3: Validate active descriptor versions**

After parsing an anchored descriptor in `EmbeddedTelemetryBootstrap`, reject it when a nonblank `descriptor.projectVersion()` differs from the supplied logical version. Descriptors without `projectVersion` remain compatible.

Repeat the validation in `TelemetryCoordinatorService.registrationFromContribution(...)`. Build the runtime registration through `TelemetryProjectRegistration.contribution(...)` using `hostPluginIdentifier`, `hostPluginVersion`, and `descriptorHash` from the contribution map.

- [ ] **Step 4: Replace only an exact passive base**

In `reconcileProjectContributions(...)`, permit replacement only when:

```java
base.isPassiveDescriptor()
        && base.pluginVersion().equals(contribution.pluginVersion())
        && base.ownerPluginIdentifiers().stream().anyMatch(
                owner -> owner.equalsIgnoreCase(contribution.pluginIdentifier()))
        && base.declaredDescriptorHash().equalsIgnoreCase(
                contribution.declaredDescriptorHash())
```

Every other base collision keeps existing token rejection behavior. Do not mutate `baseProjects`, so reconciliation without the contribution restores the passive entry. Publish only one registration per project ID.

- [ ] **Step 5: Verify one heartbeat project after upgrade**

Extend the aggregate-heartbeat test: initialize with the elected passive registration, apply the matching contribution, emit a heartbeat, and assert the serialized `projects` array has exactly one object with `projectId == "creditor"` and `pluginVersion == "1.4.0"`.

- [ ] **Step 6: Verify provider handoff reconstructs the passive base**

Extend `TelemetryContributionProviderHandoffIT` with two coordinator/provider instances built from the same discovery result containing Creditor's passive descriptor. Elect the first provider, register the matching active contribution, then retire the first provider and elect the second. Assert the second coordinator contains one Creditor project, replays the active contribution when available, and falls back to the passive Stats-only registration after that contribution closes. This exercises reconstruction rather than inspecting registry internals.

- [ ] **Step 7: Run the focused tests**

Run:

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorServiceTest --tests com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryServiceTest --tests com.alechilles.alecstelemetry.coordinator.TelemetryContributionProviderHandoffIT
```

Expected: PASS.

- [ ] **Step 8: Commit active upgrade support**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryContributionProviderHandoffIT.java
git commit -m "Feat: upgrade passive telemetry projects"
```

### Task 4: Gate and Announce Capability Expansions

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryConsentRuntime.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStoreTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java`

**Interfaces:**
- Consumes: `TelemetryConsentCapabilities` and active-upgrade reconciliation.
- Produces: `TelemetryConsentStateStore.addedSupportedCategories(Path, TelemetryProjectRegistration)`.
- Produces: compatibility default `TelemetryConsentRuntime.addedConsentCategories(String)`.
- Produces: `TelemetryProjectOverrideStore.disableCategories(Path, Collection<String>)`.
- Produces: package-visible `TelemetryConsentCoordinator.reviewNotice(List<TelemetryProjectRegistration>, Map<String,List<String>>)`.

- [ ] **Step 1: Add failing persisted-expansion tests**

Extend the consent-state helper to select supported categories. Test:

1. mark Stats-only `creditor@1.4.0` reviewed;
2. create the same identity/version with Stats, Errors, and Usage supported;
3. assert additions equal `List.of("error", "usage")`;
4. assert the expanded registration is unreviewed;
5. mark it reviewed and assert additions are empty; and
6. remove a category and assert removal creates no expansion.

Also load a legacy reviewed JSON entry with no `supportedCategories` and assert it remains reviewed, avoiding false alerts for all existing projects.

- [ ] **Step 2: Add a failing user-visible notice test**

```java
String notice = TelemetryConsentCoordinator.reviewNotice(
        List.of(creditor),
        Map.of("creditor", List.of("error", "usage"))
);
assertTrue(notice.contains("Creditor"));
assertTrue(notice.contains("Errors and Usage"));
assertTrue(notice.contains("remain disabled"));
assertTrue(notice.contains("`/telemetry consent`"));
```

- [ ] **Step 3: Run consent tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.consent.TelemetryConsentStateStoreTest --tests com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinatorTest
```

Expected: compilation fails because expansion history and formatter APIs do not exist.

- [ ] **Step 4: Persist reviewed supported-category sets**

Add `List<String> supportedCategories` to `ReviewedProjectDocument`. `from(project)` stores `TelemetryConsentCapabilities.supportedCategoryNames(project)`. Baseline selection uses the exact project/plugin/version entry when present, otherwise the most recently stored entry with the same normalized project and plugin identifier.

`addedSupportedCategories(...)` returns the current canonical list minus the baseline. A missing baseline or legacy entry with `supportedCategories == null` returns an empty list. `isReviewed(...)` requires an exact version entry and no additions.

Append `TelemetryConsentCapabilities.fingerprint(project)` to shown-notice project keys. Keep funnel-event identity project/plugin/version based.

- [ ] **Step 5: Disable new categories before publication**

Implement `TelemetryProjectOverrideStore.disableCategories(...)` by setting only named category consent keys to `false` and preserving unrelated choices.

Before `TelemetryCoordinatorService` publishes a contribution upgrading a passive project:

1. ask `TelemetryConsentStateStore` for added categories;
2. if nonempty, write `false` for them to `dataPaths.projectOverrideFile(projectId)`;
3. reload the override and attach it with `registration.withOverride(...)`; and
4. retain the passive project if persisting the protective override fails.

Add a coordinator integration test that marks passive Stats consent reviewed, reconciles an active Usage contribution, and asserts `service.isUsageEnabled("creditor")` is false while Stats keeps its previous choice.

- [ ] **Step 6: Expose additions and send the operator notice**

Add this compatibility method:

```java
@Nonnull
default List<String> addedConsentCategories(@Nonnull String projectId) {
    return List.of();
}
```

Override it in `TelemetryRuntimeProviderHandle` and `EmbeddedTelemetryService` by resolving the current consent project and calling the local state store with the shared consent file. No cross-classloader DTO is required.

In `TelemetryConsentCoordinator.onPlayerReady(...)`, build a map of nonempty additions for current unreviewed projects. Use `reviewNotice(...)` when nonempty; otherwise retain `FIRST_RUN_NOTICE`. Reuse the existing access check, per-viewer persisted notice state, and prompt metrics. Labels are `Crash`, `Errors`, `Lifecycle`, `Performance`, `Usage`, `Stats`, and `Breadcrumbs`.

- [ ] **Step 7: Centralize supported-snapshot calculation**

Use `TelemetryConsentCapabilities.supportedSnapshot(project)` in `TelemetryRuntimeProviderHandle` and `EmbeddedTelemetryService`. Remove only the redundant local helpers so UI, override persistence, fingerprints, and passive masking share one definition.

- [ ] **Step 8: Run consent and coordinator tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.consent.TelemetryConsentStateStoreTest --tests com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinatorTest --tests com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorServiceTest
```

Expected: PASS.

- [ ] **Step 9: Commit consent expansion handling**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryConsentRuntime.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStore.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStoreTest.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java
git commit -m "Feat: require review for telemetry expansions"
```

### Task 5: Document, Verify, and Package

**Files:**
- Modify: `README.md`
- Modify: `docs/project-descriptor.md`
- Modify: `docs/embedded-contributions.md`
- Modify: `docs/usage-stats.md`
- Modify: `docs/privacy-policy.md`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: author-facing descriptor-only setup, optional active integration guidance, and current privacy language.

- [ ] **Step 1: Update descriptor and integration docs**

Document the exact resource path and approved minimal JSON. State:

- presence in the final host JAR counts as installed;
- passive libraries need no Telemetry dependency or Java initialization;
- some standalone or embedded runtime must be present to process the descriptor;
- only aggregate Stats heartbeat is available passively;
- `projectVersion` belongs to the logical library and should be build-stamped;
- multiple hosts deduplicate through logical project election; and
- richer categories require explicit `contribute(...)` registration using the same identity/version/descriptor.

Replace text saying Telemetry does not enumerate the namespaced directory. Retain the warning that `Server/Telemetry/project.json` belongs to the physical host.

- [ ] **Step 2: Update stats and privacy docs**

Update `docs/usage-stats.md` to include descriptor-only shaded projects and explain that duplicates produce one logical heartbeat project.

Update `docs/privacy-policy.md` with current-state language for:

- automatic enumeration of namespaced descriptors inside installed mods;
- logical project identity/version and physical host identifier/version/source path used locally for election and diagnostics;
- passive aggregate heartbeat delivery under independent project/Stats consent;
- presence as installation signal without claiming code execution;
- no executable class loading from passive descriptors; and
- new categories remaining disabled until operator review, with permission-scoped notices.

- [ ] **Step 3: Run the full runtime tests**

```bash
bash ../gradlew :alecstelemetry:runtime:test
```

Expected: PASS.

- [ ] **Step 4: Build the standalone shaded plugin**

```bash
bash ../gradlew :alecstelemetry:standalone:build
```

Expected: `BUILD SUCCESSFUL`. Do not stage generated JARs.

- [ ] **Step 5: Verify the packaged behavior manually**

Stage the standalone plugin only in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/run/mods` with a fixture host containing a Creditor-style descriptor. Verify:

1. Creditor appears once in the scrollable consent list with only Stats available.
2. Saving Stats consent leads to one Creditor heartbeat in logs/portal ingest.
3. A second host with the same descriptor adds no duplicate row or heartbeat project.
4. Matching active integration exposes additional categories as disabled.
5. An operator gets the review message once and can save choices through `/telemetry consent`.
6. Removing every Telemetry runtime leaves the fixture host operational.

Stop the Hytale process afterward; do not leave stale processes running.

- [ ] **Step 6: Commit documentation and privacy updates**

```bash
git add README.md docs/project-descriptor.md docs/embedded-contributions.md docs/usage-stats.md docs/privacy-policy.md
git commit -m "Docs: explain passive telemetry descriptors"
```

- [ ] **Step 7: Perform final repository verification**

```bash
git diff --check HEAD~5..HEAD
git status --short
```

Expected: no whitespace errors; only the user's pre-existing untracked release artifacts remain outside the committed scope.
