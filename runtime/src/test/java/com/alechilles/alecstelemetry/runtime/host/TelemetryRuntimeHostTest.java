package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentBridgePayload;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeHostTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearRuntimeGlobals() {
        TelemetryCoordinatorRegistry.clearForTests();
        TelemetryRuntimeLocator.clear();
    }

    @Test
    void exposesStandaloneAndEmbeddedBootstrapMethods() throws Exception {
        Method standalone = TelemetryRuntimeHost.class.getMethod(
                "bootstrapStandalone",
                JavaPlugin.class
        );
        Method embedded = TelemetryRuntimeHost.class.getMethod(
                "bootstrapEmbedded",
                JavaPlugin.class,
                TelemetryProjectDescriptor.class
        );

        assertNotNull(standalone);
        assertNotNull(embedded);
        assertEquals(TelemetryRuntimeHostHandle.class, standalone.getReturnType());
        assertEquals(TelemetryRuntimeHostHandle.class, embedded.getReturnType());
    }

    @Test
    void providerHandleStartsAndExposesApi() {
        TelemetryRuntimeProviderHandle handle = handle("standalone:Alechilles:Telemetry", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");

        handle.start();

        assertSame(handle.api(), TelemetryRuntimeLocator.tryGet());
        assertTrue(handle.ownsActiveCoordinator());
        assertEquals("standalone:Alechilles:Telemetry", handle.activeCoordinatorProviderId());
        assertEquals(1, handle.registeredProjectCount());
        assertTrue(handle.api().isEnabled());
        assertEquals(1, handle.api().projects().size());

        handle.shutdown();

        assertFalse(handle.ownsActiveCoordinator());
    }

    @Test
    void providerHandleReportsElectionOwnershipChanges() {
        ProviderFixture standalone = fixture(
                "standalone:Alechilles:Telemetry",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "standalone-project",
                "Standalone Project"
        );
        ProviderFixture embedded = fixture(
                "embedded:Alechilles:Consumer",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "embedded-project",
                "Embedded Project"
        );

        standalone.handle().start();
        embedded.handle().start();

        assertFalse(standalone.handle().ownsActiveCoordinator());
        assertTrue(embedded.handle().ownsActiveCoordinator());
        assertEquals("embedded:Alechilles:Consumer", standalone.handle().activeCoordinatorProviderId());
        assertEquals("embedded:Alechilles:Consumer", embedded.handle().activeCoordinatorProviderId());

        List<TelemetryProjectHandle> projects = standalone.handle().api().projects();
        assertEquals(1, projects.size());
        assertEquals("embedded-project", projects.getFirst().projectId());
        assertEquals("Embedded Project", projects.getFirst().displayName());
        assertNotNull(standalone.handle().api().findProject("embedded-project"));
        assertNull(standalone.handle().api().findProject("standalone-project"));
    }

    @Test
    void losingProviderConsentPathsUseActiveProviderViewAndRejectStaleLocalProjects() {
        TelemetryProjectRegistration loserProject = registration(
                telemetryCategoryDescriptor("loser-mod", "Loser Mod"),
                "Example:Loser Mod",
                "1.0.0",
                tempDir.resolve("Loser Mod")
        );
        TelemetryDataPaths loserPaths = dataPaths(tempDir.resolve("losing-provider"));
        ProviderFixture loser = providerFixture(
                "standalone:Alechilles:Loser",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                TelemetryRuntimeSettings.load(loserPaths.settingsFile(), null),
                loserPaths,
                List.of(loserProject),
                List.of(loserProject),
                List.of(),
                loserProject
        );
        TelemetryProjectRegistration winnerProject = registration(
                telemetryCategoryDescriptor("winner-mod", "Winner Mod"),
                "Example:Winner Mod",
                "2.0.0",
                tempDir.resolve("Winner Mod")
        );
        TelemetryDataPaths winnerPaths = dataPaths(tempDir.resolve("winning-provider"));
        ProviderFixture winner = providerFixture(
                "embedded:Alechilles:Winner",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                TelemetryRuntimeSettings.load(winnerPaths.settingsFile(), null),
                winnerPaths,
                List.of(winnerProject),
                List.of(winnerProject),
                List.of(),
                winnerProject
        );

        loser.handle().start();
        winner.handle().start();

        assertFalse(loser.handle().ownsActiveCoordinator());
        assertTrue(winner.handle().ownsActiveCoordinator());
        TelemetryRuntimeDiagnostics loserDiagnostics = loser.handle().consentDiagnostics();
        assertEquals(1, loserDiagnostics.projects().size());
        assertEquals("winner-mod", loserDiagnostics.projects().getFirst().projectId());
        assertNull(loser.handle().consentProjectDiagnostics("loser-mod"));
        assertNotNull(loser.handle().consentProjectDiagnostics("winner-mod"));

        TelemetryConsentSnapshot disabled = new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false);
        assertFalse(loser.handle().applyConsent("loser-mod", disabled));
        assertFalse(Files.exists(loser.dataPaths().projectOverrideFile("loser-mod")));

        assertTrue(loser.handle().applyConsent("winner-mod", disabled));
        assertFalse(winner.handle().consentProjectDiagnostics("winner-mod").enabled());
        assertFalse(loser.handle().consentProjectDiagnostics("winner-mod").enabled());
        assertTrue(Files.exists(winner.dataPaths().projectOverrideFile("winner-mod")));

        TelemetryConsentSnapshot enabled = new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true);
        assertTrue(loser.handle().applyConsentToAll(enabled));
        assertTrue(winner.handle().consentProjectDiagnostics("winner-mod").enabled());
        assertTrue(loser.handle().consentProjectDiagnostics("winner-mod").enabled());
    }

    @Test
    void losingProviderConsentPathsUseActiveStandaloneBridgeViewAndReviewState() {
        TelemetryProjectRegistration activeProject = registration(
                telemetryCategoryDescriptor("standalone-active", "Standalone Active"),
                "Example:Standalone Active",
                "2.0.0",
                tempDir.resolve("Standalone Active.jar")
        );
        StandaloneStyleConsentBridge active = new StandaloneStyleConsentBridge(new TelemetryRuntimeCandidate(
                "standalone:Example:Active",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.4",
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:Active",
                "2.0.0",
                tempDir.resolve("Active.jar"),
                tempDir.resolve("active-runtime")
        ), activeProject);
        ProviderFixture loser = fixture(
                "standalone:Example:Loser",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "stale-provider",
                "Stale Provider"
        );

        TelemetryCoordinatorRegistry.register(active);
        loser.handle().start();

        assertFalse(loser.handle().ownsActiveCoordinator());
        assertEquals("standalone:Example:Active", loser.handle().activeCoordinatorProviderId());
        assertEquals("standalone-active", loser.handle().consentDiagnostics().projects().getFirst().projectId());
        assertNull(loser.handle().consentProjectDiagnostics("stale-provider"));
        assertFalse(loser.handle().applyConsent("stale-provider",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));

        assertTrue(loser.handle().applyConsent("standalone-active",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));
        assertFalse(loser.handle().consentProjectDiagnostics("standalone-active").enabled());
        assertTrue(loser.handle().markConsentReviewed("standalone-active"));
        assertEquals(List.of("standalone-active"), active.reviewedProjectIds);
        assertFalse(loser.handle().markConsentReviewed("stale-provider"));
    }

    @Test
    void loserShutdownDoesNotClearWinnerApi() {
        ProviderFixture standalone = fixture("standalone:Alechilles:Telemetry", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");
        ProviderFixture embedded = fixture("embedded:Alechilles:Consumer", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");

        standalone.handle().start();
        embedded.handle().start();

        assertSame(embedded.handle().api(), TelemetryRuntimeLocator.tryGet());
        assertFalse(standalone.commandRegistrar().registered());
        assertTrue(embedded.commandRegistrar().registered());

        standalone.handle().shutdown();

        assertSame(embedded.handle().api(), TelemetryRuntimeLocator.tryGet());
        assertFalse(standalone.commandRegistrar().registered());
        assertTrue(embedded.commandRegistrar().registered());
    }

    @Test
    void commandRegistrarFollowsCoordinatorElection() {
        ProviderFixture standalone = fixture("standalone:Alechilles:Telemetry", TelemetryRuntimeOrigin.STANDALONE, "0.1.3");
        ProviderFixture embedded = fixture("embedded:Alechilles:Consumer", TelemetryRuntimeOrigin.EMBEDDED, "0.1.4");

        standalone.handle().start();

        assertTrue(standalone.commandRegistrar().registered());
        assertSame(standalone.handle().api(), TelemetryRuntimeLocator.tryGet());

        embedded.handle().start();

        assertFalse(standalone.commandRegistrar().registered());
        assertTrue(embedded.commandRegistrar().registered());
        assertSame(embedded.handle().api(), TelemetryRuntimeLocator.tryGet());

        embedded.handle().shutdown();

        assertTrue(standalone.commandRegistrar().registered());
        assertFalse(embedded.commandRegistrar().registered());
        assertSame(standalone.handle().api(), TelemetryRuntimeLocator.tryGet());
    }

    @Test
    void providerApplyConsentToAllUpdatesEveryProjectAndTelemetryCategory() {
        TelemetryProjectRegistration first = registration(
                telemetryCategoryDescriptor("first-mod", "First Mod"),
                "Example:First Mod",
                "1.0.0",
                tempDir.resolve("First Mod")
        );
        TelemetryProjectRegistration second = registration(
                telemetryCategoryDescriptor("second-mod", "Second Mod"),
                "Example:Second Mod",
                "2.0.0",
                tempDir.resolve("Second Mod")
        );
        ProviderFixture fixture = consentFixture("bulk-consent", List.of(first, second), List.of(first, second));
        TelemetryRuntimeProviderHandle handle = fixture.handle();

        assertTrue(handle.applyConsentToAll(new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : handle.consentDiagnostics().projects()) {
            assertAllTelemetryDisabled(project);
            assertTrue(project.overridePresent());
        }

        assertTrue(handle.applyConsentToAll(new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true)));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : handle.consentDiagnostics().projects()) {
            assertAllTelemetryEnabled(project);
            assertTrue(project.overridePresent());
        }

        assertTrue(handle.applyConsentCategoryToAll("usage", false));
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : handle.consentDiagnostics().projects()) {
            assertTrue(project.enabled());
            assertTrue(project.crashEnabled());
            assertTrue(project.errorEnabled());
            assertTrue(project.lifecycleEnabled());
            assertTrue(project.performanceEnabled());
            assertFalse(project.usageEnabled());
            assertTrue(project.statsEnabled());
            assertTrue(project.breadcrumbsEnabled());
            assertTrue(project.overridePresent());
        }

        TelemetryProjectOverride override = new TelemetryProjectOverrideStore(null)
                .load(fixture.dataPaths().projectOverrideFile("first-mod"));
        assertNotNull(override);
        assertEquals(false, override.usage().enabled());
        assertEquals(true, override.stats().enabled());
    }

    @Test
    void providerConsentBulkChangesOnlyEnableCategoriesSupportedByDescriptor() {
        TelemetryProjectRegistration project = registration(
                statsOnlyTelemetryDescriptor("animal-husbandry", "Animal Husbandry"),
                "Example:Animal Husbandry",
                "1.0.0",
                tempDir.resolve("Animal Husbandry")
        );
        TelemetryRuntimeProviderHandle handle = consentFixture("stats-only-consent", List.of(project), List.of(project)).handle();

        assertTrue(handle.applyConsentToAll(new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true)));
        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = handle.consentProjectDiagnostics("animal-husbandry");
        assertNotNull(diagnostics);
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

        assertTrue(handle.applyConsentCategoryToAll("usage", true));
        diagnostics = handle.consentProjectDiagnostics("animal-husbandry");
        assertNotNull(diagnostics);
        assertFalse(diagnostics.usageEnabled());

        assertTrue(handle.applyConsent(
                "animal-husbandry",
                new TelemetryConsentSnapshot(true, true, true, true, true, true, false, true)
        ));
        diagnostics = handle.consentProjectDiagnostics("animal-husbandry");
        assertNotNull(diagnostics);
        assertFalse(diagnostics.crashEnabled());
        assertFalse(diagnostics.usageEnabled());
        assertFalse(diagnostics.statsEnabled());
        assertTrue(diagnostics.overridePresent());
    }

    @Test
    void providerReviewedStateAndDiagnosticsUseConsentProjectView() {
        TelemetryProjectRegistration first = registration(
                telemetryCategoryDescriptor("first-mod", "First Mod"),
                "Example:First Mod",
                "1.0.0",
                tempDir.resolve("First Mod")
        );
        TelemetryProjectRegistration second = registration(
                telemetryCategoryDescriptor("second-mod", "Second Mod"),
                "Example:Second Mod",
                "2.0.0",
                tempDir.resolve("Second Mod")
        );
        ProviderFixture fixture = consentFixture(
                "diagnostic-consent",
                List.of(first, second),
                List.of(first, second),
                List.of("duplicate telemetry project ignored")
        );
        TelemetryRuntimeProviderHandle handle = fixture.handle();

        assertEquals(List.of(first, second), handle.unreviewedConsentProjects());
        assertTrue(handle.markConsentReviewed("first-mod"));
        assertEquals(List.of(second), handle.unreviewedConsentProjects());
        assertTrue(handle.markConsentReviewed("second-mod"));
        assertTrue(handle.unreviewedConsentProjects().isEmpty());

        TelemetryRuntimeDiagnostics diagnostics = handle.consentDiagnostics();
        assertTrue(diagnostics.enabled());
        assertEquals(2, diagnostics.registeredProjects());
        assertEquals(2, diagnostics.loadedMods());
        assertEquals(0, diagnostics.totalPendingReports());
        assertEquals(List.of("duplicate telemetry project ignored"), diagnostics.registrationWarnings());
        assertEquals(2, diagnostics.projects().size());

        TelemetryRuntimeDiagnostics.ProjectDiagnostics firstDiagnostics = handle.consentProjectDiagnostics("first-mod");
        assertNotNull(firstDiagnostics);
        assertEquals("First Mod", firstDiagnostics.displayName());
        assertEquals("Example:First Mod", firstDiagnostics.pluginIdentifier());
        assertEquals("1.0.0", firstDiagnostics.pluginVersion());
        assertEquals(tempDir.resolve("First Mod").toString(), firstDiagnostics.sourcePath());
        assertEquals("dependency", firstDiagnostics.runtimeMode());
        assertTrue(firstDiagnostics.endpoint().contains("example.invalid"));
    }

    @Test
    void providerMirrorsEmbeddedConsentOverrideToOwnerSettings() {
        Path root = tempDir.resolve("embedded-consent");
        Path modsDir = tempDir.resolve("mods");
        TelemetryProjectRegistration embedded = registration(
                telemetryCategoryDescriptor("embedded-mod", "Embedded Mod", "embedded"),
                "Example:Embedded Mod",
                "1.2.3",
                tempDir.resolve("Embedded Mod.jar")
        );
        ProviderFixture fixture = consentFixture(
                "embedded-consent",
                List.of(),
                List.of(embedded),
                List.of(),
                root,
                modsDir
        );
        TelemetryRuntimeProviderHandle handle = fixture.handle();

        handle.start();

        assertEquals(1, handle.consentDiagnostics().registeredProjects());
        assertEquals("embedded", handle.consentDiagnostics().projects().getFirst().runtimeMode());
        assertTrue(handle.applyConsent(
                "embedded-mod",
                new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false)
        ));

        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = handle.consentProjectDiagnostics("embedded-mod");
        assertNotNull(diagnostics);
        assertFalse(diagnostics.enabled());
        assertTrue(diagnostics.overridePresent());

        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(null);
        TelemetryProjectOverride centralOverride = store.load(fixture.dataPaths().projectOverrideFile("embedded-mod"));
        Path embeddedOverrideFile = modsDir
                .resolve("Example_Embedded Mod")
                .resolve("Telemetry")
                .resolve("Settings")
                .resolve("projects")
                .resolve("embedded-mod.json");
        TelemetryProjectOverride embeddedOverride = store.load(embeddedOverrideFile);
        assertNotNull(centralOverride);
        assertNotNull(embeddedOverride);
        assertEquals(false, centralOverride.enabled());
        assertEquals(false, centralOverride.events().errors().enabled());
        assertEquals(false, embeddedOverride.enabled());
        assertEquals(false, embeddedOverride.events().errors().enabled());

        handle.shutdown();
    }

    private TelemetryRuntimeProviderHandle handle(String providerId,
                                                  TelemetryRuntimeOrigin origin,
                                                  String runtimeVersion) {
        return fixture(providerId, origin, runtimeVersion).handle();
    }

    private ProviderFixture fixture(String providerId,
                                    TelemetryRuntimeOrigin origin,
                                    String runtimeVersion) {
        return fixture(providerId, origin, runtimeVersion, "example-mod", "Example Mod");
    }

    private ProviderFixture fixture(String providerId,
                                    TelemetryRuntimeOrigin origin,
                                    String runtimeVersion,
                                    String projectId,
                                    String displayName) {
        Path root = tempDir.resolve(providerId.replace(':', '_'));
        TelemetryDataPaths dataPaths = dataPaths(root);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), null);
        TelemetryProjectRegistration project = project(providerId, projectId, displayName);
        return providerFixture(
                providerId,
                origin,
                runtimeVersion,
                settings,
                dataPaths,
                List.of(project),
                List.of(project),
                List.of(),
                project
        );
    }

    private ProviderFixture consentFixture(String fixtureName,
                                           List<TelemetryProjectRegistration> projects,
                                           List<TelemetryProjectRegistration> consentProjects) {
        return consentFixture(fixtureName, projects, consentProjects, List.of());
    }

    private ProviderFixture consentFixture(String fixtureName,
                                           List<TelemetryProjectRegistration> projects,
                                           List<TelemetryProjectRegistration> consentProjects,
                                           List<String> registrationWarnings) {
        Path root = tempDir.resolve(fixtureName);
        return consentFixture(fixtureName, projects, consentProjects, registrationWarnings, root, null);
    }

    private ProviderFixture consentFixture(String fixtureName,
                                           List<TelemetryProjectRegistration> projects,
                                           List<TelemetryProjectRegistration> consentProjects,
                                           List<String> registrationWarnings,
                                           Path root,
                                           Path modsDir) {
        TelemetryDataPaths dataPaths = dataPaths(root, modsDir);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), null);
        TelemetryProjectRegistration providerProject = consentProjects.isEmpty() ? projects.getFirst() : consentProjects.getFirst();
        return providerFixture(
                "standalone:Alechilles:" + fixtureName,
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                settings,
                dataPaths,
                projects,
                consentProjects,
                registrationWarnings,
                providerProject
        );
    }

    private ProviderFixture providerFixture(String providerId,
                                            TelemetryRuntimeOrigin origin,
                                            String runtimeVersion,
                                            TelemetryRuntimeSettings settings,
                                            TelemetryDataPaths dataPaths,
                                            List<TelemetryProjectRegistration> projects,
                                            List<TelemetryProjectRegistration> consentProjects,
                                            List<String> registrationWarnings,
                                            TelemetryProjectRegistration providerProject) {
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                projects,
                consentProjects,
                loadedMods(consentProjects.isEmpty() ? projects : consentProjects),
                new NoopCrashReportClient(),
                null,
                null
        );
        TelemetryRuntimeBootstrapRequest request = new TelemetryRuntimeBootstrapRequest(
                null,
                origin,
                providerProject.pluginIdentifier(),
                providerProject.pluginVersion(),
                runtimeVersion,
                dataPaths.runtimeRoot().resolve("provider.jar"),
                null
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                providerProject.pluginIdentifier(),
                providerProject.pluginVersion(),
                request.sourcePath(),
                dataPaths.runtimeRoot()
        );
        TelemetryRuntimeCommandRegistrar commandRegistrar = new TelemetryRuntimeCommandRegistrar();
        return new ProviderFixture(new TelemetryRuntimeProviderHandle(
                request,
                candidate,
                settings,
                dataPaths,
                service,
                consentProjects,
                registrationWarnings,
                new TelemetryPlayerCounter(),
                null,
                commandRegistrar
        ), commandRegistrar, dataPaths);
    }

    private static TelemetryDataPaths dataPaths(Path root) {
        return dataPaths(root, null);
    }

    private static TelemetryDataPaths dataPaths(Path root, Path modsDir) {
        Path settingsRoot = root.resolve("Settings");
        Path telemetryRoot = root.resolve("Telemetry");
        return new TelemetryDataPaths(
                root,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                modsDir
        );
    }

    private static TelemetryProjectRegistration project(String providerId,
                                                        String projectId,
                                                        String displayName) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\",\"displayName\":\"" + displayName + "\"}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, providerId, "1.0.0", null);
    }

    private static TelemetryProjectRegistration registration(TelemetryProjectDescriptor descriptor,
                                                             String pluginIdentifier,
                                                             String pluginVersion,
                                                             Path sourcePath) {
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, pluginVersion, sourcePath);
    }

    private static List<CrashReportEnvelope.LoadedModMetadata> loadedMods(List<TelemetryProjectRegistration> projects) {
        return projects.stream()
                .map(project -> new CrashReportEnvelope.LoadedModMetadata(project.pluginIdentifier(), project.pluginVersion()))
                .toList();
    }

    private static TelemetryProjectDescriptor telemetryCategoryDescriptor(String projectId, String displayName) {
        return telemetryCategoryDescriptor(projectId, displayName, "dependency");
    }

    private static TelemetryProjectDescriptor telemetryCategoryDescriptor(String projectId,
                                                                          String displayName,
                                                                          String runtimeMode) {
        return TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "runtimeMode": "%s",
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
                """.formatted(projectId, displayName, runtimeMode, displayName),
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

    private static final class NoopCrashReportClient implements CrashReportClient {
        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            return UploadResult.success(200);
        }
    }

    private record ProviderFixture(TelemetryRuntimeProviderHandle handle,
                                   TelemetryRuntimeCommandRegistrar commandRegistrar,
                                   TelemetryDataPaths dataPaths) {
    }

    private static final class StandaloneStyleConsentBridge implements TelemetryCoordinatorBridge {
        private final TelemetryRuntimeCandidate candidate;
        private final TelemetryProjectRegistration project;
        private final ArrayList<String> reviewedProjectIds = new ArrayList<>();
        private boolean active;
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

        private StandaloneStyleConsentBridge(TelemetryRuntimeCandidate candidate, TelemetryProjectRegistration project) {
            this.candidate = candidate;
            this.project = project;
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
        public Map<String, Object> consentDiagnostics() {
            return TelemetryConsentBridgePayload.diagnosticsSummary(new TelemetryRuntimeDiagnostics(
                    true,
                    1,
                    1,
                    0,
                    false,
                    "never",
                    null,
                    List.of(),
                    List.of(projectDiagnostics())
            ));
        }

        @Override
        public Map<String, Object> consentProjectDiagnostics(String projectId) {
            return project.projectId().equalsIgnoreCase(projectId.trim())
                    ? TelemetryConsentBridgePayload.projectDiagnosticsSummary(projectDiagnostics())
                    : Map.of();
        }

        @Override
        public boolean applyConsent(String projectId, Map<String, Object> snapshot) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            this.snapshot = TelemetryConsentBridgePayload.snapshotFromSummary(snapshot);
            return true;
        }

        @Override
        public boolean applyConsentToAll(Map<String, Object> snapshot) {
            this.snapshot = TelemetryConsentBridgePayload.snapshotFromSummary(snapshot);
            return true;
        }

        @Override
        public boolean applyConsentCategoryToAll(String category, boolean enabled) {
            snapshot = snapshot.withCategory(category, enabled);
            return true;
        }

        @Override
        public boolean markConsentReviewed(String projectId) {
            if (!project.projectId().equalsIgnoreCase(projectId.trim())) {
                return false;
            }
            reviewedProjectIds.add(project.projectId());
            return true;
        }

        private TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnostics() {
            return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                    project.projectId(),
                    project.displayName(),
                    snapshot.projectEnabled(),
                    false,
                    project.destinationMode(),
                    "https://example.invalid/telemetry",
                    0,
                    project.pluginIdentifier(),
                    project.pluginVersion(),
                    project.sourcePath().toString(),
                    project.descriptor().ui().iconTexturePath(),
                    project.packagePrefixes(),
                    project.runtimeMode(),
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
    }
}
