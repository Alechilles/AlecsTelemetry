package com.alechilles.beacon.runtime;

import com.alechilles.beacon.api.TelemetryBreadcrumbContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryBreadcrumbBridgePayloadTest {

    @Test
    void roundTripsStructuredFieldsThroughClassloaderSafeMap() {
        TelemetryBreadcrumbContext original = TelemetryBreadcrumbContext.builder("persistence", "recovery failed")
                .correlationId("trace-1")
                .incidentId("incident-1")
                .phase("RECOVERY_EVIDENCE_EVALUATED")
                .operation("capture_release")
                .scopeType("PROFILE")
                .failureClass("CONTRADICTORY_EVIDENCE")
                .disposition("SCOPED_QUARANTINE")
                .attribute("attempt", 3)
                .build();

        TelemetryBreadcrumbContext restored = TelemetryBreadcrumbBridgePayload.fromSummary(
                original.category(),
                original.detail(),
                TelemetryBreadcrumbBridgePayload.summary(original)
        );

        assertEquals(original, restored);
    }

    @Test
    void rejectsNonScalarBridgeAttributes() {
        TelemetryBreadcrumbContext restored = TelemetryBreadcrumbBridgePayload.fromSummary(
                "persistence",
                "incident",
                Map.of("attributes", Map.of("unsafe", new Object()))
        );

        assertFalse(restored.attributes().containsKey("unsafe"));
    }
}
