package com.alechilles.alecstelemetry.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Snapshot of current runtime and project telemetry state for commands and diagnostics.
 */
public record TelemetryRuntimeDiagnostics(boolean enabled,
                                          int registeredProjects,
                                          int loadedMods,
                                          int totalPendingReports,
                                          boolean flushInProgress,
                                          @Nonnull String lastFlushResult,
                                          @Nullable String modsDirectory,
                                          @Nonnull List<String> registrationWarnings,
                                          @Nonnull List<ProjectDiagnostics> projects) {

    /**
     * Project-specific diagnostics snapshot.
     */
    public record ProjectDiagnostics(@Nonnull String projectId,
                                     @Nonnull String displayName,
                                     boolean enabled,
                                     boolean overridePresent,
                                     @Nonnull String destinationMode,
                                     @Nullable String endpoint,
                                     int pendingReports,
                                     @Nonnull String pluginIdentifier,
                                     @Nonnull String pluginVersion,
                                     @Nullable String sourcePath,
                                     @Nonnull List<String> packagePrefixes) {
    }
}
