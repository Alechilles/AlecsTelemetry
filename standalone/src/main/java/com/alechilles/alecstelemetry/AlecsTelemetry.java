package com.alechilles.alecstelemetry;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Standalone telemetry runtime mod entrypoint.
 */
public final class AlecsTelemetry extends JavaPlugin {

    private static AlecsTelemetry instance;

    private TelemetryRuntimeService runtimeService;
    private TelemetryConsentCoordinator consentCoordinator;

    public AlecsTelemetry(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        try {
            runtimeService = TelemetryRuntimeService.create(this);
            consentCoordinator = new TelemetryConsentCoordinator(runtimeService, getLogger());
            TelemetryRuntimeLocator.register(runtimeService.api());
            getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, consentCoordinator::onPlayerReady);
        } catch (Exception ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to initialize Alec's Telemetry runtime; continuing without crash telemetry capture."
            );
            runtimeService = null;
            consentCoordinator = null;
            TelemetryRuntimeLocator.clear();
        }
        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new TelemetryCommandRoot(this));
        }
    }

    @Override
    protected void start() {
        if (runtimeService != null) {
            runtimeService.start();
            getLogger().at(Level.INFO).log(
                    "Alec's Telemetry enabled. Registered projects=" + runtimeService.registeredProjectCount()
            );
            return;
        }
        getLogger().at(Level.INFO).log("Alec's Telemetry enabled without an active runtime service.");
    }

    @Override
    protected void shutdown() {
        if (runtimeService != null) {
            runtimeService.shutdown();
            runtimeService = null;
        }
        consentCoordinator = null;
        TelemetryRuntimeLocator.clear();
        instance = null;
        getLogger().at(Level.INFO).log("Alec's Telemetry disabled.");
    }

    private void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        if (runtimeService == null) {
            return;
        }
        runtimeService.captureExceptionalWorldRemoval(event.getWorld(), event.getRemovalReason());
    }

    @Nullable
    public static AlecsTelemetry getInstance() {
        return instance;
    }

    @Nullable
    public TelemetryRuntimeService getRuntimeService() {
        return runtimeService;
    }

    @Nullable
    public TelemetryConsentCoordinator getConsentCoordinator() {
        return consentCoordinator;
    }
}
