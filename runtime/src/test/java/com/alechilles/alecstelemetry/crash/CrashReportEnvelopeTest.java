package com.alechilles.alecstelemetry.crash;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashReportEnvelopeTest {

    @Test
    void serializesExpectedCrashFields() {
        RuntimeException throwable = new RuntimeException("test envelope");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.SomeSystem", "tick", "SomeSystem.java", 88)
        });

        CrashAttribution.AttributionResult attribution = CrashAttribution.classify(
                throwable,
                List.of("Example:Example Mod"),
                List.of("com.example.telemetry"),
                null
        );

        CrashReportEnvelope envelope = CrashReportEnvelope.create(
                "example-mod",
                "Example Mod",
                "unit_test",
                "session-123",
                attribution.fingerprint(),
                "Example:Example Mod",
                "1.2.3",
                "MainThread",
                "Overworld",
                "EXCEPTIONAL",
                "Example:Example Mod",
                CrashReportEnvelope.EnvironmentSnapshot.capture(
                        "example-mod",
                        "Example:Example Mod",
                        "1.2.3",
                        "dependency",
                        CrashReportEnvelope.RuntimeMetadata.capture(
                                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"))
                        )
                ),
                attribution,
                List.of(new CrashReportEnvelope.BreadcrumbEntry("2026-04-14T00:00:00Z", "bootstrap", "Initialized example mod.")),
                throwable,
                CrashReportEnvelope.RuntimeMetadata.capture(
                        List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"))
                )
        );

        JsonObject json = JsonParser.parseString(envelope.toJson()).getAsJsonObject();
        assertEquals(CrashReportEnvelope.SCHEMA_VERSION, json.get("schemaVersion").getAsInt());
        assertEquals("crash", json.get("eventType").getAsString());
        assertEquals("example-mod", json.get("projectId").getAsString());
        assertEquals("session-123", json.get("sessionId").getAsString());
        assertEquals("Example:Example Mod", json.get("pluginIdentifier").getAsString());
        assertTrue(json.has("breadcrumbs"));
        assertTrue(json.getAsJsonObject("environment").has("snapshotKey"));
        assertTrue(json.getAsJsonObject("throwable").has("stack"));
        assertTrue(json.getAsJsonObject("runtime").has("loadedMods"));
    }
}
