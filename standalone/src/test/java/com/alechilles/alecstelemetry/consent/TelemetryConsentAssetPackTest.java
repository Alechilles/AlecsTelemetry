package com.alechilles.alecstelemetry.consent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentAssetPackTest {

    @Test
    void consentUiIsPackagedAsClientAssetPackContent() throws IOException {
        String manifest = resourceText("manifest.json");
        String consentPage = resourceText("Common/UI/Custom/TelemetryConsentPage.ui");

        assertTrue(
                manifest.contains("\"IncludesAssetPack\": true"),
                "manifest.json must enable asset-pack indexing for custom UI documents"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/TelemetryConsentPage.ui"),
                "TelemetryConsentPage.ui must be packaged for the client custom UI document index"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/AlecsTelemetryLogo.png"),
                "AlecsTelemetryLogo.png must be packaged for the consent page header"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/TelemetryConsentHeader.png"),
                "TelemetryConsentHeader.png must be packaged for the enlarged consent page title bar"
        );
        assertTrue(
                consentPage.contains("Background: \"AlecsTelemetryLogo.png\""),
                "TelemetryConsentPage.ui must reference the logo relative to its custom UI folder"
        );
        assertTrue(
                consentPage.contains("Anchor: (Height: 128, Top: 0)"),
                "TelemetryConsentPage.ui must enlarge the decorated title bar for the logo"
        );
        assertTrue(
                consentPage.contains("Background: \"TelemetryConsentHeader.png\""),
                "TelemetryConsentPage.ui must use the custom pre-scaled title texture"
        );
        assertTrue(
                consentPage.contains("Anchor: (Top: 128)"),
                "TelemetryConsentPage.ui must offset content below the enlarged logo title bar"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconFrame"),
                "TelemetryConsentPage.ui must include a left-side project icon frame in each consent row"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconImage"),
                "TelemetryConsentPage.ui must include a bindable project icon image layer"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconPlaceholder"),
                "TelemetryConsentPage.ui must include a fallback placeholder for mods without icons"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconFrame { Anchor: (Width: 88)"),
                "TelemetryConsentPage.ui must reserve a square 88px icon area for each consent row"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconImage { Anchor: (Width: 88, Height: 88)"),
                "TelemetryConsentPage.ui must let project icons fill the square area without padding"
        );
        assertTrue(
                !consentPage.contains("#TelemetryConsentProjectIconFrame { Anchor: (Width: 72)"),
                "TelemetryConsentPage.ui must not use the old padded 72px icon frame"
        );
        assertTrue(
                !consentPage.contains("#TelemetryConsentProjectIconImage { Anchor: (Width: 56, Height: 56)"),
                "TelemetryConsentPage.ui must not shrink project icons into the old 56px image box"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentCrashEnabled { Anchor: (Top: 1, Left: 38, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must keep the crash checkbox close to its label"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentStatsEnabled { Anchor: (Top: 1, Left: 460, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must expose the separate stats consent category"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentBreadcrumbsEnabled { Anchor: (Top: 1, Left: 576, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must keep the compact telemetry category layout with stats"
        );
        assertTrue(
                !consentPage.contains("Common/UI/Custom/AlecsTelemetryLogo.png"),
                "TelemetryConsentPage.ui must not use the unresolved asset-pack path for the logo texture"
        );
        assertTrue(
                !consentPage.contains("Background: #203b5a"),
                "TelemetryConsentPage.ui must not fall back to the unframed flat title fill"
        );
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the standalone artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
