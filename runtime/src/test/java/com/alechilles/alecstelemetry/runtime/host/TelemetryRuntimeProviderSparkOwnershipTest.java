package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerMonitor;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryRuntimeProviderSparkOwnershipTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearRuntimeGlobals() {
        TelemetryCoordinatorRegistry.clearForTests();
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

    private ProviderFixture fixture(String providerId,
                                    TelemetryRuntimeOrigin origin,
                                    String runtimeVersion,
                                    BlockingReader reader) throws Exception {
        Path root = tempDir.resolve(providerId.replace(':', '_'));
        TelemetryDataPaths dataPaths = dataPaths(root);
        TelemetryRuntimeSettings loaded = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), null);
        TelemetryRuntimeSettings settings = withEnabledSparkProfiler(loaded);
        TelemetryCoordinatorService service = new TelemetryCoordinatorService(
                settings,
                dataPaths,
                List.of(),
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
        SparkProfilerMonitor monitor = injectedMonitor(
                settings.sparkProfiler(),
                reader,
                () -> {
                    TelemetryRuntimeProviderHandle handle = handleReference.get();
                    return handle != null && handle.ownsActiveCoordinator();
                }
        );
        TelemetryRuntimeProviderHandle handle = new TelemetryRuntimeProviderHandle(
                request,
                candidate,
                settings,
                dataPaths,
                service,
                List.of(),
                List.of(),
                new TelemetryPlayerCounter(),
                null,
                new TelemetryRuntimeCommandRegistrar(),
                new NoopClient(),
                monitor
        );
        handleReference.set(handle);
        return new ProviderFixture(handle, monitor, reader);
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
            BooleanSupplier owner) throws Exception {
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
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : SparkProfilerMonitor.class.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 7) {
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
                null
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
        Method complete = resultType.getDeclaredMethod("complete", SparkProfileSnapshot.class);
        complete.setAccessible(true);
        return complete.invoke(null, snapshot);
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
                                   BlockingReader reader) {
    }

    private static final class BlockingReader {
        private final int windowKey;
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingReader(int windowKey) {
            this.windowKey = windowKey;
        }

        private Object readResult() throws Exception {
            reads.incrementAndGet();
            entered.countDown();
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
            return completeResult(windowKey);
        }
    }

    private static final class NoopClient implements CrashReportClient {
        @Override
        public UploadResult upload(DeliveryTarget target, String payloadJson) {
            return UploadResult.success(204);
        }
    }
}
