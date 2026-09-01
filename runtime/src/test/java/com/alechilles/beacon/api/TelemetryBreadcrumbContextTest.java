package com.alechilles.beacon.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryBreadcrumbContextTest {

    @Test
    void normalizesStructuredPersistenceFieldsAndScalarAttributes() {
        TelemetryBreadcrumbContext context = TelemetryBreadcrumbContext.builder(
                        " persistence ",
                        " quarantine opened "
                )
                .correlationId(" trace-123 ")
                .incidentId(" incident-456 ")
                .phase(" APPLYING ")
                .operation(" coop_release ")
                .scopeType(" COOP_SLOT ")
                .failureClass(" APPLY_AMBIGUITY ")
                .disposition(" SCOPED_QUARANTINE ")
                .attribute("attempt", 2)
                .attribute("automaticRecovery", true)
                .attribute("unsupported", new Object())
                .build();

        assertEquals("persistence", context.category());
        assertEquals("quarantine opened", context.detail());
        assertEquals("trace-123", context.correlationId());
        assertEquals("incident-456", context.incidentId());
        assertEquals("APPLYING", context.phase());
        assertEquals("coop_release", context.operation());
        assertEquals("COOP_SLOT", context.scopeType());
        assertEquals("APPLY_AMBIGUITY", context.failureClass());
        assertEquals("SCOPED_QUARANTINE", context.disposition());
        assertEquals(2, context.attributes().get("attempt"));
        assertEquals(true, context.attributes().get("automaticRecovery"));
        assertFalse(context.attributes().containsKey("unsupported"));
    }

    @Test
    void boundsAmbientPayloadAndAttributeCount() {
        TelemetryBreadcrumbContext.Builder builder = TelemetryBreadcrumbContext.builder(
                "c".repeat(100),
                "d".repeat(500)
        );
        for (int i = 0; i < 20; i++) {
            builder.attribute("key-" + i, "value-" + i);
        }

        TelemetryBreadcrumbContext context = builder.build();

        assertEquals(80, context.category().length());
        assertEquals(400, context.detail().length());
        assertEquals(12, context.attributes().size());
        assertTrue(context.attributes().containsKey("key-0"));
    }
}
