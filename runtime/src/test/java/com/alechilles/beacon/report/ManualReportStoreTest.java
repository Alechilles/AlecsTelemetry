package com.alechilles.beacon.report;

import com.alechilles.beacon.crash.CrashReportEnvelope;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualReportStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesPendingAndReviewReports() throws Exception {
        TelemetryDataPaths paths = paths();
        ManualReportStore store = new ManualReportStore(paths);
        ManualReportEnvelope envelope = envelope("Pending report");

        Path pending = store.savePending(envelope);
        Path review = store.saveForReview(envelope);

        assertTrue(Files.isRegularFile(pending));
        assertTrue(pending.startsWith(paths.pendingManualReportsDirectory("example-mod")));
        assertTrue(Files.isRegularFile(review));
        assertTrue(review.startsWith(paths.reviewManualReportsDirectory("example-mod")));
    }

    @Test
    void approvesAndRejectsReviewedReports() throws Exception {
        TelemetryDataPaths paths = paths();
        ManualReportStore store = new ManualReportStore(paths);
        ManualReportEnvelope approved = envelope("Approved report");
        ManualReportEnvelope rejected = envelope("Rejected report");
        store.saveForReview(approved);
        store.saveForReview(rejected);

        Path approvedPath = store.approveReviewed("example-mod", approved.reportId()).orElseThrow();
        Path rejectedPath = store.rejectReviewed("example-mod", rejected.reportId()).orElseThrow();

        assertTrue(approvedPath.startsWith(paths.pendingManualReportsDirectory("example-mod")));
        assertTrue(Files.isRegularFile(approvedPath));
        assertTrue(rejectedPath.startsWith(paths.rejectedManualReportsDirectory("example-mod")));
        assertTrue(Files.isRegularFile(rejectedPath));
        assertFalse(Files.list(paths.reviewManualReportsDirectory("example-mod")).findAny().isPresent());
    }

    @Test
    void appendsSubmittedAuditLog() throws Exception {
        TelemetryDataPaths paths = paths();
        ManualReportStore store = new ManualReportStore(paths);
        ManualReportEnvelope envelope = envelope("Audit report");

        store.appendSubmittedAudit(envelope, "approved", true);

        List<String> lines = Files.readAllLines(paths.submittedManualReportsLog());
        assertEquals(1, lines.size());
        JsonObject audit = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        assertEquals(envelope.reportId(), audit.get("reportId").getAsString());
        assertEquals("example-mod", audit.get("projectId").getAsString());
        assertEquals("issue", audit.get("reportKind").getAsString());
        assertEquals("approved", audit.get("reviewState").getAsString());
        assertTrue(audit.get("uploaded").getAsBoolean());
    }

    private TelemetryDataPaths paths() {
        Path runtimeRoot = tempDir.resolve("runtime");
        return new TelemetryDataPaths(
                runtimeRoot,
                runtimeRoot.resolve("Settings").resolve("runtime.json"),
                runtimeRoot.resolve("Settings").resolve("projects"),
                runtimeRoot.resolve("Telemetry"),
                runtimeRoot.resolve("Telemetry").resolve("crash-reports"),
                runtimeRoot.resolve("Telemetry").resolve("events"),
                null
        );
    }

    private ManualReportEnvelope envelope(String title) {
        ManualReportEnvelope.CreateResult result = ManualReportEnvelope.create(
                registration(),
                TelemetryRuntimeSettings.load(tempDir.resolve(title.replace(' ', '-') + "-runtime.json"), null),
                new ManualReportSubmission(
                        ManualReportKind.ISSUE,
                        title,
                        "Description",
                        null,
                        Map.of("severity", "major"),
                        false,
                        false,
                        true,
                        true,
                        false
                ),
                new ManualReportContextSnapshot("singleplayer", 1, true, true),
                List.of(),
                CrashReportEnvelope.EnvironmentSnapshot.capture("example-mod", "Example:Example Mod", "1.2.3", "dependency", runtime()),
                runtime()
        );
        return result.envelope();
    }

    private static TelemetryProjectRegistration registration() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "reports": {
                    "enabled": true,
                    "issue": {
                      "enabled": true,
                      "fields": {
                        "severity": {
                          "type": "enum",
                          "label": "Severity",
                          "required": true,
                          "values": ["minor", "moderate", "major", "blocking"]
                        }
                      }
                    }
                  }
                }
                """,
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Example Mod", "1.2.3", null);
    }

    private static CrashReportEnvelope.RuntimeMetadata runtime() {
        return CrashReportEnvelope.RuntimeMetadata.capture(List.of());
    }
}
