package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryProjectContributionBridge;
import com.alechilles.alecstelemetry.coordinator.TelemetryProjectContributionRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.profiler.SparkPublicMetricsAdapter;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerMonitor;
import com.alechilles.alecstelemetry.runtime.profiler.TelemetryProfilerBreadcrumbCounter;
import com.alechilles.alecstelemetry.runtime.profiler.TelemetryProfilerContextProvider;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeProviderSparkOwnershipTest {
    private static final AtomicInteger ELIGIBLE_FIXTURE_SEQUENCE = new AtomicInteger();
    private static final String PROFILER_BREADCRUMB_CATEGORY = "companion.ai.tick";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearRuntimeGlobals() {
        TelemetryCoordinatorRegistry.clearForTests();
        TelemetryProjectContributionRegistry.clearForTests();
        TelemetryRuntimeLocator.clear();
    }

    @Test
    void coordinatorHandoffActivatesOnlyWinnerAndDropsLosingLateCapture() throws Exception {
        ProviderFixture providerA = fixture(
                "standalone:Alechilles:SparkA",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1)
        );
        ProviderFixture providerB = fixture(
                "embedded:Alechilles:SparkB",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                new BlockingReader(2)
        );

        try {
            providerA.handle.start();
            assertEquals("standalone:Alechilles:SparkA", TelemetryCoordinatorRegistry.activeBridge().providerId());
            assertTrue(providerA.reader.entered.await(2, TimeUnit.SECONDS));

            providerB.handle.start();
            assertEquals("embedded:Alechilles:SparkB", TelemetryCoordinatorRegistry.activeBridge().providerId());
            assertFalse(providerA.handle.ownsActiveCoordinator());
            assertTrue(providerB.handle.ownsActiveCoordinator());
            assertFalse(
                    providerB.reader.entered.await(250, TimeUnit.MILLISECONDS),
                    "the winning provider must wait while the losing Spark reader is still running"
            );

            providerA.reader.release.countDown();
            awaitReads(providerA.reader, 1);
            assertEquals(1, providerA.reader.reads.get());
            assertTrue(providerA.monitor.history().isEmpty());
            assertTrue(providerA.handle.api().findProject("mod-a").profiler().history().isEmpty());

            assertTrue(providerB.reader.entered.await(2, TimeUnit.SECONDS));
            providerB.reader.release.countDown();
            awaitHistory(providerB.monitor, 1);
            assertEquals(1, providerB.reader.reads.get());
            assertEquals(1, providerB.monitor.history().size());
            assertEquals(providerB.monitor.history(), providerB.handle.sparkProfilerHistory());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> providerB.handle.sparkProfilerHistory().add(providerB.monitor.history().getFirst())
            );
        } finally {
            providerA.reader.release.countDown();
            providerB.reader.release.countDown();
            providerA.handle.shutdown();
            providerB.handle.shutdown();
        }
    }

    @Test
    void fallbackProviderPublishesAfterRegainingCoordinatorOwnership() throws Exception {
        ProviderFixture providerA = fixture(
                "standalone:Alechilles:SparkFallbackA",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1)
        );
        ProviderFixture providerB = fixture(
                "embedded:Alechilles:SparkFallbackB",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                new BlockingReader(2)
        );

        try {
            providerA.handle.start();
            assertTrue(providerA.reader.entered.await(2, TimeUnit.SECONDS));

            providerB.handle.start();
            assertEquals("embedded:Alechilles:SparkFallbackB", TelemetryCoordinatorRegistry.activeBridge().providerId());

            providerA.reader.release.countDown();
            awaitReads(providerA.reader, 1);
            providerB.reader.release.countDown();
            awaitHistory(providerB.monitor, 1);

            providerB.handle.shutdown();
            assertEquals("standalone:Alechilles:SparkFallbackA", TelemetryCoordinatorRegistry.activeBridge().providerId());

            providerA.awaitProjectWindow(2);

            TelemetryProfilerView view = providerA.handle.api().findProject("mod-a").profiler();
            assertEquals(List.of(2), view.history().stream()
                    .map(TelemetryProfilerSnapshot::windowKey)
                    .toList());
        } finally {
            providerA.reader.release.countDown();
            providerB.reader.release.countDown();
            providerA.handle.shutdown();
            providerB.handle.shutdown();
        }
    }

    @Test
    void fallbackProviderCapturesPromptlyAfterStaleInFlightCaptureRetires() throws Exception {
        ProviderFixture providerA = fixture(
                "standalone:Alechilles:SparkStaleFallbackA",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1)
        );
        ProviderFixture providerB = fixture(
                "embedded:Alechilles:SparkStaleFallbackB",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                new BlockingReader(2)
        );

        try {
            providerA.handle.start();
            assertTrue(providerA.reader.entered.await(2, TimeUnit.SECONDS));

            providerB.handle.start();
            assertEquals(
                    "embedded:Alechilles:SparkStaleFallbackB",
                    TelemetryCoordinatorRegistry.activeBridge().providerId()
            );

            providerB.handle.shutdown();
            assertEquals(
                    "standalone:Alechilles:SparkStaleFallbackA",
                    TelemetryCoordinatorRegistry.activeBridge().providerId()
            );

            providerA.reader.release.countDown();
            assertTrue(
                    providerA.reader.secondEntered.await(2, TimeUnit.SECONDS),
                    "the reactivated fallback must start a new capture after the stale attempt retires"
            );
            assertEquals(2, providerA.reader.reads.get());
        } finally {
            providerA.reader.release.countDown();
            providerB.reader.release.countDown();
            providerA.handle.shutdown();
            providerB.handle.shutdown();
        }
    }

    @Test
    void registryHandoffRevokesBreadcrumbRecordBeforeFallbackReactivation() throws Exception {
        HandoffClearBarrier barrier = new HandoffClearBarrier();
        ProviderFixture providerA = fixtureWithHandoffClearBarrier(
                "standalone:Alechilles:SparkBreadcrumbHandoffA",
                barrier
        );
        ProviderFixture providerB = fixture(
                "embedded:Alechilles:SparkBreadcrumbHandoffB",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                new BlockingReader(2)
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            providerA.handle.start();
            TelemetryCoordinatorBridge oldBridge = TelemetryCoordinatorRegistry.activeBridge();
            assertTrue(oldBridge != null);
            assertEquals(providerA.handle.activeCoordinatorProviderId(), oldBridge.providerId());
            assertTrue(oldBridge.recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "seed"));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 1),
                    providerA.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );

            barrier.blockNextEligibility();
            Future<?> handoff = executor.submit(providerB.handle::start);
            assertTrue(barrier.clearEntered.await(2, TimeUnit.SECONDS));

            Future<?> waitingRecord = executor.submit(() -> oldBridge.recordBreadcrumb(
                    "mod-a",
                    PROFILER_BREADCRUMB_CATEGORY,
                    "waiting"
            ));
            assertThrows(TimeoutException.class, () -> waitingRecord.get(250, TimeUnit.MILLISECONDS));

            barrier.releaseClear();
            assertTrue(barrier.eligibilityEntered.await(2, TimeUnit.SECONDS));
            handoff.get(2, TimeUnit.SECONDS);

            barrier.releaseEligibility();
            waitingRecord.get(2, TimeUnit.SECONDS);

            providerB.handle.shutdown();
            assertEquals(providerA.handle.activeCoordinatorProviderId(), TelemetryCoordinatorRegistry.activeBridge().providerId());
            assertEquals(
                    Map.of(),
                    providerA.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );
        } finally {
            barrier.releaseClear();
            barrier.releaseEligibility();
            providerA.reader.release.countDown();
            providerB.reader.release.countDown();
            providerA.handle.shutdown();
            providerB.handle.shutdown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void bootDoesNotReactivateASuspendedMonitor() throws Exception {
        ProviderFixture provider = fixture(
                "standalone:Alechilles:SparkBoot",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(3)
        );

        try {
            provider.handle.start();
            assertTrue(provider.reader.entered.await(2, TimeUnit.SECONDS));
            provider.monitor.suspend();
            assertFalse(provider.monitor.isActive());

            provider.handle.onBoot();

            assertFalse(provider.monitor.isActive());
            assertEquals(1, provider.reader.reads.get());
        } finally {
            provider.reader.release.countDown();
            provider.handle.shutdown();
        }
    }

    @Test
    void winningProviderPublishesOneCompletedProjectWindow() throws Exception {
        ProviderFixture fixture = fixture(
                "standalone:Alechilles:SparkProject",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1)
        );
        try {
            fixture.publishAndAwait(1);

            TelemetryProfilerView view = fixture.handle.api().findProject("mod-a").profiler();
            assertEquals(List.of(1), view.history().stream()
                    .map(TelemetryProfilerSnapshot::windowKey)
                    .toList());
        } finally {
            fixture.close();
        }
    }

    @Test
    void disablingPerformanceConsentClearsProjectProfilerEvidenceImmediately() throws Exception {
        ProviderFixture fixture = fixtureWithEligibleProject("mod-a");
        try {
            fixture.publishAndAwait(1);
            TelemetryProfilerView view = fixture.handle.api().findProject("mod-a").profiler();
            assertFalse(view.history().isEmpty());

            assertTrue(activeBridge().setPerformanceEnabled("mod-a", false));

            assertTrue(view.latest().isEmpty());
            assertTrue(view.history().isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void reEnablingPerformanceConsentDoesNotRestoreOldEvidence() throws Exception {
        ProviderFixture fixture = fixtureWithEligibleProject("mod-a");
        try {
            fixture.publishAndAwait(1);
            TelemetryProfilerView view = fixture.handle.api().findProject("mod-a").profiler();

            assertTrue(activeBridge().setPerformanceEnabled("mod-a", false));
            assertTrue(activeBridge().setPerformanceEnabled("mod-a", true));
            assertTrue(view.history().isEmpty());

            fixture.publishAndAwait(2);

            assertEquals(List.of(2), view.history().stream()
                    .map(TelemetryProfilerSnapshot::windowKey)
                    .toList());
        } finally {
            fixture.close();
        }
    }

    @Test
    void acceptedBreadcrumbsAreCountedOnceAndConsentClearsOnlyBreadcrumbContext() throws Exception {
        ProviderFixture fixture = fixtureWithBreadcrumbProject("mod-a");
        try {
            fixture.handle.recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "local");
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 1),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );

            fixture.publishAndAwait(1);
            TelemetryProfilerView view = fixture.handle.api().findProject("mod-a").profiler();
            assertFalse(view.history().isEmpty());
            assertNull(view.latest().orElseThrow().context().tps());
            assertNull(view.latest().orElseThrow().context().mspt());

            assertTrue(activeBridge().recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "bridge"));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 2),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );
            assertTrue(activeBridge().recordBreadcrumb(
                    "mod-a",
                    PROFILER_BREADCRUMB_CATEGORY,
                    "bridge-context",
                    Map.of("source", "test")
            ));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 3),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );
            assertTrue(activeBridge().recordBreadcrumb("mod-a", "undeclared.category", "ignored"));
            assertFalse(activeBridge().recordBreadcrumb("missing", PROFILER_BREADCRUMB_CATEGORY, "rejected"));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 3),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );

            assertTrue(activeBridge().setBreadcrumbsEnabled("mod-a", false));
            assertEquals(Map.of(), fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts());
            assertFalse(view.history().isEmpty());
            assertTrue(activeBridge().recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "disabled"));
            assertEquals(Map.of(), fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts());

            assertTrue(activeBridge().setBreadcrumbsEnabled("mod-a", true));
            assertEquals(Map.of(), fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts());
            assertTrue(activeBridge().recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "re-enabled"));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 1),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );

            assertTrue(activeBridge().setPerformanceEnabled("mod-a", false));
            assertTrue(view.history().isEmpty());
            assertTrue(activeBridge().recordBreadcrumb("mod-a", PROFILER_BREADCRUMB_CATEGORY, "performance-off"));
            assertEquals(
                    Map.of(PROFILER_BREADCRUMB_CATEGORY, 1),
                    fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts()
            );

            fixture.handle.shutdown();
            assertEquals(Map.of(), fixture.contextProvider.snapshot("mod-a").breadcrumbCategoryCounts());
        } finally {
            fixture.close();
        }
    }

    @Test
    void providerShutdownClosesProfilerSubscriptionsAndMakesViewUnavailable() throws Exception {
        ProviderFixture fixture = fixtureWithEligibleProject("mod-a");
        TelemetryProfilerSubscription subscription = null;
        try {
            fixture.publishAndAwait(1);
            TelemetryProfilerView view = fixture.handle.api().findProject("mod-a").profiler();
            subscription = view.subscribe(snapshot -> {
            });

            fixture.handle.shutdown();

            assertEquals(com.alechilles.alecstelemetry.api.TelemetryProfilerStatus.State.UNAVAILABLE,
                    view.status().state());
            assertTrue(view.latest().isEmpty());
            assertTrue(view.history().isEmpty());
        } finally {
            if (subscription != null) {
                subscription.close();
            }
            fixture.reader.release.countDown();
            fixture.handle.shutdown();
        }
    }

    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> everyConsentMutationRouteInvalidatesProjectProfilerHistory() {
        return profilerRevocationCases().map(revocation -> DynamicTest.dynamicTest(
                revocation.name(),
                () -> {
                    ProviderFixture fixture = fixtureWithEligibleProject("mod-a");
                    try {
                        fixture.publishAndAwait(1);
                        TelemetryProfilerView initialView = fixture.handle.api().findProject("mod-a").profiler();
                        assertFalse(initialView.history().isEmpty(), initialView.status().toString());
                        assertTrue(revocation.apply(fixture));
                        assertTrue(fixture.handle.api().findProject("mod-a").profiler().history().isEmpty());
                    } finally {
                        fixture.close();
                        TelemetryProjectContributionRegistry.clearForTests();
                    }
                }
        ));
    }

    private static Stream<RevocationCase> profilerRevocationCases() {
        return Stream.of(
                revocation("project setter", f -> activeBridge().setProjectEnabled("mod-a", false)),
                revocation("performance setter", f -> activeBridge().setPerformanceEnabled("mod-a", false)),
                revocation("single apply", f -> f.handle.applyConsent("mod-a", disabledConsent())),
                revocation("apply all", f -> f.handle.applyConsentToAll(disabledConsent())),
                revocation("category apply all", f -> f.handle.applyConsentCategoryToAll("performance", false)),
                revocation("project reconciliation", ProviderFixture::removeProjectAndReconcile)
        );
    }

    private record RevocationCase(String name,
                                  Function<ProviderFixture, Boolean> operation) {
        boolean apply(ProviderFixture fixture) {
            return operation.apply(fixture);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static RevocationCase revocation(String name,
                                             Function<ProviderFixture, Boolean> operation) {
        return new RevocationCase(name, operation);
    }

    private ProviderFixture fixture(String providerId,
                                    TelemetryRuntimeOrigin origin,
                                    String runtimeVersion,
                                    BlockingReader reader) throws Exception {
        return fixtureInternal(providerId, origin, runtimeVersion, reader, false, false, null);
    }

    private ProviderFixture fixtureWithHandoffClearBarrier(String providerId,
                                                           HandoffClearBarrier barrier) throws Exception {
        return fixtureInternal(
                providerId,
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1),
                false,
                true,
                barrier
        );
    }

    private ProviderFixture fixtureWithEligibleProject(String projectId) throws Exception {
        return fixtureInternal(
                "standalone:Alechilles:SparkConsent-" + projectId + "-"
                        + ELIGIBLE_FIXTURE_SEQUENCE.incrementAndGet(),
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1),
                true,
                false,
                null
        );
    }

    private ProviderFixture fixtureWithBreadcrumbProject(String projectId) throws Exception {
        return fixtureInternal(
                "standalone:Alechilles:SparkBreadcrumb-" + projectId + "-"
                        + ELIGIBLE_FIXTURE_SEQUENCE.incrementAndGet(),
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                new BlockingReader(1),
                false,
                true,
                null
        );
    }

    private ProviderFixture fixtureInternal(String providerId,
                                             TelemetryRuntimeOrigin origin,
                                             String runtimeVersion,
                                             BlockingReader reader,
                                             boolean contributionProject,
                                             boolean breadcrumbProject,
                                             HandoffClearBarrier handoffBarrier) throws Exception {
        Path root = tempDir.resolve(providerId.replace(':', '_'));
        TelemetryDataPaths dataPaths = dataPaths(root);
        TelemetryRuntimeSettings loaded = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), null);
        TelemetryRuntimeSettings settings = withEnabledSparkProfiler(loaded);
        TelemetryProjectRegistration project = project("mod-a", providerId, true, breadcrumbProject);
        TelemetryProjectRegistration runtimeProject = contributionProject
                ? TelemetryProjectRegistration.passiveDescriptor(
                project.descriptor(),
                root.resolve("passive.jar"),
                "Example:Host",
                "1.0.0"
        )
                : project;
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(runtimeProject),
                List.of(),
                List.of(),
                new NoopClient(),
                null,
                null
        );
        TelemetryRuntimeBootstrapRequest request = new TelemetryRuntimeBootstrapRequest(
                null,
                origin,
                "Example:" + providerId,
                runtimeVersion,
                runtimeVersion,
                root.resolve("provider.jar"),
                null
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                "Example:" + providerId,
                runtimeVersion,
                request.sourcePath(),
                root
        );
        AtomicReference<TelemetryRuntimeProviderHandle> handleReference = new AtomicReference<>();
        AtomicReference<Object> projectProfilerService = new AtomicReference<>();
        SparkProfilerMonitor monitor = injectedMonitor(
                settings.sparkProfiler(),
                reader,
                () -> {
                    TelemetryRuntimeProviderHandle handle = handleReference.get();
                    return handle != null && handle.ownsActiveCoordinator();
                },
                projectProfilerService
        );
        TelemetryProfilerBreadcrumbCounter breadcrumbCounter = new TelemetryProfilerBreadcrumbCounter();
        TelemetryProfilerContextProvider contextProvider = handoffBarrier == null
                ? new TelemetryProfilerContextProvider(
                () -> 0,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbCounter,
                projectId -> service.isProjectEnabled(projectId)
                        && service.isBreadcrumbsEnabled(projectId),
                System::currentTimeMillis,
                null
        )
                : blockingHandoffContextProvider(
                service,
                breadcrumbCounter,
                handleReference,
                handoffBarrier
        );
        TelemetryRuntimeProviderHandle handle = new TelemetryRuntimeProviderHandle(
                request,
                candidate,
                settings,
                dataPaths,
                service,
                List.of(runtimeProject),
                List.of(),
                new TelemetryPlayerCounter(),
                null,
                new TelemetryRuntimeCommandRegistrar(),
                new NoopClient(),
                monitor,
                contextProvider
        );
        handleReference.set(handle);
        projectProfilerService.set(projectProfilerService(handle));
        String contributionToken = contributionProject
                ? TelemetryProjectContributionRegistry.register(new TestContributionBridge(
                contribution(project, root.resolve("contribution.jar"))
        ))
                : null;
        return new ProviderFixture(handle, monitor, reader, contributionToken, contextProvider);
    }

    private static TelemetryRuntimeSettings withEnabledSparkProfiler(TelemetryRuntimeSettings settings) {
        return new TelemetryRuntimeSettings(
                settings.filePath(),
                settings.enabled(),
                settings.flushIntervalSeconds(),
                settings.connectTimeoutMs(),
                settings.readTimeoutMs(),
                settings.maxPendingReportsPerProject(),
                settings.maxPendingEventsPerProject(),
                settings.maxUploadsPerFlush(),
                settings.maxBreadcrumbsPerProject(),
                settings.manualReports(),
                new TelemetryRuntimeSettings.SparkProfilerSettings(true, 0, 60, 10, 5, 10),
                settings.hostedIngestEndpoint(),
                settings.hostedEventIngestEndpoint()
        );
    }

    private static SparkProfilerMonitor injectedMonitor(
            TelemetryRuntimeSettings.SparkProfilerSettings settings,
            BlockingReader reader,
            BooleanSupplier owner,
            AtomicReference<Object> projectProfilerService) throws Exception {
        Class<?> readerType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfileReader"
        );
        Object readerProxy = Proxy.newProxyInstance(
                readerType.getClassLoader(),
                new Class<?>[]{readerType},
                (proxy, method, args) -> "read".equals(method.getName())
                        ? reader.readResult()
                        : null
        );
        Class<?> sinkType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerMonitor$SparkProfilePublicationSink"
        );
        Object publicationSink = Proxy.newProxyInstance(
                sinkType.getClassLoader(),
                new Class<?>[]{sinkType},
                (proxy, method, args) -> {
                    if (!"publish".equals(method.getName())) {
                        return null;
                    }
                    Object service = projectProfilerService.get();
                    if (service == null || args == null || args.length == 0) {
                        return null;
                    }
                    for (Method candidate : service.getClass().getMethods()) {
                        if (!"publish".equals(candidate.getName()) || candidate.getParameterCount() != 1) {
                            continue;
                        }
                        try {
                            candidate.invoke(service, args[0]);
                            return null;
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            Throwable cause = failure.getCause();
                            if (cause instanceof RuntimeException runtimeException) {
                                throw runtimeException;
                            }
                            throw new IllegalStateException(cause);
                        }
                    }
                    throw new NoSuchMethodException("project profiler publish method");
                }
        );
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : SparkProfilerMonitor.class.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 8) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) {
            throw new NoSuchMethodException("injected SparkProfilerMonitor constructor");
        }
        constructor.setAccessible(true);
        return (SparkProfilerMonitor) constructor.newInstance(
                settings,
                (Supplier<Object>) Object::new,
                owner,
                readerProxy,
                (java.util.function.LongSupplier) () -> Long.MAX_VALUE,
                null,
                null,
                publicationSink
        );
    }

    private static Object projectProfilerService(TelemetryRuntimeProviderHandle handle) throws Exception {
        java.lang.reflect.Field field = TelemetryRuntimeProviderHandle.class
                .getDeclaredField("projectProfilerService");
        field.setAccessible(true);
        return field.get(handle);
    }

    private static boolean providerBridgeIsActive(
            AtomicReference<TelemetryRuntimeProviderHandle> handleReference) {
        TelemetryRuntimeProviderHandle handle = handleReference.get();
        if (handle == null) {
            return false;
        }
        try {
            Field bridgeField = TelemetryRuntimeProviderHandle.class.getDeclaredField("bridge");
            bridgeField.setAccessible(true);
            Object bridge = bridgeField.get(handle);
            Method isActive = bridge.getClass().getDeclaredMethod("isActive");
            isActive.setAccessible(true);
            return Boolean.TRUE.equals(isActive.invoke(bridge));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static TelemetryProfilerContextProvider blockingHandoffContextProvider(
            TelemetryCoordinatorService service,
            TelemetryProfilerBreadcrumbCounter breadcrumbs,
            AtomicReference<TelemetryRuntimeProviderHandle> handleReference,
            HandoffClearBarrier barrier) throws Exception {
        Class<?> providerType = TelemetryProfilerContextProvider.class;
        Class<?> refreshOperation = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.TelemetryProfilerContextProvider$RefreshOperation"
        );
        Class<?> clearOperation = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.TelemetryProfilerContextProvider$ClearOperation"
        );
        Class<?> clearAllOperation = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.TelemetryProfilerContextProvider$ClearAllOperation"
        );
        Constructor<?> constructor = providerType.getDeclaredConstructor(
                java.util.function.IntSupplier.class,
                String.class,
                SparkPublicMetricsAdapter.class,
                TelemetryProfilerBreadcrumbCounter.class,
                java.util.function.Predicate.class,
                java.util.function.LongSupplier.class,
                java.util.function.BiConsumer.class,
                refreshOperation,
                clearOperation,
                clearAllOperation
        );
        constructor.setAccessible(true);
        Object refresh = Proxy.newProxyInstance(
                refreshOperation.getClassLoader(),
                new Class<?>[]{refreshOperation},
                (proxy, method, args) -> {
                    breadcrumbs.refreshProjects((List<TelemetryProjectRegistration>) args[0]);
                    return null;
                }
        );
        Object clear = Proxy.newProxyInstance(
                clearOperation.getClassLoader(),
                new Class<?>[]{clearOperation},
                (proxy, method, args) -> {
                    breadcrumbs.clear((String) args[0]);
                    return null;
                }
        );
        Object clearAll = Proxy.newProxyInstance(
                clearAllOperation.getClassLoader(),
                new Class<?>[]{clearAllOperation},
                (proxy, method, args) -> {
                    breadcrumbs.clearAll();
                    barrier.clearEntered.countDown();
                    barrier.awaitReleaseClear();
                    return null;
                }
        );
        java.util.function.Predicate<String> eligible = projectId -> {
            boolean active = providerBridgeIsActive(handleReference)
                    && service.isProjectEnabled(projectId)
                    && service.isBreadcrumbsEnabled(projectId);
            if (barrier.blockEligibility.compareAndSet(true, false)) {
                barrier.eligibilityResult.set(active);
                barrier.eligibilityEntered.countDown();
                barrier.awaitReleaseEligibility();
                return barrier.eligibilityResult.get();
            }
            return active;
        };
        return (TelemetryProfilerContextProvider) constructor.newInstance(
                (java.util.function.IntSupplier) () -> 0,
                "0.5.9",
                new SparkPublicMetricsAdapter(() -> null),
                breadcrumbs,
                eligible,
                (java.util.function.LongSupplier) System::currentTimeMillis,
                null,
                refresh,
                clear,
                clearAll
        );
    }

    private static Object completeResult(int windowKey) throws Exception {
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "1.10.172",
                windowKey,
                1,
                1,
                1,
                1,
                1.0d,
                List.of(new SparkProfileSnapshot.HotPath(
                        "example",
                        "Example.tick()",
                        1.0d,
                        100.0d
                ))
        );
        Class<?> resultType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfileReadResult"
        );
        Class<?> frameType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfileWindow$Frame"
        );
        Constructor<?> frameConstructor = frameType.getDeclaredConstructor(
                int.class,
                String.class,
                String.class,
                String.class,
                String.class,
                double.class
        );
        frameConstructor.setAccessible(true);
        Object frame = frameConstructor.newInstance(
                -1,
                "example",
                "example.ModA",
                "tick",
                "()V",
                1.0d
        );
        Class<?> threadFramesType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfileWindow$ThreadFrames"
        );
        Constructor<?> threadFramesConstructor = threadFramesType.getDeclaredConstructor(
                String.class,
                List.class
        );
        threadFramesConstructor.setAccessible(true);
        Object threadFrames = threadFramesConstructor.newInstance(
                "WorldThread - default",
                List.of(frame)
        );
        Class<?> windowType = Class.forName(
                "com.alechilles.alecstelemetry.runtime.profiler.SparkProfileWindow"
        );
        Constructor<?> windowConstructor = windowType.getDeclaredConstructor(
                String.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                double.class,
                List.class
        );
        windowConstructor.setAccessible(true);
        Object window = windowConstructor.newInstance(
                "1.10.172",
                windowKey,
                1,
                1,
                1,
                1,
                1.0d,
                List.of(threadFrames)
        );
        Method complete = resultType.getDeclaredMethod("complete", SparkProfileSnapshot.class, windowType);
        complete.setAccessible(true);
        return complete.invoke(null, snapshot, window);
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
                root.resolve("Mods")
        );
    }

    private static TelemetryCoordinatorBridge activeBridge() {
        TelemetryCoordinatorBridge bridge = TelemetryCoordinatorRegistry.activeBridge();
        if (bridge == null) {
            throw new AssertionError("no active telemetry coordinator");
        }
        return bridge;
    }

    private static TelemetryConsentSnapshot disabledConsent() {
        return new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false);
    }

    private static TelemetryProjectRegistration project(String projectId,
                                                         String providerId,
                                                         boolean performanceEnabled,
                                                         boolean breadcrumbsEnabled) {
        String pluginIdentifier = "Example:" + providerId;
        String breadcrumbs = breadcrumbsEnabled
                ? ",\"events\":{\"breadcrumbs\":{\"enabled\":true,\"profilerCorrelationCategories\":[\""
                        + PROFILER_BREADCRUMB_CATEGORY + "\"]}}"
                : "";
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\","
                        + "\"projectVersion\":\"1.0.0\","
                        + "\"displayName\":\"" + projectId + "\","
                        + "\"ownerPluginIdentifiers\":[\"" + pluginIdentifier + "\"],"
                        + "\"packagePrefixes\":[\"example\"],"
                        + "\"telemetry\":{\"performance\":{\"supported\":true,\"defaultEnabled\":"
                        + performanceEnabled + "}" + breadcrumbs + "}}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, "1.0.0", null);
    }

    private static Map<String, Object> contribution(TelemetryProjectRegistration project,
                                                     Path sourcePath) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("token", "test-token");
        values.put("abiVersion", TelemetryProjectContributionRegistry.ABI_VERSION);
        values.put("projectId", project.projectId());
        values.put("logicalPluginIdentifier", project.pluginIdentifier());
        values.put("logicalPluginVersion", project.pluginVersion());
        values.put("origin", TelemetryProjectContributionRegistry.STANDALONE_ORIGIN);
        values.put("hostPluginIdentifier", "Example:Host");
        values.put("hostPluginVersion", "1.0.0");
        values.put("sourcePath", sourcePath.toString());
        values.put("descriptorJson", project.descriptor().toJson());
        values.put("descriptorHash", project.descriptor().canonicalHash());
        return Map.copyOf(values);
    }

    private static void awaitReads(BlockingReader reader, int expected) throws Exception {
        assertTrue(reader.entered.await(2, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (reader.reads.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(expected, reader.reads.get());
    }

    private static void awaitHistory(SparkProfilerMonitor monitor, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (monitor.history().size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(expected, monitor.history().size());
    }

    private record ProviderFixture(TelemetryRuntimeProviderHandle handle,
                                   SparkProfilerMonitor monitor,
                                   BlockingReader reader,
                                   String contributionToken,
                                   TelemetryProfilerContextProvider contextProvider) {
        private void awaitProjectWindow(int expectedWindow) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (handle.api().findProject("mod-a") != null
                        && handle.api().findProject("mod-a").profiler().history().stream()
                        .anyMatch(snapshot -> snapshot.windowKey() == expectedWindow)) {
                    return;
                }
                Thread.sleep(10L);
            }
            TelemetryProfilerView view = handle.api().findProject("mod-a").profiler();
            assertTrue(view.history().stream()
                            .anyMatch(snapshot -> snapshot.windowKey() == expectedWindow),
                    () -> "status=" + view.status()
                            + ", history=" + view.history());
        }

        private void publishAndAwait(int expectedWindow) throws Exception {
            if (!handle.ownsActiveCoordinator()) {
                handle.start();
            } else if (reader.reads.get() > 0) {
                monitor.suspend();
                monitor.activate();
            }
            reader.release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (handle.api().findProject("mod-a") != null
                        && handle.api().findProject("mod-a").profiler().history().stream()
                        .anyMatch(snapshot -> snapshot.windowKey() == expectedWindow)) {
                    return;
                }
                Thread.sleep(10L);
            }
            TelemetryProfilerView view = handle.api().findProject("mod-a").profiler();
            assertTrue(view.history().stream()
                    .anyMatch(snapshot -> snapshot.windowKey() == expectedWindow),
                    () -> "status=" + view.status()
                            + ", history=" + view.history());
        }

        private boolean removeProjectAndReconcile() {
            if (contributionToken == null) {
                return false;
            }
            TelemetryProjectContributionRegistry.unregister(contributionToken);
            return activeBridge().reconcileProjectContributions(
                    TelemetryProjectContributionRegistry.revision(),
                    TelemetryProjectContributionRegistry.activeContributions()
            );
        }

        private void close() {
            reader.release.countDown();
            handle.shutdown();
            if (contributionToken != null) {
                TelemetryProjectContributionRegistry.unregister(contributionToken);
            }
        }
    }

    private static final class BlockingReader {
        private final int windowKey;
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch secondEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingReader(int windowKey) {
            this.windowKey = windowKey;
        }

        private Object readResult() throws Exception {
            int read = reads.incrementAndGet();
            entered.countDown();
            if (read >= 2) {
                secondEntered.countDown();
            }
            boolean interrupted = false;
            while (release.getCount() > 0) {
                try {
                    release.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return completeResult(windowKey + read - 1);
        }
    }

    private static final class HandoffClearBarrier {
        private final CountDownLatch clearEntered = new CountDownLatch(1);
        private final CountDownLatch releaseClear = new CountDownLatch(1);
        private final CountDownLatch eligibilityEntered = new CountDownLatch(1);
        private final CountDownLatch releaseEligibility = new CountDownLatch(1);
        private final AtomicBoolean blockEligibility = new AtomicBoolean();
        private final AtomicBoolean eligibilityResult = new AtomicBoolean();

        private void blockNextEligibility() {
            blockEligibility.set(true);
        }

        private void releaseClear() {
            releaseClear.countDown();
        }

        private void releaseEligibility() {
            releaseEligibility.countDown();
        }

        private void awaitReleaseClear() {
            await(releaseClear, "clear callback");
        }

        private void awaitReleaseEligibility() {
            await(releaseEligibility, "eligibility predicate");
        }

        private static void await(CountDownLatch latch, String operation) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError(operation + " was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(operation + " was interrupted", interrupted);
            }
        }
    }

    private static final class TestContributionBridge implements TelemetryProjectContributionBridge {
        private final Map<String, Object> candidate;

        private TestContributionBridge(Map<String, Object> candidate) {
            this.candidate = candidate;
        }

        @Override
        public int contributionAbiVersion() {
            return TelemetryProjectContributionRegistry.ABI_VERSION;
        }

        @Override
        public Map<String, Object> candidate() {
            return candidate;
        }

        @Override
        public Map<String, Object> dispatch(String token,
                                            String operation,
                                            Map<String, Object> payload,
                                            Throwable throwable) {
            return Map.of("accepted", false);
        }
    }

    private static final class NoopClient implements CrashReportClient {
        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            return UploadResult.success(204);
        }
    }
}
