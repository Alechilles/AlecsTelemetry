package com.alechilles.alecstelemetry.reports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void reportPageBuffersTextInputChangesAndSanitizesSubmitFallbackSelectors() throws IOException {
        String reportPageSource = Files.readString(Path.of("src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportPage.java"));
        String submitEventData = sourceSlice(
                reportPageSource,
                "private EventData submitEventData()",
                "private static String fieldValueKey"
        );

        assertTrue(
                reportPageSource.contains("CustomUIEventBindingType.ValueChanged,\n                \"#TelemetryReportTitle\""),
                "Title text must be buffered from ValueChanged events"
        );
        assertTrue(
                reportPageSource.contains("CustomUIEventBindingType.FocusLost,\n                \"#TelemetryReportTitle\""),
                "Title text must also be buffered when focus leaves the field"
        );
        assertTrue(
                reportPageSource.contains("CustomUIEventBindingType.ValueChanged,\n                \"#TelemetryReportDescription\""),
                "Description text must be buffered from ValueChanged events"
        );
        assertFalse(
                reportPageSource.contains("CustomUIEventBindingType.FocusLost,\n                \"#TelemetryReportDescription\""),
                "MultilineTextField does not support FocusLost bindings"
        );
        assertTrue(
                reportPageSource.contains("CustomUIEventBindingType.ValueChanged,\n                \"#TelemetryReportContact\""),
                "Contact text must be buffered from ValueChanged events"
        );
        assertTrue(
                reportPageSource.contains("row + \" #TelemetryReportFieldTextValue\""),
                "Dynamic text fields must be buffered from their own ValueChanged events"
        );
        assertTrue(
                submitEventData.contains(".append(KEY_TITLE, valueSelector(\"#TelemetryReportTitle\"))"),
                "Submit should capture title text as a fallback"
        );
        assertTrue(
                submitEventData.contains(".append(KEY_DESCRIPTION, valueSelector(\"#TelemetryReportDescription\"))"),
                "Submit should capture description text as a fallback"
        );
        assertTrue(
                submitEventData.contains(".append(KEY_CONTACT, valueSelector(\"#TelemetryReportContact\"))"),
                "Submit should capture contact text as a fallback"
        );
        assertTrue(
                reportPageSource.contains("ManualReportInputSanitizer.isTelemetrySelector"),
                "Selector literals returned by the UI binding layer must not overwrite buffered player text"
        );
        assertTrue(
                reportPageSource.contains("case ACTION_TOGGLE_LOADED_MODS -> {\n                includeLoadedMods = data.enabledOr(!includeLoadedMods);\n                return;\n            }"),
                "Checkbox toggles must not refresh the full page and wipe client-side text controls"
        );
    }

    private static String sourceSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, "Expected source marker: " + startMarker);
        assertTrue(end > start, "Expected source marker: " + endMarker);
        return source.substring(start, end);
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the standalone artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
