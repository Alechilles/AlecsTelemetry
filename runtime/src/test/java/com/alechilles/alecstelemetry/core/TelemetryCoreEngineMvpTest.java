package com.alechilles.alecstelemetry.core;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportKind;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoreEngineMvpTest {

    @TempDir
    Path tempDir;

    @Test
    void retiredManualReportRegistrationStillSupportsReviewAndFlushButRejectsNewSubmission() throws Exception {
        Path settingsFile = tempDir.resolve("Settings").resolve("runtime.json");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, """
                {
                  "manualReports": {
                    "manualReviewRequired": true
                  }
                }
                """);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);
        TelemetryDataPaths dataPaths = dataPaths(settings);
        TelemetryProjectRegistration project = registration();
        RecordingClient client = new RecordingClient();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths,
                List.of(project),
                List.of(project),
                List.of(),
                client,
                null,
                null
        );

        ManualReportEnvelope.CreateResult created = engine.submitManualReport(
                project.projectId(),
                new ManualReportSubmission(
                        ManualReportKind.ISSUE,
                        "Queued before retirement",
                        "The report must remain reviewable.",
                        null,
                        Map.of("severity", "major"),
                        false,
                        false,
                        false,
                        false,
                        false
                ),
                PlayerReportRuntimeContext.UNKNOWN
        );
        assertTrue(created.accepted());

        assertTrue(engine.reconcileProjects(List.of(), List.of()));
        assertEquals(1, engine.manualReportsForReview(10).size());
        assertTrue(engine.approveManualReport(created.envelope().reportId()));
        assertEquals(1, engine.pendingReports(null));

        TelemetryCoreEngine.FlushSummary summary = engine.flushPendingReportsNow("manual");
        assertEquals(1, summary.attempted());
        assertEquals(1, summary.uploaded());
        assertEquals(0, engine.pendingReports(null));

        ManualReportEnvelope.CreateResult rejected = engine.submitManualReport(
                project.projectId(),
                new ManualReportSubmission(
                        ManualReportKind.ISSUE,
                        "Rejected after retirement",
                        "No new report should use a retired registration.",
                        null,
                        Map.of("severity", "major"),
                        false,
                        false,
                        false,
                        false,
                        false
                ),
                PlayerReportRuntimeContext.UNKNOWN
        );
        assertFalse(rejected.accepted());
        assertTrue(rejected.validationErrors().contains("project_not_found"));
    }

    private TelemetryDataPaths dataPaths(TelemetryRuntimeSettings settings) {
        return new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                null
        );
    }

    private static TelemetryProjectRegistration registration() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson("""
                {
                  "projectId": "retired-report",
                  "displayName": "Retired Report",
                  "runtimeMode": "standalone",
                  "ownerPluginIdentifiers": ["Example:Retired Report"],
                  "packagePrefixes": ["com.example.retired"],
                  "reports": {
                    "enabled": true,
                    "issue": {
                      "enabled": true,
                      "fields": {
                        "severity": {
                          "type": "enum",
                          "label": "Severity",
                          "required": true,
                          "values": ["minor", "major", "blocking"]
                        }
                      }
                    }
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """, null);
        return new TelemetryProjectRegistration(
                descriptor,
                "Example:Retired Report",
                "1.0.0",
                Path.of("retired-report.jar")
        );
    }

    private static final class RecordingClient implements CrashReportClient {
        private int calls;

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            calls++;
            return UploadResult.success(204);
        }
    }
}
