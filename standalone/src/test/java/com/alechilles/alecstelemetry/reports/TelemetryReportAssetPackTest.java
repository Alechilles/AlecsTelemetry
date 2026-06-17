package com.alechilles.alecstelemetry.reports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryReportAssetPackTest {

    @Test
    void reportUiUsesEditableFieldsAndTelemetryHeader() throws IOException {
        String reportPage = resourceText("Common/UI/Custom/TelemetryReportPage.ui");

        assertTrue(
                reportPage.contains("TextField #TelemetryReportTitle"),
                "TelemetryReportPage.ui must use an editable title field"
        );
        assertTrue(
                reportPage.contains("MultilineTextField #TelemetryReportDescription"),
                "TelemetryReportPage.ui must use an editable multiline description field"
        );
        assertTrue(
                reportPage.contains("TextField #TelemetryReportContact"),
                "TelemetryReportPage.ui must use an editable contact field"
        );
        assertTrue(
                reportPage.contains("TextField #TelemetryReportFieldTextValue"),
                "TelemetryReportPage.ui must use editable schema field controls"
        );
        assertTrue(
                reportPage.contains("DropdownBox #TelemetryReportFieldDropdownValue"),
                "TelemetryReportPage.ui must include dropdown controls for enum schema fields"
        );
        assertFalse(
                reportPage.contains("Label #TelemetryReportTitle"),
                "TelemetryReportPage.ui must not render the title input as a read-only label"
        );
        assertFalse(
                reportPage.contains("#TelemetryReportFieldValue"),
                "TelemetryReportPage.ui must not use the old single schema field value control"
        );
        assertTrue(
                reportPage.contains("Background: \"AlecsTelemetryLogo.png\""),
                "TelemetryReportPage.ui must show the Alec's Telemetry logo in the header"
        );
        assertTrue(
                reportPage.contains("Background: \"TelemetryConsentHeader.png\""),
                "TelemetryReportPage.ui must use the branded header texture"
        );
        assertTrue(
                reportPage.contains("Text: \"Loaded mods\""),
                "TelemetryReportPage.ui must label the optional mod snapshot as loaded mods, not installed mods"
        );
    }

    @Test
    void projectSelectorUsesProjectIconsAndOnlyRenderedRows() throws IOException {
        String selectorPage = resourceText("Common/UI/Custom/TelemetryReportProjectSelectPage.ui");

        assertNotNull(
                getClass().getClassLoader().getResource("Common/UI/Custom/TelemetryReportProjectSelectPage.ui"),
                "TelemetryReportProjectSelectPage.ui must be packaged for the client custom UI document index"
        );
        assertTrue(
                selectorPage.contains("Background: \"AlecsTelemetryLogo.png\""),
                "TelemetryReportProjectSelectPage.ui must show the Alec's Telemetry logo in the header"
        );
        assertTrue(
                selectorPage.contains("#TelemetryReportProjectSelectIconFrame"),
                "TelemetryReportProjectSelectPage.ui must include a project icon frame"
        );
        assertTrue(
                selectorPage.contains("#TelemetryReportProjectSelectIconImage"),
                "TelemetryReportProjectSelectPage.ui must include a bindable project icon image"
        );
        assertTrue(
                selectorPage.contains("#TelemetryReportProjectSelectIconPlaceholder"),
                "TelemetryReportProjectSelectPage.ui must include an icon placeholder"
        );
        assertFalse(
                selectorPage.contains("#TelemetryReportProjectSelectRow5"),
                "TelemetryReportProjectSelectPage.ui must not define unrendered rows with default buttons"
        );
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the standalone artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
