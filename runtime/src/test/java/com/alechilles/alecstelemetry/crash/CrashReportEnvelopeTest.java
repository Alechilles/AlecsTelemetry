package com.alechilles.alecstelemetry.crash;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                "server-123",
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
        assertEquals("server-123", json.get("serverId").getAsString());
        assertEquals("Example:Example Mod", json.get("pluginIdentifier").getAsString());
        assertTrue(json.has("breadcrumbs"));
        assertTrue(json.getAsJsonObject("environment").has("snapshotKey"));
        assertTrue(json.getAsJsonObject("throwable").has("stack"));
        assertTrue(json.getAsJsonObject("runtime").has("loadedMods"));
        assertTrue(json.getAsJsonObject("runtime").get("cpuCores").getAsInt() >= 1);
        assertTrue(json.getAsJsonObject("runtime").has("serverHostingMode"));
    }

    @Test
    void runtimeMetadataCapturesParityStatsFields() {
        String originalHostingMode = System.getProperty("alecs.telemetry.serverHostingMode");
        try {
            System.setProperty("alecs.telemetry.serverHostingMode", "local_client");

            CrashReportEnvelope.RuntimeMetadata metadata = CrashReportEnvelope.RuntimeMetadata.capture(List.of());

            assertTrue(metadata.cpuCores() >= 1);
            assertEquals("local_client", metadata.serverHostingMode());
        } finally {
            restoreProperty("alecs.telemetry.serverHostingMode", originalHostingMode);
        }
    }

    @Test
    void serializesStructuredBreadcrumbFieldsAdditively() {
        CrashReportEnvelope.BreadcrumbEntry entry = CrashReportEnvelope.BreadcrumbEntry.from(
                "2026-07-19T00:00:00Z",
                TelemetryBreadcrumbContext.builder("persistence", "incident opened")
                        .correlationId("trace-1")
                        .incidentId("incident-1")
                        .phase("APPLYING")
                        .operation("coop_release")
                        .scopeType("COOP_SLOT")
                        .failureClass("OUTCOME_UNKNOWN")
                        .disposition("SCOPED_QUARANTINE")
                        .attribute("recoveryAttempt", 2)
                        .build()
        );

        JsonObject json = JsonParser.parseString(new com.google.gson.Gson().toJson(entry)).getAsJsonObject();

        assertEquals("persistence", json.get("category").getAsString());
        assertEquals("trace-1", json.get("correlationId").getAsString());
        assertEquals("incident-1", json.get("incidentId").getAsString());
        assertEquals("APPLYING", json.get("phase").getAsString());
        assertEquals("coop_release", json.get("operation").getAsString());
        assertEquals("COOP_SLOT", json.get("scopeType").getAsString());
        assertEquals(2, json.getAsJsonObject("attributes").get("recoveryAttempt").getAsInt());
    }

    @Test
    void runtimeMetadataFallsBackToHytaleManifestVersionWhenSystemPropertiesAreAbsent() {
        String originalBuild = System.getProperty("hytale.build");
        String originalBuildId = System.getProperty("hytale.build.id");
        String originalBuildVersion = System.getProperty("hytale.build.version");
        String originalServerVersion = System.getProperty("hytale.server.version");
        String originalHytaleVersion = System.getProperty("hytale.version");
        try {
            System.clearProperty("hytale.build");
            System.clearProperty("hytale.build.id");
            System.clearProperty("hytale.build.version");
            System.clearProperty("hytale.server.version");
            System.clearProperty("hytale.version");

            CrashReportEnvelope.RuntimeMetadata metadata = CrashReportEnvelope.RuntimeMetadata.capture(List.of());

            assertNotEquals("unknown", metadata.hytaleBuild());
            assertNotEquals("unknown", metadata.serverVersion());
        } finally {
            restoreProperty("hytale.build", originalBuild);
            restoreProperty("hytale.build.id", originalBuildId);
            restoreProperty("hytale.build.version", originalBuildVersion);
            restoreProperty("hytale.server.version", originalServerVersion);
            restoreProperty("hytale.version", originalHytaleVersion);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
