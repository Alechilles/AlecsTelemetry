package com.alechilles.alecstelemetry.runtime;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.stream.Stream;

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
            pruneEmptyDirectories(legacyRoot.resolve("Settings"), logger);
            pruneEmptyDirectories(legacyRoot.resolve("Telemetry"), logger);
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
                if (!Files.isDirectory(modRoot) || modRoot.toAbsolutePath().normalize().equals(paths.runtimeRoot())) {
                    continue;
                }
                Path ownerProjects = modRoot.resolve("Telemetry").resolve("Settings").resolve("projects");
                if (!Files.isDirectory(ownerProjects)) {
                    continue;
                }
                copyKnownTree(ownerProjects, paths.projectSettingsDirectory(), logger);
                pruneEmptyDirectories(ownerProjects, logger);
                pruneEmptyDirectories(modRoot.resolve("Telemetry"), logger);
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

    private static void copyFileIfMissing(
            @Nonnull Path source,
            @Nonnull Path destination,
            @Nullable HytaleLogger logger) throws IOException {
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

    private static void pruneEmptyDirectories(@Nonnull Path root, @Nullable HytaleLogger logger) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> deleteIfEmpty(path, logger));
        } catch (Exception ex) {
            warn(logger, "Failed to prune empty old telemetry directories under " + root, ex);
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
