package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;

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
                                     @Nullable String iconTexturePath,
                                     @Nonnull List<String> packagePrefixes,
                                     @Nonnull String runtimeMode,
                                     boolean crashEnabled,
                                     boolean errorEnabled,
                                     boolean lifecycleEnabled,
                                     boolean performanceEnabled,
                                     boolean usageEnabled,
                                     boolean statsEnabled,
                                     boolean breadcrumbsEnabled,
                                     boolean crashSupported,
                                     boolean errorSupported,
                                     boolean lifecycleSupported,
                                     boolean performanceSupported,
                                     boolean usageSupported,
                                     boolean statsSupported,
                                     boolean breadcrumbsSupported) {
        @Nonnull
        public TelemetryConsentSnapshot consentSnapshot() {
            return new TelemetryConsentSnapshot(
                    enabled,
                    crashEnabled,
                    errorEnabled,
                    lifecycleEnabled,
                    performanceEnabled,
                    usageEnabled,
                    statsEnabled,
                    breadcrumbsEnabled
            );
        }

        @Nonnull
        public TelemetryConsentSnapshot supportedSnapshot() {
            return new TelemetryConsentSnapshot(
                    true,
                    crashSupported,
                    errorSupported,
                    lifecycleSupported,
                    performanceSupported,
                    usageSupported,
                    statsSupported,
                    breadcrumbsSupported
            );
        }
    }
}
