package com.alechilles.alecstelemetry.diagnostic;

import com.alechilles.alecstelemetry.api.TelemetryDiagnosticAttachment;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticDisposition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryDiagnosticBundleEnvelopeTest {

    @Test
    void serializesGeneralDiagnosticContractWithoutManualReportFields() {
        TelemetryDiagnosticBundle bundle = new TelemetryDiagnosticBundle(
                "11111111-2222-3333-4444-555555555555",
                "2026-08-30T12:00:00Z",
                "automatic",
                "persistence_failure",
                "Tamework persistence failure",
                "Tamework detected a final persistence failure.",
                "error",
                TelemetryDiagnosticDisposition.createOrJoinIssue("safe-fingerprint"),
                Map.of("subsystem", "persistence"),
                List.of(TelemetryDiagnosticAttachment.binary(
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        "tamework_debugdb_export",
                        "tamework-persistence-example.zip",
                        "application/zip",
                        new byte[]{0x50, 0x4b, 0x03, 0x04}
                ))
        );

        TelemetryDiagnosticBundleEnvelope envelope = TelemetryDiagnosticBundleEnvelope.create(
                "alecs-tamework",
                "AlecHilles:AlectsTamework",
                "3.4.0",
                "example-session-hash",
                "example-server-hash",
                bundle
        );
        JsonObject json = JsonParser.parseString(envelope.toJson()).getAsJsonObject();

        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("diagnostic_bundle", json.get("eventType").getAsString());
        assertEquals("persistence_failure", json.get("diagnosticKind").getAsString());
        assertEquals("create_or_join_issue",
                json.getAsJsonObject("disposition").get("mode").getAsString());
        assertEquals("base64", json.getAsJsonArray("attachments").get(0)
                .getAsJsonObject().get("contentEncoding").getAsString());
        assertFalse(json.has("contact"));
        assertFalse(json.has("followUpTokenHash"));
        assertFalse(json.has("reportKind"));
    }
}
