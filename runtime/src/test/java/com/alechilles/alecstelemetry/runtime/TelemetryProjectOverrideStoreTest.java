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

    @Test
    void savesProjectAndBreadcrumbControlsWithoutRemovingOtherFields() throws Exception {
        Path overrideFile = tempDir.resolve("projects").resolve("example-mod.json");
        Files.createDirectories(overrideFile.getParent());
        Files.writeString(
                overrideFile,
                """
                {
                  "destinationMode": "custom",
                  "customEndpoint": {
                    "url": "https://example.com/telemetry"
                  }
                }
                """
        );
        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(null);

        assertTrue(store.saveProjectEnabled(overrideFile, false));
        assertTrue(store.saveCrashEnabled(overrideFile, false));
        assertTrue(store.saveErrorEventsEnabled(overrideFile, false));
        assertTrue(store.saveLifecycleEventsEnabled(overrideFile, false));
        assertTrue(store.savePerformanceEnabled(overrideFile, false));
        assertTrue(store.saveUsageEnabled(overrideFile, false));
        assertTrue(store.saveStatsEnabled(overrideFile, false));
        assertTrue(store.saveBreadcrumbsEnabled(overrideFile, false));

        TelemetryProjectOverride override = store.load(overrideFile);
        assertEquals(false, override.enabled());
        assertEquals(false, override.capture().uncaughtExceptions());
        assertEquals(false, override.capture().setupFailures());
        assertEquals(false, override.capture().startFailures());
        assertEquals(false, override.capture().exceptionalWorldRemovals());
        assertEquals(false, override.events().errors().enabled());
        assertEquals(false, override.events().lifecycle().enabled());
        assertEquals(false, override.events().breadcrumbs().enabled());
        assertEquals(false, override.performance().enabled());
        assertEquals(false, override.usage().enabled());
        assertEquals(false, override.stats().enabled());
        assertEquals("custom", override.destinationMode());
        assertEquals("https://example.com/telemetry", override.customEndpoint().url());
    }
}
