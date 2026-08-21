package com.alechilles.alecstelemetry.runtime;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-owned filesystem layout for Alec's Telemetry.
 */
public record TelemetryDataPaths(@Nonnull Path runtimeRoot,
                                  @Nonnull Path settingsFile,
                                  @Nonnull Path projectSettingsDirectory,
                                  @Nonnull Path telemetryRoot,
                                  @Nonnull Path crashReportsRoot,
                                  @Nonnull Path eventReportsRoot,
                                  @Nullable Path modsDirectory) {
    private static final String TELEMETRY_PLUGIN_DATA_DIRECTORY = "Alechilles_Alec's Telemetry!";
    private static final String LEGACY_TELEMETRY_PLUGIN_DATA_DIRECTORY = "Alechilles_Alec's Telemetry";

    @Nonnull
    public static TelemetryDataPaths from(@Nonnull JavaPlugin plugin) {
        Path dataDirectory = plugin.getDataDirectory().toAbsolutePath().normalize();
        return fromRuntimeRoot(dataDirectory, resolveModsDirectory(dataDirectory));
    }

    @Nonnull
    public static TelemetryDataPaths forEmbeddedOwner(@Nonnull JavaPlugin plugin) {
        Path dataDirectory = plugin.getDataDirectory().toAbsolutePath().normalize();
        Path telemetryRoot = dataDirectory.resolve("Telemetry");
        Path settingsRoot = telemetryRoot.resolve("Settings");
        return new TelemetryDataPaths(
                telemetryRoot,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
    }

    @Nonnull
    public static TelemetryDataPaths forSharedCoordinator(@Nonnull JavaPlugin plugin) {
        return forSharedCoordinatorDataDirectory(plugin.getDataDirectory());
    }

    @Nonnull
    static TelemetryDataPaths forSharedCoordinatorDataDirectory(@Nonnull Path dataDirectory) {
        Path normalizedDataDirectory = dataDirectory.toAbsolutePath().normalize();
        Path modsDirectory = resolveModsDirectory(normalizedDataDirectory);
        Path runtimeRoot = modsDirectory == null
                ? normalizedDataDirectory.resolve("TelemetryCoordinator").toAbsolutePath().normalize()
                : modsDirectory.resolve(TELEMETRY_PLUGIN_DATA_DIRECTORY).toAbsolutePath().normalize();
        return fromRuntimeRoot(runtimeRoot, modsDirectory);
    }

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

    @Nonnull
    public Path pendingDirectory(@Nonnull String projectId) {
        return crashReportsRoot.resolve(projectId).resolve("pending");
    }

    @Nonnull
    public Path pendingEventsDirectory(@Nonnull String projectId) {
        return eventReportsRoot.resolve(projectId).resolve("pending");
    }

    @Nonnull
    public Path manualReportsRoot() {
        return telemetryRoot.resolve("manual-reports");
    }

    @Nonnull
    public Path pendingManualReportsDirectory(@Nonnull String projectId) {
        return manualReportsRoot().resolve(projectId).resolve("pending");
    }

    @Nonnull
    public Path reviewManualReportsDirectory(@Nonnull String projectId) {
        return manualReportsRoot().resolve(projectId).resolve("review");
    }

    @Nonnull
    public Path rejectedManualReportsDirectory(@Nonnull String projectId) {
        return manualReportsRoot().resolve(projectId).resolve("rejected");
    }

    @Nonnull
    public Path submittedManualReportsLog() {
        return manualReportsRoot().resolve("submitted-reports.jsonl");
    }

    @Nonnull
    public Path manualReportReceiptsFile() {
        return manualReportsRoot().resolve("receipts.json");
    }

    @Nonnull
    public Path serverIdentityFile() {
        return settingsFile.getParent().resolve("server-identity.json");
    }

    @Nonnull
    public Path serverIdFile() {
        return settingsFile.getParent().resolve("server-id.txt");
    }

    @Nonnull
    public Path consentStateFile() {
        return settingsFile.getParent().resolve("consent-reviewed-projects.json");
    }

    @Nonnull
    public Path projectOverrideFile(@Nonnull String projectId) {
        return projectSettingsDirectory.resolve(projectId + ".json");
    }

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
        addLegacyRuntimeRoot(roots, modsDirectory.resolve(LEGACY_TELEMETRY_PLUGIN_DATA_DIRECTORY));
        addLegacyRuntimeRoot(roots, ownerRoot.resolve("telemetry"));
        addLegacyRuntimeRoot(roots, ownerRoot.resolve("Telemetry"));
        return List.copyOf(roots);
    }

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

    @Nonnull
    public List<Path> descriptorDirectories() {
        ArrayList<Path> directories = new ArrayList<>();
        addDirectory(directories, modsDirectory);
        addDirectory(directories, resolveInstalledModsDirectory(modsDirectory));
        return List.copyOf(directories);
    }

    @Nullable
    private static Path resolveInstalledModsDirectory(@Nullable Path modsDirectory) {
        if (modsDirectory == null) {
            return null;
        }
        Path saveDirectory = modsDirectory.getParent();
        Path savesDirectory = saveDirectory == null ? null : saveDirectory.getParent();
        if (savesDirectory == null
                || savesDirectory.getFileName() == null
                || !"saves".equalsIgnoreCase(savesDirectory.getFileName().toString())) {
            return null;
        }
        Path userDataDirectory = savesDirectory.getParent();
        return userDataDirectory == null ? null : userDataDirectory.resolve("Mods");
    }

    @Nullable
    private static Path resolveModsDirectory(@Nonnull Path dataDirectory) {
        Path current = dataDirectory;
        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null && "mods".equalsIgnoreCase(fileName.toString())) {
                return current;
            }
            current = current.getParent();
        }
        Path parent = dataDirectory.getParent();
        return parent == null ? null : parent.toAbsolutePath().normalize();
    }

    private static void addDirectory(@Nonnull ArrayList<Path> directories, @Nullable Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        for (Path existing : directories) {
            if (existing.equals(normalized)) {
                return;
            }
        }
        directories.add(normalized);
    }

    private void addLegacyRuntimeRoot(@Nonnull ArrayList<Path> roots, @Nullable Path root) {
        if (root == null) {
            return;
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (normalized.toString().equals(runtimeRoot.toString())) {
            return;
        }
        for (Path existing : roots) {
            if (existing.toString().equals(normalized.toString())) {
                return;
            }
        }
        roots.add(normalized);
    }
}
