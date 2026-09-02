package com.alechilles.beacon.runtime.host;

import com.alechilles.beacon.api.TelemetryRuntimeApi;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryEmbeddedProviderPoolTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearPool() {
        TelemetryEmbeddedProviderPool.clearForTests();
    }

    @Test
    void lastLeaseCloseHoldsPoolLockThroughShutdownBeforeNewAcquire() throws Exception {
        TelemetryEmbeddedProviderPool.clearForTests();
        CopyOnWriteArrayList<BlockingHandle> handles = new CopyOnWriteArrayList<>();
        TelemetryEmbeddedProviderPool.setProviderFactoryForTests((plugin, descriptor) -> {
            BlockingHandle handle = new BlockingHandle(handles.isEmpty());
            handles.add(handle);
            return handle;
        });

        JavaPlugin plugin = testPlugin(tempDir);
        TelemetryEmbeddedProviderPool.Lease first = (TelemetryEmbeddedProviderPool.Lease)
                TelemetryEmbeddedProviderPool.acquire(plugin, null);
        first.start();

        Thread closeThread = new Thread(first::close, "telemetry-pool-close");
        closeThread.start();
        BlockingHandle firstHandle = handles.getFirst();
        assertTrue(firstHandle.shutdownEntered.await(2, TimeUnit.SECONDS));

        CountDownLatch acquireFinished = new CountDownLatch(1);
        TelemetryEmbeddedProviderPool.Lease[] second = new TelemetryEmbeddedProviderPool.Lease[1];
        Thread acquireThread = new Thread(() -> {
            second[0] = (TelemetryEmbeddedProviderPool.Lease) TelemetryEmbeddedProviderPool.acquire(plugin, null);
            acquireFinished.countDown();
        }, "telemetry-pool-acquire");
        acquireThread.start();

        assertFalse(acquireFinished.await(150, TimeUnit.MILLISECONDS),
                "a new lease must not be created while the previous delegate is shutting down");
        assertEquals(1, handles.size());

        firstHandle.allowShutdown.countDown();
        closeThread.join(2_000);
        acquireThread.join(2_000);
        assertFalse(closeThread.isAlive());
        assertFalse(acquireThread.isAlive());
        assertNotNull(second[0]);
        assertEquals(2, handles.size());
        assertEquals(1, TelemetryEmbeddedProviderPool.providerCountForTests());

        second[0].close();
        assertEquals(0, TelemetryEmbeddedProviderPool.providerCountForTests());
    }

    private static JavaPlugin testPlugin(Path dataDirectory) {
        try {
            TestPlugin plugin = (TestPlugin) unsafe().allocateInstance(TestPlugin.class);
            plugin.dataDirectory = dataDirectory;
            plugin.file = dataDirectory.resolve("Host.jar");
            return plugin;
        } catch (InstantiationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static sun.misc.Unsafe unsafe() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class TestPlugin extends JavaPlugin {
        private Path dataDirectory;
        private Path file;

        private TestPlugin() {
            super(null);
        }

        @Override
        public Path getDataDirectory() {
            return dataDirectory;
        }

        @Override
        public Path getFile() {
            return file;
        }
    }

    private static final class BlockingHandle implements TelemetryRuntimeHostHandle {
        private final boolean blockShutdown;
        private final CountDownLatch shutdownEntered = new CountDownLatch(1);
        private final CountDownLatch allowShutdown = new CountDownLatch(1);

        private BlockingHandle(boolean blockShutdown) {
            this.blockShutdown = blockShutdown;
            if (!blockShutdown) {
                allowShutdown.countDown();
            }
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
            shutdownEntered.countDown();
            if (blockShutdown) {
                try {
                    allowShutdown.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public boolean ownsActiveCoordinator() {
            return false;
        }

        @Override
        public String activeCoordinatorProviderId() {
            return null;
        }

        @Override
        public int registeredProjectCount() {
            return 0;
        }

        @Override
        public TelemetryRuntimeApi api() {
            return null;
        }
    }
}
