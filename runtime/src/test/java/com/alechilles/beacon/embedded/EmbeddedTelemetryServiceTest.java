package com.alechilles.beacon.embedded;

import com.alechilles.beacon.api.TelemetryBreadcrumbContext;
import com.alechilles.beacon.api.TelemetryDiagnosticBundle;
import com.alechilles.beacon.api.TelemetryDiagnosticBundleResult;
import com.alechilles.beacon.api.TelemetryDiagnosticDisposition;
import com.alechilles.beacon.api.TelemetryEventContext;
import com.alechilles.beacon.api.TelemetryProjectHandle;
import com.alechilles.beacon.api.TelemetryRuntimeApi;
import com.alechilles.beacon.consent.TelemetryConsentSnapshot;
import com.alechilles.beacon.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.beacon.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.beacon.coordinator.TelemetryCoordinatorService;
import com.alechilles.beacon.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.beacon.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.beacon.coordinator.TelemetryProjectContributionRegistry;
import com.alechilles.beacon.coordinator.TelemetryServerVerificationResult;
import com.alechilles.beacon.commands.TelemetryCommandRoot;
import com.alechilles.beacon.crash.CrashReportClient;
import com.alechilles.beacon.crash.CrashReportEnvelope;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectOverride;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.report.ManualReportEnvelope;
import com.alechilles.beacon.report.PlayerReportRuntimeContext;
import com.alechilles.beacon.reports.TelemetryReportOpenRequest;
import com.alechilles.beacon.runtime.TelemetryConsentBridgePayload;
import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;
import com.alechilles.beacon.runtime.host.TelemetryRuntimeHostHandle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        TelemetryProjectContributionRegistry.clearForTests();
    }

    @Test
    void anchoredContributionUsesLogicalIdentityInsteadOfPhysicalHost() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("host"));
        Class<?> anchor = isolatedAnchor(
                "isolated/contributed-project.json",
                fixtureBytes("fixtures/contributed-hosted-project.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "/isolated/contributed-project.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertEquals("example-library", service.projectId());
        assertNull(service.disabledReason());
        assertEquals("Example:Library", service.projects().getFirst().pluginIdentifier());
        assertEquals("2.4.0", service.projects().getFirst().pluginVersion());
        Map<String, Object> candidate = TelemetryProjectContributionRegistry.activeContributions().getFirst();
        assertEquals("EMBEDDED", candidate.get("origin"));
        String canonicalHash = hex(MessageDigest.getInstance("SHA-256")
                .digest(service.projects().getFirst().descriptor().toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(canonicalHash, candidate.get("descriptorHash"));
    }

    @Test
    void anchoredContributionRejectsDescriptorVersionMismatch() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("descriptor-version-mismatch"));
        String descriptor = new String(
                fixtureBytes("fixtures/contributed-hosted-project.json"),
                java.nio.charset.StandardCharsets.UTF_8
        ).replace(
                "  \"displayName\": \"Example Library\",",
                "  \"projectVersion\": \"2.0.0\",\n"
                        + "  \"displayName\": \"Example Library\","
        );
        Class<?> anchor = isolatedAnchor(
                "isolated/version-mismatch.json",
                descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "/isolated/version-mismatch.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("1.0.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertEquals("descriptor_version_mismatch", service.disabledReason());
        assertTrue(TelemetryProjectContributionRegistry.activeContributions().isEmpty());
    }

    @Test
    void anchoredContributionWithCustomDestinationIsDisabledForMvp() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("custom-contribution"));
        Class<?> anchor = isolatedAnchor(
                "isolated/custom-contribution.json",
                fixtureBytes("fixtures/contributed-project.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "/isolated/custom-contribution.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertNotNull(service.disabledReason());
        assertTrue(service.disabledReason().contains("hosted"));
        assertTrue(service.disabledReason().contains("custom"));
        assertTrue(TelemetryProjectContributionRegistry.activeContributions().isEmpty());
    }

    @Test
    void preStartManualReportsAreRejectedAndNeverDrainAfterStart() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("pre-start-manual"));
        Class<?> anchor = isolatedAnchor(
                "isolated/pre-start-manual.json",
                fixtureBytes("fixtures/contributed-hosted-project.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "/isolated/pre-start-manual.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();
        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        ManualReportEnvelope.CreateResult first = service.submitManualReport(
                service.projectId(),
                new com.alechilles.beacon.report.ManualReportSubmission(
                        com.alechilles.beacon.report.ManualReportKind.ISSUE,
                        "pre-start",
                        "not queued",
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
        ManualReportEnvelope.CreateResult second = service.submitManualReport(
                service.projectId(),
                new com.alechilles.beacon.report.ManualReportSubmission(
                        com.alechilles.beacon.report.ManualReportKind.ISSUE,
                        "pre-start-duplicate",
                        "not queued",
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

        assertFalse(first.accepted());
        assertFalse(second.accepted());
        assertTrue(first.validationErrors().stream().anyMatch(error -> error.contains("start")));
        service.start();
        assertEquals(0, service.pendingReports());
    }

    @Test
    void anchoredContributionWithoutExplicitProjectIdentityReturnsDisabledService() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("missing-identity"));
        Class<?> anchor = isolatedAnchor(
                "isolated/missing-identity.json",
                fixtureBytes("fixtures/contributed-missing-identity.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "isolated/missing-identity.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertNotNull(service.disabledReason());
        assertTrue(service.disabledReason().contains("projectId"));
    }

    @Test
    void anchoredContributionWithNonStringIdentityReturnsDisabledService() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("non-string-identity"));
        Class<?> anchor = isolatedAnchor(
                "isolated/non-string-identity.json",
                fixtureBytes("fixtures/contributed-non-string-identity.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "isolated/non-string-identity.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertNotNull(service.disabledReason());
        assertTrue(service.disabledReason().contains("projectId"));
    }

    @Test
    void anchoredContributionLinkageFailureReturnsDisabledService() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("linkage-failure"));
        Class<?> anchor = throwingAnchor();
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "isolated/linkage-failure.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = assertDoesNotThrow(
                () -> EmbeddedTelemetryBootstrap.contribute(plugin, contribution)
        );

        assertNotNull(service.disabledReason());
        assertTrue(service.disabledReason().contains("linkage failure"));
    }

    @Test
    void conventionalBootstrapLoadsDescriptorFromHostClassLoader() throws Exception {
        JavaPlugin plugin = conventionalPlugin(
                tempDir.resolve("conventional-success"),
                "Server/Telemetry/project.json",
                fixtureBytes("fixtures/conventional-project.json")
        );

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.bootstrap(plugin);

        assertEquals("conventional-project", service.projectId());
        assertNull(service.disabledReason());
        assertEquals("Example:Host", service.projects().getFirst().pluginIdentifier());
        assertEquals("9.9.9", service.projects().getFirst().pluginVersion());
    }

    @Test
    void conventionalBootstrapPrefersOwningArchiveOverForeignClassLoaderDescriptor() throws Exception {
        Path ownerJar = tempDir.resolve("owner-archive").resolve("Host.jar");
        writeDescriptorArchive(
                ownerJar,
                "telemetry/project.json",
                fixtureBytes("fixtures/conventional-project.json")
        );
        JavaPlugin plugin = conventionalPlugin(
                tempDir.resolve("owner-archive"),
                "Server/Telemetry/project.json",
                fixtureBytes("fixtures/contributed-hosted-project.json"),
                ownerJar
        );

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.bootstrap(plugin);

        assertEquals("conventional-project", service.projectId());
        assertNull(service.disabledReason());
        assertEquals("Example:Host", service.projects().getFirst().pluginIdentifier());
        assertEquals("9.9.9", service.projects().getFirst().pluginVersion());
    }

    @Test
    void conventionalBootstrapMissingDescriptorKeepsDisabledFallback() throws Exception {
        JavaPlugin plugin = conventionalPlugin(
                tempDir.resolve("conventional-missing"),
                null,
                null
        );

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.bootstrap(plugin);

        assertEquals("host", service.projectId());
        assertEquals(
                "No Server/Telemetry/project.json descriptor was found in the owning mod."
                        + " Legacy telemetry/project.json was also absent.",
                service.disabledReason()
        );
    }

    @Test
    void anchoredContributionMissingResourceReturnsDisabledServiceWithReason() {
        TestPlugin plugin = testPlugin(tempDir.resolve("missing"));
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(AnchorFixture.class, "fixtures/missing-project.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();

        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        assertNotNull(service.disabledReason());
        assertTrue(service.disabledReason().contains("descriptor"));
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
    void hostBackedEmbeddedServiceIsAvailableBeforeStart() {
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
        FakeHostHandle host = new FakeHostHandle(registration);
        EmbeddedTelemetryService service = EmbeddedTelemetryService.fromHost(settings, dataPaths, registration, host, null);

        assertTrue(service.isEnabled());
        service.recordBreadcrumb("lifecycle", "setup started");
        service.recordBreadcrumb(TelemetryBreadcrumbContext.builder("persistence", "incident opened")
                .incidentId("incident-1")
                .build());
        service.captureSetupFailure(new RuntimeException("setup failed"));
        assertEquals(2, host.project.breadcrumbs);
        assertEquals("incident-1", host.project.lastBreadcrumbContext.incidentId());
        assertEquals(1, host.project.setupFailures);

        assertTrue(service.setProjectEnabled(false));
        assertFalse(service.isEnabled());
        assertTrue(service.setProjectEnabled(true));
        assertTrue(service.setBreadcrumbsEnabled(false));
        assertFalse(host.project.breadcrumbsEnabled);
        assertTrue(service.captureTestReport("pre-start"));

        if (service.isEnabled()) {
            service.start();
        }

        assertTrue(host.started);
        assertEquals(1, host.captureTestReports);
    }

    @Test
    void hostBackedEmbeddedConsentReviewDelegatesToHostForMetricReporting() {
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
        FakeHostHandle host = new FakeHostHandle(registration);
        EmbeddedTelemetryService service = EmbeddedTelemetryService.fromHost(settings, dataPaths, registration, host, null);

        assertTrue(service.markConsentReviewed("embedded-mod"));

        assertEquals(List.of("embedded-mod"), host.reviewedProjectIds);
    }

    @Test
    void crashEnabledEmbeddedServiceQueuesManualTestReport() {
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

        assertTrue(service.captureTestReport("embedded-check"));
        assertEquals(1, service.pendingReports());
        assertEquals(1, service.flushPendingReportsNow("manual-test").attempted());
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
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "events": {
                    "errors": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "diagnostics": {
                    "supported": true,
                    "defaultEnabled": true
                  },
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
        assertEquals(0, service.flushPendingReportsNow("embedded-second").attempted());
        assertEquals(1, service.flushPendingReportsNow("manual").attempted());
        assertEquals(0, service.flushPendingReportsNow("embedded-third").attempted());
        assertEquals(2, client.calls);
        JsonObject firstPayload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("embedded-mod", firstPayload.get("projectId").getAsString());
        UUID.fromString(firstPayload.get("serverId").getAsString());
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-identity.json")));
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
                  "events": {
                    "errors": { "enabled": true }
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
        assertTrue(java.nio.file.Files.isRegularFile(telemetryRoot.resolve("Settings").resolve("server-identity.json")));
    }

    @Test
    void embeddedStatsHeartbeatQueuesStandardStatsEvent() throws Exception {
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
        Files.createDirectories(telemetryRoot.resolve("Settings"));
        Files.writeString(telemetryRoot.resolve("Settings").resolve("server-identity.json"), """
                {
                  "serverId": "550e8400-e29b-41d4-a716-446655440000",
                  "serverClaimToken": "ms_claim_test"
                }
                """);
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
        assertEquals(2, payload.getAsJsonObject("details").get("maxPlayersSinceLastReport").getAsInt());
        assertEquals(1.67, payload.getAsJsonObject("details").get("avgPlayersSinceLastReport").getAsDouble(), 0.01);
        assertEquals("ms_claim_test", payload.getAsJsonObject("details").get("serverClaimToken").getAsString());
    }

    @Test
    void startSchedulesStatsHeartbeatAfterStartupDelay() {
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

        service.start();

        assertEquals(0, service.pendingReports());
        assertEquals(1, executor.queuedTasks());
        assertTrue(executor.lastScheduledDelaySeconds() >= 120);
        assertTrue(executor.lastScheduledDelaySeconds() <= 300);

        executor.runNext();
        service.flushPendingReportsNow("startup-heartbeat-barrier");

        assertEquals(1, client.calls);
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
    }

    @Test
    void shutdownQueuesBestEffortStatsHeartbeatAfterStart() {
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
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                client,
                null,
                new QueuedScheduledExecutor(),
                new EmbeddedTelemetryPlayerCounter()
        );

        service.start();
        service.shutdown();

        assertEquals(1, client.payloads.size());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("stats", payload.get("eventType").getAsString());
        assertEquals("heartbeat", payload.get("eventName").getAsString());
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
                        new TelemetryConsentSnapshot(false, false, false, true, false, false, false, false, false)
                )
        ));
        assertFalse(service.consentProjectDiagnostics("embedded-mod").enabled());
        assertFalse(service.consentProjectDiagnostics("embedded-mod").errorEnabled());
        assertTrue(service.consentProjectDiagnostics("embedded-mod").diagnosticsEnabled());

        service.shutdown();
        assertTrue(TelemetryCoordinatorRegistry.activeBridge() == null);
    }

    @Test
    void diagnosticSubmissionFailsClosedForLegacyActiveCoordinator() {
        Path telemetryRoot = tempDir.resolve("legacy-active-telemetry");
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(
                telemetryRoot.resolve("Settings").resolve("runtime.json"), null
        );
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
        LegacyProtocolThreeBridge legacy = new LegacyProtocolThreeBridge(new TelemetryRuntimeCandidate(
                "standalone:Example:Legacy",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Legacy",
                "1.0.0",
                tempDir.resolve("Legacy.jar"),
                tempDir.resolve("LegacyTelemetry")
        ));
        TelemetryCoordinatorRegistry.register(legacy);
        EmbeddedTelemetryService service = new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Embedded Mod", "1.0.0")),
                new SequencedClient(CrashReportClient.UploadResult.success(204)),
                null,
                null,
                new EmbeddedTelemetryPlayerCounter()
        );

        TelemetryDiagnosticBundleResult result = service.submitDiagnosticBundle(
                registration.projectId(),
                diagnosticBundle()
        );

        assertEquals(TelemetryDiagnosticBundleResult.Status.DISABLED, result.status());
        assertEquals("diagnostic_telemetry_disabled", result.detail());
        assertEquals(0, legacy.diagnosticDispatches);
    }

    @Test
    void bufferedDiagnosticFailsClosedWhenLegacyCoordinatorAppearsBeforeStart() throws Exception {
        TestPlugin plugin = testPlugin(tempDir.resolve("pre-start-diagnostic"));
        Class<?> anchor = isolatedAnchor(
                "isolated/pre-start-diagnostic.json",
                fixtureBytes("fixtures/contributed-hosted-project.json")
        );
        TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
                .descriptorResource(anchor, "/isolated/pre-start-diagnostic.json")
                .logicalPluginIdentifier("Example:Library")
                .logicalPluginVersion("2.4.0")
                .build();
        EmbeddedTelemetryService service = EmbeddedTelemetryBootstrap.contribute(plugin, contribution);

        TelemetryDiagnosticBundleResult buffered = service.submitDiagnosticBundle(
                service.projectId(),
                diagnosticBundle()
        );
        assertEquals(TelemetryDiagnosticBundleResult.Status.QUEUED, buffered.status());
        assertTrue(buffered.accepted());

        LegacyProtocolThreeBridge legacy = new LegacyProtocolThreeBridge(new TelemetryRuntimeCandidate(
                "standalone:Example:Legacy",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Legacy",
                "1.0.0",
                tempDir.resolve("Legacy.jar"),
                tempDir.resolve("LegacyTelemetry")
        ));
        TelemetryCoordinatorRegistry.register(legacy);

        service.start();

        assertEquals(0, legacy.diagnosticDispatches);
        assertEquals(0, legacy.diagnosticContributionDispatches);
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
                        .map(com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics::projectId)
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
    void serverVerificationFallsBackToLocalRuntimeWhenActiveBridgeDoesNotSupportIt() throws Exception {
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
        Files.createDirectories(telemetryRoot.resolve("Settings"));
        Files.writeString(telemetryRoot.resolve("Settings").resolve("server-identity.json"), """
                {
                  "serverId": "023d9570-243a-4bbf-bca7-cf54d286b4cb",
                  "serverClaimToken": "ms_claim_gvDFjGzV7j-GTUWDrD7jZ_qBiDFmfSVT"
                }
                """);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptorWithStats(),
                "Example:Embedded Mod",
                "1.0.0",
                tempDir.resolve("Embedded Mod.jar")
        );
        SequencedClient client = new SequencedClient(CrashReportClient.UploadResult.success(204));
        EmbeddedTelemetryPlayerCounter playerCounter = new EmbeddedTelemetryPlayerCounter();
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
        ProviderStyleConsentBridge oldActiveBridge = new ProviderStyleConsentBridge(new TelemetryRuntimeCandidate(
                "standalone:Example:Old Provider",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.2.1",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Old Provider",
                "1.0.0",
                tempDir.resolve("OldProvider.jar"),
                tempDir.resolve("OldProviderTelemetry")
        ));
        TelemetryCoordinatorRegistry.register(oldActiveBridge);

        TelemetryServerVerificationResult result = service.commandServerVerification();

        assertEquals(TelemetryServerVerificationResult.Status.QUEUED, result.status());
        assertEquals(1, result.flushSummary().attempted());
        JsonObject payload = JsonParser.parseString(client.payloads.getFirst()).getAsJsonObject();
        assertEquals("ms_claim_gvDFjGzV7j-GTUWDrD7jZ_qBiDFmfSVT", payload.getAsJsonObject("details").get("serverClaimToken").getAsString());
    }

    @Test
    void shutdownCompletesStatsHeartbeatWithoutDuplicateUpload() {
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
                  "capture": {
                    "uncaughtExceptions": true,
                    "setupFailures": true,
                    "startFailures": true,
                    "exceptionalWorldRemovals": true
                  },
                  "events": {
                    "errors": { "enabled": true },
                    "breadcrumbs": { "enabled": true }
                  },
                  "diagnostics": {
                    "supported": true,
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
        private long lastScheduledDelaySeconds = -1;
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

        long lastScheduledDelaySeconds() {
            return lastScheduledDelaySeconds;
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task != null) {
                task.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastScheduledDelaySeconds = TimeUnit.SECONDS.convert(delay, unit);
            tasks.add(command);
            return new NoopScheduledFuture<>();
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
            return new NoopScheduledFuture<>();
        }

    }

    private static final class NoopScheduledFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public V get() {
            return null;
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private static TelemetryDiagnosticBundle diagnosticBundle() {
        return new TelemetryDiagnosticBundle(
                "legacy-diagnostic",
                "2026-08-31T19:00:00Z",
                "test",
                "persistence",
                "Persistence failure",
                "Legacy coordinator gate test.",
                "error",
                TelemetryDiagnosticDisposition.informational(),
                Map.of(),
                List.of()
        );
    }

    private static final class LegacyProtocolThreeBridge {
        private final TelemetryRuntimeCandidate candidate;
        private boolean active;
        private int diagnosticDispatches;
        private int diagnosticContributionDispatches;

        private LegacyProtocolThreeBridge(TelemetryRuntimeCandidate candidate) {
            this.candidate = candidate;
        }

        public String providerId() {
            return candidate.providerId();
        }

        public String origin() {
            return candidate.origin().name();
        }

        public String runtimeVersion() {
            return candidate.runtimeVersion();
        }

        public int coordinatorProtocolVersion() {
            return candidate.coordinatorProtocolVersion();
        }

        public String providerPluginIdentifier() {
            return candidate.providerPluginIdentifier();
        }

        public String providerPluginVersion() {
            return candidate.providerPluginVersion();
        }

        public String sourcePath() {
            return candidate.sourcePath().toString();
        }

        public String sharedDataRoot() {
            return candidate.sharedDataRoot().toString();
        }

        public void activate() {
            active = true;
        }

        public void deactivate() {
            active = false;
        }

        public boolean isActive() {
            return active;
        }

        public boolean reconcileProjectContributions(long revision, List<Map<String, Object>> contributions) {
            return true;
        }

        public Map<String, Object> dispatchProjectContribution(String token,
                                                               String operation,
                                                               Map<String, Object> payload,
                                                               Throwable throwable) {
            if ("diagnostic_bundle".equalsIgnoreCase(operation)) {
                diagnosticContributionDispatches++;
            }
            return Map.of("accepted", true);
        }

        public Map<String, Object> submitDiagnosticBundle(String projectId,
                                                           Map<String, Object> bundle) {
            diagnosticDispatches++;
            return Map.of("status", "QUEUED");
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
            return TelemetryConsentBridgePayload.diagnosticsSummary(new com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics(
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

        private com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics() {
            return new com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics(
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

        private com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics consentOnlyProjectDiagnostics() {
            return new com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics.ProjectDiagnostics(
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
    private static final class FakeHostHandle implements TelemetryRuntimeHostHandle {
        private final FakeProjectHandle project;
        private final TelemetryRuntimeApi api;
        private boolean started;
        private boolean shutdown;
        private int captureTestReports;
        private final java.util.ArrayList<String> reviewedProjectIds = new java.util.ArrayList<>();

        private FakeHostHandle(@Nonnull TelemetryProjectRegistration registration) {
            this.project = new FakeProjectHandle(registration);
            this.api = new TelemetryRuntimeApi() {
                @Override
                public boolean isEnabled() {
                    return project.isEnabled();
                }

                @Override
                public List<TelemetryProjectHandle> projects() {
                    return List.of(project);
                }

                @Override
                public TelemetryProjectHandle findProject(@Nonnull String projectId) {
                    return project.projectId().equalsIgnoreCase(projectId.trim()) ? project : null;
                }

                @Override
                public boolean requestFlush() {
                    return project.requestFlush();
                }
            };
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public boolean ownsActiveCoordinator() {
            return started && !shutdown;
        }

        @Override
        public String activeCoordinatorProviderId() {
            return ownsActiveCoordinator() ? "embedded:Example:Embedded Mod" : null;
        }

        @Override
        public int registeredProjectCount() {
            return 1;
        }

        @Override
        public TelemetryRuntimeApi api() {
            return api;
        }

        @Override
        public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            project.enabled = enabled;
            return true;
        }

        @Override
        public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            project.breadcrumbsEnabled = enabled;
            return true;
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            project.enabled = snapshot.projectEnabled();
            project.breadcrumbsEnabled = snapshot.breadcrumbsEnabled();
            return true;
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, String detail) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            captureTestReports++;
            return true;
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            reviewedProjectIds.add(project.projectId());
            return true;
        }
    }

    private static final class FakeProjectHandle implements TelemetryProjectHandle {
        private final TelemetryProjectRegistration registration;
        private boolean enabled;
        private boolean breadcrumbsEnabled;
        private int breadcrumbs;
        private TelemetryBreadcrumbContext lastBreadcrumbContext;
        private int setupFailures;
        private int flushRequests;

        private FakeProjectHandle(@Nonnull TelemetryProjectRegistration registration) {
            this.registration = registration;
            this.enabled = registration.isEnabled();
            this.breadcrumbsEnabled = registration.events().breadcrumbs().enabled();
        }

        @Override
        public String projectId() {
            return registration.projectId();
        }

        @Override
        public String displayName() {
            return registration.displayName();
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
            breadcrumbs++;
        }

        @Override
        public void recordBreadcrumb(@Nonnull TelemetryBreadcrumbContext context) {
            breadcrumbs++;
            lastBreadcrumbContext = context;
        }

        @Override
        public void captureSetupFailure(Throwable throwable) {
            setupFailures++;
        }

        @Override
        public void captureStartFailure(Throwable throwable) {
        }

        @Override
        public void recordError(@Nonnull String eventName, Throwable throwable, String detail) {
        }

        @Override
        public void recordErrorWithContext(@Nonnull String eventName, Throwable throwable, TelemetryEventContext context) {
        }

        @Override
        public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, String detail) {
        }

        @Override
        public void recordLifecycleWithContext(@Nonnull String eventName, int durationMs, boolean success, TelemetryEventContext context) {
        }

        @Override
        public void recordPerformance(@Nonnull String eventName, int durationMs, Double metricValue, String detail) {
        }

        @Override
        public void recordPerformanceWithContext(@Nonnull String eventName, int durationMs, Double metricValue, TelemetryEventContext context) {
        }

        @Override
        public void recordUsage(@Nonnull String eventName, String detail) {
        }

        @Override
        public void recordUsageWithContext(@Nonnull String eventName, TelemetryEventContext context) {
        }

        @Override
        public void recordStats(@Nonnull String eventName, String detail) {
        }

        @Override
        public void recordStatsWithContext(@Nonnull String eventName, TelemetryEventContext context) {
        }

        @Override
        public boolean openReportPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull TelemetryReportOpenRequest request) {
            return false;
        }

        @Override
        public boolean requestFlush() {
            flushRequests++;
            return true;
        }
    }

    private static TestPlugin testPlugin(Path dataDirectory) {
        try {
            Options.parse(new String[0]);
            TestPlugin plugin = (TestPlugin) unsafe().allocateInstance(TestPlugin.class);
            plugin.dataDirectory = dataDirectory;
            plugin.file = dataDirectory.resolve("Host.jar");
            plugin.manifest = testManifest();
            plugin.identifier = new PluginIdentifier(plugin.manifest);
            return plugin;
        } catch (java.io.IOException | InstantiationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static JavaPlugin conventionalPlugin(Path dataDirectory,
                                                  String descriptorResource,
                                                  byte[] descriptorBytes) throws Exception {
        return conventionalPlugin(
                dataDirectory,
                descriptorResource,
                descriptorBytes,
                dataDirectory.resolve("Host.jar")
        );
    }

    private static JavaPlugin conventionalPlugin(Path dataDirectory,
                                                  String descriptorResource,
                                                  byte[] descriptorBytes,
                                                  Path pluginFile) throws Exception {
        Options.parse(new String[0]);
        PluginFixtureClassLoader loader = new PluginFixtureClassLoader(
                descriptorResource,
                descriptorBytes,
                classBytes(ConventionalPlugin.class)
        );
        Class<?> pluginType = loader.definePlugin();
        Object plugin = unsafe().allocateInstance(pluginType);
        setField(pluginType, plugin, "dataDirectory", dataDirectory);
        setField(pluginType, plugin, "file", pluginFile);
        PluginManifest manifest = testManifest();
        setField(pluginType, plugin, "manifest", manifest);
        setField(pluginType, plugin, "identifier", new PluginIdentifier(manifest));
        return (JavaPlugin) plugin;
    }

    private static void writeDescriptorArchive(Path archive,
                                               String descriptorResource,
                                               byte[] descriptorBytes) throws IOException {
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(archive))) {
            stream.putNextEntry(new ZipEntry(descriptorResource));
            stream.write(descriptorBytes);
            stream.closeEntry();
        }
    }

    private static Class<?> isolatedAnchor(String resourceName, byte[] descriptorBytes) throws IOException {
        DescriptorClassLoader loader = new DescriptorClassLoader(resourceName, descriptorBytes, false);
        return loader.defineAnchor(classBytes(AnchorFixture.class));
    }

    private static Class<?> throwingAnchor() throws IOException {
        DescriptorClassLoader loader = new DescriptorClassLoader("isolated/linkage-failure.json", null, true);
        return loader.defineAnchor(classBytes(AnchorFixture.class));
    }

    private static byte[] fixtureBytes(String resourceName) throws IOException {
        try (InputStream stream = EmbeddedTelemetryServiceTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("Missing test fixture " + resourceName);
            }
            return stream.readAllBytes();
        }
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("Missing class bytes " + resourceName);
            }
            return stream.readAllBytes();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static void setField(Class<?> type, Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe unsafe() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class AnchorFixture {
    }

    private static final class DescriptorClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] descriptorBytes;
        private final boolean throwLinkageError;

        private DescriptorClassLoader(String resourceName, byte[] descriptorBytes, boolean throwLinkageError) {
            super(null);
            this.resourceName = resourceName;
            this.descriptorBytes = descriptorBytes;
            this.throwLinkageError = throwLinkageError;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (throwLinkageError) {
                throw new NoClassDefFoundError("simulated anchor linkage failure");
            }
            return resourceName.equals(name)
                    ? new ByteArrayInputStream(descriptorBytes)
                    : null;
        }

        private Class<?> defineAnchor(byte[] bytes) {
            return defineClass(AnchorFixture.class.getName(), bytes, 0, bytes.length);
        }
    }

    private static final class PluginFixtureClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] descriptorBytes;
        private final byte[] pluginBytes;

        private PluginFixtureClassLoader(String resourceName, byte[] descriptorBytes, byte[] pluginBytes) {
            super(EmbeddedTelemetryServiceTest.class.getClassLoader());
            this.resourceName = resourceName;
            this.descriptorBytes = descriptorBytes;
            this.pluginBytes = pluginBytes;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return resourceName != null && resourceName.equals(name) && descriptorBytes != null
                    ? new ByteArrayInputStream(descriptorBytes)
                    : null;
        }

        private Class<?> definePlugin() {
            return defineClass(ConventionalPlugin.class.getName(), pluginBytes, 0, pluginBytes.length);
        }
    }

    private static class ConventionalPlugin extends JavaPlugin {
        private Path dataDirectory;
        private Path file;
        private PluginManifest manifest;
        private PluginIdentifier identifier;

        private ConventionalPlugin() {
            super(null);
        }

        @Override
        public HytaleLogger getLogger() {
            return null;
        }

        @Override
        public Path getDataDirectory() {
            return dataDirectory;
        }

        @Override
        public Path getFile() {
            return file;
        }

        @Override
        public PluginManifest getManifest() {
            return manifest;
        }

        @Override
        public PluginIdentifier getIdentifier() {
            return identifier;
        }
    }

    private static final class TestPlugin extends JavaPlugin {
        private Path dataDirectory;
        private Path file;
        private PluginManifest manifest;
        private PluginIdentifier identifier;

        private TestPlugin() {
            super(null);
        }

        @Override
        public HytaleLogger getLogger() {
            return null;
        }

        @Override
        public Path getDataDirectory() {
            return dataDirectory;
        }

        @Override
        public Path getFile() {
            return file;
        }

        @Override
        public PluginManifest getManifest() {
            return manifest;
        }

        @Override
        public PluginIdentifier getIdentifier() {
            return identifier;
        }
    }

    private static PluginManifest testManifest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setGroup("Example");
        manifest.setName("Host");
        manifest.setVersion(Semver.fromString("9.9.9"));
        return manifest;
    }
}
