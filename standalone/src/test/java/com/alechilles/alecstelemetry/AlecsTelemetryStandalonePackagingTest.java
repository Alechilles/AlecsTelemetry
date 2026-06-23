package com.alechilles.alecstelemetry;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlecsTelemetryStandalonePackagingTest {

    @Test
    void standaloneResourcesContainConsentLogoAtUiPath() {
        assertTrue(
                Files.isRegularFile(Path.of("src/main/resources/Common/UI/Custom/AlecsTelemetryLogo.png")),
                "standalone source resources should own the consent logo path"
        );
        assertResource("Common/UI/Custom/AlecsTelemetryLogo.png");
    }

    @Test
    void standaloneResourcesContainCreditorMetadata() {
        assertResource("Server/Credits/alecs_telemetry.json");
    }

    @Test
    void creditorLifecycleApiIsAvailableToStandalonePlugin() throws Exception {
        Class<?> creditor = Class.forName("com.creditor.Creditor");

        assertNotNull(creditor.getMethod("setup", com.hypixel.hytale.server.core.plugin.JavaPlugin.class));
        assertNotNull(creditor.getMethod("start", com.hypixel.hytale.server.core.plugin.JavaPlugin.class));
    }

    private void assertResource(String path) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + " should be packaged as a standalone resource");
        } catch (Exception ex) {
            throw new AssertionError("Failed to read resource " + path, ex);
        }
    }
}
