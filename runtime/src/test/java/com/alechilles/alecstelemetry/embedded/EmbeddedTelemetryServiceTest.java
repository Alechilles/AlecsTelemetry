package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedTelemetryServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCoordinatorRegistry() {
        TelemetryCoordinatorRegistry.clearForTests();
    }

    @Test
    void embeddedBootstrapProjectOverridePrefersSharedSettings() throws Exception {
        TelemetryDataPaths sharedPaths = dataPaths(tempDir.resolve("shared-telemetry"));
        TelemetryDataPaths ownerPaths = dataPaths(tempDir.resolve("owner-telemetry"));
        Files.createDirectories(sharedPaths.projectSettingsDirectory());
        Files.createDirectories(ownerPaths.projectSettingsDirectory());
        Files.writeString(sharedPaths.projectOverrideFile("embedded-project"), """
                {
                  "enabled": false
                }
                """);
        Files.writeString(ownerPaths.projectOverrideFile("embedded-project"), """
                {
                  "enabled": true
                }
                """);

        TelemetryProjectOverride override = EmbeddedTelemetryBootstrap.loadProjectOverride(
                sharedPaths,
                ownerPaths,
                null,
                "embedded-project"
        );

        assertNotNull(override);
        assertEquals(Boolean.FALSE, override.enabled());
    }

    @Test
    void embeddedBootstrapUsesSharedTelemetryCommandRoot() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null
        );

        TelemetryCommandRoot root = EmbeddedTelemetryBootstrap.sharedCommandRoot(service);

        assertNotNull(root);
        assertEquals(TelemetryCommandRoot.ROOT_PERMISSION, root.getPermission());
    }

    @Test
    void embeddedServiceCapturesAndFlushesForOneOwningProject() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry"
                  }
                }
                """,
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(
                CrashReportClient.UploadResult.failure(500, "server error"),
                CrashReportClient.UploadResult.success(204)
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        service.recordBreadcrumb("bootstrap", "Embedded config loaded.");
        RuntimeException throwable = new RuntimeException("embedded setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.embedded.EmbeddedMod", "setup", "EmbeddedMod.java", 12)
        });

        service.captureSetupFailure(throwable);
        assertEquals(1, service.pendingReports());
        assertEquals(1, service.flushPendingReportsNow("embedded-first").attempted());
        assertEquals(1, service.flushPendingReportsNow("embedded-second").attempted());
        assertEquals(0, service.flushPendingReportsNow("embedded-third").attempted());
        assertEquals(2, client.calls);
        JsonObject firstPayload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", firstPayload.get("projectId").getAsString());
        UUID.fromString(firstPayload.get("serverId").getAsString());
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-id.txt")));
        assertTrue(firstPayload.getAsJsonArray("breadcrumbs").size() > 0);
    }

    @Test
    void embeddedGenericEventsIncludeStableServerId() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
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
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        service.recordError("embedded_event", null, "Embedded runtime event");

        assertEquals(1, service.flushPendingReportsNow("embedded-event").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", payload.get("projectId").getAsString());
        assertEquals(2, payload.get("schemaVersion").getAsInt());
        UUID.fromString(payload.get("serverId").getAsString());
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-id.txt")));
    }

    @Test
    void embeddedStatsHeartbeatQueuesStandardStatsEvent() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptorWithStats(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryPlayerCounter playerCounter = new EmbeddedTelemetryPlayerCounter();
        playerCounter.markReady(UUID.randomUUID());
        playerCounter.markReady(UUID.randomUUID());
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null,
                playerCounter
        );

        service.emitStatsHeartbeatNow();

        assertEquals(1, service.flushPendingReportsNow("embedded-stats-heartbeat").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
        assertEquals(2, payload.getAsJsonObject("details").get("playersOnline").getAsInt());
    }

    @Test
    void embeddedServiceRegistersCoordinatorCandidateAndForwardsThroughActiveBridge() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                tempDir.resolve("Mods")
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService coordinatorService = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                "embedded:Example:Embedded Mod",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar"),
                telemetryRoot
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null,
                new EmbeddedTelemetryPlayerCounter(),
                candidate,
                coordinatorService
        );

        service.start();
        assertTrue(service.setProjectEnabled(false));
        assertFalse(service.isEnabled());
        service.recordError("disabled_event", null, "Should not queue while disabled.");
        assertEquals(0, service.flushPendingReportsNow("embedded-disabled").attempted());
        assertTrue(service.setProjectEnabled(true));
        assertTrue(service.isEnabled());
        service.recordError("embedded_event", null, "Forwarded through coordinator bridge.");

        assertEquals("embedded:Example:Embedded Mod", TelemetryCoordinatorRegistry.activeBridge().providerId());
        Map<String, Object> diagnostics = TelemetryCoordinatorRegistry.activeBridge().consentDiagnostics();
        assertEquals(1, ((List<?>) diagnostics.get("projects")).size());
        assertEquals("embedded-mod", TelemetryCoordinatorRegistry.activeBridge()
                .consentProjectDiagnostics("embedded-mod")
                .get("projectId"));
        assertEquals(1, service.flushPendingReportsNow("embedded-bridge").attempted());
        assertEquals(1, client.calls);
        assertTrue(TelemetryCoordinatorRegistry.activeBridge().applyConsent(
                "embedded-mod",
                TelemetryConsentBridgePayload.snapshotSummary(
                        new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
                )
        ));
        assertFalse(service.consentProjectDiagnostics("embedded-mod").enabled());

        service.shutdown();
        assertTrue(TelemetryCoordinatorRegistry.activeBridge() == null);
    }

    @Test
    void coordinatorBackedEmbeddedConsentIncludesConsentOnlyProjects() throws Exception {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                tempDir.resolve("Mods")
        );
        TelemetryProjectRegistration runtimeRegistration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        TelemetryProjectRegistration consentOnlyRegistration = new TelemetryProjectRegistration(
                consentOnlyDescriptor(),
                "Example:Consent Only Mod",
                "2.0.0",
                tempDir.resolve("Consent Only Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService coordinatorService = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(runtimeRegistration),
                List.of(runtimeRegistration, consentOnlyRegistration),
                List.of(
                        new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0"),
                        new CrashReportEnvelope.LoadedModMetadata("Example:Consent Only Mod", "2.0.0")
                ),
                client,
                null,
                null
        );
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                consentOnlyRegistration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null,
                new EmbeddedTelemetryPlayerCounter(),
                new TelemetryRuntimeCandidate(
                        "embedded:Example:Embedded Mod",
                        TelemetryRuntimeOrigin.EMBEDDED,
                        "0.1.4",
                        TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                        "Example:Embedded Mod",
                        "1.0.0",
                        tempDir.resolve("Embedded Mod.jar"),
                        telemetryRoot
                ),
                coordinatorService
        );

        service.start();

        assertTrue(service.isEnabled());
        assertEquals(
                List.of("embedded-mod", "consent-only-mod"),
                service.consentDiagnostics().projects().stream()
                        .map(com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics::projectId)
                        .toList()
        );
        assertTrue(service.consentProjectDiagnostics("consent-only-mod").enabled());
        assertTrue(service.applyConsent(
                "consent-only-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));
        assertFalse(service.consentProjectDiagnostics("consent-only-mod").enabled());
        assertFalse(service.isEnabled());
        String overrideRaw = java.nio.file.Files.readString(dataPaths.projectOverrideFile("consent-only-mod"));
        JsonObject override = JsonParser.parseString(overrideRaw).getAsJsonObject();
        assertFalse(override.get("enabled").getAsBoolean());
        assertFalse(override.getAsJsonObject("events").getAsJsonObject("errors").get("enabled").getAsBoolean());
        assertTrue(service.markConsentReviewed("consent-only-mod"));
        assertTrue(service.unreviewedConsentProjects().stream()
                .noneMatch(project -> project.projectId().equals("consent-only-mod")));

        service.shutdown();
    }

    @Test
    void passiveEmbeddedConsentRuntimeUsesActiveProviderBridgeView() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                tempDir.resolve("Mods")
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        TelemetryCoordinatorService coordinatorService = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(registration),
                List.of(registration),
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );
        ProviderStyleConsentBridge active = new ProviderStyleConsentBridge(new TelemetryRuntimeCandidate(
                "standalone:Example:Provider",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.5",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Provider",
                "2.0.0",
                tempDir.resolve("Provider.jar"),
                tempDir.resolve("ProviderTelemetry")
        ));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null,
                new EmbeddedTelemetryPlayerCounter(),
                new TelemetryRuntimeCandidate(
                        "embedded:Example:Embedded Mod",
                        TelemetryRuntimeOrigin.EMBEDDED,
                        "0.1.4",
                        TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                        "Example:Embedded Mod",
                        "1.0.0",
                        tempDir.resolve("Embedded Mod.jar"),
                        telemetryRoot
                ),
                coordinatorService
        );

        TelemetryCoordinatorRegistry.register(active);
        service.start();

        assertFalse(service.ownsActiveCoordinator());
        assertEquals("standalone:Example:Provider", service.activeCoordinatorProviderId());
        assertEquals(List.of("provider-mod", "provider-consent-only"), service.commandProjects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertNull(service.commandProject("embedded-mod"));
        assertEquals("provider-mod", service.commandProject("provider-mod").projectId());
        assertEquals("provider-consent-only", service.commandProject("provider-consent-only").projectId());
        assertTrue(service.commandProjectEnabled("provider-consent-only"));
        assertEquals(5, service.commandPendingReports(null));
        assertEquals(5, service.commandPendingReports("provider-mod"));
        assertEquals("active-last", service.commandLastFlushResult());
        assertTrue(service.commandFlush("provider-mod"));
        assertEquals("provider-mod", active.lastFlushProjectId);
        assertEquals("provider-mod", service.consentDiagnostics().projects().getFirst().projectId());
        assertNull(service.consentProjectDiagnostics("embedded-mod"));
        assertTrue(service.unreviewedConsentProjects().isEmpty());
        assertFalse(service.applyConsent("embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));

        assertTrue(service.applyConsent("provider-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));
        assertFalse(service.consentProjectDiagnostics("provider-mod").enabled());
        assertTrue(service.markConsentReviewed("provider-mod"));
        assertEquals(List.of("provider-mod"), active.reviewedProjectIds);

        service.shutdown();
    }

    @Test
    void shutdownFlushesQueuedStatsHeartbeatWhenAsyncFlushHasNotRun() {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptorWithStats(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        QueuedScheduledExecutor executor = new QueuedScheduledExecutor();
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                executor,
                new EmbeddedTelemetryPlayerCounter()
        );

        service.emitStatsHeartbeatNow();

        assertEquals(1, service.pendingReports());
        assertEquals(1, executor.queuedTasks());
        assertEquals(0, client.calls);

        service.shutdown();

        assertEquals(1, client.calls);
        assertEquals(0, service.pendingReports());
    }

    @Test
    void embeddedProjectEnabledControlPersistsAndBlocksCaptureImmediately() throws Exception {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        assertTrue(service.setProjectEnabled(false));
        assertFalse(service.isEnabled());
        service.captureSetupFailure(testThrowable());
        service.recordError("embedded_event", null, "Embedded runtime event");

        assertEquals(0, service.pendingReports());
        assertEquals(0, service.flushPendingReportsNow("disabled").attempted());
        String overrideRaw = java.nio.file.Files.readString(dataPaths.projectOverrideFile("embedded-mod"));
        JsonObject override = JsonParser.parseString(overrideRaw).getAsJsonObject();
        assertFalse(override.get("enabled").getAsBoolean());
    }

    @Test
    void embeddedBreadcrumbControlPersistsAndSuppressesBreadcrumbPayloads() throws Exception {
        Path telemetryRoot = tempDir.resolve("Telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(telemetryRoot.resolve("Settings").resolve("runtime.json"), null);
        TelemetryDataPaths dataPaths = new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                null
        );

        assertTrue(service.setBreadcrumbsEnabled(false));
        service.recordBreadcrumb("bootstrap", "Should not be included.");
        service.captureSetupFailure(testThrowable());

        assertEquals(1, service.flushPendingReportsNow("breadcrumbs-disabled").attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals(0, payload.getAsJsonArray("breadcrumbs").size());
        String overrideRaw = java.nio.file.Files.readString(dataPaths.projectOverrideFile("embedded-mod"));
        JsonObject override = JsonParser.parseString(overrideRaw).getAsJsonObject();
        assertFalse(override.getAsJsonObject("events").getAsJsonObject("breadcrumbs").get("enabled").getAsBoolean());
    }

    private static TelemetryDataPaths dataPaths(Path telemetryRoot) {
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                telemetryRoot.resolve("Settings").resolve("runtime.json"),
                null
        );
        return new TelemetryDataPaths(
                telemetryRoot,
                settings.filePath(),
                telemetryRoot.resolve("Settings").resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
        );
    }

    private static TelemetryProjectDescriptor descriptor() {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
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

    private static TelemetryProjectDescriptor descriptorWithStats() {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "embedded-mod",
                  "displayName": "Embedded Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Embedded Mod"],
                  "packagePrefixes": ["com.example.embedded"],
                  "defaults": {
                    "destinationMode": "custom"
                  },
                  "customEndpoint": {
                    "url": "https://example.invalid/telemetry",
                    "eventUrl": "https://example.invalid/telemetry/event"
                  },
                  "stats": {
                    "enabled": true
                  }
                }
                """,
                null
        );
    }

    private static TelemetryProjectDescriptor consentOnlyDescriptor() {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "consent-only-mod",
                  "displayName": "Consent Only Mod",
                  "runtimeMode": "embedded",
                  "ownerPluginIdentifiers": ["Example:Consent Only Mod"],
                  "packagePrefixes": ["com.example.consentonly"],
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
                  "reports": {
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
    }

    private static RuntimeException testThrowable() {
        RuntimeException throwable = new RuntimeException("embedded setup failed");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.example.embedded.EmbeddedMod", "setup", "EmbeddedMod.java", 12)
        });
        return throwable;
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

    private static final class QueuedScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int queuedTasks() {
            return tasks.size();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("schedule");
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("schedule");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                      long initialDelay,
                                                      long period,
                                                      TimeUnit unit) {
            throw new UnsupportedOperationException("scheduleAtFixedRate");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                         long initialDelay,
                                                         long delay,
                                                         TimeUnit unit) {
            throw new UnsupportedOperationException("scheduleWithFixedDelay");
        }

    }

    private static final class ProviderStyleConsentBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final java.util.ArrayList<String> reviewedProjectIds = new java.util.ArrayList<>();
        private boolean active;
        private String lastFlushProjectId;
        private int pendingReports = 5;
        private String lastFlushResult = "active-last";
        private TelemetryConsentSnapshot snapshot = new TelemetryConsentSnapshot(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );

        private ProviderStyleConsentBridge(TelemetryRuntimeCandidate candidate) {
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
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<Map<String, Object>> projectSummaries() {
            return List.of(projectSummary());
        }

        @Override
        public Map<String, Object> findProjectSummary(String projectId) {
            return "provider-mod".equalsIgnoreCase(projectId.trim()) ? projectSummary() : Map.of();
        }

        @Override
        public boolean isProjectEnabled(String projectId) {
            return "provider-mod".equalsIgnoreCase(projectId.trim()) && snapshot.projectEnabled();
        }

        @Override
        public boolean requestFlush(String projectId) {
            lastFlushProjectId = projectId;
            return projectId == null || "provider-mod".equalsIgnoreCase(projectId.trim());
        }

        @Override
        public Map<String, Object> consentDiagnostics() {
            return TelemetryConsentBridgePayload.diagnosticsSummary(new com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics(
                    true,
                    2,
                    1,
                    pendingReports,
                    false,
                    lastFlushResult,
                    null,
                    List.of(),
                    List.of(projectDiagnostics(), consentOnlyProjectDiagnostics())
            ));
        }

        @Override
        public Map<String, Object> consentProjectDiagnostics(String projectId) {
            String normalized = projectId.trim();
            if ("provider-mod".equalsIgnoreCase(normalized)) {
                return TelemetryConsentBridgePayload.projectDiagnosticsSummary(projectDiagnostics());
            }
            return "provider-consent-only".equalsIgnoreCase(normalized)
                    ? TelemetryConsentBridgePayload.projectDiagnosticsSummary(consentOnlyProjectDiagnostics())
                    : Map.of();
        }

        @Override
        public boolean applyConsent(String projectId, Map<String, Object> snapshot) {
            if (!"provider-mod".equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            this.snapshot = TelemetryConsentBridgePayload.snapshotFromSummary(snapshot);
            return true;
        }

        @Override
        public boolean markConsentReviewed(String projectId) {
            if (!"provider-mod".equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            reviewedProjectIds.add("provider-mod");
            return true;
        }

        private com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics() {
            return new com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                    "provider-mod",
                    "Provider Mod",
                    snapshot.projectEnabled(),
                    false,
                    "custom",
                    "https://example.invalid/telemetry",
                    pendingReports,
                    "Example:Provider",
                    "2.0.0",
                    null,
                    null,
                    List.of("com.example.provider"),
                    "dependency",
                    snapshot.crashEnabled(),
                    snapshot.errorEnabled(),
                    snapshot.lifecycleEnabled(),
                    snapshot.performanceEnabled(),
                    snapshot.usageEnabled(),
                    snapshot.statsEnabled(),
                    snapshot.breadcrumbsEnabled(),
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        private com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics consentOnlyProjectDiagnostics() {
            return new com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                    "provider-consent-only",
                    "Provider Consent Only",
                    true,
                    false,
                    "custom",
                    "https://example.invalid/telemetry",
                    0,
                    "Example:Provider Consent Only",
                    "2.0.0",
                    null,
                    null,
                    List.of("com.example.provider.consentonly"),
                    "dependency",
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
                    "projectId", "provider-mod",
                    "displayName", "Provider Mod",
                    "runtimeMode", "dependency",
                    "pluginIdentifier", "Example:Provider",
                    "pluginVersion", "2.0.0",
                    "sourcePath", candidate.sourcePath().toString()
            );
        }
    }
}
