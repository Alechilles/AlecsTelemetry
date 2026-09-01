package com.alechilles.beacon.consent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentAssetPackTest {

    @Test
    void consentUiIsPackagedAsRuntimeAssetPackContent() throws IOException {
        String consentPage = resourceText("Common/UI/Custom/TelemetryConsentPage.ui")
                + resourceText("Common/UI/Custom/TelemetryConsentProjectRow.ui");

        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/TelemetryConsentPage.ui"),
                "TelemetryConsentPage.ui must be packaged for the client custom UI document index"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/AlecsTelemetryLogo.png"),
                "AlecsTelemetryLogo.png must be packaged for the consent page header"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/icon-256.png"),
                "icon-256.png must be packaged for the Alec's Telemetry project row icon"
        );
        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/TelemetryConsentHeader.png"),
                "TelemetryConsentHeader.png must be packaged for the enlarged consent page title bar"
        );
        assertTrue(consentPage.contains("Background: \"AlecsTelemetryLogo.png\""));
        assertTrue(consentPage.contains("Anchor: (Height: 128, Top: 0)"));
        assertTrue(consentPage.contains("Background: \"TelemetryConsentHeader.png\""));
        assertTrue(consentPage.contains("Anchor: (Top: 128)"));
        assertTrue(consentPage.contains("#TelemetryConsentProjectIconFrame"));
        assertTrue(consentPage.contains("#TelemetryConsentProjectIconImage"));
        assertTrue(consentPage.contains("#TelemetryConsentProjectIconPlaceholder"));
        assertTrue(consentPage.contains("#TelemetryConsentGridHeader"));
        assertTrue(consentPage.contains("Anchor: (Width: 835, Height: 710);"));
        assertTrue(consentPage.contains("#TelemetryConsentCaptureAllEnabled"));
        assertTrue(consentPage.contains("#TelemetryConsentDiagnosticsAllEnabled"));
        assertTrue(consentPage.contains("#TelemetryConsentStatsAllEnabled"));
        assertTrue(consentPage.contains("#TelemetryConsentBreadcrumbsAllEnabled"));
        assertTrue(consentPage.contains("@TelemetryConsentUnsupportedCellLabel"));
        assertTrue(consentPage.contains("#TelemetryConsentCaptureUnsupported"));
        assertTrue(consentPage.contains("#TelemetryConsentCaptureAllUnsupported"));
        assertTrue(consentPage.contains("TooltipText: \"Crash reports, uncaught exceptions"));
        assertTrue(consentPage.contains("Text: \"Diag\""));
        assertTrue(consentPage.contains("TooltipText: \"Automatic diagnostic bundles with technical metadata and optional redacted attachments.\""));
        assertTrue(consentPage.contains("TextTooltipStyle: @TelemetryConsentHintStyle"));
        assertTrue(consentPage.contains("Anchor: (Left: 0, Width: 783, Height: 64)"));
        assertTrue(consentPage.contains("Background: #28415f"));
        assertTrue(consentPage.contains("TextButton #TelemetryConsentAllToggleButton"));
        assertTrue(consentPage.contains("TextButton #TelemetryConsentUsageAllToggleButton"));
        assertTrue(consentPage.contains("TextButton #TelemetryConsentPrivacyDisclaimerButton"));
        assertTrue(consentPage.contains("Group #TelemetryConsentPrivacyDisclaimerPopup"));
        assertTrue(consentPage.contains("Label #TelemetryConsentPrivacyDisclaimerBody"));
        assertTrue(consentPage.contains("TextButton #TelemetryConsentPrivacyDisclaimerClose"));
        assertTrue(consentPage.contains("#TelemetryConsentProjectIconFrame { Anchor: (Left: 0, Top: 0, Width: 56, Height: 56)"));
        assertTrue(consentPage.contains("#TelemetryConsentProjectIconImage { Anchor: (Width: 56, Height: 56)"));
        assertTrue(consentPage.contains("Padding: (Full: 0); Group #TelemetryConsentProjectIconImage"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentAllEnabled"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentProjectEnabled"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentCaptureAllEnabled"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentCaptureEnabled"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentDiagnosticsEnabled"));
        assertTrue(!consentPage.contains("TelemetryConsentCrash"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentStatsEnabled"));
        assertTrue(consentPage.contains("CheckBox #TelemetryConsentBreadcrumbsEnabled"));
        assertTrue(!consentPage.contains("Common/UI/Custom/AlecsTelemetryLogo.png"));
        assertTrue(!consentPage.contains("Background: #203b5a"));
    }

    @Test
    void consentProjectRowsUseScrollableViewport() throws IOException {
        String consentPage = resourceText("Common/UI/Custom/TelemetryConsentPage.ui");
        String normalizedConsentPage = consentPage.replace("\r\n", "\n");
        int viewportStart = consentPage.indexOf("Group #TelemetryConsentViewport");
        assertTrue(viewportStart >= 0);

        int rowsStart = consentPage.indexOf("Group #TelemetryConsentRows", viewportStart);
        assertTrue(rowsStart > viewportStart);

        String viewportDefinition = consentPage.substring(viewportStart, rowsStart);
        assertTrue(viewportDefinition.contains("LayoutMode: TopScrolling"));
        assertTrue(viewportDefinition.contains("KeepScrollPosition: true"));
        assertTrue(viewportDefinition.contains("ScrollbarStyle: $C.@DefaultScrollbarStyle"));
        assertTrue(normalizedConsentPage.contains("Group #TelemetryConsentGridHeader {\n          Anchor: (Left: 0, Width: 783, Height: 64);"));
        assertTrue(normalizedConsentPage.contains("Group #TelemetryConsentRows {\n            LayoutMode: Top;\n            Anchor: (Width: 783, Bottom: 0);"));
        assertTrue(consentPage.contains("Anchor: (Left: 8, Width: 280, Top: 5, Height: 24);"));
        assertTrue(consentPage.contains("Anchor: (Left: 288, Width: 55, Top: 5, Height: 24);"));
        assertTrue(consentPage.contains("Anchor: (Left: 453, Width: 55, Top: 5, Height: 24);"));
        assertTrue(consentPage.contains("Anchor: (Left: 728, Width: 55, Top: 5, Height: 24);"));
        assertTrue(consentPage.contains("Anchor: (Top: 34, Left: 744, Width: 22, Height: 22);"));
        assertTrue(consentPage.contains("Anchor: (Width: 783, Bottom: 0);"));
        assertTrue(!consentPage.contains("Anchor: (Width: 740, Height: 56)"));
        assertFalse(consentPage.contains("Left: 685"));
        assertFalse(consentPage.contains("Left: 701"));
        assertFalse(consentPage.contains("Width: 292"));
        assertFalse(consentPage.contains("Width: 228"));
    }

    @Test
    void consentPageUsesDeterministicSelectorAndActionContract() {
        assertEquals("toggle_all", TelemetryConsentUiContract.ACTION_TOGGLE_ALL);
        assertEquals("toggle_global_category", TelemetryConsentUiContract.ACTION_TOGGLE_GLOBAL_CATEGORY);
        assertEquals("toggle_project", TelemetryConsentUiContract.ACTION_TOGGLE_PROJECT);
        assertEquals("toggle_category", TelemetryConsentUiContract.ACTION_TOGGLE_CATEGORY);
        assertEquals("show_privacy_disclaimer", TelemetryConsentUiContract.ACTION_SHOW_PRIVACY_DISCLAIMER);
        assertEquals("hide_privacy_disclaimer", TelemetryConsentUiContract.ACTION_HIDE_PRIVACY_DISCLAIMER);
        assertEquals("Capture", TelemetryConsentUiContract.selectorToken("crash"));
        assertEquals("Diagnostics", TelemetryConsentUiContract.selectorToken("diagnostics"));
        assertEquals("#TelemetryConsentCaptureAllToggleButton", TelemetryConsentUiContract.globalCategoryToggleSelector("crash"));
        assertEquals("#TelemetryConsentCaptureAllEnabled", TelemetryConsentUiContract.globalCategoryCheckSelector("crash"));
        assertEquals("#TelemetryConsentDiagnosticsAllToggleButton", TelemetryConsentUiContract.globalCategoryToggleSelector("diagnostics"));
        assertEquals("#TelemetryConsentDiagnosticsAllEnabled", TelemetryConsentUiContract.globalCategoryCheckSelector("diagnostics"));
        assertEquals("#TelemetryConsentRows[0] #TelemetryConsentCaptureToggleButton",
                TelemetryConsentUiContract.categoryToggleSelector(0, "crash"));
        assertEquals("#TelemetryConsentRows[0] #TelemetryConsentCaptureEnabled",
                TelemetryConsentUiContract.categoryCheckSelector(0, "crash"));
        assertEquals("#TelemetryConsentRows[0] #TelemetryConsentDiagnosticsToggleButton",
                TelemetryConsentUiContract.categoryToggleSelector(0, "diagnostics"));
        assertEquals("#TelemetryConsentRows[0] #TelemetryConsentDiagnosticsEnabled",
                TelemetryConsentUiContract.categoryCheckSelector(0, "diagnostics"));
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the runtime artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
