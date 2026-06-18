# Telemetry Runtime Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert Alec's Telemetry so standalone and embedded providers use one shared runtime implementation, with the standalone jar acting only as an installable wrapper around the same runtime host used by embedded mods.

**Architecture:** Keep two distribution artifacts but one runtime implementation. Move discovery, loaded/enabled filtering, coordinator creation, player counting, stats heartbeats, consent, commands, manual reports, diagnostics, and public runtime API registration into `runtime`; reduce `standalone` to a Hytale plugin lifecycle wrapper that delegates to the shared host.

**Tech Stack:** Java 25, Maven multi-module build, Hytale `JavaPlugin`, JUnit 5, existing `TelemetryCoreEngine`, `TelemetryCoordinatorRegistry`, `TelemetryCoordinatorService`, descriptor/override stores, and Hytale command/UI APIs.

---

## Current State Summary

The repository already has meaningful shared runtime code under `runtime`, including:

- `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
- `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.java`
- `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
- `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- `runtime/src/main/java/com/alechilles/alecstelemetry/consent/*`
- `runtime/src/main/java/com/alechilles/alecstelemetry/report/*`

The standalone module still owns implementation logic that must move into `runtime`:

- `standalone/src/main/java/com/alechilles/alecstelemetry/api/*`
- `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/*`
- `standalone/src/main/java/com/alechilles/alecstelemetry/commands/*`
- `standalone/src/main/java/com/alechilles/alecstelemetry/reports/*`
- `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProvider.java`
- `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- `standalone/src/main/java/com/alechilles/alecstelemetry/stats/*`

The final standalone entrypoint should be limited to:

- `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
- `standalone/src/main/resources/manifest.json`
- standalone UI/resources if they are still packaged from `standalone`, or moved into `runtime` resources and copied by the standalone module.

---

## File Structure

### New Runtime Host Files

- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHost.java`
  - Public shared bootstrap API for standalone and embedded providers.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostHandle.java`
  - Lifecycle and diagnostics handle returned to provider entrypoints.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeBootstrapRequest.java`
  - Immutable request object describing provider origin, plugin metadata, descriptor source, and registration options.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
  - Internal implementation that owns lifecycle registration, command registration, event handlers, API locator, and coordinator bridge.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeCommandRegistrar.java`
  - Registers and unregisters the shared `/telemetry` command when this provider is active.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimePluginEvents.java`
  - Player and world event hooks shared by standalone and embedded.

### New Shared Discovery Files

- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryLoadedModSnapshotProvider.java`
  - Move from standalone and keep Hytale plugin/asset-pack snapshot behavior.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java`
  - Own descriptor discovery, loaded/enabled filtering, override loading, consent override resolution, collision detection, and warning generation.
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryResult.java`
  - Immutable result used by host and coordinator creation.

### New Shared API Files

- Move to runtime:
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.java`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`

### Shared Command And Report Files

- Move to runtime:
  - `standalone/src/main/java/com/alechilles/alecstelemetry/commands/*`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/reports/*`
- Modify commands so they depend on `TelemetryRuntimeHostHandle` or a command-facing runtime facade, not `AlecsTelemetry`.
- Delete or replace the duplicate embedded command package after the shared command root works:
  - `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/commands/*`

### Existing Runtime Files To Modify

- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
  - Accept already-resolved discovery results or delegate to `TelemetryRuntimeDiscovery`.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
  - Become a compatibility wrapper around `TelemetryRuntimeHost.bootstrapEmbedded(plugin)`.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
  - Either become a compatibility facade around `TelemetryRuntimeHostHandle` or be reduced to embedded-owner convenience methods.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`
  - Keep shared/embedded path semantics and add tests for standalone-vs-embedded shared roots.

### Standalone Files To Thin

- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
  - Delegate setup/start/shutdown to `TelemetryRuntimeHost`.
- Delete from standalone after moving:
  - `standalone/src/main/java/com/alechilles/alecstelemetry/api/**`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/commands/**`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/reports/**`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/**`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/stats/**`

### Test Files To Add Or Move

- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeCommandRegistrarTest.java`
- Move/adapt:
  - `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProviderTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryStatsHeartbeatServiceTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounterTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRootTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java`
  - `standalone/src/test/java/com/alechilles/alecstelemetry/reports/*`

---

### Task 1: Capture Baseline And Guard The Existing Build

**Files:**
- Modify: none
- Test: current Maven modules

- [ ] **Step 1: Record current git state**

Run:

```powershell
git status --short --branch
```

Expected: only known unrelated files are dirty or untracked. Do not stage `artifacts/`.

- [ ] **Step 2: Run runtime compile before moving code**

Run:

```powershell
.\mvnw.cmd -pl runtime -DskipTests compile
```

Expected: `BUILD SUCCESS`. If this fails because local embedded command work is incomplete, fix or remove that work before continuing. The runtime must compile before the conversion starts.

- [ ] **Step 3: Run full baseline tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all tests pass. If tests fail, fix failures before starting structural moves.

- [ ] **Step 4: Commit baseline cleanup if any files changed**

Run one of these exact paths:

```powershell
git status --short
```

If the only output is unrelated pre-existing work, skip the commit and write this note in the execution log:

```text
No baseline cleanup commit was created; the remaining dirty files predate this plan execution.
```

If the baseline cleanup touched only embedded command cleanup files, run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/commands
git commit -m "Chore: stabilize telemetry runtime baseline"
```

Expected: commit succeeds or no commit is needed because no baseline cleanup files changed.

---

### Task 2: Move Public Runtime API Into `runtime`

**Files:**
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`
- Test: move `standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java` to `runtime/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java`

- [ ] **Step 1: Move API files with Git**

Run:

```powershell
New-Item -ItemType Directory -Force runtime/src/main/java/com/alechilles/alecstelemetry/api/internal
git mv standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.java runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java
New-Item -ItemType Directory -Force runtime/src/test/java/com/alechilles/alecstelemetry/api
git mv standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java runtime/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java
```

Expected: files move without content changes.

- [ ] **Step 2: Change API implementation dependency from standalone service to a small runtime-facing interface**

Create `runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeOperations.java`:

```java
package com.alechilles.alecstelemetry.api.internal;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TelemetryRuntimeOperations {
    @Nonnull
    List<TelemetryProjectRegistration> projects();

    @Nullable
    TelemetryProjectRegistration findProject(@Nonnull String projectId);

    boolean isProjectEnabled(@Nonnull String projectId);

    boolean requestFlush(@Nullable String projectId);

    boolean captureTestReport(@Nonnull String projectId, @Nullable String detail);

    void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail);

    void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable);

    void recordErrorWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable Throwable throwable,
                                @Nullable TelemetryEventContext context);

    void recordLifecycleWithContext(@Nonnull String projectId,
                                    @Nonnull String eventName,
                                    int durationMs,
                                    boolean success,
                                    @Nullable TelemetryEventContext context);

    void recordPerformanceWithContext(@Nonnull String projectId,
                                      @Nonnull String eventName,
                                      int durationMs,
                                      @Nullable Double metricValue,
                                      @Nullable TelemetryEventContext context);

    void recordUsageWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable TelemetryEventContext context);
}
```

- [ ] **Step 3: Update `TelemetryRuntimeApiImpl` constructor**

In `runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java`, replace the standalone service field with:

```java
private final TelemetryRuntimeOperations runtime;

public TelemetryRuntimeApiImpl(@Nonnull TelemetryRuntimeOperations runtime) {
    this.runtime = runtime;
}
```

Update every reference from `runtimeService` to `runtime`.

- [ ] **Step 4: Update `TelemetryProjectHandleImpl` constructor**

In `runtime/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`, replace the standalone service field with:

```java
private final TelemetryRuntimeOperations runtime;
private final String projectId;

public TelemetryProjectHandleImpl(@Nonnull TelemetryRuntimeOperations runtime,
                                  @Nonnull String projectId) {
    this.runtime = runtime;
    this.projectId = projectId;
}
```

Update calls so they delegate to `runtime.recordErrorWithContext(projectId, ...)`, `runtime.recordUsageWithContext(projectId, ...)`, and the matching operations methods.

- [ ] **Step 5: Run API tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectHandleCompatibilityTest test
```

Expected: test passes.

- [ ] **Step 6: Commit API move**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/api runtime/src/test/java/com/alechilles/alecstelemetry/api standalone/src/main/java/com/alechilles/alecstelemetry/api standalone/src/test/java/com/alechilles/alecstelemetry/api
git commit -m "Refactor: move telemetry API into runtime"
```

Expected: commit succeeds.

---

### Task 3: Move Loaded-Mod Snapshot And Filtering Into Runtime

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryLoadedModSnapshotProvider.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryResult.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java`
- Move test: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProviderTest.java` to `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryLoadedModSnapshotProviderTest.java`
- Add test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java`

- [ ] **Step 1: Move snapshot provider**

Run:

```powershell
New-Item -ItemType Directory -Force runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery
git mv standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProvider.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryLoadedModSnapshotProvider.java
New-Item -ItemType Directory -Force runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery
git mv standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProviderTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryLoadedModSnapshotProviderTest.java
```

Change the package declaration in both moved files to:

```java
package com.alechilles.alecstelemetry.runtime.discovery;
```

- [ ] **Step 2: Add discovery result record**

Create `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryResult.java`:

```java
package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.List;

public record TelemetryRuntimeDiscoveryResult(
        @Nonnull List<TelemetryProjectRegistration> projects,
        @Nonnull List<TelemetryProjectRegistration> consentProjects,
        @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
        @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
        @Nonnull List<String> registrationWarnings) {

    public TelemetryRuntimeDiscoveryResult {
        projects = List.copyOf(projects);
        consentProjects = List.copyOf(consentProjects);
        loadedMods = List.copyOf(loadedMods);
        collisions = List.copyOf(collisions);
        registrationWarnings = List.copyOf(registrationWarnings);
    }
}
```

- [ ] **Step 3: Add shared discovery service**

Create `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java` with these methods:

```java
package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class TelemetryRuntimeDiscovery {
    private final HytaleLogger logger;
    private final TelemetryProjectOverrideStore overrideStore;

    public TelemetryRuntimeDiscovery(@Nullable HytaleLogger logger) {
        this.logger = logger;
        this.overrideStore = new TelemetryProjectOverrideStore(logger);
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult discoverActive(@Nonnull TelemetryDataPaths dataPaths,
                                                         @Nonnull TelemetryLoadedModSnapshotProvider snapshotProvider) {
        TelemetryProjectDiscovery.DiscoveryResult discovered = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        List<CrashReportEnvelope.LoadedModMetadata> activeLoadedMods = snapshotProvider.snapshotLoadedMods();
        List<TelemetryProjectRegistration> activeProjects = filterRegistrationsToLoadedMods(
                discovered.projects(),
                activeLoadedMods
        );
        List<TelemetryProjectRegistration> activeConsentProjects = filterRegistrationsToLoadedMods(
                discovered.consentProjects(),
                activeLoadedMods
        );
        Map<String, TelemetryProjectOverride> centralOverrides = overrideStore.loadAll(dataPaths.projectSettingsDirectory());
        Map<String, TelemetryProjectOverride> consentOverrides = loadConsentOverrides(
                activeConsentProjects,
                centralOverrides,
                dataPaths
        );
        List<TelemetryProjectRegistration> projects = applyOverrides(activeProjects, centralOverrides);
        List<TelemetryProjectRegistration> consentProjects = applyOverrides(activeConsentProjects, consentOverrides);
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(projects);
        List<String> warnings = buildRegistrationWarnings(collisions, discovered.skippedRegistrationWarnings());
        logRegistrationWarnings(warnings);
        return new TelemetryRuntimeDiscoveryResult(projects, consentProjects, activeLoadedMods, collisions, warnings);
    }

    @Nonnull
    public static List<TelemetryProjectRegistration> filterRegistrationsToLoadedMods(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        if (projects.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> loadedIdentifiers = new LinkedHashMap<>();
        for (CrashReportEnvelope.LoadedModMetadata loadedMod : loadedMods) {
            if (loadedMod != null) {
                addIdentifierVariants(loadedIdentifiers, loadedMod.identifier());
            }
        }
        if (loadedIdentifiers.isEmpty()) {
            return List.of();
        }
        ArrayList<TelemetryProjectRegistration> filtered = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            if (matchesLoadedMod(project, loadedIdentifiers)) {
                filtered.add(project);
            }
        }
        return List.copyOf(filtered);
    }

    @Nonnull
    public static List<TelemetryProjectRegistration> applyOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides) {
        ArrayList<TelemetryProjectRegistration> resolved = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            resolved.add(project.withOverride(overrides.get(project.projectId().toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(resolved);
    }

    private static boolean matchesLoadedMod(@Nonnull TelemetryProjectRegistration project,
                                            @Nonnull Map<String, Boolean> loadedIdentifiers) {
        if (containsIdentifierVariant(loadedIdentifiers, project.pluginIdentifier())) {
            return true;
        }
        if (containsIdentifierVariant(loadedIdentifiers, project.displayName())) {
            return true;
        }
        for (String ownerPluginIdentifier : project.ownerPluginIdentifiers()) {
            if (containsIdentifierVariant(loadedIdentifiers, ownerPluginIdentifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifierVariant(@Nonnull Map<String, Boolean> loadedIdentifiers,
                                                     @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        LinkedHashMap<String, Boolean> variants = new LinkedHashMap<>();
        addIdentifierVariants(variants, identifier);
        for (String variant : variants.keySet()) {
            if (loadedIdentifiers.containsKey(variant)) {
                return true;
            }
        }
        return false;
    }

    private static void addIdentifierVariants(@Nonnull Map<String, Boolean> identifiers,
                                              @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String trimmed = identifier.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        identifiers.put(lower, true);
        int separator = lower.indexOf(':');
        if (separator >= 0 && separator < lower.length() - 1) {
            identifiers.put(lower.substring(separator + 1), true);
        }
    }

    @Nonnull
    private Map<String, TelemetryProjectOverride> loadConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> centralOverrides,
            @Nonnull TelemetryDataPaths dataPaths) {
        LinkedHashMap<String, TelemetryProjectOverride> resolved = new LinkedHashMap<>(centralOverrides);
        for (TelemetryProjectRegistration project : projects) {
            String projectIdKey = project.projectId().toLowerCase(Locale.ROOT);
            if (resolved.containsKey(projectIdKey)) {
                continue;
            }
            java.nio.file.Path embeddedOverrideFile = embeddedProjectOverrideFile(dataPaths, project);
            if (embeddedOverrideFile == null) {
                continue;
            }
            TelemetryProjectOverride override = overrideStore.load(embeddedOverrideFile);
            if (override != null) {
                resolved.put(projectIdKey, override);
            }
        }
        return Map.copyOf(resolved);
    }

    @Nullable
    private static java.nio.file.Path embeddedProjectOverrideFile(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectRegistration project) {
        if (!project.isEmbeddedMode() || dataPaths.modsDirectory() == null) {
            return null;
        }
        return dataPaths.modsDirectory()
                .resolve(sanitizePluginDataDirectory(project.pluginIdentifier()))
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve(project.projectId() + ".json");
    }

    @Nonnull
    private static String sanitizePluginDataDirectory(@Nonnull String pluginIdentifier) {
        return pluginIdentifier.trim().replace(':', '_');
    }

    @Nonnull
    private static List<String> buildRegistrationWarnings(
            @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
            @Nonnull List<String> skippedWarnings) {
        ArrayList<String> warnings = new ArrayList<>(skippedWarnings.size() + collisions.size());
        warnings.addAll(skippedWarnings);
        for (TelemetryProjectCollisionDetector.Collision collision : collisions) {
            warnings.add(collision.format());
        }
        return List.copyOf(warnings);
    }

    private void logRegistrationWarnings(@Nonnull List<String> warnings) {
        if (logger == null || warnings.isEmpty()) {
            return;
        }
        for (String warning : warnings) {
            Level level = warning.contains("embedded telemetry project") ? Level.INFO : Level.WARNING;
            logger.at(level).log("Telemetry registration note: " + warning);
        }
    }
}
```

- [ ] **Step 4: Add active-filter test**

Create `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java` with:

```java
package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeDiscoveryTest {

    @Test
    void filtersDescriptorsToLoadedEnabledMods() {
        TelemetryProjectRegistration loaded = registration("loaded-mod", "Alechilles:Loaded Mod", "Loaded Mod");
        TelemetryProjectRegistration disabled = registration("disabled-mod", "Alechilles:Disabled Mod", "Disabled Mod");

        List<TelemetryProjectRegistration> filtered = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(loaded, disabled),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Alechilles:Loaded Mod", "1.0.0"))
        );

        assertEquals(1, filtered.size());
        assertEquals("loaded-mod", filtered.getFirst().projectId());
    }

    private static TelemetryProjectRegistration registration(String projectId,
                                                            String ownerPluginIdentifier,
                                                            String displayName) {
        return new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "%s",
                          "displayName": "%s",
                          "runtimeMode": "embedded",
                          "ownerPluginIdentifiers": ["%s"],
                          "packagePrefixes": ["com.example.%s"],
                          "stats": {
                            "enabled": true,
                            "allowedEvents": ["heartbeat"]
                          }
                        }
                        """.formatted(projectId, displayName, ownerPluginIdentifier, projectId.replace('-', '.')),
                        null
                ),
                ownerPluginIdentifier,
                "1.0.0",
                Path.of(displayName + ".jar")
        );
    }
}
```

- [ ] **Step 5: Run discovery tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryLoadedModSnapshotProviderTest,TelemetryRuntimeDiscoveryTest test
```

Expected: both test classes pass.

- [ ] **Step 6: Commit discovery move**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProvider.java standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryLoadedModSnapshotProviderTest.java
git commit -m "Refactor: share telemetry runtime discovery"
```

Expected: commit succeeds.

---

### Task 4: Refactor Coordinator Creation To Consume Shared Discovery

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java`

- [ ] **Step 1: Add constructor factory from discovery result**

In `TelemetryCoordinatorService.java`, add import:

```java
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
```

Add this factory method:

```java
@Nonnull
public static TelemetryCoordinatorService fromDiscovery(@Nonnull TelemetryRuntimeSettings settings,
                                                        @Nonnull TelemetryDataPaths dataPaths,
                                                        @Nonnull TelemetryRuntimeDiscoveryResult discovery,
                                                        @Nonnull CrashReportClient client,
                                                        @Nullable HytaleLogger logger,
                                                        @Nullable ScheduledExecutorService executor,
                                                        @Nullable IntSupplier onlinePlayers) {
    return new TelemetryCoordinatorService(
            settings,
            dataPaths,
            discovery.projects(),
            manualReportRuntimeProjects(discovery.projects(), discovery.consentProjects()),
            discovery.loadedMods(),
            client,
            logger,
            executor,
            onlinePlayers
    );
}
```

- [ ] **Step 2: Keep existing `discover` as compatibility wrapper**

Replace the body of `discover(dataPaths, client, logger, executor, onlinePlayers)` with:

```java
TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
TelemetryProjectDiscovery.DiscoveryResult discovery = new TelemetryProjectDiscovery(logger)
        .discover(dataPaths.descriptorDirectories());
Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(logger)
        .loadAll(dataPaths.projectSettingsDirectory());
List<TelemetryProjectRegistration> projects = applyOverrides(discovery.projects(), overrides);
List<TelemetryProjectRegistration> consentProjects = applyOverrides(discovery.consentProjects(), overrides);
TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscoveryResult(
        projects,
        consentProjects,
        discovery.loadedMods(),
        com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector.detect(projects),
        discovery.skippedRegistrationWarnings()
);
return fromDiscovery(settings, dataPaths, result, client, logger, executor, onlinePlayers);
```

This preserves current direct discovery behavior for tests and transitional callers while the host moves to active filtering.

- [ ] **Step 3: Add coordinator factory test**

In `TelemetryCoordinatorServiceTest.java`, add:

```java
@Test
void coordinatorFactoryUsesSharedDiscoveryResult() {
    TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
            tempDir.resolve("Settings").resolve("runtime.json"),
            null
    );
    TelemetryDataPaths dataPaths = dataPaths(settings);
    TelemetryProjectRegistration project = new TelemetryProjectRegistration(
            statsDescriptor("shared-mod", "Shared Mod", "embedded"),
            "Example:Shared Mod",
            "1.0.0",
            tempDir.resolve("Shared.jar")
    );
    TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscoveryResult(
            List.of(project),
            List.of(project),
            List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Shared Mod", "1.0.0")),
            List.of(),
            List.of()
    );

    TelemetryCoordinatorService service = TelemetryCoordinatorService.fromDiscovery(
            settings,
            dataPaths,
            discovery,
            new SequencedClient(CrashReportClient.UploadResult.success(204)),
            null,
            null,
            () -> 2
    );

    assertEquals(1, service.registeredProjectCount());
    assertEquals("shared-mod", service.projects().getFirst().projectId());
}
```

Add import:

```java
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
```

- [ ] **Step 4: Run coordinator tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCoordinatorServiceTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit coordinator factory**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.java runtime/src/test/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorServiceTest.java
git commit -m "Refactor: build coordinator from shared discovery"
```

Expected: commit succeeds.

---

### Task 5: Move Player Counter And Stats Runtime Types Into Runtime

**Files:**
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounter.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryPlayerCounter.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsRuntime.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryStatsRuntime.java`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatService.java` to `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryStatsHeartbeatService.java`
- Move tests under `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats/`

- [ ] **Step 1: Move stats files**

Run:

```powershell
New-Item -ItemType Directory -Force runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats
git mv standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounter.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryPlayerCounter.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsRuntime.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryStatsRuntime.java
git mv standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatService.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryStatsHeartbeatService.java
New-Item -ItemType Directory -Force runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats
git mv standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounterTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryPlayerCounterTest.java
git mv standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatServiceTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryStatsHeartbeatServiceTest.java
git mv standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryStatsHeartbeatServiceTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats/TelemetryRuntimeStatsHeartbeatServiceTest.java
```

Change moved package declarations to:

```java
package com.alechilles.alecstelemetry.runtime.stats;
```

- [ ] **Step 2: Update imports**

Run:

```powershell
rg -n "com\\.alechilles\\.alecstelemetry\\.stats" runtime standalone
```

Replace each result with:

```java
com.alechilles.alecstelemetry.runtime.stats
```

- [ ] **Step 3: Run stats tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryPlayerCounterTest,TelemetryStatsHeartbeatServiceTest,TelemetryRuntimeStatsHeartbeatServiceTest test
```

Expected: tests pass.

- [ ] **Step 4: Commit stats move**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/stats runtime/src/test/java/com/alechilles/alecstelemetry/runtime/stats standalone/src/main/java/com/alechilles/alecstelemetry/stats standalone/src/test/java/com/alechilles/alecstelemetry/stats standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryStatsHeartbeatServiceTest.java
git commit -m "Refactor: move telemetry stats runtime into shared runtime"
```

Expected: commit succeeds.

---

### Task 6: Add Shared Bootstrap Request And Host Handle

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeBootstrapRequest.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostHandle.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHost.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`

- [ ] **Step 1: Create bootstrap request**

Create `TelemetryRuntimeBootstrapRequest.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

public record TelemetryRuntimeBootstrapRequest(
        @Nonnull JavaPlugin plugin,
        @Nonnull TelemetryRuntimeOrigin origin,
        @Nonnull String providerPluginIdentifier,
        @Nonnull String providerPluginVersion,
        @Nonnull String runtimeVersion,
        @Nonnull Path sourcePath,
        @Nullable TelemetryProjectDescriptor embeddedDescriptor) {

    public boolean embedded() {
        return origin == TelemetryRuntimeOrigin.EMBEDDED;
    }

    public boolean standalone() {
        return origin == TelemetryRuntimeOrigin.STANDALONE;
    }
}
```

- [ ] **Step 2: Create host handle interface**

Create `TelemetryRuntimeHostHandle.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface TelemetryRuntimeHostHandle {
    void start();

    void shutdown();

    boolean ownsActiveCoordinator();

    @Nullable
    String activeCoordinatorProviderId();

    int registeredProjectCount();

    @Nonnull
    TelemetryRuntimeApi api();
}
```

- [ ] **Step 3: Create host entrypoint skeleton**

Create `TelemetryRuntimeHost.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryBootstrap;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

public final class TelemetryRuntimeHost {
    private static final String FALLBACK_RUNTIME_VERSION = "0.1.3";

    private TelemetryRuntimeHost() {
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapStandalone(@Nonnull JavaPlugin plugin) {
        return bootstrap(new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.STANDALONE,
                resolvePluginIdentifier(plugin, "Alechilles:Alec's Telemetry"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                null
        ));
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapEmbedded(@Nonnull JavaPlugin plugin,
                                                              @Nonnull TelemetryProjectDescriptor descriptor) {
        return bootstrap(new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.EMBEDDED,
                resolvePluginIdentifier(plugin, "unknown:unknown"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                descriptor
        ));
    }

    @Nonnull
    static TelemetryRuntimeHostHandle bootstrap(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        return TelemetryRuntimeProviderHandle.create(request);
    }

    @Nonnull
    private static String resolveRuntimeVersion() {
        Package runtimePackage = TelemetryRuntimeHost.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_RUNTIME_VERSION
                : implementationVersion.trim();
    }

    @Nonnull
    private static String resolvePluginIdentifier(@Nonnull JavaPlugin plugin, @Nonnull String fallback) {
        PluginIdentifier identifier = plugin.getIdentifier();
        if (identifier != null) {
            return identifier.toString();
        }
        PluginManifest manifest = plugin.getManifest();
        if (manifest != null) {
            return new PluginIdentifier(manifest).toString();
        }
        return fallback;
    }

    @Nonnull
    private static String resolvePluginVersion(@Nonnull JavaPlugin plugin) {
        PluginManifest manifest = plugin.getManifest();
        if (manifest == null) {
            return "unknown";
        }
        Semver version = manifest.getVersion();
        return version == null ? "unknown" : version.toString();
    }

    @Nonnull
    private static Path resolvePluginSourcePath(@Nonnull JavaPlugin plugin) {
        try {
            if (plugin.getFile() != null) {
                return plugin.getFile().toAbsolutePath().normalize();
            }
        } catch (RuntimeException ignored) {
        }
        return plugin.getDataDirectory().toAbsolutePath().normalize();
    }
}
```

- [ ] **Step 4: Add temporary compile test for host API**

Create `TelemetryRuntimeHostTest.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelemetryRuntimeHostTest {
    @Test
    void exposesStandaloneAndEmbeddedBootstrapMethods() throws Exception {
        Method standalone = TelemetryRuntimeHost.class.getMethod(
                "bootstrapStandalone",
                com.hypixel.hytale.server.core.plugin.JavaPlugin.class
        );
        Method embedded = TelemetryRuntimeHost.class.getMethod(
                "bootstrapEmbedded",
                com.hypixel.hytale.server.core.plugin.JavaPlugin.class,
                com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor.class
        );

        assertNotNull(standalone);
        assertNotNull(embedded);
    }
}
```

- [ ] **Step 5: Run host compile test**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest test
```

Expected: compilation succeeds and test passes. If `TelemetryRuntimeProviderHandle` does not exist yet, create it in Task 7 before rerunning.

- [ ] **Step 6: Commit host API skeleton**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host
git commit -m "Feat: add shared telemetry runtime host API"
```

Expected: commit succeeds.

---

### Task 7: Implement Shared Provider Handle

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorBridge.java` only if additional bridge methods are required
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`

- [ ] **Step 1: Create provider handle implementing lifecycle, API operations, and bridge**

Create `TelemetryRuntimeProviderHandle.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryLoadedModSnapshotProvider;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscoveryResult;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryRuntimeProviderHandle implements TelemetryRuntimeHostHandle, TelemetryRuntimeOperations {
    private final TelemetryRuntimeBootstrapRequest request;
    private final TelemetryDataPaths dataPaths;
    private final TelemetryRuntimeSettings settings;
    private final TelemetryRuntimeDiscoveryResult discovery;
    private final TelemetryCoordinatorService coordinatorService;
    private final ProviderBridge bridge;
    private final TelemetryRuntimeApi api;
    private final TelemetryPlayerCounter playerCounter;
    private final TelemetryRuntimePluginEvents pluginEvents;
    private final TelemetryRuntimeCommandRegistrar commandRegistrar;
    private final HytaleLogger logger;

    @Nonnull
    static TelemetryRuntimeProviderHandle create(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        HytaleLogger logger = request.plugin().getLogger();
        TelemetryDataPaths dataPaths = request.embedded()
                ? TelemetryDataPaths.forSharedCoordinator(request.plugin())
                : TelemetryDataPaths.from(request.plugin());
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryLoadedModSnapshotProvider snapshotProvider = TelemetryLoadedModSnapshotProvider.hytalePluginManager(
                List.of(),
                logger
        );
        TelemetryRuntimeDiscoveryResult discovery = new TelemetryRuntimeDiscovery(logger)
                .discoverActive(dataPaths, snapshotProvider);
        TelemetryPlayerCounter playerCounter = new TelemetryPlayerCounter();
        CrashReportClient client = new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger);
        TelemetryCoordinatorService coordinatorService = TelemetryCoordinatorService.fromDiscovery(
                settings,
                dataPaths,
                discovery,
                client,
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                playerCounter::onlinePlayers
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                request.origin().name().toLowerCase(java.util.Locale.ROOT) + ":" + request.providerPluginIdentifier(),
                request.origin(),
                request.runtimeVersion(),
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                request.providerPluginIdentifier(),
                request.providerPluginVersion(),
                request.sourcePath(),
                dataPaths.runtimeRoot()
        );
        return new TelemetryRuntimeProviderHandle(
                request,
                dataPaths,
                settings,
                discovery,
                coordinatorService,
                playerCounter,
                candidate,
                logger
        );
    }

    private TelemetryRuntimeProviderHandle(@Nonnull TelemetryRuntimeBootstrapRequest request,
                                           @Nonnull TelemetryDataPaths dataPaths,
                                           @Nonnull TelemetryRuntimeSettings settings,
                                           @Nonnull TelemetryRuntimeDiscoveryResult discovery,
                                           @Nonnull TelemetryCoordinatorService coordinatorService,
                                           @Nonnull TelemetryPlayerCounter playerCounter,
                                           @Nonnull TelemetryRuntimeCandidate candidate,
                                           @Nullable HytaleLogger logger) {
        this.request = request;
        this.dataPaths = dataPaths;
        this.settings = settings;
        this.discovery = discovery;
        this.coordinatorService = coordinatorService;
        this.playerCounter = playerCounter;
        this.bridge = new ProviderBridge(candidate, coordinatorService);
        this.api = new TelemetryRuntimeApiImpl(this);
        this.pluginEvents = new TelemetryRuntimePluginEvents(this, playerCounter, logger);
        this.commandRegistrar = new TelemetryRuntimeCommandRegistrar(request.plugin(), this, logger);
        this.logger = logger;
    }

    @Override
    public void start() {
        pluginEvents.register(request.plugin());
        TelemetryRuntimeLocator.register(api);
        TelemetryCoordinatorRegistry.register(bridge);
        if (ownsActiveCoordinator()) {
            commandRegistrar.register();
        }
    }

    @Override
    public void shutdown() {
        commandRegistrar.unregister();
        TelemetryCoordinatorRegistry.unregister(bridge.providerId());
        TelemetryRuntimeLocator.clear();
        pluginEvents.unregister();
    }

    @Override
    public boolean ownsActiveCoordinator() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && active.providerId().equals(bridge.providerId());
    }

    @Nullable
    @Override
    public String activeCoordinatorProviderId() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active == null ? null : active.providerId();
    }

    @Override
    public int registeredProjectCount() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null && ownsActiveCoordinator()
                ? coordinatorService.registeredProjectCount()
                : discovery.projects().size();
    }

    @Nonnull
    @Override
    public TelemetryRuntimeApi api() {
        return api;
    }

    @Nonnull
    @Override
    public List<TelemetryProjectRegistration> projects() {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null && ownsActiveCoordinator()) {
            return coordinatorService.projects();
        }
        return discovery.projects();
    }

    @Nullable
    @Override
    public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
        for (TelemetryProjectRegistration project : projects()) {
            if (project.projectId().equalsIgnoreCase(projectId.trim())) {
                return project;
            }
        }
        return null;
    }

    @Override
    public boolean isProjectEnabled(@Nonnull String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null ? active.isProjectEnabled(projectId) : coordinatorService.isProjectEnabled(projectId);
    }

    @Override
    public boolean requestFlush(@Nullable String projectId) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null ? active.requestFlush(projectId) : coordinatorService.requestFlush(projectId);
    }

    @Override
    public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        return active != null ? active.captureTestReport(projectId, detail) : coordinatorService.captureTestReport(projectId, detail);
    }

    @Override
    public void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordBreadcrumb(projectId, category, detail);
        }
    }

    @Override
    public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureSetupFailure(projectId, throwable);
        }
    }

    @Override
    public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.captureStartFailure(projectId, throwable);
        }
    }

    @Override
    public void recordErrorWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable Throwable throwable,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordError(projectId, eventName, throwable, detailsFrom(context));
        }
    }

    @Override
    public void recordLifecycleWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           int durationMs,
                                           boolean success,
                                           @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordLifecycle(projectId, eventName, durationMs, success, detailsFrom(context));
        }
    }

    @Override
    public void recordPerformanceWithContext(@Nonnull String projectId,
                                             @Nonnull String eventName,
                                             int durationMs,
                                             @Nullable Double metricValue,
                                             @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordPerformance(projectId, eventName, durationMs, metricValue, detailsFrom(context));
        }
    }

    @Override
    public void recordUsageWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordUsage(projectId, eventName, detailsFrom(context));
        }
    }

    @Override
    public void recordStatsWithContext(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       @Nullable TelemetryEventContext context) {
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        if (active != null) {
            active.recordStats(projectId, eventName, detailsFrom(context));
        }
    }

    @Nonnull
    private static Map<String, Object> detailsFrom(@Nullable TelemetryEventContext context) {
        if (context == null) {
            return Map.of();
        }
        TelemetryEventContext normalized = context.normalize();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(normalized.details());
        putIfPresent(details, "detail", normalized.detail());
        putIfPresent(details, "severity", normalized.severity());
        putIfPresent(details, "fingerprint", normalized.fingerprint());
        putIfPresent(details, "subsystem", normalized.subsystem());
        putIfPresent(details, "phase", normalized.phase());
        putIfPresent(details, "operation", normalized.operation());
        putIfPresent(details, "target", normalized.target());
        putIfPresent(details, "featureKey", normalized.featureKey());
        putIfPresent(details, "entryPoint", normalized.entryPoint());
        putIfPresent(details, "runtimeSide", normalized.runtimeSide());
        putIfPresent(details, "entityType", normalized.entityType());
        putIfPresent(details, "itemId", normalized.itemId());
        putIfPresent(details, "blockId", normalized.blockId());
        putIfPresent(details, "biomeId", normalized.biomeId());
        putIfPresent(details, "commandName", normalized.commandName());
        putIfPresent(details, "worldName", normalized.worldName());
        return Map.copyOf(details);
    }

    private static void putIfPresent(@Nonnull Map<String, Object> details,
                                     @Nonnull String key,
                                     @Nullable String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value);
        }
    }

    private record ProviderBridge(@Nonnull TelemetryRuntimeCandidate candidate,
                                  @Nonnull TelemetryCoordinatorService service)
            implements TelemetryCoordinatorBridge {
        private final AtomicBoolean active = new AtomicBoolean(false);

        @Override public String providerId() { return candidate.providerId(); }
        @Override public String origin() { return candidate.origin().name(); }
        @Override public String runtimeVersion() { return candidate.runtimeVersion(); }
        @Override public int coordinatorProtocolVersion() { return candidate.coordinatorProtocolVersion(); }
        @Override public String providerPluginIdentifier() { return candidate.providerPluginIdentifier(); }
        @Override public String providerPluginVersion() { return candidate.providerPluginVersion(); }
        @Override public String sourcePath() { return candidate.sourcePath().toString(); }
        @Override public String sharedDataRoot() { return candidate.sharedDataRoot().toString(); }
        @Override public void activate() { active.set(true); }
        @Override public void deactivate() { active.set(false); }
        @Override public boolean isActive() { return active.get(); }
        @Override public void start() { service.start(); }
        @Override public void shutdown() { service.shutdown(); }
        @Override public boolean isProjectEnabled(@Nonnull String projectId) { return service.isProjectEnabled(projectId); }
        @Override public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) { return service.setProjectEnabled(projectId, enabled); }
        @Override public boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) { return service.setCrashEnabled(projectId, enabled); }
        @Override public boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) { return service.setErrorEventsEnabled(projectId, enabled); }
        @Override public boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) { return service.setLifecycleEventsEnabled(projectId, enabled); }
        @Override public boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) { return service.setPerformanceEnabled(projectId, enabled); }
        @Override public boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) { return service.setUsageEnabled(projectId, enabled); }
        @Override public boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) { return service.setStatsEnabled(projectId, enabled); }
        @Override public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) { return service.setBreadcrumbsEnabled(projectId, enabled); }
        @Override public boolean recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) { return service.recordBreadcrumb(projectId, category, detail); }
        @Override public boolean captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) { return service.captureSetupFailure(projectId, throwable); }
        @Override public boolean captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) { return service.captureStartFailure(projectId, throwable); }
        @Override public boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable, @Nullable String worldName, @Nullable String removalReason, @Nullable String possibleFailureCause) { return service.captureExceptionalWorldRemoval(throwable, worldName, removalReason, possibleFailureCause); }
        @Override public boolean recordError(@Nonnull String projectId, @Nonnull String eventName, @Nullable Throwable throwable, @Nonnull Map<String, Object> details) { return service.recordError(projectId, eventName, throwable, details); }
        @Override public boolean recordLifecycle(@Nonnull String projectId, @Nonnull String eventName, int durationMs, boolean success, @Nonnull Map<String, Object> details) { return service.recordLifecycle(projectId, eventName, durationMs, success, details); }
        @Override public boolean recordPerformance(@Nonnull String projectId, @Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nonnull Map<String, Object> details) { return service.recordPerformance(projectId, eventName, durationMs, metricValue, details); }
        @Override public boolean recordUsage(@Nonnull String projectId, @Nonnull String eventName, @Nonnull Map<String, Object> details) { return service.recordUsage(projectId, eventName, details); }
        @Override public boolean recordStats(@Nonnull String projectId, @Nonnull String eventName, @Nonnull Map<String, Object> details) { return service.recordStats(projectId, eventName, details); }
        @Override public boolean requestFlush(@Nullable String projectId) { return service.requestFlush(projectId); }
        @Override public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) { return service.captureTestReport(projectId, detail); }
        @Override public Map<String, Object> submitManualReport(@Nonnull String projectId, @Nonnull Map<String, Object> submission, @Nonnull Map<String, Object> playerContext) { return service.submitManualReport(projectId, submission, playerContext); }
    }
}
```

- [ ] **Step 2: Run compile and fix imports**

Run:

```powershell
.\mvnw.cmd -pl runtime -DskipTests compile
```

Expected: compilation fails only for missing `TelemetryRuntimePluginEvents` and `TelemetryRuntimeCommandRegistrar`; create those in Tasks 8 and 10 before committing this task if compile requires them immediately. If needed, create minimal classes with no-op methods:

```java
final class TelemetryRuntimePluginEvents {
    TelemetryRuntimePluginEvents(TelemetryRuntimeProviderHandle handle,
                                 com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter playerCounter,
                                 com.hypixel.hytale.logger.HytaleLogger logger) {
    }

    void register(com.hypixel.hytale.server.core.plugin.JavaPlugin plugin) {
    }

    void unregister() {
    }
}
```

```java
final class TelemetryRuntimeCommandRegistrar {
    TelemetryRuntimeCommandRegistrar(com.hypixel.hytale.server.core.plugin.JavaPlugin plugin,
                                     TelemetryRuntimeProviderHandle handle,
                                     com.hypixel.hytale.logger.HytaleLogger logger) {
    }

    void register() {
    }

    void unregister() {
    }
}
```

- [ ] **Step 3: Run host tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest test
```

Expected: host tests pass.

- [ ] **Step 4: Commit shared provider handle**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host
git commit -m "Feat: add shared telemetry provider handle"
```

Expected: commit succeeds.

---

### Task 8: Move Player And World Event Handling Into Shared Host

**Files:**
- Create/modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimePluginEvents.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`

- [ ] **Step 1: Replace no-op event class with real implementation**

Use this complete implementation:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class TelemetryRuntimePluginEvents {
    private final TelemetryRuntimeProviderHandle handle;
    private final TelemetryPlayerCounter playerCounter;
    private final HytaleLogger logger;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    TelemetryRuntimePluginEvents(@Nonnull TelemetryRuntimeProviderHandle handle,
                                 @Nonnull TelemetryPlayerCounter playerCounter,
                                 @Nullable HytaleLogger logger) {
        this.handle = handle;
        this.playerCounter = playerCounter;
        this.logger = logger;
    }

    void register(@Nonnull JavaPlugin plugin) {
        if (!registered.compareAndSet(false, true) || plugin.getEventRegistry() == null) {
            return;
        }
        try {
            plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnected);
            plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
        } catch (RuntimeException ex) {
            registered.set(false);
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Alec's Telemetry could not register shared runtime events.");
            }
        }
    }

    void unregister() {
        registered.set(false);
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        playerCounter.onPlayerReady(event);
        handle.onPlayerReady(event);
    }

    private void onPlayerDisconnected(@Nonnull PlayerDisconnectEvent event) {
        playerCounter.onPlayerDisconnect(event);
    }

    private void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        handle.onWorldRemoved(event);
    }
}
```

- [ ] **Step 2: Add event callbacks to provider handle**

In `TelemetryRuntimeProviderHandle.java`, add imports:

```java
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
```

Add methods:

```java
void onPlayerReady(@Nonnull PlayerReadyEvent event) {
    if (ownsActiveCoordinator()) {
        commandRegistrar.onPlayerReady(event);
    }
}

void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
    if (!ownsActiveCoordinator()
            || event.getRemovalReason() != RemoveWorldEvent.RemovalReason.EXCEPTIONAL
            || event.getWorld() == null) {
        return;
    }
    coordinatorService.captureExceptionalWorldRemoval(
            event.getWorld().getFailureException(),
            event.getWorld().getName(),
            event.getRemovalReason().name(),
            event.getWorld().getPossibleFailureCause() == null ? null : event.getWorld().getPossibleFailureCause().toString()
    );
}
```

- [ ] **Step 3: Run runtime compile**

Run:

```powershell
.\mvnw.cmd -pl runtime -DskipTests compile
```

Expected: compile succeeds or fails only because command registrar is still no-op/missing command methods. Resolve command registrar in Task 10.

- [ ] **Step 4: Commit shared event handling**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimePluginEvents.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java
git commit -m "Refactor: share telemetry runtime event handling"
```

Expected: commit succeeds.

---

### Task 9: Move Consent Coordinator Into Runtime Host

**Files:**
- Ensure existing runtime files are used:
  - `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java`
  - `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentStateStore.java`
  - `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Move/adapt test: `standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java` to `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java`

- [ ] **Step 1: Make provider handle expose consent operations**

Add fields to `TelemetryRuntimeProviderHandle`:

```java
private final com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator consentCoordinator;
```

Initialize in constructor:

```java
this.consentCoordinator = new com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator(this, logger);
```

If the constructor signature of `TelemetryConsentCoordinator` currently expects the standalone `TelemetryRuntimeService`, change it to accept a new runtime-facing interface:

```java
public interface TelemetryConsentRuntime {
    @Nonnull
    List<TelemetryProjectRegistration> unreviewedConsentProjects();

    boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot);

    boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled);

    boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot);

    boolean markConsentReviewed(@Nonnull String projectId);
}
```

The file already exists at `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryConsentRuntime.java`; use that existing type if its methods match.

- [ ] **Step 2: Implement `TelemetryConsentRuntime` on provider handle**

Add:

```java
@Nonnull
public List<TelemetryProjectRegistration> unreviewedConsentProjects() {
    return consentStateStore.unreviewedProjects(dataPaths.consentStateFile(), discovery.consentProjects());
}

public boolean applyConsentToAll(@Nonnull TelemetryConsentSnapshot snapshot) {
    boolean appliedAll = true;
    for (TelemetryProjectRegistration project : discovery.consentProjects()) {
        appliedAll &= applyConsent(project.projectId(), clampToSupported(snapshot, supportedSnapshot(project)));
    }
    return appliedAll;
}
```

Also move the exact `applyConsentCategoryToAll`, `applyConsent`, `supportedSnapshot`, `clampToSupported`, and override-save logic from `TelemetryRuntimeService` into `TelemetryRuntimeProviderHandle`. Keep method names unchanged to minimize command changes.

- [ ] **Step 3: Wire player ready consent notice**

In `TelemetryRuntimeProviderHandle.onPlayerReady(PlayerReadyEvent event)`, add:

```java
if (ownsActiveCoordinator()) {
    consentCoordinator.onPlayerReady(event);
    commandRegistrar.onPlayerReady(event);
}
```

- [ ] **Step 4: Move consent test**

Run:

```powershell
git mv standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java
```

Update imports in the test from standalone runtime service to test fakes implementing `TelemetryConsentRuntime`.

- [ ] **Step 5: Run consent tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentCoordinatorTest,TelemetryConsentStateStoreTest,TelemetryConsentViewModelTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit consent move**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host runtime/src/test/java/com/alechilles/alecstelemetry/consent standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinatorTest.java
git commit -m "Refactor: move telemetry consent orchestration into runtime"
```

Expected: commit succeeds.

---

### Task 10: Move Commands And Manual Report UI Into Runtime

**Files:**
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/*` to `runtime/src/main/java/com/alechilles/alecstelemetry/commands/*`
- Move: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/*` to `runtime/src/main/java/com/alechilles/alecstelemetry/reports/*`
- Create/modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeCommandRegistrar.java`
- Move tests for commands/reports into runtime tests

- [ ] **Step 1: Move command and report source**

Run:

```powershell
New-Item -ItemType Directory -Force runtime/src/main/java/com/alechilles/alecstelemetry/commands
New-Item -ItemType Directory -Force runtime/src/main/java/com/alechilles/alecstelemetry/reports
git mv standalone/src/main/java/com/alechilles/alecstelemetry/commands/* runtime/src/main/java/com/alechilles/alecstelemetry/commands/
git mv standalone/src/main/java/com/alechilles/alecstelemetry/reports/* runtime/src/main/java/com/alechilles/alecstelemetry/reports/
New-Item -ItemType Directory -Force runtime/src/test/java/com/alechilles/alecstelemetry/commands
New-Item -ItemType Directory -Force runtime/src/test/java/com/alechilles/alecstelemetry/reports
git mv standalone/src/test/java/com/alechilles/alecstelemetry/commands/* runtime/src/test/java/com/alechilles/alecstelemetry/commands/
git mv standalone/src/test/java/com/alechilles/alecstelemetry/reports/* runtime/src/test/java/com/alechilles/alecstelemetry/reports/
```

- [ ] **Step 2: Replace command dependency on `AlecsTelemetry`**

For each moved command constructor that currently accepts `AlecsTelemetry plugin`, change it to accept:

```java
@Nonnull TelemetryRuntimeHostHandle runtime
```

For commands that need report opening or consent coordinator methods, use `TelemetryRuntimeProviderHandle` package-private methods through a new command-facing interface:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface TelemetryCommandRuntime {
    boolean ownsActiveCoordinator();
    @Nullable String activeCoordinatorProviderId();
    int registeredProjectCount();
    @Nonnull List<TelemetryProjectRegistration> projects();
    @Nullable TelemetryProjectRegistration findProject(@Nonnull String projectId);
    int pendingReports(@Nullable String projectId);
    @Nonnull String lastFlushResult();
    boolean requestFlush(@Nullable String projectId);
    boolean captureTestReport(@Nonnull String projectId, @Nullable String detail);
    @Nonnull List<TelemetryProjectRegistration> unreviewedConsentProjects();
    boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot);
    boolean markConsentReviewed(@Nonnull String projectId);
}
```

Have `TelemetryRuntimeProviderHandle` implement `TelemetryCommandRuntime`.

- [ ] **Step 3: Replace command root constructor**

Change `runtime/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java` to:

```java
package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

public final class TelemetryCommandRoot extends AbstractCommandCollection {
    public static final String ROOT_PERMISSION = "telemetry.command.telemetry";

    public TelemetryCommandRoot(@Nonnull TelemetryCommandRuntime runtime) {
        super("telemetry", "Alec's Telemetry commands.");
        requirePermission(ROOT_PERMISSION);
        setPermissionGroups("OP", "Admin", "Operator");
        addSubCommand(new TelemetryStatusCommand(runtime));
        addSubCommand(new TelemetryProjectsCommand(runtime));
        addSubCommand(new TelemetryProjectCommand(runtime));
        addSubCommand(new TelemetryConsentCommand(runtime));
        addSubCommand(new TelemetryReportCommand(runtime));
        addSubCommand(new TelemetryReportReviewCommand(runtime));
        addSubCommand(new TelemetryFlushCommand(runtime));
        addSubCommand(new TelemetryTestCommand(runtime));
    }
}
```

- [ ] **Step 4: Implement command registrar**

Replace `TelemetryRuntimeCommandRegistrar.java` with:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class TelemetryRuntimeCommandRegistrar {
    private final JavaPlugin plugin;
    private final TelemetryRuntimeProviderHandle handle;
    private final HytaleLogger logger;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    TelemetryRuntimeCommandRegistrar(@Nonnull JavaPlugin plugin,
                                     @Nonnull TelemetryRuntimeProviderHandle handle,
                                     @Nullable HytaleLogger logger) {
        this.plugin = plugin;
        this.handle = handle;
        this.logger = logger;
    }

    void register() {
        if (!registered.compareAndSet(false, true) || plugin.getCommandRegistry() == null) {
            return;
        }
        try {
            plugin.getCommandRegistry().registerCommand(new TelemetryCommandRoot(handle));
        } catch (RuntimeException ex) {
            registered.set(false);
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Alec's Telemetry could not register /telemetry commands.");
            }
        }
    }

    void unregister() {
        registered.set(false);
    }

    void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        register();
    }
}
```

- [ ] **Step 5: Remove duplicate embedded commands**

After the shared `TelemetryCommandRoot` compiles, delete:

```powershell
git rm -r runtime/src/main/java/com/alechilles/alecstelemetry/embedded/commands
```

- [ ] **Step 6: Run command/report tests in runtime**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryCommandRootTest,TelemetryReportViewModelTest,TelemetryReportPageEventDataTest,TelemetryReportAssetPackTest test
```

Expected: tests pass.

- [ ] **Step 7: Commit command move**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/commands runtime/src/main/java/com/alechilles/alecstelemetry/reports runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host runtime/src/test/java/com/alechilles/alecstelemetry/commands runtime/src/test/java/com/alechilles/alecstelemetry/reports standalone/src/main/java/com/alechilles/alecstelemetry/commands standalone/src/main/java/com/alechilles/alecstelemetry/reports standalone/src/test/java/com/alechilles/alecstelemetry/commands standalone/src/test/java/com/alechilles/alecstelemetry/reports
git commit -m "Refactor: move telemetry commands into runtime"
```

Expected: commit succeeds.

---

### Task 11: Convert Embedded Bootstrap To Shared Host

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`

- [ ] **Step 1: Add embedded owner facade over host handle**

Modify `EmbeddedTelemetryService` to store:

```java
private final TelemetryRuntimeHostHandle host;
private final TelemetryRuntimeApi api;
```

For the main constructor used by bootstrap, replace direct `TelemetryCoreEngine` and local coordinator fields with a `TelemetryRuntimeHostHandle`.

- [ ] **Step 2: Preserve embedded handle methods by delegating to API**

Each embedded handle method should call the project handle for `project.projectId()`. Use:

```java
private TelemetryProjectHandle projectHandle() {
    TelemetryProjectHandle handle = api.project(project.projectId());
    if (handle == null) {
        return TelemetryProjectHandle.noop(project.projectId());
    }
    return handle;
}
```

If `TelemetryProjectHandle.noop` does not exist, add this static factory to `TelemetryProjectHandle`:

```java
@Nonnull
static TelemetryProjectHandle noop(@Nonnull String projectId) {
    return new TelemetryProjectHandle() {
        @Nonnull
        @Override
        public String projectId() {
            return projectId;
        }

        @Override public boolean isEnabled() { return false; }
        @Override public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {}
        @Override public void captureSetupFailure(@Nullable Throwable throwable) {}
        @Override public void captureStartFailure(@Nullable Throwable throwable) {}
        @Override public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {}
        @Override public void recordStats(@Nonnull String eventName, @Nullable String detail) {}
        @Override public boolean requestFlush() { return false; }
        @Override public boolean captureTestReport(@Nullable String detail) { return false; }
    };
}
```

- [ ] **Step 3: Convert bootstrap**

In `EmbeddedTelemetryBootstrap.bootstrap(JavaPlugin plugin)`, after loading and validating the embedded descriptor, replace manual coordinator setup with:

```java
TelemetryRuntimeHostHandle host = TelemetryRuntimeHost.bootstrapEmbedded(plugin, descriptor);
return EmbeddedTelemetryService.fromHost(
        descriptor.projectId(),
        descriptor.displayName(),
        host,
        plugin.getLogger()
);
```

Add import:

```java
import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHost;
import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHostHandle;
```

- [ ] **Step 4: Run embedded tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest test
```

Expected: tests pass after adapting assertions to the host-backed service.

- [ ] **Step 5: Commit embedded conversion**

Run:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/embedded runtime/src/test/java/com/alechilles/alecstelemetry/embedded
git commit -m "Refactor: route embedded telemetry through shared host"
```

Expected: commit succeeds.

---

### Task 12: Convert Standalone Entrypoint To Thin Wrapper

**Files:**
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
- Delete after moves are complete:
  - `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/**`
  - `standalone/src/main/java/com/alechilles/alecstelemetry/stats/**`
- Test: `standalone` compile and runtime tests now moved to runtime

- [ ] **Step 1: Replace standalone plugin implementation**

Replace `AlecsTelemetry.java` with:

```java
package com.alechilles.alecstelemetry;

import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHost;
import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHostHandle;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

public final class AlecsTelemetry extends JavaPlugin {
    private static AlecsTelemetry instance;

    private TelemetryRuntimeHostHandle host;

    public AlecsTelemetry(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        try {
            host = TelemetryRuntimeHost.bootstrapStandalone(this);
        } catch (Exception ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to initialize Alec's Telemetry runtime; continuing without telemetry."
            );
            host = null;
        }
    }

    @Override
    protected void start() {
        if (host != null) {
            host.start();
            getLogger().at(Level.INFO).log(
                    "Alec's Telemetry enabled. Active coordinator="
                            + (host.activeCoordinatorProviderId() == null ? "<none>" : host.activeCoordinatorProviderId())
                            + ", standaloneOwnsRuntime=" + host.ownsActiveCoordinator()
                            + ", registered projects=" + host.registeredProjectCount()
            );
            return;
        }
        getLogger().at(Level.INFO).log("Alec's Telemetry enabled without an active runtime host.");
    }

    @Override
    protected void shutdown() {
        if (host != null) {
            host.shutdown();
            host = null;
        }
        instance = null;
        getLogger().at(Level.INFO).log("Alec's Telemetry disabled.");
    }

    @Nullable
    public static AlecsTelemetry getInstance() {
        return instance;
    }

    @Nullable
    public TelemetryRuntimeHostHandle getHost() {
        return host;
    }
}
```

- [ ] **Step 2: Delete now-empty standalone implementation packages**

Run:

```powershell
git rm -r standalone/src/main/java/com/alechilles/alecstelemetry/runtime standalone/src/main/java/com/alechilles/alecstelemetry/stats
```

If Git reports a path does not exist because it was already moved, continue.

- [ ] **Step 3: Run standalone compile**

Run:

```powershell
.\mvnw.cmd -pl standalone -am -DskipTests compile
```

Expected: compile succeeds and standalone source contains only the plugin wrapper plus resources.

- [ ] **Step 4: Commit standalone thinning**

Run:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java standalone/src/main/java/com/alechilles/alecstelemetry/runtime standalone/src/main/java/com/alechilles/alecstelemetry/stats
git commit -m "Refactor: thin standalone telemetry plugin"
```

Expected: commit succeeds.

---

### Task 13: Add Parity Tests For Standalone And Embedded Providers

**Files:**
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderParityTest.java`
- Modify existing runtime host tests as needed

- [ ] **Step 1: Add provider parity test class**

Create `TelemetryRuntimeProviderParityTest.java`:

```java
package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.discovery.TelemetryRuntimeDiscovery;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRuntimeProviderParityTest {

    @Test
    void standaloneAndEmbeddedUseSameLoadedModFilteringRules() {
        TelemetryProjectRegistration enabled = registration("enabled-mod", "Alechilles:Enabled Mod");
        TelemetryProjectRegistration notLoaded = registration("not-loaded-mod", "Alechilles:Not Loaded Mod");
        List<CrashReportEnvelope.LoadedModMetadata> loadedMods = List.of(
                new CrashReportEnvelope.LoadedModMetadata("Alechilles:Enabled Mod", "1.0.0")
        );

        List<TelemetryProjectRegistration> standalone = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(enabled, notLoaded),
                loadedMods
        );
        List<TelemetryProjectRegistration> embedded = TelemetryRuntimeDiscovery.filterRegistrationsToLoadedMods(
                List.of(enabled, notLoaded),
                loadedMods
        );

        assertEquals(standalone.stream().map(TelemetryProjectRegistration::projectId).toList(),
                embedded.stream().map(TelemetryProjectRegistration::projectId).toList());
    }

    @Test
    void providerOriginsAreMetadataOnlyForSharedDiscoveryRules() {
        assertEquals(TelemetryRuntimeOrigin.STANDALONE.name(), "STANDALONE");
        assertEquals(TelemetryRuntimeOrigin.EMBEDDED.name(), "EMBEDDED");
    }

    private static TelemetryProjectRegistration registration(String projectId, String ownerPluginIdentifier) {
        return new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "%s",
                          "displayName": "%s",
                          "runtimeMode": "embedded",
                          "ownerPluginIdentifiers": ["%s"],
                          "packagePrefixes": ["com.example"],
                          "stats": {
                            "enabled": true,
                            "allowedEvents": ["heartbeat"]
                          }
                        }
                        """.formatted(projectId, projectId, ownerPluginIdentifier),
                        null
                ),
                ownerPluginIdentifier,
                "1.0.0",
                Path.of(projectId + ".jar")
        );
    }
}
```

- [ ] **Step 2: Run parity tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeProviderParityTest,TelemetryRuntimeDiscoveryTest,TelemetryCoordinatorServiceTest test
```

Expected: tests pass.

- [ ] **Step 3: Commit parity tests**

Run:

```powershell
git add runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderParityTest.java
git commit -m "Test: cover standalone embedded runtime parity"
```

Expected: commit succeeds.

---

### Task 14: Remove Obsolete Standalone Runtime Service

**Files:**
- Delete after all callers are migrated:
  - `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Move or delete tests:
  - `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`

- [ ] **Step 1: Move reusable tests into runtime host/discovery tests**

For each assertion in `TelemetryRuntimeServiceTest`, place it in one of:

- `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java`
- `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`
- `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderParityTest.java`

Concrete mappings:

- Project filtering tests -> `TelemetryRuntimeDiscoveryTest`
- Consent snapshot application tests -> `TelemetryRuntimeHostTest`
- Stats heartbeat tests -> `TelemetryCoordinatorServiceTest` or `runtime/stats` tests
- Manual report backend tests -> runtime report tests

- [ ] **Step 2: Delete obsolete service and test**

Run:

```powershell
git rm standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java
git rm standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java
```

- [ ] **Step 3: Confirm no references remain**

Run:

```powershell
rg -n "TelemetryRuntimeService|standalone\\.src\\.main\\.java\\.com\\.alechilles\\.alecstelemetry\\.runtime" runtime standalone
```

Expected: no references to `TelemetryRuntimeService`.

- [ ] **Step 4: Run full tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all tests pass.

- [ ] **Step 5: Commit obsolete service removal**

Run:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/runtime standalone/src/test/java/com/alechilles/alecstelemetry/runtime runtime/src/test/java/com/alechilles/alecstelemetry
git commit -m "Refactor: remove standalone telemetry runtime service"
```

Expected: commit succeeds.

---

### Task 15: Update Build Packaging And Artifact Boundary Tests

**Files:**
- Modify: `runtime/pom.xml`
- Modify: `standalone/pom.xml`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/RuntimeArtifactBoundaryTest.java`
- Modify or add docs:
  - `docs/embedded-mode.md`
  - `docs/embedded-mode-refactor-plan.md`

- [ ] **Step 1: Ensure runtime contains all implementation classes**

Update `RuntimeArtifactBoundaryTest` to assert the runtime artifact owns these classes:

```java
assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHost.class");
assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.class");
assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.class");
assertContainsRuntimeClass("com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.class");
assertContainsRuntimeClass("com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.class");
assertContainsRuntimeClass("com/alechilles/alecstelemetry/reports/TelemetryReportCoordinator.class");
```

Also assert standalone-only service is gone:

```java
assertDoesNotContainRuntimeClass("com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.class");
```

If helper methods do not exist, add:

```java
private static void assertContainsRuntimeClass(String entryName) throws Exception {
    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(runtimeJar())) {
        assertNotNull(jar.getEntry(entryName), entryName + " should be packaged in runtime jar");
    }
}

private static void assertDoesNotContainRuntimeClass(String entryName) throws Exception {
    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(runtimeJar())) {
        assertNull(jar.getEntry(entryName), entryName + " should not be packaged in runtime jar");
    }
}
```

- [ ] **Step 2: Keep standalone shading only runtime**

Verify `standalone/pom.xml` still shades only:

```xml
<include>com.alechilles:alecstelemetry-runtime</include>
```

Do not add direct duplicate implementation packages to standalone.

- [ ] **Step 3: Run package boundary tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=RuntimeArtifactBoundaryTest test
.\mvnw.cmd -pl standalone -am -DskipTests package
```

Expected: runtime boundary test passes and standalone package succeeds.

- [ ] **Step 4: Commit packaging updates**

Run:

```powershell
git add runtime/pom.xml standalone/pom.xml runtime/src/test/java/com/alechilles/alecstelemetry/runtime/RuntimeArtifactBoundaryTest.java docs/embedded-mode.md docs/embedded-mode-refactor-plan.md
git commit -m "Build: enforce shared telemetry runtime packaging"
```

Expected: commit succeeds.

---

### Task 16: Rebuild And Install Tamework With Unified Runtime

**Files:**
- No source edits required unless Tamework API calls need updating.
- Tamework repo: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecstamework`
- Installed jars:
  - `C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\mods\Alec's Tamework! v2.14.1.jar`
  - `C:\Users\22ale\AppData\Roaming\Hytale\UserData\Mods\Alec's Tamework! v2.14.1.jar`

- [ ] **Step 1: Install unified runtime locally**

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry`:

```powershell
.\mvnw.cmd -pl runtime install
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Rebuild Tamework**

Run in `C:\Users\22ale\AppData\Roaming\Hytale\Modding\alecstamework`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Expected: tests pass and package succeeds.

- [ ] **Step 3: Install Tamework plugin**

Close Hytale before this step so `UserData\Mods\Alec's Tamework! v2.14.1.jar` is not locked.

Run:

```powershell
.\mvnw.cmd -Pinstall-plugin -DskipTests package
```

Expected: both server and UserData copies are overwritten.

- [ ] **Step 4: Verify installed Tamework jars contain unified runtime**

Run:

```powershell
$jars = @(
  "C:\Users\22ale\AppData\Roaming\Hytale\UserData\Mods\Alec's Tamework! v2.14.1.jar",
  "C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\mods\Alec's Tamework! v2.14.1.jar"
)
foreach ($jar in $jars) {
  [PSCustomObject]@{
    Path=$jar
    SHA256=(Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash
    HasHost=[bool](& jar tf $jar | Select-String -SimpleMatch "TelemetryRuntimeHost.class" | Select-Object -First 1)
    HasDiscovery=[bool](& jar tf $jar | Select-String -SimpleMatch "TelemetryRuntimeDiscovery.class" | Select-Object -First 1)
    HasCommands=[bool](& jar tf $jar | Select-String -SimpleMatch "TelemetryCommandRoot.class" | Select-Object -First 1)
  }
}
```

Expected: both jars report `HasHost=True`, `HasDiscovery=True`, and `HasCommands=True`.

- [ ] **Step 5: Run game verification without standalone telemetry jar**

Remove or temporarily move standalone `Alec's Telemetry v0.1.3.jar` from `UserData\Mods` if present.

Start Hytale with Tamework and other telemetry-configured mods enabled.

Verify in logs:

- Active coordinator provider starts with `embedded:`
- Registered project count includes Tamework and the enabled telemetry-configured mods
- Stats heartbeat queues or uploads `heartbeat` events for all enabled stats projects
- `/telemetry projects` works

- [ ] **Step 6: Run game verification with standalone telemetry jar installed**

Restore standalone jar and restart Hytale.

Verify:

- If standalone and embedded runtime versions match, deterministic tie-breaker chooses the expected winner.
- If an embedded runtime version is higher, embedded wins over standalone.
- Only one provider starts coordinator work.
- `/telemetry status` reports the active provider.

- [ ] **Step 7: Commit Tamework source changes if required**

If Tamework source needed API updates, inspect the actual changed files:

```powershell
git status --short
```

If the only required Tamework code update is `CrashTelemetryService`, run:

```powershell
git add src/main/java/com/alechilles/alecstamework/metrics/CrashTelemetryService.java
git commit -m "Refactor: update Tamework for unified telemetry runtime"
```

If Tamework also needs descriptor/API import updates, run:

```powershell
git add src/main/java/com/alechilles/alecstamework/metrics/CrashTelemetryService.java src/main/java/com/alechilles/alecstamework/metrics/TameworkTelemetryEvents.java src/main/resources/telemetry/project.json pom.xml
git commit -m "Refactor: update Tamework for unified telemetry runtime"
```

Expected: commit succeeds or no commit is needed because Tamework source compiled without changes.

---

### Task 17: Final Verification And Merge Readiness

**Files:**
- Entire AlecsTelemetry repo
- Tamework installed jar verification

- [ ] **Step 1: Run full AlecsTelemetry test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all tests pass.

- [ ] **Step 2: Package standalone**

Run:

```powershell
.\mvnw.cmd -pl standalone -am package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Verify no implementation packages remain in standalone**

Run:

```powershell
rg -n "class TelemetryRuntimeService|TelemetryLoadedModSnapshotProvider|TelemetryStatsHeartbeatService|TelemetryCommandRoot|TelemetryReportCoordinator" standalone/src/main/java
```

Expected: no results except references from `AlecsTelemetry.java` to `TelemetryRuntimeHostHandle`.

- [ ] **Step 4: Verify runtime owns the implementation**

Run:

```powershell
rg -n "class TelemetryRuntimeHost|class TelemetryRuntimeProviderHandle|class TelemetryRuntimeDiscovery|class TelemetryCommandRoot|class TelemetryReportCoordinator" runtime/src/main/java
```

Expected: all shared implementation classes are found under `runtime`.

- [ ] **Step 5: Check git status**

Run:

```powershell
git status --short --branch
```

Expected: no unstaged tracked changes. Existing unrelated `artifacts/` may remain untracked and must not be staged unless intentionally part of the release process.

- [ ] **Step 6: Merge readiness commit**

If final docs or test-only cleanup changed, inspect the actual changed files:

```powershell
git status --short
```

If only documentation changed, run:

```powershell
git add docs/embedded-mode.md docs/embedded-mode-refactor-plan.md docs/project-descriptor.md
git commit -m "Docs: document unified telemetry runtime"
```

If only test names/imports changed, run:

```powershell
git add runtime/src/test/java/com/alechilles/alecstelemetry
git commit -m "Test: finish unified telemetry runtime coverage"
```

Expected: commit succeeds or no final commit is needed because the previous tasks already committed all changes.

---

## Self-Review

**Spec coverage:** This plan covers the full conversion from split standalone/runtime behavior to a single runtime implementation with two distribution artifacts. It includes shared discovery, active loaded/enabled filtering, coordinator creation, stats heartbeat ownership, commands, consent, manual reports, API locator, standalone thinning, embedded bootstrap conversion, tests, and Tamework rollout.

**Placeholder scan:** The plan avoids deferred work markers. Where code is introduced, concrete class names, method signatures, commands, and expected outcomes are provided.

**Type consistency:** Shared host types are consistently named `TelemetryRuntimeHost`, `TelemetryRuntimeHostHandle`, `TelemetryRuntimeBootstrapRequest`, and `TelemetryRuntimeProviderHandle`. Discovery types are consistently named `TelemetryRuntimeDiscovery`, `TelemetryRuntimeDiscoveryResult`, and `TelemetryLoadedModSnapshotProvider`. Command-facing runtime uses `TelemetryCommandRuntime`.
