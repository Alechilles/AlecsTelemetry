package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProfilerBridgePayloadTest {

    @Test
    void roundTripsHytaleVersionAndContextValuesThroughTheJdkBridge() {
        TelemetryProfilerContext context = new TelemetryProfilerContext(
                1234L,
                "0.5.9",
                12,
                29.75d,
                11.5d,
                Map.of("companion.ai.tick", 7)
        );

        TelemetryProfilerSnapshot decoded = TelemetryProfilerBridgePayload.snapshotFromSummary(
                TelemetryProfilerBridgePayload.snapshotSummary(snapshotWith(context))
        );

        assertNotNull(decoded);
        assertEquals("0.5.9", decoded.context().hytaleVersion());
        assertEquals(12, decoded.context().playerCount());
        assertEquals(29.75d, decoded.context().tps());
        assertEquals(11.5d, decoded.context().mspt());
        assertEquals(Map.of("companion.ai.tick", 7), decoded.context().breadcrumbCategoryCounts());
    }

    @Test
    void treatsMissingHytaleVersionAsNullForMixedVersionSummaries() {
        Map<String, Object> summary = TelemetryProfilerBridgePayload.snapshotSummary(
                snapshotWith(new TelemetryProfilerContext(
                        1234L,
                        "0.5.9",
                        12,
                        29.75d,
                        11.5d,
                        Map.of("companion.ai.tick", 7)
                ))
        );
        Map<String, Object> context = new LinkedHashMap<>();
        Object rawContext = summary.get("context");
        assertTrue(rawContext instanceof Map<?, ?>);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawContext).entrySet()) {
            if (entry.getKey() instanceof String key && !"hytaleVersion".equals(key)) {
                context.put(key, entry.getValue());
            }
        }
        Map<String, Object> withoutVersion = new LinkedHashMap<>(summary);
        withoutVersion.put("context", context);

        TelemetryProfilerSnapshot decoded = TelemetryProfilerBridgePayload.snapshotFromSummary(withoutVersion);

        assertNotNull(decoded);
        assertNull(decoded.context().hytaleVersion());
    }

    private static TelemetryProfilerSnapshot snapshotWith(TelemetryProfilerContext context) {
        return new TelemetryProfilerSnapshot(
                "example-mod",
                "1.0.0",
                "spark-1.10.172",
                12,
                1234L,
                11.5d,
                "unqualified-v1",
                java.util.List.of(),
                context
        );
    }
}
