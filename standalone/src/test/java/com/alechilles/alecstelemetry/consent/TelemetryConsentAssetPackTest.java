package com.alechilles.alecstelemetry.consent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
                consentPage.contains("#TelemetryConsentCaptureAllEnabled"),
                "TelemetryConsentPage.ui must expose a global crash consent checkbox with a safe selector token"
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
                consentPage.contains("@TelemetryConsentUnsupportedCellLabel")
                        && consentPage.contains("#TelemetryConsentCaptureUnsupported")
                        && consentPage.contains("#TelemetryConsentCaptureAllUnsupported"),
                "TelemetryConsentPage.ui must include passive disabled placeholders for unsupported telemetry cells"
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
                consentPage.contains("Anchor: (Width: 740, Height: 64)"),
                "TelemetryConsentPage.ui must give the grid header enough height to keep labels clear of checkboxes"
        );
        assertTrue(
                consentPage.contains("Background: #28415f"),
                "TelemetryConsentPage.ui must draw visible grid lines between telemetry columns"
        );
        assertTrue(
                consentPage.contains("Group #TelemetryConsentViewport {\r\n          FlexWeight: 1;\r\n          LayoutMode: Top;")
                        || consentPage.contains("Group #TelemetryConsentViewport {\n          FlexWeight: 1;\n          LayoutMode: Top;"),
                "TelemetryConsentPage.ui must keep project rows in the same coordinate space as the header grid"
        );
        assertTrue(
                consentPage.contains("Group #TelemetryConsentRows {\r\n            LayoutMode: Top;\r\n            Anchor: (Width: 740, Bottom: 0);")
                        || consentPage.contains("Group #TelemetryConsentRows {\n            LayoutMode: Top;\n            Anchor: (Width: 740, Bottom: 0);"),
                "TelemetryConsentPage.ui must align rows to the same fixed-width origin as the header grid"
        );
        assertTrue(
                consentPage.contains("Group #TelemetryConsentProjectRow0 { Anchor: (Width: 740, Height: 56)"),
                "TelemetryConsentPage.ui must make project rows span the same full width as the header"
        );
        assertTrue(
                consentPage.contains("TextButton #TelemetryConsentAllToggleButton"),
                "TelemetryConsentPage.ui must include a reliable hit target for the all-toggle header checkbox"
        );
        assertTrue(
                consentPage.contains("TextButton #TelemetryConsentUsageAllToggleButton"),
                "TelemetryConsentPage.ui must include reliable hit targets for category header checkboxes"
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
                consentPage.contains("CheckBox #TelemetryConsentCaptureAllEnabled {\r\n            Anchor: (Top: 34, Left: 371, Width: 22, Height: 22)")
                        || consentPage.contains("CheckBox #TelemetryConsentCaptureAllEnabled {\n            Anchor: (Top: 34, Left: 371, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must place the header crash checkbox in the same column as row crash checkboxes"
        );
        assertTrue(
                consentPage.contains("CheckBox #TelemetryConsentCaptureEnabled { Anchor: (Top: 17, Left: 371, Width: 22, Height: 22)"),
                "TelemetryConsentPage.ui must align crash checkboxes under the crash column"
        );
        assertTrue(
                !consentPage.contains("TelemetryConsentCrash"),
                "TelemetryConsentPage.ui must avoid Crash in selector ids because those controls do not reliably activate in-client"
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

    @Test
    void consentPageBindsHeaderToggleButtonsToDeterministicServerActions() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java"));

        assertTrue(
                source.contains("CustomUIEventBindingType.Activating,\n                \"#TelemetryConsentAllToggleButton\""),
                "The all-toggle header hit target must use Activating so it cannot depend on CheckBox ValueChanged behavior"
        );
        assertTrue(
                source.contains(".append(KEY_ENABLED_LITERAL, Boolean.toString(!allTelemetryEnabled(projects)))"),
                "The all-toggle header hit target must send the next aggregate state explicitly"
        );
        assertTrue(
                source.contains("globalCategoryToggleSelector(category)"),
                "Category header hit targets must be bound for every telemetry category"
        );
        assertTrue(
                source.contains(".append(KEY_ENABLED_LITERAL, Boolean.toString(!categoryAllEnabled(projects, category)))"),
                "Category header hit targets must send the next aggregate category state explicitly"
        );
        assertTrue(
                source.contains("projectToggleSelector(index)"),
                "Project row hit targets must use deterministic Activating bindings"
        );
        assertTrue(
                source.contains("categoryToggleSelector(index, category)"),
                "Project category hit targets must use deterministic Activating bindings"
        );
        assertTrue(
                source.contains("if (\"crash\".equals(category)) {\n            return \"Capture\";"),
                "Crash runtime category must use a safe UI selector token while still sending the crash category to the runtime"
        );
        assertTrue(
                source.contains("CustomUIEventBindingType.ValueChanged,\n                    categoryCheck,"),
                "Project category checkboxes must keep ValueChanged bindings when the checkbox wins hit testing over the transparent button"
        );
        assertTrue(
                source.contains("EventData.of(KEY_ACTION, ACTION_TOGGLE_GLOBAL_CATEGORY)\n                            .append(KEY_CATEGORY, category)\n                            .append(KEY_ENABLED, categoryCheck + \".Value\")"),
                "Global category checkboxes must keep ValueChanged bindings for direct checkbox clicks"
        );
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the standalone artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
