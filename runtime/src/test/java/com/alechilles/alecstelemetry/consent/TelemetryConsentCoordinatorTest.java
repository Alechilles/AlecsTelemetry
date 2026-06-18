package com.alechilles.alecstelemetry.consent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentCoordinatorTest {

    @Test
    void firstRunConsentSendsChatNoticeInsteadOfOpeningUi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCoordinator.java"
        )).replace("\r\n", "\n");
        String onPlayerReady = source.substring(
                source.indexOf("public void onPlayerReady"),
                source.indexOf("public boolean openConsentPage")
        );

        assertTrue(
                source.contains("One or more mods are using Alec's Telemetry to report anonymous statistics and/or crash, error, performance diagnostics. You may change your telemetry consent settings at any time using `/telemetry consent`."),
                "The first-run chat notice must use the requested consent wording"
        );
        assertTrue(
                source.contains("playerRef.sendMessage(Message.raw(FIRST_RUN_NOTICE).color(FIRST_RUN_NOTICE_COLOR));"),
                "The first-run notice must be sent as a colored chat message"
        );
        assertTrue(
                source.contains("private static final String FIRST_RUN_NOTICE_COLOR = \"#f0a33a\";"),
                "The first-run notice must be orange"
        );
        assertFalse(
                onPlayerReady.contains("openConsentPage("),
                "First-run consent should notify in chat instead of opening the consent UI"
        );
        assertTrue(
                source.contains("public boolean openConsentPage"),
                "`/telemetry consent` should still be able to open the consent UI explicitly"
        );
    }
}
