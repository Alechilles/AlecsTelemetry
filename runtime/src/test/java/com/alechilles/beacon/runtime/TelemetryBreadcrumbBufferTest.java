package com.alechilles.beacon.runtime;

import com.alechilles.beacon.api.TelemetryBreadcrumbContext;
import com.alechilles.beacon.crash.CrashReportEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Test
    void concurrentWritersKeepAValidBoundedSnapshot() throws Exception {
        TelemetryBreadcrumbBuffer buffer = new TelemetryBreadcrumbBuffer(50);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < 100; i++) {
                int sequence = i;
                executor.submit(() -> {
                    start.await();
                    buffer.record("example", TelemetryBreadcrumbContext.builder("persistence", "event")
                            .correlationId("trace-" + sequence)
                            .build());
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertEquals(true, executor.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        List<CrashReportEnvelope.BreadcrumbEntry> snapshot = buffer.snapshot("example");
        assertEquals(50, snapshot.size());
        assertEquals(50, snapshot.stream().map(CrashReportEnvelope.BreadcrumbEntry::correlationId).distinct().count());
    }
}
