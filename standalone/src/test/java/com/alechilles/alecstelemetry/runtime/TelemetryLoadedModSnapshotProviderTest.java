package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryLoadedModSnapshotProviderTest {

    @Test
    void filtersBaseGameIdentifiersAndGeneratedRuntimePacks() {
        assertFalse(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("Hytale:Core"));
        assertFalse(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("hytale:Adventure"));
        assertFalse(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("Hytale"));
        assertFalse(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("Alechilles:Alec's Tamework!_GeneratedPatches"));
        assertTrue(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("Alechilles:Alec's Tamework!"));
        assertTrue(TelemetryLoadedModSnapshotProvider.isReportableModIdentifier("Example:Enabled Asset Pack"));
    }

    @Test
    void reportableLoadedModsKeepsEnabledPluginAndAssetPackModsOnly() {
        List<CrashReportEnvelope.LoadedModMetadata> reportableMods = TelemetryLoadedModSnapshotProvider.reportableLoadedMods(List.of(
                new CrashReportEnvelope.LoadedModMetadata("Hytale:Core", "1.0.0"),
                new CrashReportEnvelope.LoadedModMetadata("Alechilles:Alec's Tamework!", "1.3.0"),
                new CrashReportEnvelope.LoadedModMetadata("Alechilles:Alec's Tamework!", "1.3.0"),
                new CrashReportEnvelope.LoadedModMetadata("Example:Enabled Asset Pack", "2.0.0"),
                new CrashReportEnvelope.LoadedModMetadata("Alechilles:Alec's Tamework!_GeneratedPatches", "1.3.0")
        ));

        assertEquals(
                List.of("Alechilles:Alec's Tamework!", "Example:Enabled Asset Pack"),
                reportableMods.stream()
                        .map(CrashReportEnvelope.LoadedModMetadata::identifier)
                        .toList()
        );
    }
}
