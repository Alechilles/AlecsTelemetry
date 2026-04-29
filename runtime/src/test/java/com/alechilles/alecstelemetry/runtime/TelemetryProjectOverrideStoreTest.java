package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectOverrideStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsProjectOverrideFile() throws Exception {
        Path overridesDir = tempDir.resolve("projects");
        Files.createDirectories(overridesDir);
        Files.writeString(
                overridesDir.resolve("example-mod.json"),
                """
                {
                  "enabled": true,
                  "destinationMode": "custom",
                  "customEndpoint": {
                    "url": "https://example.com/telemetry",
                    "headers": {
                      "Authorization": "Bearer token"
                    }
                  }
                }
                """
        );

        Map<String, TelemetryProjectOverride> overrides = new TelemetryProjectOverrideStore(null).loadAll(overridesDir);

        assertTrue(overrides.containsKey("example-mod"));
        assertEquals("custom", overrides.get("example-mod").destinationMode());
        assertEquals("Bearer token", overrides.get("example-mod").customEndpoint().headers().get("Authorization"));
    }
}
