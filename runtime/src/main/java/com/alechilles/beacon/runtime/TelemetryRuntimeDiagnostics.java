package com.alechilles.beacon.runtime;

import com.alechilles.beacon.consent.TelemetryConsentSnapshot;

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
                                     boolean diagnosticsEnabled,
                                     boolean lifecycleEnabled,
                                     boolean performanceEnabled,
                                     boolean usageEnabled,
                                     boolean statsEnabled,
                                     boolean breadcrumbsEnabled,
                                     boolean crashSupported,
                                     boolean errorSupported,
                                     boolean diagnosticsSupported,
                                     boolean lifecycleSupported,
                                     boolean performanceSupported,
                                     boolean usageSupported,
                                     boolean statsSupported,
                                     boolean breadcrumbsSupported) {
        public ProjectDiagnostics(@Nonnull String projectId,
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
            this(
                    projectId,
                    displayName,
                    enabled,
                    overridePresent,
                    destinationMode,
                    endpoint,
                    pendingReports,
                    pluginIdentifier,
                    pluginVersion,
                    sourcePath,
                    iconTexturePath,
                    packagePrefixes,
                    runtimeMode,
                    crashEnabled,
                    errorEnabled,
                    false,
                    lifecycleEnabled,
                    performanceEnabled,
                    usageEnabled,
                    statsEnabled,
                    breadcrumbsEnabled,
                    crashSupported,
                    errorSupported,
                    false,
                    lifecycleSupported,
                    performanceSupported,
                    usageSupported,
                    statsSupported,
                    breadcrumbsSupported
            );
        }

        @Nonnull
        public TelemetryConsentSnapshot consentSnapshot() {
            return new TelemetryConsentSnapshot(
                    enabled,
                    crashEnabled,
                    errorEnabled,
                    diagnosticsEnabled,
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
                    diagnosticsSupported,
                    lifecycleSupported,
                    performanceSupported,
                    usageSupported,
                    statsSupported,
                    breadcrumbsSupported
            );
        }
    }
}
