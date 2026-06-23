# Telemetry Runtime Data Root Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Alec's Telemetry runtime state into the standalone telemetry plugin data root, migrate existing save data once, and remove old Alec-created clutter without deleting Hytale-owned session telemetry logs.

**Architecture:** Keep `mods/Alechilles_Alec's Telemetry` as the canonical runtime root for both standalone and embedded/shared coordinator modes. Add a small runtime migration helper that runs before settings/discovery load, copies missing old central and embedded-owner state into the canonical root, then removes only empty or known Alec-created old directories after successful copy. Legacy central roots include both save-local lowercase `<SaveRoot>/telemetry` and hosted Linux/server-root uppercase `<ServerRoot>/Telemetry`; lowercase root telemetry logs remain protected. Stop writing embedded owner override mirrors so consumer mod data directories no longer accumulate telemetry settings.

**Tech Stack:** Java 25, Maven, JUnit 5, Gson, `java.nio.file`, Hytale `JavaPlugin` data directories.

---

## Target Layout

Canonical runtime root for a save-local install:

```text
<SaveRoot>/mods/Alechilles_Alec's Telemetry/
  Settings/
    runtime.json
    server-identity.json
    server-id.txt
    consent-reviewed-projects.json
    projects/<project-id>.json
  Telemetry/
    crash-reports/<project-id>/pending/
    events/<project-id>/pending/
    manual-reports/
      <project-id>/pending/
      <project-id>/review/
      <project-id>/rejected/
      submitted-reports.jsonl
      receipts.json
```

Canonical runtime root for a hosted Linux/server-root install such as BisectHosting:

```text
/home/container/mods/Alechilles_Alec's Telemetry/
```

Legacy central roots that must be imported if present:

```text
<SaveRoot>/telemetry/
<ServerRoot>/Telemetry/
```

Do not remove root-level Hytale logs:

```text
<SaveRoot>/telemetry/*.jsonl.gz
<ServerRoot>/telemetry/*.jsonl.gz
```

Those are Hytale session telemetry files, not Alec's Telemetry runtime queue files.

## File Structure

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryDataPaths.java`
  - Add constants and path helpers for the canonical telemetry plugin data directory.
  - Change shared coordinator path resolution from old central roots to `<Root>/mods/Alechilles_Alec's Telemetry`.
  - Expose legacy path candidates used by migration and fallback override reads.

- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\TelemetryDataMigrator.java`
  - Copy settings, project overrides, server identity, consent state, queues, and manual report data from old roots into the canonical root when the destination file is absent.
  - Clean old Alec-created central directories and embedded owner override folders when they become empty.
  - Never delete root-level lowercase telemetry log folders such as `<SaveRoot>/telemetry/*.jsonl.gz` or `<ServerRoot>/telemetry/*.jsonl.gz`.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\host\TelemetryRuntimeProviderHandle.java`
  - Run migration before `TelemetryRuntimeSettings.load(...)`.
  - Stop writing per-consumer embedded override mirrors in `applyLocalConsent(...)`.
  - Keep owner override reads as a legacy fallback for old worlds that have not yet migrated.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\main\java\com\alechilles\alecstelemetry\runtime\discovery\TelemetryRuntimeDiscovery.java`
  - Replace direct embedded owner override path construction with `TelemetryDataPaths.legacyEmbeddedProjectOverrideFile(...)`.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\runtime\TelemetryDataPathsTest.java`
  - Pin the new shared coordinator root.
  - Pin old root candidate behavior for migration.

- Create: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\runtime\TelemetryDataMigratorTest.java`
  - Cover central migration, embedded owner override import, non-overwrite behavior, cleanup, and Hytale log preservation.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\runtime\src\test\java\com\alechilles\alecstelemetry\runtime\host\TelemetryRuntimeHostTest.java`
  - Update the consent override mirror test so embedded consent writes only the canonical project override.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\embedded-mode.md`
  - Document the canonical data root and legacy migration behavior.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\docs\runtime-overrides.md`
  - Document the full runtime override path under `mods/Alechilles_Alec's Telemetry/Settings/projects`.

- Modify: `C:\Users\22ale\AppData\Roaming\Hytale\Modding\AlecsTelemetry\README.md`
  - Update the advanced options note that references `Settings/projects/<project-id>.json`.

## Task 1: Pin The New Shared Coordinator Root

**Files:**
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`

- [ ] **Step 1: Write the failing path test**

In `TelemetryDataPathsTest`, replace `sharedCoordinatorRootUsesGlobalUserDataTelemetryDirectory` with:

```java
@Test
void sharedCoordinatorRootUsesTelemetryPluginDataDirectoryInsideSaveMods() throws Exception {
    Path hytaleRoot = tempDir.resolve("Hytale");
    Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
            .resolve("Demo World")
            .resolve("mods")
            .resolve("Alechilles_Alec's Tamework!");
    Files.createDirectories(pluginDataDirectory);

    TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinatorDataDirectory(pluginDataDirectory);

    Path saveModsDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
            .resolve("Demo World")
            .resolve("mods")
            .toAbsolutePath()
            .normalize();
    Path expectedRoot = saveModsDirectory.resolve("Alechilles_Alec's Telemetry").toAbsolutePath().normalize();
    assertEquals(expectedRoot, paths.runtimeRoot());
    assertEquals(expectedRoot.resolve("Settings").resolve("runtime.json"), paths.settingsFile());
    assertEquals(expectedRoot.resolve("Settings").resolve("projects"), paths.projectSettingsDirectory());
    assertEquals(expectedRoot.resolve("Telemetry"), paths.telemetryRoot());
    assertEquals(saveModsDirectory, paths.modsDirectory());
}
```

Add this new legacy-root candidate test:

```java
@Test
void sharedCoordinatorExposesLegacyCentralRootsForMigration() throws Exception {
    Path hytaleRoot = tempDir.resolve("Hytale");
    Path pluginDataDirectory = hytaleRoot.resolve("UserData").resolve("Saves")
            .resolve("Demo World")
            .resolve("mods")
            .resolve("Alechilles_Alec's Tamework!");
    Files.createDirectories(pluginDataDirectory);

    TelemetryDataPaths paths = TelemetryDataPaths.forSharedCoordinatorDataDirectory(pluginDataDirectory);

    assertEquals(
            List.of(
                    hytaleRoot.resolve("UserData").resolve("Saves").resolve("Demo World").resolve("telemetry").toAbsolutePath().normalize(),
                    hytaleRoot.resolve("UserData").resolve("Saves").resolve("Demo World").resolve("Telemetry").toAbsolutePath().normalize()
            ),
            paths.legacyRuntimeRoots()
    );
}
```

- [ ] **Step 2: Run the path tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: the new shared root test fails because `forSharedCoordinatorDataDirectory(...)` still returns `<SaveRoot>/Telemetry` or `<UserData>/Telemetry`, and `legacyRuntimeRoots()` does not exist.

- [ ] **Step 3: Implement canonical path helpers**

In `TelemetryDataPaths.java`, add constants immediately after the record declaration:

```java
private static final String TELEMETRY_PLUGIN_DATA_DIRECTORY = "Alechilles_Alec's Telemetry";
```

Replace `forSharedCoordinatorDataDirectory(...)` with:

```java
@Nonnull
static TelemetryDataPaths forSharedCoordinatorDataDirectory(@Nonnull Path dataDirectory) {
    Path normalizedDataDirectory = dataDirectory.toAbsolutePath().normalize();
    Path modsDirectory = resolveModsDirectory(normalizedDataDirectory);
    Path runtimeRoot = modsDirectory == null
            ? normalizedDataDirectory.resolve("TelemetryCoordinator").toAbsolutePath().normalize()
            : modsDirectory.resolve(TELEMETRY_PLUGIN_DATA_DIRECTORY).toAbsolutePath().normalize();
    return fromRuntimeRoot(runtimeRoot, modsDirectory);
}
```

Add this helper below `forSharedCoordinatorDataDirectory(...)`:

```java
@Nonnull
private static TelemetryDataPaths fromRuntimeRoot(@Nonnull Path runtimeRoot, @Nullable Path modsDirectory) {
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

Simplify `from(...)` to use the helper:

```java
@Nonnull
public static TelemetryDataPaths from(@Nonnull JavaPlugin plugin) {
    Path dataDirectory = plugin.getDataDirectory().toAbsolutePath().normalize();
    return fromRuntimeRoot(dataDirectory, resolveModsDirectory(dataDirectory));
}
```

Add this legacy root method below `projectOverrideFile(...)`:

```java
@Nonnull
public List<Path> legacyRuntimeRoots() {
    ArrayList<Path> roots = new ArrayList<>();
    if (modsDirectory == null) {
        return List.of();
    }
    Path ownerRoot = modsDirectory.getParent();
    if (ownerRoot == null) {
        return List.of();
    }
    addLegacyRuntimeRoot(roots, ownerRoot.resolve("telemetry"));
    addLegacyRuntimeRoot(roots, ownerRoot.resolve("Telemetry"));
    return List.copyOf(roots);
}

private void addLegacyRuntimeRoot(@Nonnull ArrayList<Path> roots, @Nullable Path root) {
    if (root == null) {
        return;
    }
    Path normalized = root.toAbsolutePath().normalize();
    if (normalized.equals(runtimeRoot)) {
        return;
    }
    for (Path existing : roots) {
        if (existing.equals(normalized)) {
            return;
        }
    }
    roots.add(normalized);
}
```

- [ ] **Step 4: Run the path tests and verify pass**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: all `TelemetryDataPathsTest` tests pass.

- [ ] **Step 5: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java
git commit -m "Fix: move shared telemetry root under telemetry plugin data"
```

## Task 2: Add One-Time Data Migration And Cleanup

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataMigrator.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataMigratorTest.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`

- [ ] **Step 1: Write migration tests**

Create `TelemetryDataMigratorTest.java`:

```java
package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryDataMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyLowercaseCentralSettingsAndQueuesWithoutDeletingHytaleSessionLogs() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("server-id.txt"), "legacy-server");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json"), "{}");
        Files.writeString(legacyRoot.resolve("2026-06-16_01-49-25_a109ad9b.jsonl.gz"), "hytale-log");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("legacy-server", Files.readString(canonicalRoot.resolve("Settings").resolve("server-id.txt")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-cats").resolve("pending").resolve("event.json")));
        assertTrue(Files.isRegularFile(legacyRoot.resolve("2026-06-16_01-49-25_a109ad9b.jsonl.gz")));
        assertFalse(Files.exists(legacyRoot.resolve("Settings")));
        assertFalse(Files.exists(legacyRoot.resolve("Telemetry")));
        assertTrue(Files.isDirectory(legacyRoot));
    }

    @Test
    void migratesLegacyUppercaseServerRootTelemetryDirectory() throws Exception {
        Path serverRoot = tempDir.resolve("container");
        Path modsDir = serverRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = serverRoot.resolve("Telemetry");
        Files.createDirectories(legacyRoot.resolve("Settings").resolve("projects"));
        Files.createDirectories(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"flushIntervalSeconds\":99}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json"), "{\"enabled\":false}");
        Files.writeString(legacyRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json"), "{}");
        Files.createDirectories(serverRoot.resolve("telemetry"));
        Files.writeString(serverRoot.resolve("telemetry").resolve("2026-06-20_12-58-00_server.jsonl.gz"), "hytale-log");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"flushIntervalSeconds\":99}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertEquals("{}", Files.readString(canonicalRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending").resolve("event.json")));
        assertFalse(Files.exists(legacyRoot));
        assertTrue(Files.isRegularFile(serverRoot.resolve("telemetry").resolve("2026-06-20_12-58-00_server.jsonl.gz")));
    }

    @Test
    void doesNotOverwriteCanonicalFiles() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path legacyRoot = saveRoot.resolve("telemetry");
        Files.createDirectories(canonicalRoot.resolve("Settings"));
        Files.createDirectories(legacyRoot.resolve("Settings"));
        Files.writeString(canonicalRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":true}");
        Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), "{\"enabled\":false}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":true}", Files.readString(canonicalRoot.resolve("Settings").resolve("runtime.json")));
        assertEquals("{\"enabled\":false}", Files.readString(legacyRoot.resolve("Settings").resolve("runtime.json")));
    }

    @Test
    void migratesEmbeddedOwnerOverridesAndRemovesEmptyTelemetryFolders() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Cats!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-cats.json");
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-cats.json")));
        assertFalse(Files.exists(ownerRoot.resolve("Telemetry")));
        assertTrue(Files.isDirectory(ownerRoot));
    }

    @Test
    void keepsEmbeddedOwnerTelemetryFolderWhenItContainsNonSettingsData() throws Exception {
        Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
        Path modsDir = saveRoot.resolve("mods");
        Path canonicalRoot = modsDir.resolve("Alechilles_Alec's Telemetry");
        Path ownerRoot = modsDir.resolve("Alechilles_Alec's Tamework!");
        Path ownerOverride = ownerRoot.resolve("Telemetry").resolve("Settings").resolve("projects").resolve("alecs-tamework.json");
        Files.createDirectories(ownerOverride.getParent());
        Files.writeString(ownerOverride, "{\"enabled\":false}");
        Files.createDirectories(ownerRoot.resolve("Telemetry").resolve("events").resolve("alecs-tamework").resolve("pending"));
        TelemetryDataPaths paths = paths(canonicalRoot, modsDir);

        TelemetryDataMigrator.migrate(paths, null);

        assertEquals("{\"enabled\":false}", Files.readString(canonicalRoot.resolve("Settings").resolve("projects").resolve("alecs-tamework.json")));
        assertTrue(Files.isDirectory(ownerRoot.resolve("Telemetry").resolve("events")));
        assertFalse(Files.exists(ownerOverride));
    }

    private static TelemetryDataPaths paths(Path root, Path modsDir) {
        Path settingsRoot = root.resolve("Settings");
        Path telemetryRoot = root.resolve("Telemetry");
        return new TelemetryDataPaths(
                root,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                modsDir
        );
    }
}
```

- [ ] **Step 2: Run migration tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataMigratorTest test
```

Expected: compilation fails because `TelemetryDataMigrator` does not exist.

- [ ] **Step 3: Add legacy embedded override path helper**

In `TelemetryDataPaths.java`, add this public helper below `legacyRuntimeRoots()`:

```java
@Nullable
public Path legacyEmbeddedProjectOverrideFile(@Nonnull String pluginIdentifier, @Nonnull String projectId) {
    if (modsDirectory == null) {
        return null;
    }
    return modsDirectory
            .resolve(sanitizePluginDataDirectory(pluginIdentifier))
            .resolve("Telemetry")
            .resolve("Settings")
            .resolve("projects")
            .resolve(projectId + ".json");
}

@Nonnull
public static String sanitizePluginDataDirectory(@Nonnull String pluginIdentifier) {
    return pluginIdentifier.trim().replace(':', '_');
}
```

- [ ] **Step 4: Implement migrator**

Create `TelemetryDataMigrator.java`:

```java
package com.alechilles.alecstelemetry.runtime;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * One-time filesystem migration for older Alec's Telemetry runtime data roots.
 */
public final class TelemetryDataMigrator {

    private TelemetryDataMigrator() {
    }

    public static void migrate(@Nonnull TelemetryDataPaths paths, @Nullable HytaleLogger logger) {
        migrateLegacyRuntimeRoots(paths, logger);
        migrateEmbeddedOwnerOverrides(paths, logger);
    }

    private static void migrateLegacyRuntimeRoots(@Nonnull TelemetryDataPaths paths, @Nullable HytaleLogger logger) {
        for (Path legacyRoot : paths.legacyRuntimeRoots()) {
            copyKnownTree(legacyRoot.resolve("Settings"), paths.runtimeRoot().resolve("Settings"), logger);
            copyKnownTree(legacyRoot.resolve("Telemetry"), paths.runtimeRoot().resolve("Telemetry"), logger);
            deleteIfEmpty(legacyRoot.resolve("Settings"), logger);
            deleteIfEmpty(legacyRoot.resolve("Telemetry"), logger);
            deleteIfEmpty(legacyRoot, logger);
        }
    }

    private static void migrateEmbeddedOwnerOverrides(@Nonnull TelemetryDataPaths paths, @Nullable HytaleLogger logger) {
        Path modsDirectory = paths.modsDirectory();
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return;
        }
        try (DirectoryStream<Path> mods = Files.newDirectoryStream(modsDirectory)) {
            for (Path modRoot : mods) {
                if (!Files.isDirectory(modRoot) || modRoot.equals(paths.runtimeRoot())) {
                    continue;
                }
                Path ownerProjects = modRoot.resolve("Telemetry").resolve("Settings").resolve("projects");
                if (!Files.isDirectory(ownerProjects)) {
                    continue;
                }
                copyKnownTree(ownerProjects, paths.projectSettingsDirectory(), logger);
                deleteIfEmpty(ownerProjects, logger);
                deleteIfEmpty(ownerProjects.getParent(), logger);
                deleteIfEmpty(ownerProjects.getParent().getParent(), logger);
            }
        } catch (Exception ex) {
            warn(logger, "Failed to migrate embedded telemetry owner override folders from " + modsDirectory, ex);
        }
    }

    private static void copyKnownTree(@Nonnull Path source, @Nonnull Path destination, @Nullable HytaleLogger logger) {
        if (!Files.exists(source)) {
            return;
        }
        try {
            if (Files.isRegularFile(source)) {
                copyFileIfMissing(source, destination, logger);
                return;
            }
            if (!Files.isDirectory(source)) {
                return;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyKnownTree(child, destination.resolve(child.getFileName().toString()), logger);
                }
            }
        } catch (Exception ex) {
            warn(logger, "Failed to migrate telemetry data from " + source + " to " + destination, ex);
        }
    }

    private static void copyFileIfMissing(@Nonnull Path source, @Nonnull Path destination, @Nullable HytaleLogger logger) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            Files.delete(source);
        } catch (Exception ex) {
            warn(logger, "Copied telemetry data but could not remove old file " + source, ex);
        }
    }

    private static void deleteIfEmpty(@Nullable Path directory, @Nullable HytaleLogger logger) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            if (stream.iterator().hasNext()) {
                return;
            }
        } catch (Exception ex) {
            warn(logger, "Failed to check whether old telemetry directory is empty: " + directory, ex);
            return;
        }
        try {
            Files.deleteIfExists(directory);
        } catch (Exception ex) {
            warn(logger, "Failed to remove empty old telemetry directory " + directory, ex);
        }
    }

    private static void warn(@Nullable HytaleLogger logger, @Nonnull String message, @Nullable Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.at(Level.WARNING).log(message);
            return;
        }
        logger.at(Level.WARNING).withCause(throwable).log(message);
    }
}
```

- [ ] **Step 5: Run migration tests and verify pass**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataMigratorTest,TelemetryDataPathsTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataMigrator.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataMigratorTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java
git commit -m "Feat: migrate telemetry data into canonical runtime root"
```

## Task 3: Wire Migration Into Runtime Startup

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`

- [ ] **Step 1: Write startup settings migration test**

In `TelemetryRuntimeHostTest.java`, add this test near the existing provider creation tests:

```java
@Test
void loadMigratedSettingsMigratesBeforeReadingRuntimeJson() throws Exception {
    Path saveRoot = tempDir.resolve("Saves").resolve("Update5Test");
    Path legacyRoot = saveRoot.resolve("Telemetry");
    Files.createDirectories(legacyRoot.resolve("Settings"));
    Files.writeString(legacyRoot.resolve("Settings").resolve("runtime.json"), """
            {
              "enabled": false,
              "flushIntervalSeconds": 99
            }
            """);
    TelemetryDataPaths paths = dataPaths(saveRoot.resolve("mods").resolve("Alechilles_Alec's Telemetry"), saveRoot.resolve("mods"));

    TelemetryRuntimeSettings settings = TelemetryRuntimeProviderHandle.loadMigratedSettings(paths, null);

    assertFalse(settings.enabled());
    assertEquals(99, settings.flushIntervalSeconds());
    assertEquals("""
            {
              "enabled": false,
              "flushIntervalSeconds": 99
            }
            """, Files.readString(paths.settingsFile()));
    assertFalse(Files.exists(legacyRoot.resolve("Settings")));
}
```

- [ ] **Step 2: Run startup migration test and verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest#loadMigratedSettingsMigratesBeforeReadingRuntimeJson test
```

Expected: compilation fails because `TelemetryRuntimeProviderHandle.loadMigratedSettings(...)` does not exist.

- [ ] **Step 3: Add migrated settings helper and call it from startup**

In `TelemetryRuntimeProviderHandle.java`, add this package-private helper below `create(...)`:

```java
@Nonnull
static TelemetryRuntimeSettings loadMigratedSettings(@Nonnull TelemetryDataPaths dataPaths, @Nullable HytaleLogger logger) {
    TelemetryDataMigrator.migrate(dataPaths, logger);
    return TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
}
```

In `TelemetryRuntimeProviderHandle.create(...)`, change:

```java
TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
```

to:

```java
TelemetryRuntimeSettings settings = loadMigratedSettings(dataPaths, logger);
```

Add the import:

```java
import com.alechilles.alecstelemetry.runtime.TelemetryDataMigrator;
```

- [ ] **Step 4: Run startup migration test and verify pass**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest#loadMigratedSettingsMigratesBeforeReadingRuntimeJson test
```

Expected: test passes.

- [ ] **Step 5: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java
git commit -m "Fix: migrate telemetry data before runtime settings load"
```

## Task 4: Stop Writing Embedded Owner Override Mirrors

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java`

- [ ] **Step 1: Update consent write test**

In `TelemetryRuntimeHostTest.java`, rename `applyConsentWritesCentralAndEmbeddedOwnerOverrides` to:

```java
@Test
void applyConsentWritesOnlyCanonicalOverrideForEmbeddedProjects() throws Exception {
```

Within that test, keep the central override assertions and replace embedded override assertions with:

```java
Path embeddedOverrideFile = modsDir
        .resolve("Example_Embedded Mod")
        .resolve("Telemetry")
        .resolve("Settings")
        .resolve("projects")
        .resolve("embedded-mod.json");
assertFalse(Files.exists(embeddedOverrideFile));
```

- [ ] **Step 2: Add legacy fallback read test**

Add this test to `TelemetryRuntimeDiscoveryTest.java`:

```java
@Test
void loadConsentOverridesReadsLegacyEmbeddedOwnerOverrideWhenCanonicalMissing() throws Exception {
    Path modsDirectory = tempDir.resolve("mods");
    Path ownerOverride = modsDirectory.resolve("Example_Embedded Mod")
            .resolve("Telemetry")
            .resolve("Settings")
            .resolve("projects")
            .resolve("embedded-project.json");
    Files.createDirectories(ownerOverride.getParent());
    Files.writeString(ownerOverride, "{\"enabled\":false}");
    TelemetryProjectRegistration project = project("embedded-project", "Example:Embedded Mod", "1.0.0");
    TelemetryDataPaths dataPaths = dataPaths(modsDirectory);
    TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery.DiscoveryResult(
            List.of(),
            List.of(project),
            List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
            List.of()
    );

    TelemetryRuntimeDiscoveryResult result = new TelemetryRuntimeDiscovery(null)
            .discoverActive(dataPaths, discoveryResult);

    assertEquals(1, result.consentProjects().size());
    assertFalse(result.consentProjects().getFirst().enabled());
}
```

Use existing helper constructors in `TelemetryRuntimeDiscoveryTest` if names differ; the assertion must prove legacy embedded owner overrides are read when canonical overrides do not exist.

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest#applyConsentWritesOnlyCanonicalOverrideForEmbeddedProjects,TelemetryRuntimeDiscoveryTest#loadConsentOverridesReadsLegacyEmbeddedOwnerOverrideWhenCanonicalMissing test
```

Expected: host test fails because `applyLocalConsent(...)` still writes the embedded owner mirror; fallback read may already pass.

- [ ] **Step 4: Stop embedded mirror writes**

In `TelemetryRuntimeProviderHandle.applyLocalConsent(...)`, remove this block:

```java
Path embeddedOverrideFile = embeddedProjectOverrideFile(project);
if (embeddedOverrideFile != null) {
    saved = saved && saveConsentOverride(embeddedOverrideFile, normalized);
}
```

In `TelemetryRuntimeProviderHandle.embeddedProviderOverride(...)`, replace:

```java
return store.load(TelemetryDataPaths.forEmbeddedOwner(request.plugin()).projectOverrideFile(projectId));
```

with:

```java
Path legacyOverrideFile = dataPaths.legacyEmbeddedProjectOverrideFile(request.providerPluginIdentifier(), projectId);
return legacyOverrideFile == null ? null : store.load(legacyOverrideFile);
```

Remove the private `embeddedProjectOverrideFile(...)` helper from `TelemetryRuntimeProviderHandle` if it becomes unused.

- [ ] **Step 5: Route discovery fallback through TelemetryDataPaths**

In `TelemetryRuntimeDiscovery.embeddedProjectOverrideFile(...)`, replace the method body with:

```java
return dataPaths.legacyEmbeddedProjectOverrideFile(project.pluginIdentifier(), project.projectId());
```

Remove `sanitizePluginDataDirectory(...)` from `TelemetryRuntimeDiscovery` because `TelemetryDataPaths.sanitizePluginDataDirectory(...)` owns that rule.

- [ ] **Step 6: Run tests and verify pass**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryRuntimeHostTest#applyConsentWritesOnlyCanonicalOverrideForEmbeddedProjects,TelemetryRuntimeDiscoveryTest#loadConsentOverridesReadsLegacyEmbeddedOwnerOverrideWhenCanonicalMissing test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java
git commit -m "Fix: stop mirroring consent overrides into consumer mod folders"
```

## Task 5: Update Documentation For The New Root

**Files:**
- Modify: `README.md`
- Modify: `docs/embedded-mode.md`
- Modify: `docs/runtime-overrides.md`

- [ ] **Step 1: Update runtime override docs**

In `docs/runtime-overrides.md`, replace:

```text
Override files live under Alec's Telemetry data directory:

```text
Settings/projects/<project-id>.json
```
```

with:

```text
Override files live under the active Alec's Telemetry runtime data directory. In a save-local Hytale world, the canonical path is:

```text
<SaveRoot>/mods/Alechilles_Alec's Telemetry/Settings/projects/<project-id>.json
```

Older releases also read legacy files from `<SaveRoot>/telemetry/Settings/projects`, `<ServerRoot>/Telemetry/Settings/projects`, and `<SaveRoot>/mods/<ConsumerMod>/Telemetry/Settings/projects` during migration. New writes use the canonical path only.
```

Replace:

```text
Global runtime settings live in `Settings/runtime.json`, not per-project override
files. The runtime creates this file with defaults the first time it starts.
```

with:

```text
Global runtime settings live in `<SaveRoot>/mods/Alechilles_Alec's Telemetry/Settings/runtime.json` or `<ServerRoot>/mods/Alechilles_Alec's Telemetry/Settings/runtime.json`, not per-project override files. The runtime creates this file with defaults the first time it starts.
```

- [ ] **Step 2: Update embedded mode docs**

In `docs/embedded-mode.md`, replace the entire `## Storage Layout` section with:

```markdown
## Storage Layout

Standalone and embedded runtime providers share one canonical runtime data root per save:

```text
<SaveRoot>/mods/Alechilles_Alec's Telemetry/
<ServerRoot>/mods/Alechilles_Alec's Telemetry/
```

That root stores runtime settings, project overrides, consent review state, server identity, local queues, and manual report files:

```text
<SaveRoot>/mods/Alechilles_Alec's Telemetry/Settings/
<SaveRoot>/mods/Alechilles_Alec's Telemetry/Telemetry/
<ServerRoot>/mods/Alechilles_Alec's Telemetry/Settings/
<ServerRoot>/mods/Alechilles_Alec's Telemetry/Telemetry/
```

Older embedded releases wrote owner override mirrors under:

```text
<ConsumerModDataDir>/Telemetry/Settings/projects/
```

Older hosted Linux/server-root installs can also have the Alec-owned runtime root here:

```text
<ServerRoot>/Telemetry/
```

The current runtime imports those files into the canonical root during startup and writes new consent changes only to the canonical root. Hytale's own root-level lowercase `<SaveRoot>/telemetry/*.jsonl.gz` and `<ServerRoot>/telemetry/*.jsonl.gz` session logs are not Alec's Telemetry queue files and are left alone.
```

- [ ] **Step 3: Update README advanced options**

In `README.md`, replace:

```text
- Server owners can override packaged destination settings at runtime through
  `Settings/projects/<project-id>.json`.
```

with:

```text
- Server owners can override packaged destination settings at runtime through
  `<SaveRoot>/mods/Alechilles_Alec's Telemetry/Settings/projects/<project-id>.json`
  or `<ServerRoot>/mods/Alechilles_Alec's Telemetry/Settings/projects/<project-id>.json`.
```

- [ ] **Step 4: Run docs grep verification**

Run:

```powershell
rg -n "<HytaleUserData>/Telemetry|<ConsumerModDataDir>/Telemetry|Settings/projects/<project-id>\\.json|UserData\\\\Telemetry|UserData/Telemetry|<ServerRoot>/Telemetry" README.md docs wiki
```

Expected: remaining matches are either updated canonical paths, examples explicitly labeled as legacy migration paths, or unrelated descriptor packaging references.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/embedded-mode.md docs/runtime-overrides.md
git commit -m "Docs: document canonical telemetry runtime data root"
```

## Task 6: Full Runtime Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run focused runtime tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest,TelemetryDataMigratorTest,TelemetryRuntimeHostTest,TelemetryRuntimeDiscoveryTest test
```

Expected: all focused runtime tests pass.

- [ ] **Step 2: Run full runtime module tests**

Run:

```powershell
.\mvnw.cmd -pl runtime test
```

Expected: all runtime tests pass.

- [ ] **Step 3: Run full project tests if runtime module passes**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all Maven tests pass.

- [ ] **Step 4: Commit verification-only docs if any test fixtures were corrected**

If no files changed during verification, skip this commit. If fixture corrections were required, inspect the status and stage only the concrete runtime test files touched by those corrections:

```powershell
git status --short
git add runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataMigratorTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHostTest.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscoveryTest.java
git commit -m "Test: align telemetry data root fixtures"
```

## Task 7: Manual Update5Test Migration Verification

**Files:**
- Runtime save data only, under `C:\Users\22ale\AppData\Roaming\Hytale\UserData\Saves\Update5Test`.
- Do not stage or commit save data.

- [ ] **Step 1: Capture before snapshot**

Run:

```powershell
$save = 'C:\Users\22ale\AppData\Roaming\Hytale\UserData\Saves\Update5Test'
Get-ChildItem -LiteralPath $save -Directory | Select-Object FullName
Get-ChildItem -LiteralPath "$save\telemetry" -Force -ErrorAction SilentlyContinue | Select-Object FullName,Mode,Length
Get-ChildItem -LiteralPath "$save\Telemetry" -Force -ErrorAction SilentlyContinue | Select-Object FullName,Mode,Length
Get-ChildItem -LiteralPath "$save\mods" -Directory -Filter 'Alechilles_*' | ForEach-Object {
    [PSCustomObject]@{
        Name = $_.Name
        Items = (Get-ChildItem -LiteralPath $_.FullName -Force -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count
    }
} | Format-Table -AutoSize
```

Expected: the old clutter exists before running the fixed runtime.

- [ ] **Step 2: Install or run the newly built telemetry runtime**

Use the repo's existing packaging/install workflow for local Hytale testing. If the current repo has a release package script, run it from the repo root and install the built jar into the Hytale mod location used by `Update5Test`.

Verification command before launching Hytale:

```powershell
.\mvnw.cmd -pl standalone package
```

Expected: standalone package build succeeds and produces an updated Alec's Telemetry jar.

- [ ] **Step 3: Start Update5Test once and let telemetry initialize**

Launch the `Update5Test` world with the updated runtime installed. Wait until the server reaches a stable ready state and telemetry commands are available.

In game or server console, run:

```text
/telemetry status
/telemetry projects
```

Expected: commands work, the active coordinator is present, and projects are discovered.

- [ ] **Step 4: Capture after snapshot**

Run:

```powershell
$save = 'C:\Users\22ale\AppData\Roaming\Hytale\UserData\Saves\Update5Test'
$canonical = "$save\mods\Alechilles_Alec's Telemetry"
Get-ChildItem -LiteralPath "$canonical\Settings" -Recurse -Force | Select-Object FullName,Mode,Length
Get-ChildItem -LiteralPath "$canonical\Telemetry" -Recurse -Force | Select-Object -First 80 FullName,Mode,Length
Get-ChildItem -LiteralPath "$save\telemetry" -Force -ErrorAction SilentlyContinue | Select-Object FullName,Mode,Length
Get-ChildItem -LiteralPath "$save\Telemetry" -Force -ErrorAction SilentlyContinue | Select-Object FullName,Mode,Length
Get-ChildItem -LiteralPath "$save\mods" -Directory -Filter 'Alechilles_*' | ForEach-Object {
    [PSCustomObject]@{
        Name = $_.Name
        TelemetryExists = Test-Path -LiteralPath ($_.FullName + '\Telemetry')
        ItemCount = (Get-ChildItem -LiteralPath $_.FullName -Force -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count
    }
} | Format-Table -AutoSize
```

Expected:
- `mods\Alechilles_Alec's Telemetry\Settings` contains runtime settings, identity, consent state, and project overrides.
- `mods\Alechilles_Alec's Telemetry\Telemetry` contains queues/manual report folders.
- `telemetry\Settings` and `telemetry\Telemetry` are gone if fully migrated.
- `Telemetry` is gone if an uppercase Alec-owned central root existed in the save.
- `telemetry\*.jsonl.gz` files remain.
- Consumer mod folders no longer contain `Telemetry\Settings\projects` if those folders were empty after migration.
- Tamework-owned non-telemetry folders such as `AssetPatchSelfTestPack` and `GeneratedPatches` remain untouched.

- [ ] **Step 5: Commit no files from save data**

Run:

```powershell
git status --short
```

Expected: only repo source/docs/test changes are present. Save data under `UserData\Saves\Update5Test` is outside the repo and must not be staged.

## Task 8: Manual Hosted Linux Server-Root Migration Verification

**Files:**
- Runtime server data only, under `/home/container` on the hosted server.
- Do not stage or commit server data.

- [ ] **Step 1: Capture before snapshot**

Run through SFTP/console on the hosted server:

```bash
cd /home/container
find . -maxdepth 2 \( -path './Telemetry' -o -path './Telemetry/*' -o -path './telemetry' -o -path './telemetry/*' -o -path "./mods/Alechilles_Alec's Telemetry" -o -path "./mods/Alechilles_Alec's Telemetry/*" \) -print | sort
```

Expected before migration, based on the BisectHosting screenshot:
- `/home/container/Telemetry/Settings` exists.
- `/home/container/Telemetry/Telemetry` exists.
- `/home/container/telemetry` may also exist and must be treated as Hytale-owned lowercase telemetry/log storage.
- `/home/container/mods/Alechilles_Alec's Telemetry` may be missing or may contain older standalone data.

- [ ] **Step 2: Install or run the newly built telemetry runtime**

Install the built Alec's Telemetry jar on the hosted server using the same deployment path used for the current server.

Verification command before upload, from the local repo:

```powershell
.\mvnw.cmd -pl standalone package
```

Expected: standalone package build succeeds and produces an updated Alec's Telemetry jar.

- [ ] **Step 3: Start the hosted server once and let telemetry initialize**

Start the hosted Hytale server from the BisectHosting panel or server console. Wait until the server reaches a stable ready state and telemetry commands are available.

In game or server console, run:

```text
/telemetry status
/telemetry projects
```

Expected: commands work, the active coordinator is present, and projects are discovered.

- [ ] **Step 4: Capture after snapshot**

Run through SFTP/console on the hosted server:

```bash
cd /home/container
find "./mods/Alechilles_Alec's Telemetry" -maxdepth 4 -print | sort
find ./Telemetry -maxdepth 4 -print 2>/dev/null | sort
find ./telemetry -maxdepth 2 -print 2>/dev/null | sort
```

Expected:
- `/home/container/mods/Alechilles_Alec's Telemetry/Settings` contains runtime settings, identity, consent state, and project overrides.
- `/home/container/mods/Alechilles_Alec's Telemetry/Telemetry` contains queues/manual report folders.
- `/home/container/Telemetry` is gone if its `Settings` and nested `Telemetry` data were fully migrated and no unrelated files remained.
- `/home/container/telemetry` remains if Hytale is still using it for lowercase session telemetry/log storage.
- Consumer mod folders no longer contain `Telemetry/Settings/projects` if those folders were empty after migration.

- [ ] **Step 5: Commit no files from server data**

Run locally:

```powershell
git status --short
```

Expected: only repo source/docs/test changes are present. Hosted `/home/container` server data is outside the repo and must not be staged.

## Self-Review

- Spec coverage: The plan covers choosing `mods/Alechilles_Alec's Telemetry` as the canonical root, one-time migration from `Saves/<world>/telemetry`, one-time migration from hosted Linux/server-root `<ServerRoot>/Telemetry`, one-time migration from per-consumer embedded owner override folders, cleanup of old Alec-created clutter, Hytale lowercase root session-log preservation, tests, docs, manual Update5Test verification, and manual hosted Linux verification.
- Placeholder scan: The plan contains no deferred implementation slots. Each code task includes exact file paths, concrete snippets, commands, and expected results.
- Type consistency: New methods are consistently named `legacyRuntimeRoots()`, `legacyEmbeddedProjectOverrideFile(...)`, and `TelemetryDataMigrator.migrate(...)`. The same names are used in tests and implementation steps.
