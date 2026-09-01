package com.alechilles.beacon.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void preservesBoundedJsonCompatibleNestedDetailValues() {
        TelemetryEventContext context = TelemetryEventContext.stats()
                .detail("projects", List.of(
                        Map.of("projectId", "example-mod", "playersOnline", 2),
                        Map.of("projectId", "second-mod")
                ))
                .detail("unsupported", new Object())
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) context.details().get("projects");

        assertEquals(2, projects.size());
        assertEquals("example-mod", projects.getFirst().get("projectId"));
        assertEquals(2, projects.getFirst().get("playersOnline"));
        assertFalse(context.details().containsKey("unsupported"));
    }
}
