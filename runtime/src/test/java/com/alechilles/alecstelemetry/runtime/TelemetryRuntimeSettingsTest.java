package com.alechilles.alecstelemetry.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    void sparkProfilerRequiresExplicitOptInByDefault() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("runtime.json"),
                null
        );

        TelemetryRuntimeSettings.SparkProfilerSettings profiler = settings.sparkProfiler();

        assertFalse(profiler.enabled());
        assertEquals(90, profiler.initialDelaySeconds());
        assertEquals(300, profiler.intervalSeconds());
        assertEquals(10, profiler.timeoutSeconds());
        assertEquals(5, profiler.maxSummaryEntries());
        assertEquals(10, profiler.maxHistorySnapshots());
    }

    @Test
    void sparkProfilerReadsBoundedOperatorSettings() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfiler": {
                    "enabled": true,
                    "initialDelaySeconds": 1,
                    "intervalSeconds": 1,
                    "timeoutSeconds": 500,
                    "maxSummaryEntries": 50,
                    "maxHistorySnapshots": 50
                  }
                }
                """);

        TelemetryRuntimeSettings.SparkProfilerSettings settings =
                TelemetryRuntimeSettings.load(settingsFile, null).sparkProfiler();

        assertTrue(settings.enabled());
        assertEquals(60, settings.initialDelaySeconds());
        assertEquals(300, settings.intervalSeconds());
        assertEquals(60, settings.timeoutSeconds());
        assertEquals(10, settings.maxSummaryEntries());
        assertEquals(10, settings.maxHistorySnapshots());
    }

    @Test
    void sparkProfilerCanaryJsonRemainsAcceptedAsCompatibilityAlias() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfilerCanary": {
                    "enabled": true,
                    "initialDelaySeconds": 120,
                    "timeoutSeconds": 20,
                    "maxSummaryEntries": 7
                  }
                }
                """);

        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);

        assertTrue(settings.sparkProfiler().enabled());
        assertEquals(120, settings.sparkProfiler().initialDelaySeconds());
        assertEquals(300, settings.sparkProfiler().intervalSeconds());
        assertEquals(20, settings.sparkProfiler().timeoutSeconds());
        assertEquals(7, settings.sparkProfiler().maxSummaryEntries());
        assertEquals(10, settings.sparkProfiler().maxHistorySnapshots());
        assertEquals(settings.sparkProfiler().enabled(), settings.sparkProfilerCanary().enabled());
        assertEquals(settings.sparkProfiler().initialDelaySeconds(), settings.sparkProfilerCanary().initialDelaySeconds());
        assertEquals(settings.sparkProfiler().timeoutSeconds(), settings.sparkProfilerCanary().timeoutSeconds());
        assertEquals(settings.sparkProfiler().maxSummaryEntries(), settings.sparkProfilerCanary().maxSummaryEntries());
    }

    @Test
    void canonicalSparkProfilerWinsWhenLegacyAliasIsAlsoPresent() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfiler": {
                    "enabled": true,
                    "initialDelaySeconds": 300,
                    "intervalSeconds": 90,
                    "timeoutSeconds": 15,
                    "maxSummaryEntries": 6,
                    "maxHistorySnapshots": 8
                  },
                  "sparkProfilerCanary": {
                    "enabled": false,
                    "initialDelaySeconds": 3600,
                    "timeoutSeconds": 1,
                    "maxSummaryEntries": 1
                  }
                }
                """);

        TelemetryRuntimeSettings.SparkProfilerSettings settings =
                TelemetryRuntimeSettings.load(settingsFile, null).sparkProfiler();

        assertTrue(settings.enabled());
        assertEquals(300, settings.initialDelaySeconds());
        assertEquals(300, settings.intervalSeconds());
        assertEquals(15, settings.timeoutSeconds());
        assertEquals(6, settings.maxSummaryEntries());
        assertEquals(8, settings.maxHistorySnapshots());
    }

    @Test
    void explicitNullCanonicalSparkProfilerWinsOverLegacyAlias() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfiler": null,
                  "sparkProfilerCanary": {
                    "enabled": true,
                    "initialDelaySeconds": 300,
                    "timeoutSeconds": 15,
                    "maxSummaryEntries": 6
                  }
                }
                """);

        TelemetryRuntimeSettings.SparkProfilerSettings settings =
                TelemetryRuntimeSettings.load(settingsFile, null).sparkProfiler();

        assertFalse(settings.enabled());
        assertEquals(90, settings.initialDelaySeconds());
        assertEquals(300, settings.intervalSeconds());
        assertEquals(10, settings.timeoutSeconds());
        assertEquals(5, settings.maxSummaryEntries());
        assertEquals(10, settings.maxHistorySnapshots());
    }

    @Test
    void sparkProfilerClampsUpperDelayAndIntervalAndLowerOperatorLimits() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "sparkProfiler": {
                    "initialDelaySeconds": 9999,
                    "intervalSeconds": 9999,
                    "timeoutSeconds": 0,
                    "maxSummaryEntries": 0,
                    "maxHistorySnapshots": 0
                  }
                }
                """);

        TelemetryRuntimeSettings.SparkProfilerSettings settings =
                TelemetryRuntimeSettings.load(settingsFile, null).sparkProfiler();

        assertEquals(3600, settings.initialDelaySeconds());
        assertEquals(3600, settings.intervalSeconds());
        assertEquals(1, settings.timeoutSeconds());
        assertEquals(1, settings.maxSummaryEntries());
        assertEquals(1, settings.maxHistorySnapshots());
    }

    @Test
    void newTemplateWritesOnlyCanonicalSparkProfilerBlock() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");

        TelemetryRuntimeSettings.load(settingsFile, null);

        JsonObject document = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
        assertTrue(document.has("sparkProfiler"));
        assertFalse(document.has("sparkProfilerCanary"));
        assertEquals(300, document.getAsJsonObject("sparkProfiler").get("intervalSeconds").getAsInt());
        assertEquals(10, document.getAsJsonObject("sparkProfiler").get("maxHistorySnapshots").getAsInt());
    }
}
