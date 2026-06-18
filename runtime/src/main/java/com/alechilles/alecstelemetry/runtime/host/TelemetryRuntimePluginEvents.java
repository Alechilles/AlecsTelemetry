package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class TelemetryRuntimePluginEvents {
    private final TelemetryRuntimeProviderHandle handle;
    private final TelemetryPlayerCounter playerCounter;
    private final HytaleLogger logger;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private EventRegistration<?, ?> playerReadyRegistration;
    private EventRegistration<?, ?> playerDisconnectRegistration;
    private EventRegistration<?, ?> removeWorldRegistration;

    TelemetryRuntimePluginEvents(@Nonnull TelemetryRuntimeProviderHandle handle,
                                 @Nonnull TelemetryPlayerCounter playerCounter,
                                 @Nullable HytaleLogger logger) {
        this.handle = handle;
        this.playerCounter = playerCounter;
        this.logger = logger;
    }

    void register(@Nullable JavaPlugin plugin) {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        if (plugin == null || plugin.getEventRegistry() == null) {
            registered.set(false);
            return;
        }
        try {
            playerReadyRegistration = plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            playerDisconnectRegistration = plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnected);
            removeWorldRegistration = plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
        } catch (RuntimeException ex) {
            unregister();
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Alec's Telemetry could not register shared runtime events.");
            }
        }
    }

    void unregister() {
        registered.set(false);
        unregister(removeWorldRegistration);
        unregister(playerDisconnectRegistration);
        unregister(playerReadyRegistration);
        removeWorldRegistration = null;
        playerDisconnectRegistration = null;
        playerReadyRegistration = null;
    }

    private void unregister(@Nullable EventRegistration<?, ?> registration) {
        if (registration != null) {
            registration.unregister();
        }
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        playerCounter.onPlayerReady(event);
        handle.onPlayerReady(event);
    }

    private void onPlayerDisconnected(@Nonnull PlayerDisconnectEvent event) {
        playerCounter.onPlayerDisconnect(event);
    }

    private void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        handle.onWorldRemoved(event);
    }
}
