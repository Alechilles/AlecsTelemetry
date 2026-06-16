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
        assertTrue(
                consentPage.contains("Background: \"AlecsTelemetryLogo.png\""),
                "TelemetryConsentPage.ui must reference the logo relative to its custom UI folder"
        );
        assertTrue(
                consentPage.contains("Anchor: (Height: 128, Top: 0)"),
                "TelemetryConsentPage.ui must enlarge the decorated title bar for the logo"
        );
        assertTrue(
                consentPage.contains("Background: #203b5a"),
                "TelemetryConsentPage.ui must avoid vertically stretching the stock decorated title texture"
        );
        assertTrue(
                consentPage.contains("Anchor: (Top: 128)"),
                "TelemetryConsentPage.ui must offset content below the enlarged logo title bar"
        );
        assertTrue(
                !consentPage.contains("Common/UI/Custom/AlecsTelemetryLogo.png"),
                "TelemetryConsentPage.ui must not use the unresolved asset-pack path for the logo texture"
        );
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " must be present on the standalone artifact classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
