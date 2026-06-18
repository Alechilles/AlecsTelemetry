package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryProjectHandle;
import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

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
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(project),
                List.of(project),
                List.of(new CrashReportEnvelope.LoadedModMetadata(project.pluginIdentifier(), project.pluginVersion())),
                new NoopCrashReportClient(),
                null,
                null
        );
        TelemetryRuntimeBootstrapRequest request = new TelemetryRuntimeBootstrapRequest(
                null,
                origin,
                project.pluginIdentifier(),
                project.pluginVersion(),
                runtimeVersion,
                root.resolve("provider.jar"),
                null
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                project.pluginIdentifier(),
                project.pluginVersion(),
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
                List.of(project),
                List.of(),
                new TelemetryPlayerCounter(),
                null,
                commandRegistrar
        ), commandRegistrar);
    }

    private static TelemetryDataPaths dataPaths(Path root) {
        Path settingsRoot = root.resolve("Settings");
        Path telemetryRoot = root.resolve("Telemetry");
        return new TelemetryDataPaths(
                root,
                settingsRoot.resolve("runtime.json"),
                settingsRoot.resolve("projects"),
                telemetryRoot,
                telemetryRoot.resolve("crash-reports"),
                telemetryRoot.resolve("events"),
                null
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

    private static final class NoopCrashReportClient implements CrashReportClient {
        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            return UploadResult.success(200);
        }
    }

    private record ProviderFixture(TelemetryRuntimeProviderHandle handle,
                                   TelemetryRuntimeCommandRegistrar commandRegistrar) {
    }
}
