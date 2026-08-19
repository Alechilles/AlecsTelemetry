package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void sparkProfilerCanaryRequiresExplicitOptIn() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("runtime.json"),
                null
        );

        assertFalse(settings.sparkProfilerCanary().enabled());
    }

    @Test
    void sparkProfilerCanaryReadsBoundedOperatorSettings() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfilerCanary": {
                    "enabled": true,
                    "initialDelaySeconds": 1,
                    "timeoutSeconds": 500,
                    "maxSummaryEntries": 50
                  }
                }
                """);

        TelemetryRuntimeSettings.SparkProfilerCanarySettings settings =
                TelemetryRuntimeSettings.load(settingsFile, null).sparkProfilerCanary();

        assertTrue(settings.enabled());
        assertEquals(60, settings.initialDelaySeconds());
        assertEquals(60, settings.timeoutSeconds());
        assertEquals(10, settings.maxSummaryEntries());
    }
}
