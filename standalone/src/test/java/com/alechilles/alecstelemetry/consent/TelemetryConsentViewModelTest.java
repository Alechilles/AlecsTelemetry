package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(viewModel.projects().get(0).overridePresent());
        assertFalse(viewModel.projects().get(0).consent().projectEnabled());
        assertFalse(viewModel.projects().get(0).consent().crashEnabled());
        assertEquals("zeta-mod", viewModel.projects().get(1).projectId());
        assertTrue(viewModel.projects().get(1).consent().projectEnabled());
        assertTrue(viewModel.explanationLines().contains(
                "Each mod controls its suggested defaults, and you can override them here."
        ));
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
}
