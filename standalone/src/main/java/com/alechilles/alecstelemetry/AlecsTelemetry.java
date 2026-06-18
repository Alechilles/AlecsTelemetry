package com.alechilles.alecstelemetry;

import com.alechilles.alecstelemetry.api.TelemetryRuntimeLocator;
import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryStatsHeartbeatService;
import com.alechilles.alecstelemetry.runtime.stats.TelemetryStatsRuntime;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
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
    private TelemetryPlayerCounter playerCounter;
    private TelemetryStatsHeartbeatService statsHeartbeatService;

    public AlecsTelemetry(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        try {
            runtimeService = TelemetryRuntimeService.create(this);
            consentCoordinator = new TelemetryConsentCoordinator(runtimeService, getLogger());
            playerCounter = new TelemetryPlayerCounter();
            statsHeartbeatService = new TelemetryStatsHeartbeatService(
                    TelemetryStatsRuntime.from(runtimeService),
                    playerCounter,
                    HytaleServer.SCHEDULED_EXECUTOR,
                    getLogger()
            );
            runtimeService.attachStatsHeartbeat(statsHeartbeatService);
            TelemetryRuntimeLocator.register(runtimeService.api());
            getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnected);
        } catch (Exception ex) {
            getLogger().at(Level.WARNING).withCause(ex).log(
                    "Failed to initialize Alec's Telemetry runtime; continuing without crash telemetry capture."
            );
            runtimeService = null;
            consentCoordinator = null;
            playerCounter = null;
            statsHeartbeatService = null;
            TelemetryRuntimeLocator.clear();
        }
        if (getCommandRegistry() != null) {
            getCommandRegistry().registerCommand(new TelemetryCommandRoot(runtimeService));
        }
    }

    @Override
    protected void start() {
        if (runtimeService != null) {
            runtimeService.start();
            String activeProvider = runtimeService.activeCoordinatorProviderId();
            getLogger().at(Level.INFO).log(
                    "Alec's Telemetry enabled. Active coordinator="
                            + (activeProvider == null ? "<none>" : activeProvider)
                            + ", standaloneOwnsRuntime=" + runtimeService.ownsActiveCoordinator()
                            + ", registered projects=" + runtimeService.registeredProjectCount()
            );
            return;
        }
        getLogger().at(Level.INFO).log("Alec's Telemetry enabled without an active runtime service.");
    }

    @Override
    protected void shutdown() {
        if (runtimeService != null) {
            if (statsHeartbeatService != null) {
                statsHeartbeatService.shutdown();
            }
            runtimeService.shutdown();
            runtimeService = null;
        }
        consentCoordinator = null;
        playerCounter = null;
        statsHeartbeatService = null;
        TelemetryRuntimeLocator.clear();
        instance = null;
        getLogger().at(Level.INFO).log("Alec's Telemetry disabled.");
    }

    private void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        if (runtimeService == null) {
            return;
        }
        if (runtimeService.ownsActiveCoordinator()) {
            runtimeService.captureExceptionalWorldRemoval(event.getWorld(), event.getRemovalReason());
        }
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (playerCounter != null) {
            playerCounter.onPlayerReady(event);
        }
        if (consentCoordinator != null) {
            consentCoordinator.onPlayerReady(event);
        }
    }

    private void onPlayerDisconnected(@Nonnull PlayerDisconnectEvent event) {
        if (playerCounter != null) {
            playerCounter.onPlayerDisconnect(event);
        }
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
