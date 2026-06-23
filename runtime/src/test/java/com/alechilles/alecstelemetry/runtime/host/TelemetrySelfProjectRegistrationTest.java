package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetrySelfProjectRegistrationTest {

    @Test
    void standaloneSelfProjectReportsCrashErrorsAndStatsByDefault() {
        TelemetryProjectRegistration registration = TelemetrySelfProjectRegistration.create(
                "Alechilles:Alec's Telemetry",
                "0.2.5",
                Path.of("Alec's Telemetry.jar"),
                null
        );

        assertEquals("alecs-telemetry", registration.projectId());
        assertEquals("Alec's Telemetry", registration.displayName());
        assertEquals("dependency", registration.runtimeMode());
        assertEquals("Alechilles:Alec's Telemetry", registration.pluginIdentifier());
        assertEquals("0.2.5", registration.pluginVersion());
        assertEquals(Path.of("Alec's Telemetry.jar"), registration.sourcePath());
        assertEquals("hosted", registration.destinationMode());
        assertEquals("proj_aqocXOn4Gw3unr0p22QoNd3k", registration.descriptor().hosted().projectKey());
        assertEquals("Common/UI/Custom/AlecsTelemetryLogo.png", registration.descriptor().ui().iconTexturePath());
        assertTrue(registration.stats().enabled());
        assertTrue(registration.stats().allows("heartbeat"));
        assertTrue(registration.isCrashTelemetryEnabled());
        assertTrue(registration.capture().uncaughtExceptions());
        assertTrue(registration.capture().setupFailures());
        assertTrue(registration.capture().startFailures());
        assertTrue(registration.capture().exceptionalWorldRemovals());
        assertTrue(registration.events().errors().enabled());
        assertEquals(
                Map.of("component", "self_runtime", "operationKind", "command_register"),
                registration.events().errors().sanitizeDetails(
                        "runtime_command_register_failed",
                        Map.of(
                                "component", "self_runtime",
                                "operationKind", "command_register",
                                "ignored", "drop"
                        )
                )
        );
        assertFalse(registration.events().lifecycle().enabled());
        assertFalse(registration.performance().enabled());
        assertFalse(registration.usage().enabled());
        assertFalse(registration.events().breadcrumbs().enabled());
    }

    @Test
    void standaloneSelfProjectHonorsStatsOverride() {
        TelemetryProjectOverride override = TelemetryProjectOverride.fromJson("""
                {
                  "stats": { "enabled": false }
                }
                """);

        TelemetryProjectRegistration registration = TelemetrySelfProjectRegistration.create(
                "Alechilles:Alec's Telemetry",
                "0.2.5",
                Path.of("Alec's Telemetry.jar"),
                override
        );

        assertTrue(registration.hasOverride());
        assertFalse(registration.stats().enabled());
    }
}
