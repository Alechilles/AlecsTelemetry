package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.api.TelemetryBreadcrumbContext;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryBreadcrumbBufferTest {

    @Test
    void preservesStructuredBreadcrumbContext() {
        TelemetryBreadcrumbBuffer buffer = new TelemetryBreadcrumbBuffer(5);

        buffer.record("alecs-tamework", TelemetryBreadcrumbContext.builder("persistence", "incident opened")
                .correlationId("trace-1")
                .incidentId("incident-1")
                .phase("COMMIT")
                .operation("breeding_birth")
                .scopeType("BREEDING_ATTEMPT")
                .failureClass("OUTCOME_UNKNOWN")
                .disposition("SCOPED_QUARANTINE")
                .attribute("attempt", 1)
                .build());

        List<CrashReportEnvelope.BreadcrumbEntry> snapshot = buffer.snapshot("ALECS-TAMEWORK");
        assertEquals(1, snapshot.size());
        CrashReportEnvelope.BreadcrumbEntry entry = snapshot.getFirst();
        assertEquals("trace-1", entry.correlationId());
        assertEquals("incident-1", entry.incidentId());
        assertEquals("COMMIT", entry.phase());
        assertEquals("breeding_birth", entry.operation());
        assertEquals("BREEDING_ATTEMPT", entry.scopeType());
        assertEquals("OUTCOME_UNKNOWN", entry.failureClass());
        assertEquals("SCOPED_QUARANTINE", entry.disposition());
        assertEquals(1, entry.attributes().get("attempt"));
    }

    @Test
    void legacyBreadcrumbsRetainThreeArgumentContract() {
        TelemetryBreadcrumbBuffer buffer = new TelemetryBreadcrumbBuffer(2);

        buffer.record("example", "bootstrap", "started");

        CrashReportEnvelope.BreadcrumbEntry entry = buffer.snapshot("example").getFirst();
        assertEquals("bootstrap", entry.category());
        assertEquals("started", entry.detail());
        assertEquals(0, entry.attributes().size());
    }
}
