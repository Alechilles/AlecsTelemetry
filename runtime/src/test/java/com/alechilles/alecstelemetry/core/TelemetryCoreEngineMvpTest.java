package com.alechilles.alecstelemetry.core;

import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticAttachment;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundleResult;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticDisposition;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCoreEngineMvpTest {

    @TempDir
    Path tempDir;

    @Test
    void diagnosticBundleUsesDurableEventQueue() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryProjectRegistration project = registration();
        TelemetryDataPaths paths = dataPaths(settings);
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                paths,
                List.of(project),
                List.of(),
                new RecordingClient(),
                null,
                null
        );

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(TelemetryDiagnosticDisposition.createOrJoinIssue("safe-fingerprint"))
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.QUEUED, result.status());
        assertEquals(1, engine.pendingReports(project.projectId()));
        Path queued = Files.list(paths.pendingEventsDirectory(project.projectId()))
                .findFirst()
                .orElseThrow();
        String payload = Files.readString(queued);
        assertTrue(payload.contains("\"eventType\": \"diagnostic_bundle\""));
        assertFalse(payload.contains("followUpTokenHash"));
    }

    @Test
    void diagnosticBundleUsesIndependentConsent() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"), null
        );
        TelemetryProjectRegistration project = registration();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings, dataPaths(settings), List.of(project), List.of(),
                new RecordingClient(), null, null
        );
        engine.setErrorEventsEnabled(project.projectId(), true);
        engine.setDiagnosticsEnabled(project.projectId(), false);

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(TelemetryDiagnosticDisposition.informational())
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.DISABLED, result.status());
        assertEquals("diagnostic_telemetry_disabled", result.detail());
        assertEquals(0, engine.pendingReports(project.projectId()));

        engine.setErrorEventsEnabled(project.projectId(), false);
        engine.setDiagnosticsEnabled(project.projectId(), true);

        result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(TelemetryDiagnosticDisposition.informational())
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.QUEUED, result.status());
        assertEquals(1, engine.pendingReports(project.projectId()));
    }

    @Test
    void diagnosticBundleRejectsWhenRuntimeIsDisabled() throws Exception {
        Path settingsFile = tempDir.resolve("Settings").resolve("runtime.json");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, "{\"enabled\":false}");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);
        TelemetryProjectRegistration project = registration();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings, dataPaths(settings), List.of(project), List.of(),
                new RecordingClient(), null, null
        );
        engine.setDiagnosticsEnabled(project.projectId(), true);

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(TelemetryDiagnosticDisposition.informational())
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.DISABLED, result.status());
        assertEquals("event_telemetry_disabled", result.detail());
        assertEquals(0, engine.pendingReports(project.projectId()));
    }

    @Test
    void diagnosticBundleRejectsUnsupportedDiagnosticsEvenWhenOverrideIsEnabled() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"), null
        );
        TelemetryProjectRegistration project = registration(false);
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings, dataPaths(settings), List.of(project), List.of(),
                new RecordingClient(), null, null
        );
        engine.setDiagnosticsEnabled(project.projectId(), true);

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(TelemetryDiagnosticDisposition.informational())
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.DISABLED, result.status());
        assertEquals("diagnostic_telemetry_disabled", result.detail());
        assertEquals(0, engine.pendingReports(project.projectId()));
    }

    @Test
    void diagnosticBundleRejectsIssueDispositionWithoutFingerprint() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"),
                null
        );
        TelemetryProjectRegistration project = registration();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths(settings),
                List.of(project),
                List.of(),
                new RecordingClient(),
                null,
                null
        );

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(),
                diagnosticBundle(new TelemetryDiagnosticDisposition("create_or_join_issue", null))
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals(0, engine.pendingReports(project.projectId()));
    }

    @Test
    void diagnosticBundleRejectsUnsafeAttachmentFileName() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"), null
        );
        TelemetryProjectRegistration project = registration();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings, dataPaths(settings), List.of(project), List.of(),
                new RecordingClient(), null, null
        );
        TelemetryDiagnosticBundle base = diagnosticBundle(
                TelemetryDiagnosticDisposition.informational()
        );
        TelemetryDiagnosticBundle unsafe = new TelemetryDiagnosticBundle(
                base.diagnosticId(), base.capturedAtUtc(), base.source(),
                base.diagnosticKind(), base.title(), base.summary(),
                base.severity(), base.disposition(), base.attributes(),
                List.of(TelemetryDiagnosticAttachment.binary(
                        "attachment-1", "database_export", "../../database.zip",
                        "application/zip", new byte[]{1}
                ))
        );

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                project.projectId(), unsafe
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals("diagnostic_attachment_file_name_invalid", result.detail());
        assertEquals(0, engine.pendingReports(project.projectId()));
    }

    @Test
    void diagnosticBundleRejectsNestedAttributeValue() {
        TelemetryCoreEngine engine = diagnosticEngine();
        TelemetryDiagnosticBundle bundle = diagnosticBundle(
                "diagnostic-nested",
                Map.of("context", Map.of("private", "nested"))
        );

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                registration().projectId(), bundle
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals("diagnostic_attribute_value_invalid", result.detail());
    }

    @Test
    void diagnosticBundleRejectsExcessiveAttributeCount() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            attributes.put("attribute-" + index, index);
        }
        TelemetryCoreEngine engine = diagnosticEngine();

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                registration().projectId(),
                diagnosticBundle("diagnostic-count", attributes)
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals("diagnostic_attribute_count_exceeded", result.detail());
    }

    @Test
    void diagnosticBundleRejectsOversizedStringAttribute() {
        TelemetryCoreEngine engine = diagnosticEngine();

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                registration().projectId(),
                diagnosticBundle(
                        "diagnostic-value-size",
                        Map.of("detail", "x".repeat(257))
                )
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals("diagnostic_attribute_value_too_long", result.detail());
    }

    @Test
    void diagnosticBundleRejectsNonStringCharacterSequence() {
        TelemetryCoreEngine engine = diagnosticEngine();

        TelemetryDiagnosticBundleResult result = engine.submitDiagnosticBundle(
                registration().projectId(),
                diagnosticBundle(
                        "diagnostic-character-sequence",
                        Map.of("detail", new StringBuilder("nested-value"))
                )
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.REJECTED, result.status());
        assertEquals("diagnostic_attribute_value_invalid", result.detail());
    }

    private TelemetryCoreEngine diagnosticEngine() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                tempDir.resolve("Settings").resolve("runtime.json"), null
        );
        return new TelemetryCoreEngine(
                settings, dataPaths(settings), List.of(registration()), List.of(),
                new RecordingClient(), null, null
        );
    }

    private static TelemetryDiagnosticBundle diagnosticBundle(
            TelemetryDiagnosticDisposition disposition
    ) {
        return diagnosticBundle(
                "diagnostic-1", Map.of("subsystem", "persistence"), disposition
        );
    }

    private static TelemetryDiagnosticBundle diagnosticBundle(
            String diagnosticId,
            Map<String, Object> attributes
    ) {
        return diagnosticBundle(
                diagnosticId, attributes,
                TelemetryDiagnosticDisposition.informational()
        );
    }

    private static TelemetryDiagnosticBundle diagnosticBundle(
            String diagnosticId,
            Map<String, Object> attributes,
            TelemetryDiagnosticDisposition disposition
    ) {
        return new TelemetryDiagnosticBundle(
                diagnosticId,
                "2026-08-30T12:00:00Z",
                "automatic",
                "persistence_failure",
                "Persistence failure",
                "Safe summary",
                "error",
                disposition,
                attributes,
                List.of()
        );
    }

    @Test
    void blockedAsyncUploadDoesNotDelaySchedulerTasks() throws Exception {
        Path settingsFile = tempDir.resolve("Settings").resolve("runtime.json");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);
        TelemetryProjectRegistration project = registration();
        BlockingClient client = new BlockingClient();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "test-scheduler")
        );
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths(settings),
                List.of(project),
                List.of(project),
                List.of(),
                client,
                null,
                scheduler
        );

        try {
            ManualReportEnvelope.CreateResult created = engine.submitManualReport(
                    project.projectId(),
                    new ManualReportSubmission(
                            ManualReportKind.ISSUE,
                            "Blocked upload",
                            "The scheduler must remain available while this upload waits.",
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
            assertTrue(client.uploadStarted.await(2, TimeUnit.SECONDS));
            assertEquals(
                    "test-scheduler",
                    scheduler.submit(() -> Thread.currentThread().getName()).get(1, TimeUnit.SECONDS)
            );
        } finally {
            client.releaseUpload.countDown();
            engine.flushPendingReportsNow("test-cleanup");
            scheduler.shutdown();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdownWaitsForAsyncFlushWithoutUploadingPendingReportTwice() throws Exception {
        Path settingsFile = tempDir.resolve("Settings").resolve("runtime.json");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);
        TelemetryProjectRegistration project = registration();
        BlockingClient client = new BlockingClient();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths(settings),
                List.of(project),
                List.of(project),
                List.of(),
                client,
                null,
                scheduler
        );
        CountDownLatch shutdownComplete = new CountDownLatch(1);

        try {
            ManualReportEnvelope.CreateResult created = engine.submitManualReport(
                    project.projectId(),
                    new ManualReportSubmission(
                            ManualReportKind.ISSUE,
                            "Shutdown overlap",
                            "Shutdown must not upload this queued report twice.",
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
            assertTrue(client.uploadStarted.await(2, TimeUnit.SECONDS));
            Thread.ofPlatform().start(() -> {
                try {
                    engine.shutdown();
                } finally {
                    shutdownComplete.countDown();
                }
            });

            assertFalse(client.secondUploadStarted.await(500, TimeUnit.MILLISECONDS));
        } finally {
            client.releaseUpload.countDown();
            assertTrue(shutdownComplete.await(2, TimeUnit.SECONDS));
            scheduler.shutdownNow();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        }

        assertEquals(1, client.calls.get());
    }

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

    @Test
    void retainsPendingReportWhenUploadClientThrowsALinkageError() {
        Path settingsFile = tempDir.resolve("Settings").resolve("runtime.json");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(settingsFile, null);
        TelemetryProjectRegistration project = registration();
        TelemetryCoreEngine engine = new TelemetryCoreEngine(
                settings,
                dataPaths(settings),
                List.of(project),
                List.of(project),
                List.of(),
                new ErrorClient(),
                null,
                null
        );

        assertTrue(engine.captureTestReport(project.projectId(), "simulated upload error"));

        TelemetryCoreEngine.FlushSummary summary = assertDoesNotThrow(
                () -> engine.flushPendingReportsNow("manual")
        );

        assertEquals(1, summary.attempted());
        assertEquals(0, summary.uploaded());
        assertEquals(1, summary.pendingAfter());
        assertTrue(summary.lastFailure().contains("JVM error"));
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
        return registration(true);
    }

    private static TelemetryProjectRegistration registration(boolean diagnosticsSupported) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson("""
                {
                  "projectId": "retired-report",
                  "displayName": "Retired Report",
                  "runtimeMode": "standalone",
                  "ownerPluginIdentifiers": ["Example:Retired Report"],
                  "packagePrefixes": ["com.example.retired"],
                  "capture": {
                    "uncaughtExceptions": true
                  },
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
                  "events": {
                    "errors": {
                      "supported": true,
                      "enabled": true
                    }
                  },
                  "diagnostics": {
                    "supported": %s,
                    "defaultEnabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """.formatted(diagnosticsSupported), null);
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

    private static final class BlockingClient implements CrashReportClient {
        private final CountDownLatch uploadStarted = new CountDownLatch(1);
        private final CountDownLatch secondUploadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseUpload = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            if (calls.incrementAndGet() == 1) {
                uploadStarted.countDown();
            } else {
                secondUploadStarted.countDown();
            }
            try {
                releaseUpload.await();
                return UploadResult.success(204);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return UploadResult.failure(0, "Interrupted");
            }
        }
    }

    private static final class ErrorClient implements CrashReportClient {

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            throw new NoClassDefFoundError("simulated upload failure");
        }
    }
}
