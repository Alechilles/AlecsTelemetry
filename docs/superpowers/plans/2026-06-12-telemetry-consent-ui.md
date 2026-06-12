# Telemetry Consent UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-run Hytale UI that explains Alec's Telemetry and lets an operator opt all telemetry-enabled mods in or out globally, per project, and per telemetry category.

**Architecture:** Keep consent enforcement in the runtime/core layer, then build the page as a thin standalone-mod UI adapter. Project descriptors continue to define mod-authored defaults; persisted runtime overrides record the operator's decisions. A separate consent state file tracks which discovered project versions have been reviewed so newly installed telemetry-enabled mods can reopen the UI without nagging for already-reviewed projects.

**Tech Stack:** Java 25, Maven, JUnit 5, Gson, Hytale `InteractiveCustomUIPage`, `UICommandBuilder`, `UIEventBuilder`, `PageManager`, existing Alec's Telemetry runtime/standalone modules.

---

## Current State

- Project discovery already reads `telemetry/project.json` from installed mod folders and archives in `standalone/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDiscovery.java`.
- Per-project descriptor defaults already exist through `TelemetryProjectDescriptor.defaults().enabled()`.
- Category defaults already exist for error events, lifecycle events, breadcrumbs, performance events, and usage events.
- Runtime overrides already parse `events.errors.enabled`, `events.lifecycle.enabled`, `events.breadcrumbs.enabled`, `performance.enabled`, and `usage.enabled` in `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectOverride.java`.
- The override store only has write helpers for project enabled and breadcrumbs today.
- `TelemetryCoreEngine` enforces project enabled, error, lifecycle, performance, usage, and breadcrumbs gates for explicit events, but crash capture only checks project enabled plus `capture.*`.
- The standalone mod currently exposes commands only. The local Hytale server jar has `InteractiveCustomUIPage`, `UICommandBuilder`, `UIEventBuilder`, `PageManager`, `PlayerReadyEvent`, and related page infrastructure.

## Product Decisions

- `defaults.enabled` remains the mod author's default project-level opt-in or opt-out value. Do not add a duplicate top-level descriptor field.
- First-run state is per discovered project identity and version: `projectId`, `pluginIdentifier`, and `pluginVersion`.
- Until a project is reviewed, effective behavior follows descriptor defaults. The UI is transparency and control, not a hard pre-upload consent blocker.
- "All" toggle is a bulk action that sets each displayed project's `enabled` override. It does not mutate global `Settings/runtime.json.enabled`.
- Category toggles are project-specific:
  - `crash` gates crash report capture paths.
  - `error` gates explicit non-fatal error events.
  - `lifecycle` gates lifecycle timing events. Include it because the runtime already supports it, even if the page groups it under advanced event telemetry.
  - `performance` gates performance events.
  - `usage` gates usage events.
  - `breadcrumbs` can stay under advanced controls because breadcrumbs are context attached to crashes/errors rather than a separate upload stream.
- The page should open for the first OP/Admin/Operator player that reaches `PlayerReadyEvent` when any telemetry-enabled dependency-mode project is unreviewed.
- A manual command should always reopen the page: `/telemetry consent`.

## Files

- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCategory.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentSnapshot.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStoreTest.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStoreTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryConsentCommand.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeDiagnostics.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
- Modify: `docs/project-descriptor.md`
- Modify: `docs/runtime-overrides.md`
- Modify: `README.md` only if its current dirty changes are understood and should be extended. Otherwise defer README updates to a separate pass.

### Task 1: Consent Category Model

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCategory.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentSnapshot.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java`

- [ ] **Step 1: Add failing tests for effective category defaults**

Add tests that create a descriptor with `events.errors.enabled=false`, `events.lifecycle.enabled=true`, `performance.enabled=false`, `usage.enabled=true`, and `events.breadcrumbs.enabled=false`; assert the new category helpers return those exact defaults.

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectRegistrationTest test`

Expected: compile fails because `TelemetryConsentCategory` and category helpers do not exist.

- [ ] **Step 2: Add the category enum**

Implement:

```java
package com.alechilles.alecstelemetry.consent;

public enum TelemetryConsentCategory {
    CRASH("crash"),
    ERROR("error"),
    LIFECYCLE("lifecycle"),
    PERFORMANCE("performance"),
    USAGE("usage"),
    BREADCRUMBS("breadcrumbs");

    private final String key;

    TelemetryConsentCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

- [ ] **Step 3: Add a compact snapshot record**

Implement a record with `projectEnabled`, `crashEnabled`, `errorEnabled`, `lifecycleEnabled`, `performanceEnabled`, `usageEnabled`, and `breadcrumbsEnabled`.

- [ ] **Step 4: Add effective category helpers to registration**

Add `isCrashTelemetryEnabled()` to `TelemetryProjectRegistration`. For the first version, return `isEnabled() && descriptor.capture()` has at least one capture source enabled. Add `consentSnapshot()` that reads effective project/category state from existing descriptor-plus-override methods.

- [ ] **Step 5: Verify and commit**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectRegistrationTest test`

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java
git commit -m "Feat: add telemetry consent category model"
```

### Task 2: Persist Category Overrides

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStore.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStoreTest.java`

- [ ] **Step 1: Add failing persistence tests**

Extend `savesProjectAndBreadcrumbControlsWithoutRemovingOtherFields` or add a new test that calls:

```java
assertTrue(store.saveCrashEnabled(overrideFile, false));
assertTrue(store.saveErrorEventsEnabled(overrideFile, false));
assertTrue(store.saveLifecycleEventsEnabled(overrideFile, false));
assertTrue(store.savePerformanceEnabled(overrideFile, false));
assertTrue(store.saveUsageEnabled(overrideFile, false));
```

Then reload and assert:

```java
assertEquals(false, override.enabled());
assertEquals(false, override.events().errors().enabled());
assertEquals(false, override.events().lifecycle().enabled());
assertEquals(false, override.events().breadcrumbs().enabled());
assertEquals(false, override.performance().enabled());
assertEquals(false, override.usage().enabled());
```

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectOverrideStoreTest test`

Expected: compile fails because the save helpers do not exist.

- [ ] **Step 2: Add save helpers**

Add methods:

```java
public boolean saveCrashEnabled(@Nonnull Path file, boolean enabled) {
    return update(file, root -> {
        JsonObject capture = object(root, "capture");
        capture.addProperty("uncaughtExceptions", enabled);
        capture.addProperty("setupFailures", enabled);
        capture.addProperty("startFailures", enabled);
        capture.addProperty("exceptionalWorldRemovals", enabled);
    });
}

public boolean saveErrorEventsEnabled(@Nonnull Path file, boolean enabled) {
    return updateEventTypeEnabled(file, "errors", enabled);
}

public boolean saveLifecycleEventsEnabled(@Nonnull Path file, boolean enabled) {
    return updateEventTypeEnabled(file, "lifecycle", enabled);
}

public boolean savePerformanceEnabled(@Nonnull Path file, boolean enabled) {
    return update(file, root -> object(root, "performance").addProperty("enabled", enabled));
}

public boolean saveUsageEnabled(@Nonnull Path file, boolean enabled) {
    return update(file, root -> object(root, "usage").addProperty("enabled", enabled));
}

private boolean updateEventTypeEnabled(@Nonnull Path file, @Nonnull String eventType, boolean enabled) {
    return update(file, root -> {
        JsonObject events = object(root, "events");
        JsonObject eventObject = object(events, eventType);
        eventObject.addProperty("enabled", enabled);
    });
}
```

This requires Task 3 to teach `TelemetryProjectOverride` to parse `capture` overrides; do not claim crash override is effective until Task 3 is complete.

- [ ] **Step 3: Verify and commit**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectOverrideStoreTest test`

Expected: non-crash category persistence passes; crash reload assertion may remain pending until Task 3 if `capture` parsing is added there instead.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStore.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryProjectOverrideStoreTest.java
git commit -m "Feat: persist telemetry category overrides"
```

### Task 3: Crash Category Override Enforcement

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectOverride.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`

- [ ] **Step 1: Add failing test for crash opt-out**

Create a service with a project whose descriptor enables crash capture, then write an override with all `capture.*` false or construct registration with an override. Call `service.captureSetupFailure("example-mod", throwable)` and assert `service.flushPendingReportsNow("test").attempted()` is `0`.

Run: `.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test`

Expected: test fails because crash capture ignores category-level crash override.

- [ ] **Step 2: Parse capture override**

Add a nullable `CaptureOverride` record to `TelemetryProjectOverride` with nullable booleans for `uncaughtExceptions`, `setupFailures`, `startFailures`, and `exceptionalWorldRemovals`. Include it in `hasAnyValue()`.

- [ ] **Step 3: Merge capture settings in registration**

Add a `capture()` method to `TelemetryProjectRegistration` that returns descriptor capture options with override values applied. Update `capturesSource(source)` to call this merged method instead of `descriptor.capture()` directly.

- [ ] **Step 4: Ensure category helper uses merged capture**

Update `isCrashTelemetryEnabled()` to return true when any merged capture source is enabled and the project is enabled.

- [ ] **Step 5: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectOverrideStoreTest,TelemetryProjectRegistrationTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/project runtime/src/main/java/com/alechilles/alecstelemetry/core standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java
git commit -m "Feat: enforce crash telemetry consent"
```

### Task 4: First-Run Consent State Store

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStoreTest.java`

- [ ] **Step 1: Add failing tests**

Test these cases:

- Empty or missing file returns no reviewed projects.
- `markReviewed(project)` writes a stable entry using `projectId`, `pluginIdentifier`, and `pluginVersion`.
- Same project and version is reviewed after reload.
- Same project with a new `pluginVersion` is unreviewed.

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentStateStoreTest test`

Expected: compile fails because the store does not exist.

- [ ] **Step 2: Add consent state path**

Add to `TelemetryDataPaths`:

```java
@Nonnull
public Path consentStateFile() {
    return settingsFile.getParent().resolve("consent-reviewed-projects.json");
}
```

- [ ] **Step 3: Implement store**

Use Gson pretty printing and atomic write. File shape:

```json
{
  "version": 1,
  "reviewedProjects": [
    {
      "projectId": "example-mod",
      "pluginIdentifier": "Example:Example Mod",
      "pluginVersion": "1.2.3"
    }
  ]
}
```

Expose:

```java
public boolean isReviewed(TelemetryProjectRegistration project)
public boolean markReviewed(Path file, TelemetryProjectRegistration project)
public List<TelemetryProjectRegistration> unreviewedProjects(Path file, List<TelemetryProjectRegistration> projects)
```

- [ ] **Step 4: Verify and commit**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentStateStoreTest test`

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStoreTest.java
git commit -m "Feat: track reviewed telemetry projects"
```

### Task 5: Runtime Consent API

**Files:**
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeDiagnostics.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`

- [ ] **Step 1: Add failing tests for runtime mutations**

Assert that:

- `runtimeService.unreviewedConsentProjects()` returns newly discovered projects.
- `runtimeService.applyConsent(projectId, snapshot)` writes override fields and updates in-memory engine state.
- Applying all-off disables capture and explicit events immediately.

Run: `.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test`

Expected: compile fails because service APIs do not exist.

- [ ] **Step 2: Add mutation API**

Add methods:

```java
public List<TelemetryProjectRegistration> unreviewedConsentProjects()
public boolean applyConsent(String projectId, TelemetryConsentSnapshot snapshot)
public boolean applyConsentToAll(TelemetryConsentSnapshot snapshot)
public boolean markConsentReviewed(String projectId)
```

Each mutation writes to `TelemetryProjectOverrideStore`, updates engine runtime override maps, and marks reviewed only after successful write.

- [ ] **Step 3: Add diagnostics fields**

Extend project diagnostics with effective category booleans so commands and UI can render the same state.

- [ ] **Step 4: Verify and commit**

Run: `.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test`

Expected: tests pass.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/runtime standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java
git commit -m "Feat: expose telemetry consent runtime API"
```

### Task 6: Consent UI View Model

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModelTest.java`

- [ ] **Step 1: Add failing view-model tests**

Build diagnostics for two projects, one enabled and one disabled. Assert the view model:

- has `allEnabled=false`
- includes display name, project id, plugin version, source path, override-present flag
- includes category states
- sorts by display name

Run: `.\mvnw.cmd -pl standalone -Dtest=TelemetryConsentViewModelTest test`

Expected: compile fails because view model does not exist.

- [ ] **Step 2: Implement view model**

Keep this class UI-framework independent. It should expose plain records only:

```java
public record TelemetryConsentViewModel(
        boolean allEnabled,
        List<ProjectRow> projects,
        List<String> explanationLines) {
}
```

Use explanation lines:

- `Alec's Telemetry helps mod authors diagnose crashes, errors, performance issues, and feature usage.`
- `Each mod controls its suggested defaults, and you can override them here.`
- `No player names, chat, coordinates, or secrets should be sent by descriptor-validated telemetry.`

- [ ] **Step 3: Verify and commit**

Run: `.\mvnw.cmd -pl standalone -Dtest=TelemetryConsentViewModelTest test`

Expected: tests pass.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModelTest.java
git commit -m "Feat: build telemetry consent view model"
```

### Task 7: Consent Custom Page

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java`

- [ ] **Step 1: Inspect `UIGalleryPage` bytecode for binding patterns**

Run:

```powershell
& javap -classpath "C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar" -c com.hypixel.hytale.server.core.command.system.pages.UIGalleryPage
```

Confirm how `append`, `appendInline`, `set`, and `addEventBinding` are used for buttons/toggles in this Hytale build.

- [ ] **Step 2: Implement page skeleton**

Create `TelemetryConsentPage extends InteractiveCustomUIPage<TelemetryConsentPage.EventData>`.

Constructor inputs:

- `PlayerRef playerRef`
- `TelemetryRuntimeService runtimeService`
- `boolean firstRun`

Page structure:

- title: `Alec's Telemetry`
- explanation block
- `All mods` enabled toggle
- repeated project rows with project enabled toggle and category toggles
- `Save` button
- `Not now` button for first-run prompt, which marks nothing reviewed

- [ ] **Step 3: Implement event handling**

Event data fields:

- `action`
- `projectId`
- `category`
- `enabled`

Actions:

- `toggle_all`
- `toggle_project`
- `toggle_category`
- `save`
- `close`

Use `runtimeService.applyConsent(...)` for every toggle so runtime behavior changes immediately. Use `markConsentReviewed` for each displayed project only on `save`.

- [ ] **Step 4: Compile**

Run: `.\mvnw.cmd -pl standalone -DskipTests compile`

Expected: compile passes against local Hytale UI classes.

- [ ] **Step 5: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java
git commit -m "Feat: add telemetry consent custom page"
```

### Task 8: First-Run Page Coordinator

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`

- [ ] **Step 1: Wire player ready listener**

Register `PlayerReadyEvent` in `AlecsTelemetry.setup()` after runtime service creation.

- [ ] **Step 2: Implement coordinator**

Behavior:

- If runtime service is unavailable, do nothing.
- If there are no unreviewed projects, do nothing.
- Only open once per runtime session unless a new unreviewed project appears.
- Only open for OP/Admin/Operator-capable players if the Hytale API exposes permission checks. If no permission check is available, initially open only via manual command and log a warning.
- Open with `PageManager.openCustomPage(ref, store, new TelemetryConsentPage(playerRef, runtimeService, true))`.

- [ ] **Step 3: Compile and smoke test**

Run: `.\mvnw.cmd -pl standalone -DskipTests compile`

Expected: compile passes.

- [ ] **Step 4: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java
git commit -m "Feat: open telemetry consent on first run"
```

### Task 9: Manual Consent Command

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryConsentCommand.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java`

- [ ] **Step 1: Add command**

Implement `/telemetry consent` as an `AbstractPlayerCommand` that opens the same page with `firstRun=false`.

- [ ] **Step 2: Add command registration**

Add `addSubCommand(new TelemetryConsentCommand(plugin));`.

- [ ] **Step 3: Compile and commit**

Run: `.\mvnw.cmd -pl standalone -DskipTests compile`

Expected: compile passes.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryConsentCommand.java standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java
git commit -m "Feat: add telemetry consent command"
```

### Task 10: Docs

**Files:**
- Modify: `docs/project-descriptor.md`
- Modify: `docs/runtime-overrides.md`
- Modify: `wiki/Integration-Guides/Project-Descriptor.md`
- Modify: `wiki/Integration-Guides/Runtime-Overrides.md`
- Maybe modify: `README.md` after reconciling pre-existing dirty changes.

- [ ] **Step 1: Document descriptor defaults**

Clarify that:

- `defaults.enabled` is the mod author's default project-level consent setting.
- category defaults are defined by `capture`, `events`, `performance`, and `usage`.
- first-run UI uses these defaults as the initial checked state.

- [ ] **Step 2: Document override shape**

Add examples:

```json
{
  "enabled": true,
  "capture": {
    "uncaughtExceptions": false,
    "setupFailures": false,
    "startFailures": false,
    "exceptionalWorldRemovals": false
  },
  "events": {
    "errors": { "enabled": false },
    "lifecycle": { "enabled": false },
    "breadcrumbs": { "enabled": false }
  },
  "performance": { "enabled": false },
  "usage": { "enabled": false }
}
```

- [ ] **Step 3: Verify docs links**

Run a markdown link scan if this repo has one. Otherwise run:

```powershell
rg -n "consent|defaults.enabled|capture" docs wiki README.md
```

- [ ] **Step 4: Commit**

```powershell
git add docs/project-descriptor.md docs/runtime-overrides.md wiki/Integration-Guides/Project-Descriptor.md wiki/Integration-Guides/Runtime-Overrides.md
git commit -m "Docs: document telemetry consent controls"
```

### Task 11: Full Verification

**Files:**
- No source changes unless verification exposes issues.

- [ ] **Step 1: Run focused tests**

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectRegistrationTest,TelemetryProjectOverrideStoreTest,TelemetryConsentStateStoreTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest,TelemetryConsentViewModelTest test
```

- [ ] **Step 2: Run full Maven suite**

```powershell
.\mvnw.cmd test
```

- [ ] **Step 3: Compile/install locally if needed**

```powershell
.\mvnw.cmd -pl standalone -Pinstall-plugin package
```

- [ ] **Step 4: Runtime smoke test**

Start a local server, install at least one telemetry-enabled example mod, connect as an operator, and verify:

- first-run page appears once
- `/telemetry consent` reopens it
- all toggle changes each project
- project toggle disables all uploads for that project
- crash toggle suppresses `captureSetupFailure` and uncaught capture for that project
- error/performance/usage toggles suppress only their event type
- saved choices persist under `Settings/projects/<project-id>.json`
- reviewed state persists under `Settings/consent-reviewed-projects.json`
- upgrading the telemetry-enabled mod version causes the first-run page to include it again

- [ ] **Step 5: Final commit if verification fixes were needed**

```powershell
git status --short
git add <only files changed for verification fixes>
git commit -m "Fix: polish telemetry consent UI"
```

## Risks

- Hytale UI APIs are present but not documented in this repo. Keep the UI class isolated so any API mismatch does not affect consent persistence or enforcement.
- Permission checks for OP/Admin/Operator may require inspection of the current Hytale API. If a reliable check is not available, use `/telemetry consent` first and add first-run auto-open later.
- Embedded-mode owners will not be discovered by the standalone runtime. This plan only covers dependency-mode projects managed by the standalone Alec's Telemetry mod.
- If the product decision changes to "no upload until explicit review," add a global `requireConsentBeforeUpload` runtime setting and gate `requestFlushAsync` for unreviewed projects. That is a stricter privacy mode but changes current descriptor default semantics.

## Self-Review

- Spec coverage: first install prompt, explanation, all toggle, per-mod toggle, crash/error/performance/usage toggles, and mod-defined defaults are covered.
- Placeholder scan: no task depends on undefined behavior without a verification step; Hytale UI uncertainty is isolated to the page task.
- Type consistency: category names are `CRASH`, `ERROR`, `LIFECYCLE`, `PERFORMANCE`, `USAGE`, and `BREADCRUMBS`; persisted JSON keys remain existing descriptor/override keys.
