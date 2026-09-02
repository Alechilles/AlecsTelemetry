package com.alechilles.beacon.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualReportReceiptStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsReceiptHistory() {
        Path file = tempDir.resolve("receipts.json");
        ManualReportReceiptStore store = new ManualReportReceiptStore();
        ManualReportReceiptStore.Receipt receipt = new ManualReportReceiptStore.Receipt(
                "report-1",
                "example-mod",
                "issue",
                "Broken config screen",
                "2026-06-16T20:00:00Z",
                "follow-up-token",
                "queued",
                null
        );

        store.saveReceipt(file, receipt);

        List<ManualReportReceiptStore.Receipt> receipts = store.loadAll(file);
        assertEquals(1, receipts.size());
        assertEquals("report-1", receipts.getFirst().reportId());
        assertEquals("example-mod", receipts.getFirst().projectId());
        assertEquals("issue", receipts.getFirst().reportKind());
        assertEquals("follow-up-token", receipts.getFirst().followUpToken());
        assertEquals("queued", receipts.getFirst().lastKnownStatus());
    }

    @Test
    void updatesReceiptStatusWithoutDroppingFollowUpToken() {
        Path file = tempDir.resolve("receipts.json");
        ManualReportReceiptStore store = new ManualReportReceiptStore();
        store.saveReceipt(
                file,
                new ManualReportReceiptStore.Receipt(
                        "report-1",
                        "example-mod",
                        "issue",
                        "Broken config screen",
                        "2026-06-16T20:00:00Z",
                        "follow-up-token",
                        "queued",
                        null
                )
        );

        assertTrue(store.updateStatus(file, "report-1", "uploaded", "2026-06-16T20:01:00Z"));

        List<ManualReportReceiptStore.Receipt> receipts = store.loadAll(file);
        assertEquals(1, receipts.size());
        assertEquals("follow-up-token", receipts.getFirst().followUpToken());
        assertEquals("uploaded", receipts.getFirst().lastKnownStatus());
        assertEquals("2026-06-16T20:01:00Z", receipts.getFirst().lastCheckedAtUtc());
    }
}
