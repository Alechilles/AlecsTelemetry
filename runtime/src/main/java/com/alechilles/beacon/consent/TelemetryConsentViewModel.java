package com.alechilles.beacon.consent;

import com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * UI-independent consent state projected from runtime diagnostics.
 */
public record TelemetryConsentViewModel(boolean allEnabled,
                                        @Nonnull List<ProjectRow> projects,
                                        @Nonnull List<String> explanationLines) {

    @Nonnull
    public static TelemetryConsentViewModel from(@Nonnull TelemetryRuntimeDiagnostics diagnostics) {
        List<ProjectRow> rows = diagnostics.projects().stream()
                .map(ProjectRow::from)
                .sorted(Comparator
                        .comparing(ProjectRow::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProjectRow::projectId, String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean allEnabled = !rows.isEmpty() && rows.stream().allMatch(row -> row.consent().projectEnabled());
        return new TelemetryConsentViewModel(
                allEnabled,
                rows,
                List.of(
                        "Beacon helps mod authors diagnose crashes, errors, performance issues, and feature usage.",
                        "Each mod controls its suggested defaults, and you can override them here.",
                        "No player names, IDs, chat, or any other personally identifiable information will be sent by Beacon."
                )
        );
    }

    public record ProjectRow(@Nonnull String projectId,
                             @Nonnull String displayName,
                             boolean overridePresent,
                             @Nonnull String destinationMode,
                             int pendingReports,
                             @Nonnull String pluginIdentifier,
                             @Nonnull String pluginVersion,
                             @Nullable String sourcePath,
                             @Nullable String iconTexturePath,
                             @Nonnull String runtimeMode,
                             @Nonnull TelemetryConsentSnapshot consent,
                             @Nonnull TelemetryConsentSnapshot supported) {

        @Nonnull
        private static ProjectRow from(@Nonnull TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
            return new ProjectRow(
                    project.projectId(),
                    project.displayName(),
                    project.overridePresent(),
                    project.destinationMode(),
                    project.pendingReports(),
                    project.pluginIdentifier(),
                    project.pluginVersion(),
                    project.sourcePath(),
                    project.iconTexturePath(),
                    project.runtimeMode(),
                    project.consentSnapshot(),
                    project.supportedSnapshot()
            );
        }

        public boolean hasIconTexture() {
            return iconTexturePath != null && !iconTexturePath.isBlank();
        }
    }
}
