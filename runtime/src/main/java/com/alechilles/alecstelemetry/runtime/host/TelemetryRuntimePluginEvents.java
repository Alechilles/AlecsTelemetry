package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.runtime.stats.TelemetryPlayerCounter;
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
            plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnected);
            plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
        } catch (RuntimeException ex) {
            registered.set(false);
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Alec's Telemetry could not register shared runtime events.");
            }
        }
    }

    void unregister() {
        registered.set(false);
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
