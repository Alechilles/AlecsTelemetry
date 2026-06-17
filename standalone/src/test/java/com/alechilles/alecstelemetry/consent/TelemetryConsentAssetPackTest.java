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
                consentPage.contains("#TelemetryConsentGridHeader"),
                "TelemetryConsentPage.ui must include a grid header for global consent toggles"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentCrashAllEnabled"),
                "TelemetryConsentPage.ui must expose a global crash consent checkbox"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentStatsAllEnabled"),
                "TelemetryConsentPage.ui must expose a global stats consent checkbox"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentBreadcrumbsAllEnabled"),
                "TelemetryConsentPage.ui must expose a global breadcrumbs consent checkbox"
        );
        assertTrue(
                consentPage.contains("TooltipText: \"Crash reports, uncaught exceptions"),
                "TelemetryConsentPage.ui must explain crash telemetry through a tooltip"
        );
        assertTrue(
                consentPage.contains("TextTooltipStyle: @TelemetryConsentHintStyle"),
                "TelemetryConsentPage.ui must apply the shared tooltip styling to consent category hints"
        );
        assertTrue(
                consentPage.contains("Anchor: (Height: 64, Left: 0, Right: 0)"),
                "TelemetryConsentPage.ui must give the grid header enough height to keep labels clear of checkboxes"
        );
        assertTrue(
                consentPage.contains("Background: #28415f"),
                "TelemetryConsentPage.ui must draw visible grid lines between telemetry columns"
        );
        assertTrue(
                consentPage.contains("Padding: (Left: 0, Right: 0, Top: 0, Bottom: 0)"),
                "TelemetryConsentPage.ui must not offset project rows inside the scrolling viewport"
        );
        assertTrue(
                consentPage.contains("Group #TelemetryConsentProjectRow0 { Anchor: (Left: 0, Right: 0, Height: 56)"),
                "TelemetryConsentPage.ui must make project rows span the same full width as the header"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconFrame { Anchor: (Left: 0, Top: 0, Width: 56, Height: 56)"),
                "TelemetryConsentPage.ui must reserve a perfectly square 56px icon area for each consent row"
        );
        assertTrue(
                consentPage.contains("#TelemetryConsentProjectIconImage { Anchor: (Width: 56, Height: 56)"),
                "TelemetryConsentPage.ui must let project icons fill the square area without padding"
        );
        assertTrue(
                consentPage.contains("Padding: (Full: 0); Group #TelemetryConsentProjectIconImage"),
                "TelemetryConsentPage.ui must not pad project icons within their square slot"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentAllEnabled {\r\n            Anchor: (Top: 34, Left: 316, Width: 22, Height: 22)")
                        || consentPage.contains("CheckBox #TelemetryConsentAllEnabled {\n            Anchor: (Top: 34, Left: 316, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must place the header enable checkbox in the same column as project row checkboxes"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentProjectEnabled { Anchor: (Top: 17, Left: 316, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must align each project checkbox under the global enable column"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentCrashAllEnabled {\r\n            Anchor: (Top: 34, Left: 371, Width: 22, Height: 22)")
                        || consentPage.contains("CheckBox #TelemetryConsentCrashAllEnabled {\n            Anchor: (Top: 34, Left: 371, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must place the header crash checkbox in the same column as row crash checkboxes"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentCrashEnabled { Anchor: (Top: 17, Left: 371, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must align crash checkboxes under the crash column"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentStatsEnabled { Anchor: (Top: 17, Left: 646, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must align stats checkboxes under the stats column"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentBreadcrumbsEnabled { Anchor: (Top: 17, Left: 701, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must align breadcrumbs checkboxes under the breadcrumbs column"
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
