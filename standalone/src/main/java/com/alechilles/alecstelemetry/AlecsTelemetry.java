package com.alechilles.alecstelemetry;

import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHost;
import com.alechilles.alecstelemetry.runtime.host.TelemetryRuntimeHostHandle;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Standalone telemetry runtime mod entrypoint.
 */
public final class AlecsTelemetry extends JavaPlugin {
    private static AlecsTelemetry instance;

    private TelemetryRuntimeHostHandle host;

    public AlecsTelemetry(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
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
    public static AlecsTelemetry getInstance() {
        return instance;
    }

    @Nullable
    public TelemetryRuntimeHostHandle getHost() {
        return host;
    }
}
