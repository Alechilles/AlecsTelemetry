package com.alechilles.alecstelemetry.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryEventEnvelopeTest {

    @Test
    void legacyEventJsonWithoutServerIdNormalizesToUnknownServer() {
        TelemetryEventEnvelope envelope = TelemetryEventEnvelope.fromJson(
                """
                {
                  "schemaVersion": 1,
                  "eventType": "usage",
                  "eventName": "opened_menu",
                  "eventId": "event-123",
                  "projectId": "example-mod",
                  "projectDisplayName": "Example Mod",
                  "source": "runtime_api",
                  "sessionId": "session-123",
                  "capturedAtUtc": "2026-04-14T00:00:00Z",
                  "pluginIdentifier": "Example:Example Mod",
                  "pluginVersion": "1.2.3",
                  "severity": "info",
                  "attributes": {}
                }
                """
        );

        assertEquals("unknown-server", envelope.serverId());
    }
}
