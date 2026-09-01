package com.alechilles.beacon.consent;

import com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentViewModelTest {

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
        assertFalse(viewModel.projects().get(0).consent().statsEnabled());
        assertFalse(viewModel.projects().get(0).supported().crashEnabled());
        assertFalse(viewModel.projects().get(0).supported().statsEnabled());
        assertEquals("zeta-mod", viewModel.projects().get(1).projectId());
        assertTrue(viewModel.projects().get(1).consent().projectEnabled());
        assertTrue(viewModel.projects().get(1).consent().statsEnabled());
        assertTrue(viewModel.projects().get(1).supported().statsEnabled());
        assertTrue(viewModel.explanationLines().contains(
                "Each mod controls its suggested defaults, and you can override them here."
        ));
    }

    @Test
    void exposesSupportedTelemetryCategoriesSeparatelyFromCurrentConsent() {
        TelemetryRuntimeDiagnostics diagnostics = diagnosticsFor(new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                "stats-mod",
                "Stats Mod",
                true,
                false,
                "hosted",
                "https://example.invalid/ingest",
                0,
                "Example:Stats Mod",
                "1.0.0",
                "C:/mods/stats",
                null,
                List.of("com.example.stats"),
                "dependency",
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false
        ));

        TelemetryConsentViewModel.ProjectRow row = TelemetryConsentViewModel.from(diagnostics).projects().getFirst();

        assertTrue(row.consent().statsEnabled());
        assertFalse(row.consent().usageEnabled());
        assertTrue(row.supported().statsEnabled());
        assertFalse(row.supported().usageEnabled());
    }

    @Test
    void exposesDescriptorIconTexturePath() {
        TelemetryRuntimeDiagnostics diagnostics = diagnosticsFor(project(
                "example-mod",
                "Example Mod",
                true,
                false,
                true,
                "C:/mods/example",
                "Tamework/Telemetry/TameworkConsentIcon.png"
        ));

        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(diagnostics);

        assertEquals("Tamework/Telemetry/TameworkConsentIcon.png", viewModel.projects().getFirst().iconTexturePath());
        assertTrue(viewModel.projects().getFirst().hasIconTexture());
    }

    @Test
    void ignoresRootIconSourcePathWithoutDescriptorIconTexturePath() {
        TelemetryRuntimeDiagnostics diagnostics = diagnosticsFor(project(
                "example-mod",
                "Example Mod",
                true,
                false,
                true,
                "C:/mods/Example Mod.jar"
        ));

        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(diagnostics);

        assertNull(viewModel.projects().getFirst().iconTexturePath());
        assertFalse(viewModel.projects().getFirst().hasIconTexture());
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
                null,
                List.of("com.example." + projectId.replace("-", "")),
                "dependency",
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
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
        return project(projectId, displayName, enabled, overridePresent, categoriesEnabled, sourcePath, null);
    }

    private static TelemetryRuntimeDiagnostics.ProjectDiagnostics project(
            String projectId,
            String displayName,
            boolean enabled,
            boolean overridePresent,
            boolean categoriesEnabled,
            String sourcePath,
            String iconTexturePath) {
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
                iconTexturePath,
                List.of("com.example." + projectId.replace("-", "")),
                "dependency",
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled,
                categoriesEnabled
        );
    }
}
