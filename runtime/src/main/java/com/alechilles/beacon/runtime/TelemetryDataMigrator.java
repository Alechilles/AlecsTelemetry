package com.alechilles.beacon.runtime;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * One-time, copy-only filesystem migration for older Alec's Telemetry runtime data roots.
 */
public final class TelemetryDataMigrator {
    private static final int MAX_WARNINGS = 32;
    private static final Set<Path> ATTEMPTED_ROOTS = ConcurrentHashMap.newKeySet();

    private TelemetryDataMigrator() {
    }

    /**
     * Copies known runtime-owned state into the Beacon root. Legacy files and directories are
     * deliberately retained so that operators can recover or downgrade during the transition.
     */
    public static synchronized void migrate(@Nonnull TelemetryDataPaths paths,
                                             @Nullable HytaleLogger logger) {
        Path canonicalRoot = paths.runtimeRoot().toAbsolutePath().normalize();
        if (!ATTEMPTED_ROOTS.add(canonicalRoot)) {
            return;
        }
        MigrationContext context = new MigrationContext(logger);
        for (Path legacyRoot : paths.legacyRuntimeRoots()) {
            copyKnownSettings(legacyRoot.resolve("Settings"), canonicalRoot.resolve("Settings"), context);
            copyKnownTelemetry(legacyRoot.resolve("Telemetry"), canonicalRoot.resolve("Telemetry"), context);
        }
        migrateEmbeddedOwnerOverrides(paths, canonicalRoot, context);
    }

    private static void copyKnownSettings(@Nonnull Path source,
                                          @Nonnull Path destination,
                                          @Nonnull MigrationContext context) {
        if (!Files.exists(source) || Files.isSymbolicLink(source)) {
            return;
        }
        if (!Files.isDirectory(source)) {
            context.failure("Settings", wrongType(source));
            return;
        }

        copyFile(source.resolve("runtime.json"), destination.resolve("runtime.json"), "Settings/runtime.json", context);
        copyFile(source.resolve("consent-reviewed-projects.json"),
                destination.resolve("consent-reviewed-projects.json"),
                "Settings/consent-reviewed-projects.json",
                context);
        copyFile(source.resolve("server-identity.json"),
                destination.resolve("server-identity.json"),
                "Settings/server-identity.json",
                context);
        copyFile(source.resolve("server-id.txt"), destination.resolve("server-id.txt"), "Settings/server-id.txt", context);
        copyKnownDirectory(source.resolve("projects"), destination.resolve("projects"), "Settings/projects", context);
    }

    private static void copyKnownTelemetry(@Nonnull Path source,
                                           @Nonnull Path destination,
                                           @Nonnull MigrationContext context) {
        if (!Files.exists(source) || Files.isSymbolicLink(source)) {
            return;
        }
        if (!Files.isDirectory(source)) {
            context.failure("Telemetry", wrongType(source));
            return;
        }

        copyKnownDirectory(source.resolve("crash-reports"),
                destination.resolve("crash-reports"),
                "Telemetry/crash-reports",
                context);
        copyKnownDirectory(source.resolve("events"),
                destination.resolve("events"),
                "Telemetry/events",
                context);
        copyKnownDirectory(source.resolve("manual-reports"),
                destination.resolve("manual-reports"),
                "Telemetry/manual-reports",
                context);
    }

    private static void migrateEmbeddedOwnerOverrides(@Nonnull TelemetryDataPaths paths,
                                                      @Nonnull Path canonicalRoot,
                                                      @Nonnull MigrationContext context) {
        Path modsDirectory = paths.modsDirectory();
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return;
        }
        Set<Path> centralRoots = new HashSet<>();
        centralRoots.add(canonicalRoot);
        for (Path legacyRoot : paths.legacyRuntimeRoots()) {
            centralRoots.add(legacyRoot.toAbsolutePath().normalize());
        }
        try (DirectoryStream<Path> mods = Files.newDirectoryStream(modsDirectory)) {
            for (Path modRoot : mods) {
                Path normalizedModRoot = modRoot.toAbsolutePath().normalize();
                if (Files.isSymbolicLink(modRoot)
                        || !Files.isDirectory(modRoot)
                        || centralRoots.contains(normalizedModRoot)) {
                    continue;
                }
                copyKnownDirectory(
                        modRoot.resolve("Telemetry").resolve("Settings").resolve("projects"),
                        canonicalRoot.resolve("Settings").resolve("projects"),
                        "Settings/projects",
                        context
                );
            }
        } catch (Exception ex) {
            context.failure("Settings/projects", ex);
        }
    }

    private static void copyKnownDirectory(@Nonnull Path source,
                                           @Nonnull Path destination,
                                           @Nonnull String relativePath,
                                           @Nonnull MigrationContext context) {
        if (!Files.exists(source) || Files.isSymbolicLink(source)) {
            return;
        }
        if (!Files.isDirectory(source)) {
            context.failure(relativePath, wrongType(source));
            return;
        }
        if (!destinationIsSafe(destination, relativePath, context)) {
            return;
        }
        if (Files.exists(destination) && !Files.isDirectory(destination)) {
            context.conflict(relativePath);
            return;
        }
        try {
            Files.createDirectories(destination);
        } catch (Exception ex) {
            context.failure(relativePath, ex);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                if (Files.isSymbolicLink(child)) {
                    continue;
                }
                Path childDestination = destination.resolve(child.getFileName().toString());
                String childRelativePath = relativePath + "/" + child.getFileName();
                if (Files.isDirectory(child)) {
                    copyKnownDirectory(child, childDestination, childRelativePath, context);
                } else if (Files.isRegularFile(child)) {
                    copyFile(child, childDestination, childRelativePath, context);
                }
            }
        } catch (Exception ex) {
            context.failure(relativePath, ex);
        }
    }

    private static void copyFile(@Nonnull Path source,
                                 @Nonnull Path destination,
                                 @Nonnull String relativePath,
                                 @Nonnull MigrationContext context) {
        if (!Files.exists(source) || Files.isSymbolicLink(source)) {
            return;
        }
        if (!Files.isRegularFile(source)) {
            return;
        }
        if (!destinationIsSafe(destination, relativePath, context)) {
            return;
        }
        if (Files.exists(destination)) {
            context.conflict(relativePath);
            return;
        }
        try {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException | RuntimeException ex) {
            context.failure(relativePath, ex);
        }
    }

    @Nonnull
    private static IOException wrongType(@Nonnull Path source) {
        return new IOException("Expected a directory at legacy runtime path " + source);
    }

    private static boolean destinationIsSafe(@Nonnull Path destination,
                                             @Nonnull String relativePath,
                                             @Nonnull MigrationContext context) {
        Path current = destination.toAbsolutePath().normalize();
        try {
            while (current != null) {
                if (Files.isSymbolicLink(current)) {
                    context.failure(
                            relativePath,
                            new IOException("Beacon destination contains a symbolic link: " + current)
                    );
                    return false;
                }
                current = current.getParent();
            }
            return true;
        } catch (RuntimeException ex) {
            context.failure(relativePath, ex);
            return false;
        }
    }

    private static final class MigrationContext {
        private final HytaleLogger logger;
        private final Set<String> conflictPaths = new HashSet<>();
        private int warningCount;
        private boolean warningLimitReported;

        private MigrationContext(@Nullable HytaleLogger logger) {
            this.logger = logger;
        }

        private void conflict(@Nonnull String relativePath) {
            if (conflictPaths.add(relativePath)) {
                warn("Beacon kept its existing file for " + relativePath + "; the legacy file remains.", null);
            }
        }

        private void failure(@Nonnull String relativePath, @Nonnull Throwable throwable) {
            warn("Beacon could not migrate " + relativePath + "; the legacy file remains and startup will continue.", throwable);
        }

        private void warn(@Nonnull String message, @Nullable Throwable throwable) {
            if (logger == null || warningCount >= MAX_WARNINGS) {
                if (logger != null && !warningLimitReported && warningCount >= MAX_WARNINGS) {
                    warningLimitReported = true;
                    logger.at(Level.WARNING).log("Beacon data migration warnings were capped at " + MAX_WARNINGS + ".");
                }
                warningCount++;
                return;
            }
            warningCount++;
            if (throwable == null) {
                logger.at(Level.WARNING).log(message);
            } else {
                logger.at(Level.WARNING).withCause(throwable).log(message);
            }
        }
    }
}
