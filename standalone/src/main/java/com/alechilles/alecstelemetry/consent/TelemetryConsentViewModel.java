package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * UI-independent consent state projected from runtime diagnostics.
 */
public record TelemetryConsentViewModel(boolean allEnabled,
                                        @Nonnull List<ProjectRow> projects,
                                        @Nonnull List<String> explanationLines) {

    private static final String MOD_ICON_TEXTURE_PATH = "icon-256.png";

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
                        "Alec's Telemetry helps mod authors diagnose crashes, errors, performance issues, and feature usage.",
                        "Each mod controls its suggested defaults, and you can override them here.",
                        "No player names, IDs, chat, or any other personally identifiable information will be sent by Alec's Telemetry."
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
                             @Nonnull TelemetryConsentSnapshot consent) {

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
                    resolveIconTexturePath(project.sourcePath()),
                    project.runtimeMode(),
                    new TelemetryConsentSnapshot(
                            project.enabled(),
                            project.crashEnabled(),
                            project.errorEnabled(),
                            project.lifecycleEnabled(),
                            project.performanceEnabled(),
                            project.usageEnabled(),
                            project.breadcrumbsEnabled()
                    )
            );
        }

        public boolean hasIconTexture() {
            return iconTexturePath != null && !iconTexturePath.isBlank();
        }
    }

    @Nullable
    private static String resolveIconTexturePath(@Nullable String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(sourcePath);
        } catch (Exception ignored) {
            return null;
        }
        try {
            if (Files.isDirectory(path)) {
                return Files.isRegularFile(path.resolve(MOD_ICON_TEXTURE_PATH)) ? MOD_ICON_TEXTURE_PATH : null;
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
                return null;
            }
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                return zipFile.getEntry(MOD_ICON_TEXTURE_PATH) == null ? null : MOD_ICON_TEXTURE_PATH;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
