package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsSortedRowsAndAllEnabledAggregateFromDiagnostics() {
        TelemetryRuntimeDiagnostics diagnostics = new TelemetryRuntimeDiagnostics(
                true,
                2,
                2,
                0,
                false,
                "No flush attempts yet.",
                "C:/mods",
                List.of(),
                List.of(
                        project("zeta-mod", "Zeta Mod", true, true, true),
                        project("alpha-mod", "Alpha Mod", false, false, false)
                )
        );

        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(diagnostics);

        assertFalse(viewModel.allEnabled());
        assertEquals(2, viewModel.projects().size());
        assertEquals("alpha-mod", viewModel.projects().get(0).projectId());
        assertEquals("Alpha Mod", viewModel.projects().get(0).displayName());
        assertEquals("Example:Alpha Mod", viewModel.projects().get(0).pluginIdentifier());
        assertEquals("1.0.0", viewModel.projects().get(0).pluginVersion());
        assertEquals("C:/mods/alpha", viewModel.projects().get(0).sourcePath());
        assertNull(viewModel.projects().get(0).iconTexturePath());
        assertFalse(viewModel.projects().get(0).overridePresent());
        assertFalse(viewModel.projects().get(0).consent().projectEnabled());
        assertFalse(viewModel.projects().get(0).consent().crashEnabled());
        assertEquals("zeta-mod", viewModel.projects().get(1).projectId());
        assertTrue(viewModel.projects().get(1).consent().projectEnabled());
        assertTrue(viewModel.explanationLines().contains(
                "Each mod controls its suggested defaults, and you can override them here."
        ));
    }

    @Test
    void exposesIconTexturePathWhenSourceFolderHasRootIcon() throws Exception {
        Path modFolder = tempDir.resolve("Example Mod");
        Files.createDirectories(modFolder);
        Files.write(modFolder.resolve("icon-256.png"), new byte[]{1, 2, 3});
        TelemetryRuntimeDiagnostics diagnostics = diagnosticsFor(project(
                "example-mod",
                "Example Mod",
                true,
                false,
                true,
                modFolder.toString()
        ));

        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(diagnostics);

        assertEquals("icon-256.png", viewModel.projects().getFirst().iconTexturePath());
        assertTrue(viewModel.projects().getFirst().hasIconTexture());
    }

    @Test
    void exposesIconTexturePathWhenSourceArchiveHasRootIcon() throws Exception {
        Path modJar = tempDir.resolve("Example Mod.jar");
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(modJar))) {
            stream.putNextEntry(new ZipEntry("icon-256.png"));
            stream.write(new byte[]{1, 2, 3});
            stream.closeEntry();
        }
        TelemetryRuntimeDiagnostics diagnostics = diagnosticsFor(project(
                "example-mod",
                "Example Mod",
                true,
                false,
                true,
                modJar.toString()
        ));

        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(diagnostics);

        assertEquals("icon-256.png", viewModel.projects().getFirst().iconTexturePath());
        assertTrue(viewModel.projects().getFirst().hasIconTexture());
    }

    private static TelemetryRuntimeDiagnostics diagnosticsFor(TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        return new TelemetryRuntimeDiagnostics(
                true,
                1,
                1,
                0,
                false,
                "No flush attempts yet.",
                "C:/mods",
                List.of(),
                List.of(project)
        );
    }

    private static TelemetryRuntimeDiagnostics.ProjectDiagnostics project(
            String projectId,
            String displayName,
            boolean enabled,
            boolean overridePresent,
            boolean categoriesEnabled) {
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                projectId,
                displayName,
                enabled,
                overridePresent,
                "hosted",
                "https://example.invalid/ingest",
                0,
                "Example:" + displayName,
                "1.0.0",
                "C:/mods/" + projectId.replace("-mod", ""),
                List.of("com.example." + projectId.replace("-", "")),
                "dependency",
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled
        );
    }

    private static TelemetryRuntimeDiagnostics.ProjectDiagnostics project(
            String projectId,
            String displayName,
            boolean enabled,
            boolean overridePresent,
            boolean categoriesEnabled,
            String sourcePath) {
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                projectId,
                displayName,
                enabled,
                overridePresent,
                "hosted",
                "https://example.invalid/ingest",
                0,
                "Example:" + displayName,
                "1.0.0",
                sourcePath,
                List.of("com.example." + projectId.replace("-", "")),
                "dependency",
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled
        );
    }
}
