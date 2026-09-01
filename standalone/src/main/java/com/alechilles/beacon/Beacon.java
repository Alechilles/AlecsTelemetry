package com.alechilles.beacon;

import com.alechilles.beacon.runtime.host.TelemetryRuntimeHost;
import com.alechilles.beacon.runtime.host.TelemetryRuntimeHostHandle;
import com.creditor.Creditor;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Standalone telemetry runtime mod entrypoint.
 */
public final class Beacon extends JavaPlugin {
    private static Beacon instance;

    private TelemetryRuntimeHostHandle host;

    public Beacon(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        try {
            Creditor.setup(this);
        } catch (RuntimeException ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to initialize Creditor integration; continuing without /credits support."
            );
        }
        try {
            host = TelemetryRuntimeHost.bootstrapStandalone(this);
        } catch (Exception ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to initialize Alec's Telemetry runtime; continuing without telemetry."
            );
            host = null;
        }
    }

    @Override
    protected void start() {
        try {
            Creditor.start(this);
        } catch (RuntimeException ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to start Creditor integration; continuing without /credits support."
            );
        }
        if (host != null) {
            host.start();
            getLogger().at(Level.INFO).log(
                    "Alec's Telemetry enabled. Active coordinator="
                            + (host.activeCoordinatorProviderId() == null ? "<none>" : host.activeCoordinatorProviderId())
                            + ", standaloneOwnsRuntime=" + host.ownsActiveCoordinator()
                            + ", registered projects=" + host.registeredProjectCount()
            );
            return;
        }
        getLogger().at(Level.INFO).log("Alec's Telemetry enabled without an active runtime host.");
    }

    @Override
    protected void shutdown() {
        if (host != null) {
            host.shutdown();
            host = null;
        }
        instance = null;
        getLogger().at(Level.INFO).log("Alec's Telemetry disabled.");
    }

    @Nullable
    public static Beacon getInstance() {
        return instance;
    }

    @Nullable
    public TelemetryRuntimeHostHandle getHost() {
        return host;
    }
}
