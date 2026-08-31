package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeApi;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-local reference-counted pool for embedded runtime providers.
 *
 * <p>A physical plugin host gets one provider handle even when it exposes a conventional
 * descriptor and several logical contribution services. Leases are the only objects that
 * callers retain; releasing the last started lease shuts the provider down.</p>
 */
public final class TelemetryEmbeddedProviderPool {
    private static final Object LOCK = new Object();
    private static final Map<HostKey, Entry> ENTRIES = new HashMap<>();
    private static final ProviderFactory DEFAULT_PROVIDER_FACTORY = TelemetryRuntimeHost::bootstrapDirectEmbedded;
    private static volatile ProviderFactory providerFactory = DEFAULT_PROVIDER_FACTORY;

    private TelemetryEmbeddedProviderPool() {
    }

    @FunctionalInterface
    interface ProviderFactory {
        TelemetryRuntimeHostHandle create(JavaPlugin plugin, TelemetryProjectDescriptor descriptor);
    }

    static void setProviderFactoryForTests(@Nullable ProviderFactory factory) {
        providerFactory = factory == null ? DEFAULT_PROVIDER_FACTORY : factory;
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle acquire(@Nonnull JavaPlugin plugin,
                                                     @Nullable TelemetryProjectDescriptor descriptor) {
        HostKey key = HostKey.from(plugin);
        synchronized (LOCK) {
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                entry = new Entry(key, providerFactory.create(plugin, descriptor));
                entry.descriptorRegistered = descriptor != null;
                ENTRIES.put(key, entry);
            }
            if (descriptor != null && !entry.descriptorRegistered) {
                TelemetryProjectRegistration registration = TelemetryRuntimeHost.embeddedRegistration(plugin, descriptor);
                if (registration != null && entry.delegate.ensureEmbeddedProject(registration)) {
                    entry.descriptorRegistered = true;
                }
            }
            entry.leaseCount++;
            return new Lease(entry);
        }
    }

    /** Test hook that also leaves the coordinator and locator in the same state as shutdown. */
    public static void clearForTests() {
        synchronized (LOCK) {
            for (Entry entry : List.copyOf(ENTRIES.values())) {
                entry.delegate.shutdown();
            }
            ENTRIES.clear();
            providerFactory = DEFAULT_PROVIDER_FACTORY;
        }
    }

    public static int providerCountForTests() {
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    private static void release(@Nonnull Lease lease) {
        synchronized (LOCK) {
            Entry entry = lease.entry;
            if (lease.closed) {
                return;
            }
            lease.closed = true;
            entry.leaseCount = Math.max(0, entry.leaseCount - 1);
            if (lease.started) {
                lease.started = false;
                entry.startedLeaseCount = Math.max(0, entry.startedLeaseCount - 1);
                if (entry.startedLeaseCount == 0) {
                    entry.started = false;
                    entry.delegate.shutdown();
                }
            }
            if (entry.leaseCount == 0) {
                ENTRIES.remove(entry.key, entry);
            }
        }
    }

    private static void start(@Nonnull Lease lease) {
        synchronized (LOCK) {
            Entry entry = lease.entry;
            if (lease.closed || lease.started) {
                return;
            }
            lease.started = true;
            entry.startedLeaseCount++;
            if (!entry.started) {
                entry.started = true;
                entry.delegate.start();
            }
        }
    }

    private static final class Entry {
        private final HostKey key;
        private final TelemetryRuntimeHostHandle delegate;
        private int leaseCount;
        private int startedLeaseCount;
        private boolean started;
        private boolean descriptorRegistered;

        private Entry(@Nonnull HostKey key, @Nonnull TelemetryRuntimeHostHandle delegate) {
            this.key = key;
            this.delegate = delegate;
        }
    }

    /** A provider lease also acts as the host handle consumed by embedded services. */
    public static final class Lease implements TelemetryRuntimeHostHandle, AutoCloseable {
        private final Entry entry;
        private boolean started;
        private boolean closed;

        private Lease(@Nonnull Entry entry) {
            this.entry = entry;
        }

        @Override
        public void start() {
            TelemetryEmbeddedProviderPool.start(this);
        }

        @Override
        public void shutdown() {
            close();
        }

        @Override
        public void close() {
            TelemetryEmbeddedProviderPool.release(this);
        }

        @Override
        public boolean ownsActiveCoordinator() {
            return entry.delegate.ownsActiveCoordinator();
        }

        @Nullable
        @Override
        public String activeCoordinatorProviderId() {
            return entry.delegate.activeCoordinatorProviderId();
        }

        @Override
        public int registeredProjectCount() {
            return entry.delegate.registeredProjectCount();
        }

        @Override
        public boolean ensureEmbeddedProject(@Nonnull TelemetryProjectRegistration project) {
            return entry.delegate.ensureEmbeddedProject(project);
        }

        @Nonnull
        @Override
        public TelemetryRuntimeApi api() {
            return entry.delegate.api();
        }

        @Override
        public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
            return entry.delegate.setProjectEnabled(projectId, enabled);
        }

        @Override
        public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
            return entry.delegate.setBreadcrumbsEnabled(projectId, enabled);
        }

        @Override
        public boolean isDiagnosticsEnabled(@Nonnull String projectId) {
            return entry.delegate.isDiagnosticsEnabled(projectId);
        }

        @Override
        public boolean setDiagnosticsEnabled(@Nonnull String projectId, boolean enabled) {
            return entry.delegate.setDiagnosticsEnabled(projectId, enabled);
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull TelemetryConsentSnapshot snapshot) {
            return entry.delegate.applyConsent(projectId, snapshot);
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            return entry.delegate.markConsentReviewed(projectId);
        }

        @Override
        public void recordConsentPromptShown(@Nonnull String viewerKey,
                                             @Nonnull List<TelemetryProjectRegistration> projects) {
            entry.delegate.recordConsentPromptShown(viewerKey, projects);
        }

        @Override
        public void recordConsentUiOpened(@Nonnull String viewerKey,
                                          @Nonnull List<TelemetryProjectRegistration> projects) {
            entry.delegate.recordConsentUiOpened(viewerKey, projects);
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return entry.delegate.captureTestReport(projectId, detail);
        }
    }

    private record HostKey(@Nonnull String physicalHost,
                           @Nonnull String runtimeVersion,
                           @Nullable ClassLoader telemetryClassLoader) {
        private static HostKey from(@Nonnull JavaPlugin plugin) {
            String physicalHost;
            try {
                Path file = plugin.getFile();
                physicalHost = file == null
                        ? plugin.getDataDirectory().toAbsolutePath().normalize().toString()
                        : file.toAbsolutePath().normalize().toString();
            } catch (RuntimeException ex) {
                physicalHost = plugin.getClass().getName();
            }
            return new HostKey(
                    physicalHost,
                    TelemetryRuntimeHost.runtimeVersionForPool(),
                    TelemetryEmbeddedProviderPool.class.getClassLoader()
            );
        }
    }
}
