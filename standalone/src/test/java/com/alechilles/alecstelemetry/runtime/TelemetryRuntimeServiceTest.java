package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportKind;
import com.alechilles.alecstelemetry.report.ManualReportReceiptStore;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryStatsHeartbeatService;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryStatsRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCoordinatorRegistry() {
        TelemetryCoordinatorRegistry.clearForTests();
    }

    @Test
    void standaloneRuntimeServiceReportsPassiveWhenNewerEmbeddedCoordinatorWins() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        RecordingCoordinatorBridge embedded = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
                "embedded:Example:Embedded Mod",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar"),
                tempDir.resolve("Telemetry")
        ));
        embedded.runtimeEnabled = false;
        TelemetryCoordinatorRegistry.register(embedded);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                manualReportDescriptor("standalone-local", "Standalone Local", true, "dependency"),
                "Example:Standalone Local",
                "1.0.0",
                tempDir.resolve("Standalone Local.jar")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Standalone Local", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        service.start();

        assertFalse(service.ownsActiveCoordinator());
        assertEquals("embedded:Example:Embedded Mod", service.activeCoordinatorProviderId());
        assertFalse(service.isEnabled());
        assertFalse(service.api().isEnabled());
        assertEquals(List.of("embedded-mod", "embedded-consent-only"), service.projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertNull(service.api().findProject("standalone-local"));
        assertEquals("embedded-mod", service.api().findProject("embedded-mod").projectId());
        assertEquals("embedded-consent-only", service.api().findProject("embedded-consent-only").projectId());
        assertTrue(service.api().findProject("embedded-consent-only").isEnabled());
        assertTrue(service.triggerFlushAsync("embedded-mod"));
        service.recordUsage("embedded-mod", "settings_opened", "from standalone api");
        assertEquals("embedded-mod", embedded.lastFlushProjectId);
        assertEquals("embedded-mod", embedded.lastProjectId);
        assertEquals("settings_opened", embedded.lastEventName);
        assertEquals("from standalone api", embedded.lastDetails.get("detail"));
        assertNull(service.findProject("standalone-local"));
        assertEquals("embedded-mod", service.findProject("embedded-mod").projectId());
        assertEquals("embedded-consent-only", service.findProject("embedded-consent-only").projectId());
        assertEquals("embedded-mod", service.diagnostics().projects().getFirst().projectId());
        assertNull(service.projectDiagnostics("standalone-local"));
        assertEquals("embedded-mod", service.projectDiagnostics("embedded-mod").projectId());
        assertEquals(7, service.pendingReports(null));
        assertEquals(7, service.pendingReports("embedded-mod"));
        TelemetryRuntimeService.FlushSummary flushSummary = service.flushPendingReportsNow("test", "embedded-mod");
        assertEquals(7, flushSummary.attempted());
        assertEquals("embedded-mod", embedded.lastFlushProjectId);
        assertEquals("embedded-mod", service.consentDiagnostics().projects().getFirst().projectId());
        assertNull(service.consentProjectDiagnostics("standalone-only"));
        assertTrue(service.unreviewedConsentProjects().isEmpty());
        assertTrue(service.applyConsent(
                "embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));
        assertFalse(embedded.projectEnabled);
        assertFalse(embedded.crashEnabled);
        assertFalse(embedded.errorEventsEnabled);
        assertFalse(embedded.lifecycleEventsEnabled);
        assertFalse(embedded.performanceEnabled);
        assertFalse(embedded.usageEnabled);
        assertFalse(embedded.statsEnabled);
        assertFalse(embedded.breadcrumbsEnabled);
        assertTrue(service.markConsentReviewed("embedded-mod"));
        assertEquals(List.of("embedded-mod"), embedded.reviewedProjectIds);
        assertFalse(service.markConsentReviewed("standalone-only"));

        service.shutdown();
    }

    @Test
    void passiveStandaloneManualReportSubmissionRoutesToActiveCoordinator() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        RecordingCoordinatorBridge embedded = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
                "embedded:Example:Embedded Mod",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar"),
                tempDir.resolve("Telemetry")
        ));
        embedded.manualReportResult = Map.of(
                "accepted", false,
                "validationErrors", List.of("proxied_manual_report")
        );
        TelemetryCoordinatorRegistry.register(embedded);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                manualReportDescriptor("embedded-mod", "Embedded Mod", true, "embedded"),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        service.start();

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "embedded-mod",
                issueSubmission(),
                playerContext()
        );

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("proxied_manual_report"));
        assertEquals("embedded-mod", embedded.lastManualReportProjectId);
        assertEquals("issue", embedded.lastManualReportSubmission.get("kind"));
        assertEquals("test-world", embedded.lastManualReportPlayerContext.get("worldName"));
        assertEquals(0, fileCount(dataPaths.pendingManualReportsDirectory("embedded-mod")));

        service.shutdown();
    }

    @Test
    void manualReportSubmissionReturnsValidationErrorWhenGloballyDisabled() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("""
                {
                  "manualReports": {
                    "enabled": false
                  }
                }
                """);
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = manualReportService(settings, dataPaths, true, client);

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(),
                playerContext()
        );

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("manual_reports_disabled"));
        assertEquals(0, service.pendingReports("example-mod"));
        assertEquals(0, client.calls);
    }

    @Test
    void manualReportSubmissionReturnsValidationErrorWhenProjectReportsAreDisabled() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = manualReportService(settings, dataPaths, false, client);

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(),
                playerContext()
        );

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("project_reports_disabled"));
        assertEquals(0, service.pendingReports("example-mod"));
        assertEquals(0, client.calls);
    }

    @Test
    void runtimeBackedProjectHandleExposesReportPageOpenApi() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryRuntimeService service = manualReportService(
                settings,
                dataPaths,
                true,
                new SequencedClient(CrashReportClient.UploadResult.success(204))
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");

        assertFalse(handle == null);
        assertFalse(handle.openReportPage(
                null,
                null,
                null,
                new TelemetryReportOpenRequest("issue", null, null)
        ));
    }

    @Test
    void manualReportProjectsOnlyIncludesReportEnabledProjects() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration enabled = new TelemetryProjectRegistration(
                manualReportDescriptor("enabled-mod", "Enabled Mod", true),
                "Example:Enabled Mod",
                "1.0.0",
                tempDir.resolve("Enabled Mod")
        );
        TelemetryProjectRegistration disabled = new TelemetryProjectRegistration(
                manualReportDescriptor("disabled-mod", "Disabled Mod", false),
                "Example:Disabled Mod",
                "1.0.0",
                tempDir.resolve("Disabled Mod")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(enabled, disabled),
                List.of(),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        assertEquals(
                List.of("enabled-mod"),
                service.manualReportProjects().stream().map(TelemetryProjectRegistration::projectId).toList()
        );
    }

    @Test
    void filtersDiscoveredProjectsToActuallyLoadedMods() {
        TelemetryProjectRegistration active = new TelemetryProjectRegistration(
                manualReportDescriptor("active-mod", "Active Mod", true),
                "Example:Active Mod",
                "1.0.0",
                tempDir.resolve("Active Mod.jar")
        );
        TelemetryProjectRegistration assetOnly = new TelemetryProjectRegistration(
                manualReportDescriptor("asset-only-mod", "Asset Only Mod", true),
                "Example:Asset Only Mod",
                "1.0.0",
                tempDir.resolve("Asset Only Mod.jar")
        );
        TelemetryProjectRegistration installedButDisabled = new TelemetryProjectRegistration(
                manualReportDescriptor("disabled-mod", "Disabled Mod", true),
                "Example:Disabled Mod",
                "1.0.0",
                tempDir.resolve("Disabled Mod.jar")
        );

        List<TelemetryProjectRegistration> filtered = TelemetryRuntimeService.filterRegistrationsToLoadedMods(
                List.of(active, assetOnly, installedButDisabled),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Active Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Asset Only Mod", "1.0.0")
                )
        );

        assertEquals(
                List.of("active-mod", "asset-only-mod"),
                filtered.stream().map(TelemetryProjectRegistration::projectId).toList()
        );
    }

    @Test
    void noLoadedModsMeansNoRuntimeOrConsentProjects() {
        TelemetryProjectRegistration installedButDisabled = new TelemetryProjectRegistration(
                manualReportDescriptor("disabled-mod", "Disabled Mod", true),
                "Example:Disabled Mod",
                "1.0.0",
                tempDir.resolve("Disabled Mod.jar")
        );

        assertTrue(TelemetryRuntimeService.filterRegistrationsToLoadedMods(
                List.of(installedButDisabled),
                List.of()
        ).isEmpty());
    }

    @Test
    void activeStandaloneApiListsAndFindsConsentOnlyProjects() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration runtimeProject = new TelemetryProjectRegistration(
                manualReportDescriptor("runtime-mod", "Runtime Mod", true, "dependency"),
                "Example:Runtime Mod",
                "1.0.0",
                tempDir.resolve("Runtime Mod.jar")
        );
        TelemetryProjectRegistration consentOnlyProject = new TelemetryProjectRegistration(
                manualReportDescriptor("standalone-consent-only", "Standalone Consent Only", true, "dependency"),
                "Example:Standalone Consent Only",
                "1.0.0",
                tempDir.resolve("Standalone Consent Only.jar")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(runtimeProject),
                List.of(runtimeProject, consentOnlyProject),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Runtime Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Standalone Consent Only", "1.0.0")
                ),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        service.start();

        assertTrue(service.ownsActiveCoordinator());
        assertEquals(List.of("runtime-mod", "standalone-consent-only"), service.projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertEquals(List.of("runtime-mod", "standalone-consent-only"), service.api().projects().stream()
                .map(TelemetryProjectHandle::projectId)
                .toList());
        assertEquals("standalone-consent-only", service.findProject("standalone-consent-only").projectId());
        assertEquals("standalone-consent-only", service.api().findProject("standalone-consent-only").projectId());
        assertTrue(service.api().findProject("standalone-consent-only").isEnabled());

        service.shutdown();
    }

    @Test
    void standaloneStatsHeartbeatStopsWhenElectionMovesToNewerCoordinator() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                statsOnlyTelemetryDescriptor("standalone-stats", "Standalone Stats"),
                "Example:Standalone Stats",
                "1.0.0",
                tempDir.resolve("Standalone Stats.jar")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Standalone Stats", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );
        RecordingScheduledExecutor heartbeatExecutor = new RecordingScheduledExecutor();
        TelemetryStatsHeartbeatService heartbeat = new TelemetryStatsHeartbeatService(
                TelemetryStatsRuntime.from(service),
                new TelemetryPlayerCounter(),
                heartbeatExecutor,
                null
        );
        service.attachStatsHeartbeat(heartbeat);

        service.start();

        assertTrue(service.ownsActiveCoordinator());
        assertEquals(1, heartbeatExecutor.futures.size());
        RecordingScheduledFuture<?> standaloneHeartbeat = heartbeatExecutor.futures.getFirst();
        assertFalse(standaloneHeartbeat.isCancelled());

        RecordingCoordinatorBridge embedded = new RecordingCoordinatorBridge(new TelemetryRuntimeCandidate(
                "embedded:Example:Embedded Mod",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar"),
                tempDir.resolve("Telemetry")
        ));
        TelemetryCoordinatorRegistry.register(embedded);

        assertFalse(service.ownsActiveCoordinator());
        assertEquals("embedded:Example:Embedded Mod", service.activeCoordinatorProviderId());
        assertTrue(standaloneHeartbeat.isCancelled());
        heartbeat.emitHeartbeatNow();
        assertNull(embedded.lastStatsProjectId);
        assertNull(embedded.lastStatsEventName);

        service.shutdown();
    }

    @Test
    void embeddedConsentProjectCanAcceptManualReportsWithoutStandaloneRegistration() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                manualReportDescriptor("embedded-mod", "Embedded Mod", true, "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded Mod")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        assertTrue(service.projects().isEmpty());
        assertNull(service.api().findProject("embedded-mod"));
        assertEquals(
                List.of("embedded-mod"),
                service.manualReportProjects().stream().map(TelemetryProjectRegistration::projectId).toList()
        );

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "embedded-mod",
                issueSubmission(),
                playerContext()
        );

        assertTrue(result.accepted());
        assertEquals(1, fileCount(dataPaths.pendingManualReportsDirectory("embedded-mod")));
    }

    @Test
    void embeddedManualReportsIgnoreAutomatedTelemetryConsent() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration embedded = new TelemetryProjectRegistration(
                manualReportDescriptor("embedded-mod", "Embedded Mod", true, "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(),
                List.of(embedded),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertTrue(service.applyConsent(
                "embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));
        assertFalse(service.projectDiagnostics("embedded-mod").enabled());
        assertEquals(
                List.of("embedded-mod"),
                service.manualReportProjects().stream().map(TelemetryProjectRegistration::projectId).toList()
        );

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "embedded-mod",
                issueSubmission(),
                playerContext()
        );

        assertTrue(result.accepted());
        assertEquals(1, fileCount(dataPaths.pendingManualReportsDirectory("embedded-mod")));

        TelemetryRuntimeService.FlushSummary summary = service.flushPendingReportsNow("test", "embedded-mod");

        assertEquals(1, summary.attempted());
        assertEquals(1, summary.uploaded());
        assertEquals(1, client.calls);
        assertEquals(0, fileCount(dataPaths.pendingManualReportsDirectory("embedded-mod")));
    }

    @Test
    void manualReviewRequiredStoresReportForReviewAndSkipsFlushUpload() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("""
                {
                  "manualReports": {
                    "manualReviewRequired": true
                  }
                }
                """);
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = manualReportService(settings, dataPaths, true, client);

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(),
                playerContext()
        );

        assertTrue(result.accepted());
        assertEquals(0, service.pendingReports("example-mod"));
        assertEquals(1, fileCount(dataPaths.reviewManualReportsDirectory("example-mod")));
        assertEquals(0, service.flushPendingReportsNow("manual-review", "example-mod").attempted());
        assertEquals(0, client.calls);
    }

    @Test
    void manualReviewDisabledQueuesAndFlushesManualReports() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        Files.writeString(tempDir.resolve("latest.log"), "server booted\ncontact admin@example.com\n");
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = manualReportService(settings, dataPaths, true, client);

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(true),
                playerContext()
        );

        assertTrue(result.accepted());
        assertEquals(1, service.pendingReports("example-mod"));
        TelemetryRuntimeService.FlushSummary summary = service.flushPendingReportsNow("manual-upload", "example-mod");
        assertEquals(1, summary.attempted());
        assertEquals(1, summary.uploaded());
        assertEquals(0, summary.pendingAfter());
        assertEquals(1, client.calls);

        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("manual_report", payload.get("eventType").getAsString());
        assertEquals("issue", payload.get("reportKind").getAsString());
        assertEquals("example-mod", payload.get("projectId").getAsString());
        assertEquals(false, payload.getAsJsonObject("context").get("singleplayer").getAsBoolean());
        assertEquals(7, payload.getAsJsonObject("context").get("onlinePlayerCount").getAsInt());
        UUID.fromString(payload.get("serverId").getAsString());
        assertTrue(payload.get("sessionId").getAsString().length() > 10);
        assertEquals(1, payload.getAsJsonArray("attachments").size());
        String attachmentContent = payload.getAsJsonArray("attachments")
                .get(0)
                .getAsJsonObject()
                .get("content")
                .getAsString();
        assertTrue(attachmentContent.contains("server booted"));
        assertFalse(attachmentContent.contains("admin@example.com"));
        assertEquals(0, fileCount(dataPaths.pendingManualReportsDirectory("example-mod")));
        assertEquals(1, Files.readAllLines(dataPaths.submittedManualReportsLog()).size());

        List<ManualReportReceiptStore.Receipt> receipts = new ManualReportReceiptStore()
                .loadAll(dataPaths.manualReportReceiptsFile());
        assertEquals(1, receipts.size());
        assertEquals(result.envelope().reportId(), receipts.getFirst().reportId());
        assertEquals("uploaded", receipts.getFirst().lastKnownStatus());
        assertEquals("uploaded", service.manualReportReceiptStatus(result.envelope().reportId()));
        assertTrue(receipts.getFirst().followUpToken().length() > 10);
    }

    @Test
    void manualReportRuntimeContextUsesActiveLoadedModsSnapshot() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                manualReportDescriptor(true),
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        List<CrashReportEnvelope.LoadedModMetadata> discoveredInstalledMods = List.of(
                new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"),
                new CrashReportEnvelope.LoadedModMetadata("Installed:Disabled Mod", "9.9.9")
        );
        List<CrashReportEnvelope.LoadedModMetadata> activeLoadedMods = List.of(
                new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                discoveredInstalledMods,
                () -> activeLoadedMods,
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(),
                service.currentPlayerReportRuntimeContext()
        );

        assertTrue(result.accepted());
        assertEquals(
                List.of("Example:Example Mod"),
                result.envelope().runtime().loadedMods().stream()
                        .map(CrashReportEnvelope.LoadedModMetadata::identifier)
                        .toList()
        );
    }

    @Test
    void failedManualReportUploadLeavesPendingReportForRetry() throws Exception {
        TelemetryRuntimeSettings settings = manualReportSettings("{}");
        TelemetryDataPaths dataPaths = manualReportPaths(settings);
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.failure(500, "server error"));
        TelemetryRuntimeService service = manualReportService(settings, dataPaths, true, client);

        ManualReportEnvelope.CreateResult result = service.submitManualReport(
                "example-mod",
                issueSubmission(),
                playerContext()
        );

        assertTrue(result.accepted());
        TelemetryRuntimeService.FlushSummary summary = service.flushPendingReportsNow("manual-fail", "example-mod");
        assertEquals(1, summary.attempted());
        assertEquals(0, summary.uploaded());
        assertEquals(1, summary.pendingAfter());
        assertEquals("server error", summary.lastFailure());
        assertEquals(1, fileCount(dataPaths.pendingManualReportsDirectory("example-mod")));
        assertEquals("upload_failed", service.manualReportReceiptStatus(result.envelope().reportId()));
    }

    @Test
    void explicitCapturePersistsAndFlushRetainsFailuresForRetry() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(500, "server error"),
                CrashReportClient.UploadResult.success(204)
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordBreadcrumb("bootstrap", "Resolved optional dependency bridge.");
        }

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });

        service.captureSetupFailure("example-mod", throwable);
        assertEquals(1, service.flushPendingReportsNow("test-first").attempted());
        assertEquals(1, service.flushPendingReportsNow("test-second").attempted());
        assertEquals(2, client.calls);
        JsonObject firstPayload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("bootstrap", firstPayload.getAsJsonArray("breadcrumbs").get(0).getAsJsonObject().get("category").getAsString());
        assertTrue(firstPayload.get("sessionId").getAsString().length() > 10);
        UUID.fromString(firstPayload.get("serverId").getAsString());
        assertEquals("dependency", firstPayload.getAsJsonObject("environment").get("runtimeMode").getAsString());
        assertEquals(0, service.flushPendingReportsNow("test-third").attempted());
    }

    @Test
    void runtimeApiCanQueueAndFlushGenericEventsWithStableServerId() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordError("handled_exception", new IllegalStateException("bad state"), "Recovered after retry");
            handle.recordLifecycle("startup", 42, true, "Loaded telemetry bridge");
        }

        assertEquals(2, service.flushPendingReportsNow("test-events").attempted());
        JsonObject firstPayload = JsonParser.parseString(client.payloads.get(0)).getAsJsonObject();
        JsonObject secondPayload = JsonParser.parseString(client.payloads.get(1)).getAsJsonObject();
        assertEquals(2, firstPayload.get("schemaVersion").getAsInt());
        assertEquals("error", firstPayload.get("eventType").getAsString());
        assertEquals("handled_exception", firstPayload.get("eventName").getAsString());
        String serverId = firstPayload.get("serverId").getAsString();
        UUID.fromString(serverId);
        assertEquals(serverId, secondPayload.get("serverId").getAsString());

        SequencedClient secondClient = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService secondService = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                secondClient,
                null,
                null
        );
        TelemetryProjectHandle secondHandle = secondService.api().findProject("example-mod");
        if (secondHandle != null) {
            secondHandle.recordError("second_session_event", null, "Recorded after restart");
        }

        assertEquals(1, secondService.flushPendingReportsNow("test-events-second-session").attempted());
        JsonObject secondSessionPayload = JsonParser.parseString(secondClient.payloads.getFirst()).getAsJsonObject();
        assertEquals(serverId, secondSessionPayload.get("serverId").getAsString());
        assertNotEquals(firstPayload.get("sessionId").getAsString(), secondSessionPayload.get("sessionId").getAsString());
    }

    @Test
    void typedContextPromotesFieldsAndSanitizesUsageDetails() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"],
                    "details": {
                      "settings_opened": {
                        "allowedFields": {
                          "source": { "type": "enum", "values": ["command", "settings_ui"] },
                          "changedSettingCount": { "type": "number" }
                        }
                      }
                    }
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordUsageWithContext(
                    "settings_opened",
                    TelemetryEventContext.usage()
                            .subsystem("settings")
                            .featureKey("settings_page")
                            .entryPoint("/tw settings")
                            .runtimeSide("server")
                            .detail("source", "settings_ui")
                            .detail("changedSettingCount", 2)
                            .detail("ignored", "drop me")
                            .build()
            );
        }

        assertEquals(1, service.flushPendingReportsNow("test-events").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("usage", payload.get("eventType").getAsString());
        assertEquals("settings", payload.get("subsystem").getAsString());
        assertEquals("settings_page", payload.get("featureKey").getAsString());
        assertEquals("/tw settings", payload.get("entryPoint").getAsString());
        assertEquals("server", payload.get("runtimeSide").getAsString());
        assertEquals("settings_ui", payload.getAsJsonObject("details").get("source").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("changedSettingCount").getAsInt());
        assertTrue(!payload.getAsJsonObject("details").has("ignored"));
    }

    @Test
    void errorContextIncludesBreadcrumbDetailsAndHonorsEventControls() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "events": {
                    "errors": {
                      "enabled": true,
                      "details": {
                        "handled_exception": {
                          "allowedFields": {
                            "reason": { "type": "string", "maxLength": 120 }
                          }
                        }
                      }
                    },
                    "lifecycle": { "enabled": false },
                    "breadcrumbs": { "enabled": true }
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordBreadcrumb("ui", "Opened settings.");
            handle.recordLifecycleWithContext("suppressed_lifecycle", 10, true, TelemetryEventContext.lifecycle().phase("start").build());
            handle.recordErrorWithContext(
                    "handled_exception",
                    new IllegalStateException("bad state"),
                    TelemetryEventContext.error()
                            .subsystem("settings")
                            .operation("apply")
                            .fingerprint("settings-apply")
                            .detail("Recovered after retry")
                            .detail("reason", "bad_state")
                            .detail("ignored", "drop me")
                            .build()
            );
        }

        assertEquals(1, service.flushPendingReportsNow("test-events").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("handled_exception", payload.get("eventName").getAsString());
        assertEquals("settings-apply", payload.get("fingerprint").getAsString());
        assertEquals("settings", payload.get("subsystem").getAsString());
        assertEquals("apply", payload.get("operation").getAsString());
        assertEquals("Recovered after retry", payload.getAsJsonObject("attributes").get("detail").getAsString());
        assertEquals("bad_state", payload.getAsJsonObject("details").get("reason").getAsString());
        assertTrue(!payload.getAsJsonObject("details").has("ignored"));
        assertTrue(payload.getAsJsonObject("details").getAsJsonArray("breadcrumbs").size() > 0);
    }

    @Test
    void crashCaptureOverrideSuppressesCrashReports() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectOverride override = TelemetryProjectOverride.fromJson(
                """
                {
                  "capture": {
                    "uncaughtExceptions": false,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  }
                }
                """
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod"),
                override
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });

        service.captureSetupFailure("example-mod", throwable);

        assertEquals(0, service.flushPendingReportsNow("test-crash-consent").attempted());
        assertEquals(0, client.calls);
    }

    @Test
    void runtimeConsentApiPersistsAppliesAndMarksReviewedProjects() throws Exception {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "ownerPluginIdentifiers": ["Example:Example Mod"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "events": {
                    "errors": { "enabled": true },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "performance": {
                    "enabled": true,
                    "thresholdMs": 1
                  },
                  "usage": {
                    "enabled": true,
                    "allowedEvents": ["settings_opened"]
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertEquals(List.of(registration), service.unreviewedConsentProjects());
        assertTrue(service.applyConsent(
                "example-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));
        assertFalse(service.isProjectEnabled("example-mod"));
        assertFalse(service.projectDiagnostics("example-mod").crashEnabled());
        assertFalse(service.projectDiagnostics("example-mod").errorEnabled());
        assertFalse(service.projectDiagnostics("example-mod").lifecycleEnabled());
        assertFalse(service.projectDiagnostics("example-mod").performanceEnabled());
        assertFalse(service.projectDiagnostics("example-mod").usageEnabled());
        assertFalse(service.projectDiagnostics("example-mod").statsEnabled());
        assertFalse(service.projectDiagnostics("example-mod").breadcrumbsEnabled());

        TelemetryProjectOverride override = new TelemetryProjectOverrideStore(null)
                .load(dataPaths.projectOverrideFile("example-mod"));
        assertEquals(false, override.enabled());
        assertEquals(false, override.capture().setupFailures());
        assertEquals(false, override.events().errors().enabled());
        assertEquals(false, override.events().lifecycle().enabled());
        assertEquals(false, override.events().breadcrumbs().enabled());
        assertEquals(false, override.performance().enabled());
        assertEquals(false, override.usage().enabled());

        RuntimeException throwable = new RuntimeException("setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.telemetry.Setup", "run", "Setup.java", 10)
        });
        service.captureSetupFailure("example-mod", throwable);
        TelemetryProjectHandle handle = service.api().findProject("example-mod");
        if (handle != null) {
            handle.recordError("handled_exception", new IllegalStateException("bad state"), "Recovered after retry");
            handle.recordPerformance("reload_config_duration", 10, null, "Reloaded config");
            handle.recordUsage("settings_opened", "Opened settings");
        }

        assertEquals(0, service.flushPendingReportsNow("test-consent-off").attempted());
        assertEquals(0, client.calls);
        assertTrue(service.markConsentReviewed("example-mod"));
        assertTrue(service.unreviewedConsentProjects().isEmpty());
    }

    @Test
    void applyConsentToAllUpdatesEveryProjectAndTelemetryCategory() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectRegistration first = new TelemetryProjectRegistration(
                telemetryCategoryDescriptor("first-mod", "First Mod"),
                "Example:First Mod",
                "1.0.0",
                tempDir.resolve("First Mod")
        );
        TelemetryProjectRegistration second = new TelemetryProjectRegistration(
                telemetryCategoryDescriptor("second-mod", "Second Mod"),
                "Example:Second Mod",
                "2.0.0",
                tempDir.resolve("Second Mod")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(first, second),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:First Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Second Mod", "2.0.0")
                ),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        assertTrue(service.applyConsentToAll(new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : service.diagnostics().projects()) {
            assertAllTelemetryDisabled(project);
        }

        assertTrue(service.applyConsentToAll(new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true)));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : service.diagnostics().projects()) {
            assertAllTelemetryEnabled(project);
        }

        assertTrue(service.applyConsentCategoryToAll("usage", false));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : service.diagnostics().projects()) {
            assertTrue(project.enabled());
            assertTrue(project.crashEnabled());
            assertTrue(project.errorEnabled());
            assertTrue(project.lifecycleEnabled());
            assertTrue(project.performanceEnabled());
            assertFalse(project.usageEnabled());
            assertTrue(project.statsEnabled());
            assertTrue(project.breadcrumbsEnabled());
        }
    }

    @Test
    void standaloneCoordinatorBridgeExposesConsentBridgeSurfaceThroughRegistry() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectRegistration project = new TelemetryProjectRegistration(
                telemetryCategoryDescriptor("standalone-mod", "Standalone Mod"),
                "Example:Standalone Mod",
                "1.0.0",
                tempDir.resolve("Standalone Mod")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(project),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Standalone Mod", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        service.start();
        TelemetryCoordinatorBridge active = TelemetryCoordinatorRegistry.activeBridge();
        assertTrue(active != null);
        Map<String, Object> diagnostics = active.consentDiagnostics();
        assertEquals(1, ((List<?>) diagnostics.get("projects")).size());
        assertEquals("standalone-mod", active.consentProjectDiagnostics("standalone-mod").get("projectId"));
        assertTrue(active.applyConsent(
                "standalone-mod",
                TelemetryConsentBridgePayload.snapshotSummary(
                        new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
                )
        ));
        assertFalse(service.projectDiagnostics("standalone-mod").enabled());
        assertFalse(active.isProjectEnabled("standalone-mod"));
        assertEquals(0, service.pendingReports("standalone-mod"));
        active.recordUsage("standalone-mod", "settings_opened", Map.of("detail", "after disable"));
        assertEquals(0, service.pendingReports("standalone-mod"));
        assertFalse(active.applyConsent(
                "missing-mod",
                TelemetryConsentBridgePayload.snapshotSummary(
                        new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true)
                )
        ));
    }

    @Test
    void consentBulkChangesOnlyEnableCategoriesSupportedByDescriptor() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
        TelemetryProjectRegistration project = new TelemetryProjectRegistration(
                statsOnlyTelemetryDescriptor("animal-husbandry", "Animal Husbandry"),
                "Example:Animal Husbandry",
                "1.0.0",
                tempDir.resolve("Animal Husbandry")
        );
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(project),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Animal Husbandry", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        assertTrue(service.applyConsentToAll(new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true)));
        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = service.projectDiagnostics("animal-husbandry");
        assertTrue(diagnostics.enabled());
        assertFalse(diagnostics.crashEnabled());
        assertFalse(diagnostics.errorEnabled());
        assertFalse(diagnostics.lifecycleEnabled());
        assertFalse(diagnostics.performanceEnabled());
        assertFalse(diagnostics.usageEnabled());
        assertTrue(diagnostics.statsEnabled());
        assertFalse(diagnostics.breadcrumbsEnabled());
        assertFalse(diagnostics.crashSupported());
        assertFalse(diagnostics.usageSupported());
        assertTrue(diagnostics.statsSupported());

        assertTrue(service.applyConsentCategoryToAll("usage", true));
        assertFalse(service.projectDiagnostics("animal-husbandry").usageEnabled());

        assertTrue(service.applyConsent(
                "animal-husbandry",
                new TelemetryConsentSnapshot(true, true, true, true, true, true, false, true)
        ));
        diagnostics = service.projectDiagnostics("animal-husbandry");
        assertFalse(diagnostics.crashEnabled());
        assertFalse(diagnostics.usageEnabled());
        assertFalse(diagnostics.statsEnabled());
    }

    @Test
    void embeddedConsentProjectIsVisibleAndMirrorsOverridesToOwnerSettings() {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(tempDir.resolve("Settings").resolve("runtime.json"), null);
        Path modsDir = tempDir.resolve("mods");
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                modsDir
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "events": {
                    "errors": { "enabled": true },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "performance": {
                    "enabled": true
                  },
                  "usage": {
                    "enabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Embedded Mod",
                "1.2.3",
                modsDir.resolve("Embedded.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryRuntimeService service = new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.2.3")),
                client,
                null,
                null
        );

        assertTrue(service.projects().isEmpty());
        assertNull(service.api().findProject("embedded-mod"));
        assertEquals(1, service.diagnostics().registeredProjects());
        assertEquals(1, service.diagnostics().projects().size());
        assertEquals("embedded", service.diagnostics().projects().getFirst().runtimeMode());
        assertTrue(service.diagnostics().projects().getFirst().enabled());

        assertTrue(service.applyConsent(
                "embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));

        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = service.projectDiagnostics("embedded-mod");
        assertFalse(diagnostics.enabled());
        assertFalse(diagnostics.crashEnabled());
        assertFalse(diagnostics.errorEnabled());
        assertFalse(diagnostics.lifecycleEnabled());
        assertFalse(diagnostics.performanceEnabled());
        assertFalse(diagnostics.usageEnabled());
        assertFalse(diagnostics.statsEnabled());
        assertFalse(diagnostics.breadcrumbsEnabled());

        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(null);
        TelemetryProjectOverride centralOverride = store.load(dataPaths.projectOverrideFile("embedded-mod"));
        Path embeddedOverrideFile = modsDir
                .resolve("Example_Embedded Mod")
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve("embedded-mod.json");
        TelemetryProjectOverride embeddedOverride = store.load(embeddedOverrideFile);
        assertEquals(false, centralOverride.enabled());
        assertEquals(false, centralOverride.events().errors().enabled());
        assertEquals(false, embeddedOverride.enabled());
        assertEquals(false, embeddedOverride.events().errors().enabled());
        assertEquals(0, service.flushPendingReportsNow("embedded-consent").attempted());
        assertEquals(0, client.calls);
    }

    private TelemetryRuntimeSettings manualReportSettings(String rawJson) throws Exception {
        Path settingsFile = tempDir.resolve("Settings").resolve("manual-report-runtime.json");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, rawJson);
        return TelemetryRuntimeSettings.load(settingsFile, null);
    }

    private TelemetryDataPaths manualReportPaths(TelemetryRuntimeSettings settings) {
        return new TelemetryDataPaths(
                tempDir,
                settings.filePath(),
                tempDir.resolve("Settings").resolve("projects"),
                tempDir.resolve("Telemetry"),
                tempDir.resolve("Telemetry").resolve("crash-reports"),
                tempDir.resolve("Telemetry").resolve("events"),
                tempDir
        );
    }

    private TelemetryRuntimeService manualReportService(TelemetryRuntimeSettings settings,
                                                       TelemetryDataPaths dataPaths,
                                                       boolean reportsEnabled,
                                                       SequencedClient client) {
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                manualReportDescriptor(reportsEnabled),
                "Example:Example Mod",
                "1.2.3",
                tempDir.resolve("Example Mod")
        );
        List<CrashReportEnvelope.LoadedModMetadata> loadedMods = List.of(
                new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"),
                new CrashReportEnvelope.LoadedModMetadata("Other:Compat Mod", "4.5.6")
        );
        return new TelemetryRuntimeService(
                settings,
                dataPaths,
                List.of(registration),
                loadedMods,
                client,
                null,
                null
        );
    }

    private static TelemetryProjectDescriptor manualReportDescriptor(boolean reportsEnabled) {
        return manualReportDescriptor("example-mod", "Example Mod", reportsEnabled);
    }

    private static TelemetryProjectDescriptor manualReportDescriptor(String projectId,
                                                                    String displayName,
                                                                    boolean reportsEnabled) {
        return manualReportDescriptor(projectId, displayName, reportsEnabled, null);
    }

    private static TelemetryProjectDescriptor manualReportDescriptor(String projectId,
                                                                    String displayName,
                                                                    boolean reportsEnabled,
                                                                    String runtimeMode) {
        String runtimeModeField = runtimeMode == null ? "" : "  \"runtimeMode\": \"%s\",%n".formatted(runtimeMode);
        String reports = reportsEnabled
                ? """
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
                    },
                    "suggestion": {
                      "enabled": true
                    },
                    "contact": {
                      "enabled": true
                    }
                  },
                """
                : "";
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                """.formatted(projectId, displayName) + runtimeModeField + """
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                """.formatted(displayName) + reports + """
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """,
                null
        );
    }

    private static TelemetryProjectDescriptor telemetryCategoryDescriptor(String projectId, String displayName) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "events": {
                    "errors": { "enabled": true },
                    "lifecycle": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "performance": {
                    "enabled": true
                  },
                  "usage": {
                    "enabled": true
                  },
                  "stats": {
                    "enabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """.formatted(projectId, displayName, displayName),
                null
        );
    }

    private static TelemetryProjectDescriptor statsOnlyTelemetryDescriptor(String projectId, String displayName) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example.telemetry"],
                  "capture": {
                    "uncaughtExceptions": false,
                    "setupFailures": false,
                    "startFailures": false,
                    "exceptionalWorldRemovals": false
                  },
                  "events": {
                    "errors": { "enabled": false },
                    "lifecycle": { "enabled": false },
                    "breadcrumbs": { "enabled": false }
                  },
                  "performance": {
                    "enabled": false
                  },
                  "usage": {
                    "enabled": false
                  },
                  "stats": {
                    "enabled": true
                  },
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  }
                }
                """.formatted(projectId, displayName, displayName),
                null
        );
    }

    private static void assertAllTelemetryEnabled(TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        assertTrue(project.enabled());
        assertTrue(project.crashEnabled());
        assertTrue(project.errorEnabled());
        assertTrue(project.lifecycleEnabled());
        assertTrue(project.performanceEnabled());
        assertTrue(project.usageEnabled());
        assertTrue(project.statsEnabled());
        assertTrue(project.breadcrumbsEnabled());
    }

    private static void assertAllTelemetryDisabled(TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        assertFalse(project.enabled());
        assertFalse(project.crashEnabled());
        assertFalse(project.errorEnabled());
        assertFalse(project.lifecycleEnabled());
        assertFalse(project.performanceEnabled());
        assertFalse(project.usageEnabled());
        assertFalse(project.statsEnabled());
        assertFalse(project.breadcrumbsEnabled());
    }

    private static ManualReportSubmission issueSubmission() {
        return issueSubmission(false);
    }

    private static ManualReportSubmission issueSubmission(boolean includeCurrentServerLog) {
        return new ManualReportSubmission(
                ManualReportKind.ISSUE,
                "Config screen does not save",
                "Opening the config screen and pressing Save leaves old values in place.",
                "discord: example",
                Map.of("severity", "major"),
                includeCurrentServerLog,
                false,
                true,
                true,
                true
        );
    }

    private static PlayerReportRuntimeContext playerContext() {
        return new PlayerReportRuntimeContext(
                false,
                7,
                "test-world",
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"))
        );
    }

    private static long fileCount(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final java.util.ArrayList<RecordingScheduledFuture<?>> futures = new java.util.ArrayList<>();
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, @Nonnull TimeUnit unit) {
            return scheduleWithFixedDelay(command, delay, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(@Nonnull Callable<V> callable, long delay, @Nonnull TimeUnit unit) {
            RecordingScheduledFuture<V> future = new RecordingScheduledFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(@Nonnull Runnable command,
                                                      long initialDelay,
                                                      long period,
                                                      @Nonnull TimeUnit unit) {
            return scheduleWithFixedDelay(command, initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(@Nonnull Runnable command,
                                                         long initialDelay,
                                                         long delay,
                                                         @Nonnull TimeUnit unit) {
            RecordingScheduledFuture<Void> future = new RecordingScheduledFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Nonnull
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(@Nonnull Runnable command) {
            command.run();
        }
    }

    private static final class RecordingScheduledFuture<V> implements ScheduledFuture<V> {
        private boolean cancelled;

        @Override
        public long getDelay(@Nonnull TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(@Nonnull Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public V get() {
            return null;
        }

        @Override
        public V get(long timeout, @Nonnull TimeUnit unit) {
            return null;
        }
    }

    private static final class SequencedClient implements CrashReportClient {
        private final Queue<UploadResult> responses = new ArrayDeque<>();
        private final java.util.ArrayList<String> payloads = new java.util.ArrayList<>();
        private int calls;

        private SequencedClient(UploadResult... uploadResults) {
            for (UploadResult uploadResult : uploadResults) {
                responses.add(uploadResult);
            }
        }

        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            calls++;
            payloads.add(payloadJson);
            UploadResult next = responses.poll();
            return next == null ? UploadResult.success(200) : next;
        }
    }

    private static final class RecordingCoordinatorBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private boolean active;
        private boolean runtimeEnabled = true;
        private String lastFlushProjectId;
        private String lastProjectId;
        private String lastEventName;
        private String lastStatsProjectId;
        private String lastStatsEventName;
        private boolean projectEnabled = true;
        private boolean crashEnabled = true;
        private boolean errorEventsEnabled = true;
        private boolean lifecycleEventsEnabled = true;
        private boolean performanceEnabled = true;
        private boolean usageEnabled = true;
        private boolean statsEnabled = true;
        private boolean breadcrumbsEnabled = true;
        private String consentProjectId = "embedded-mod";
        private String consentDisplayName = "Embedded Mod";
        private String consentOnlyProjectId = "embedded-consent-only";
        private String consentOnlyDisplayName = "Embedded Consent Only";
        private int pendingReports = 7;
        private final java.util.ArrayList<String> reviewedProjectIds = new java.util.ArrayList<>();
        private Map<String, Object> lastDetails = Map.of();
        private String lastManualReportProjectId;
        private Map<String, Object> lastManualReportSubmission = Map.of();
        private Map<String, Object> lastManualReportPlayerContext = Map.of();
        private Map<String, Object> manualReportResult = Map.of(
                "accepted", false,
                "validationErrors", List.of("manual_report_not_configured")
        );

        private RecordingCoordinatorBridge(TelemetryRuntimeCandidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public String providerId() {
            return candidate.providerId();
        }

        @Override
        public String origin() {
            return candidate.origin().name();
        }

        @Override
        public String runtimeVersion() {
            return candidate.runtimeVersion();
        }

        @Override
        public int coordinatorProtocolVersion() {
            return candidate.coordinatorProtocolVersion();
        }

        @Override
        public String providerPluginIdentifier() {
            return candidate.providerPluginIdentifier();
        }

        @Override
        public String providerPluginVersion() {
            return candidate.providerPluginVersion();
        }

        @Override
        public String sourcePath() {
            return candidate.sourcePath().toString();
        }

        @Override
        public String sharedDataRoot() {
            return candidate.sharedDataRoot().toString();
        }

        @Override
        public void activate() {
            active = true;
        }

        @Override
        public void deactivate() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            lastFlushProjectId = projectId;
            return true;
        }

        @Override
        public boolean isEnabled() {
            return runtimeEnabled;
        }

        @Override
        public List<Map<String, Object>> projectSummaries() {
            return List.of(projectSummary());
        }

        @Override
        public Map<String, Object> findProjectSummary(@Nonnull String projectId) {
            return consentProjectId.equalsIgnoreCase(projectId.trim()) ? projectSummary() : Map.of();
        }

        @Override
        public boolean isProjectEnabled(@Nonnull String projectId) {
            return projectEnabled;
        }

        @Override
        public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
            projectEnabled = enabled;
            return true;
        }

        @Override
        public boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) {
            crashEnabled = enabled;
            return true;
        }

        @Override
        public boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
            errorEventsEnabled = enabled;
            return true;
        }

        @Override
        public boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
            lifecycleEventsEnabled = enabled;
            return true;
        }

        @Override
        public boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
            performanceEnabled = enabled;
            return true;
        }

        @Override
        public boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) {
            usageEnabled = enabled;
            return true;
        }

        @Override
        public boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) {
            statsEnabled = enabled;
            return true;
        }

        @Override
        public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
            breadcrumbsEnabled = enabled;
            return true;
        }

        @Override
        public Map<String, Object> consentDiagnostics() {
            return TelemetryConsentBridgePayload.diagnosticsSummary(new TelemetryRuntimeDiagnostics(
                    true,
                    2,
                    1,
                    pendingReports,
                    false,
                    "never",
                    null,
                    List.of(),
                    List.of(consentProjectDiagnostics(), consentOnlyProjectDiagnostics())
            ));
        }

        @Override
        public Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
            String normalized = projectId.trim();
            if (consentProjectId.equalsIgnoreCase(normalized)) {
                return TelemetryConsentBridgePayload.projectDiagnosticsSummary(consentProjectDiagnostics());
            }
            return consentOnlyProjectId.equalsIgnoreCase(normalized)
                    ? TelemetryConsentBridgePayload.projectDiagnosticsSummary(consentOnlyProjectDiagnostics())
                    : Map.of();
        }

        @Override
        public boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
            return applyConsent(consentProjectId, snapshot);
        }

        @Override
        public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
            TelemetryConsentSnapshot snapshot = new TelemetryConsentSnapshot(
                    projectEnabled,
                    crashEnabled,
                    errorEventsEnabled,
                    lifecycleEventsEnabled,
                    performanceEnabled,
                    usageEnabled,
                    statsEnabled,
                    breadcrumbsEnabled
            );
            return applyConsent(consentProjectId, TelemetryConsentBridgePayload.snapshotSummary(snapshot.withCategory(category, enabled)));
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
            if (!consentProjectId.equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            TelemetryConsentSnapshot normalized = TelemetryConsentBridgePayload.snapshotFromSummary(snapshot);
            projectEnabled = normalized.projectEnabled();
            crashEnabled = normalized.crashEnabled();
            errorEventsEnabled = normalized.errorEnabled();
            lifecycleEventsEnabled = normalized.lifecycleEnabled();
            performanceEnabled = normalized.performanceEnabled();
            usageEnabled = normalized.usageEnabled();
            statsEnabled = normalized.statsEnabled();
            breadcrumbsEnabled = normalized.breadcrumbsEnabled();
            return true;
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            if (!consentProjectId.equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            reviewedProjectIds.add(consentProjectId);
            return true;
        }

        @Override
        public boolean recordUsage(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            lastProjectId = projectId;
            lastEventName = eventName;
            lastDetails = details;
            return true;
        }

        @Override
        public boolean recordStats(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            lastStatsProjectId = projectId;
            lastStatsEventName = eventName;
            lastDetails = details;
            return true;
        }

        @Override
        public Map<String, Object> submitManualReport(@Nonnull String projectId,
                                                      @Nonnull Map<String, Object> submission,
                                                      @Nonnull Map<String, Object> playerContext) {
            lastManualReportProjectId = projectId;
            lastManualReportSubmission = submission;
            lastManualReportPlayerContext = playerContext;
            return manualReportResult;
        }

        private TelemetryRuntimeDiagnostics.ProjectDiagnostics consentProjectDiagnostics() {
            return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                    consentProjectId,
                    consentDisplayName,
                    projectEnabled,
                    false,
                    "custom",
                    "https://example.invalid/telemetry",
                    pendingReports,
                    "Example:" + consentDisplayName,
                    "1.0.0",
                    null,
                    null,
                    List.of("com.example"),
                    "embedded",
                    crashEnabled,
                    errorEventsEnabled,
                    lifecycleEventsEnabled,
                    performanceEnabled,
                    usageEnabled,
                    statsEnabled,
                    breadcrumbsEnabled,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        private TelemetryRuntimeDiagnostics.ProjectDiagnostics consentOnlyProjectDiagnostics() {
            return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                    consentOnlyProjectId,
                    consentOnlyDisplayName,
                    true,
                    false,
                    "custom",
                    "https://example.invalid/telemetry",
                    0,
                    "Example:" + consentOnlyDisplayName,
                    "1.0.0",
                    null,
                    null,
                    List.of("com.example.consentonly"),
                    "embedded",
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        private Map<String, Object> projectSummary() {
            return Map.of(
                    "projectId", consentProjectId,
                    "displayName", consentDisplayName,
                    "runtimeMode", "embedded",
                    "pluginIdentifier", "Example:" + consentDisplayName,
                    "pluginVersion", "1.0.0",
                    "sourcePath", candidate.sourcePath().toString()
            );
        }
    }
}
