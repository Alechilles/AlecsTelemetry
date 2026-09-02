package com.alechilles.beacon.consent;

import com.alechilles.beacon.crash.CrashReportClient;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentMetricReporterTest {

    @Test
    void firstReviewReportsDiagnosticsCategory(@TempDir Path tempDir) {
        CapturingClient client = new CapturingClient();
        TelemetryConsentMetricReporter reporter = reporter(tempDir, client);

        reporter.recordFirstReview(
                project(),
                snapshot(true),
                supportedSnapshot()
        );

        assertEquals(1, client.payloads.size());
        JsonObject payload = payload(client.payloads.getFirst());
        assertEquals("diagnostics", payload.get("category").getAsString());
        assertTrue(payload.get("supported").getAsBoolean());
        assertTrue(payload.get("enabled").getAsBoolean());
        assertTrue(payload.get("previousEnabled").getAsBoolean());
        assertTrue(payload.get("firstReview").getAsBoolean());
    }

    @Test
    void consentChangeReportsDiagnosticsCategory(@TempDir Path tempDir) {
        CapturingClient client = new CapturingClient();
        TelemetryConsentMetricReporter reporter = reporter(tempDir, client);

        reporter.recordConsentChange(
                project(),
                snapshot(true),
                snapshot(false),
                supportedSnapshot()
        );

        assertEquals(1, client.payloads.size());
        JsonObject payload = payload(client.payloads.getFirst());
        assertEquals("diagnostics", payload.get("category").getAsString());
        assertTrue(payload.get("supported").getAsBoolean());
        assertFalse(payload.get("enabled").getAsBoolean());
        assertTrue(payload.get("previousEnabled").getAsBoolean());
        assertFalse(payload.get("firstReview").getAsBoolean());
    }

    private static TelemetryConsentMetricReporter reporter(Path tempDir, CapturingClient client) {
        return new TelemetryConsentMetricReporter(
                TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null),
                client,
                "test-runtime",
                null
        );
    }

    private static TelemetryProjectRegistration project() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "diagnostics-project",
                  "displayName": "Diagnostics Project",
                  "runtimeMode": "embedded",
                  "diagnostics": {
                    "supported": true,
                    "defaultEnabled": true
                  },
                  "hosted": {
                    "eventEndpoint": "https://example.invalid/ingest/event"
                  }
                }
                """,
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:diagnostics", "1.0.0", null);
    }

    private static TelemetryConsentSnapshot snapshot(boolean diagnosticsEnabled) {
        return new TelemetryConsentSnapshot(
                true, false, false, diagnosticsEnabled, false, false, false, false, false
        );
    }

    private static TelemetryConsentSnapshot supportedSnapshot() {
        return new TelemetryConsentSnapshot(
                true, false, false, true, false, false, false, false, false
        );
    }

    private static JsonObject payload(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static final class CapturingClient implements CrashReportClient {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            payloads.add(payloadJson);
            return UploadResult.success(202);
        }
    }
}
