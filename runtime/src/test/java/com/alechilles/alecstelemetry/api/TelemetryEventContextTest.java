package com.alechilles.alecstelemetry.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryEventContextTest {

    @Test
    void buildDropsNullableDetailValuesBeforeCopying() {
        TelemetryEventContext context = TelemetryEventContext.usage()
                .detail("kept", 42)
                .detail("missing", null)
                .build();

        assertEquals(42, context.details().get("kept"));
        assertFalse(context.details().containsKey("missing"));
    }

    @Test
    void statsBuilderCreatesStatsContext() {
        TelemetryEventContext context = TelemetryEventContext.stats()
                .detail("playersOnline", 7)
                .detail("serverImplementation", "Hytale")
                .build();

        assertEquals(7, context.details().get("playersOnline"));
        assertEquals("Hytale", context.details().get("serverImplementation"));
    }
}
