package com.alechilles.beacon.runtime.host;

import com.alechilles.beacon.runtime.stats.TelemetryPlayerCounter;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
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
    private EventRegistration<?, ?> bootRegistration;

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
            bootRegistration = plugin.getEventRegistry().registerGlobal(BootEvent.class, this::onBoot);
            playerReadyRegistration = plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            playerDisconnectRegistration = plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnected);
            removeWorldRegistration = plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
        } catch (RuntimeException ex) {
            unregister();
            logWarning("Alec's Telemetry could not register shared runtime events.", ex);
            handle.recordSelfError(
                    "runtime_event_register_failed",
                    ex,
                    Map.of(
                            "operation", "event_register",
                            "event", "shared_runtime"
                    )
            );
        }
    }

    void unregister() {
        registered.set(false);
        EventRegistration<?, ?> boot = bootRegistration;
        EventRegistration<?, ?> removeWorld = removeWorldRegistration;
        EventRegistration<?, ?> playerDisconnect = playerDisconnectRegistration;
        EventRegistration<?, ?> playerReady = playerReadyRegistration;
        bootRegistration = null;
        removeWorldRegistration = null;
        playerDisconnectRegistration = null;
        playerReadyRegistration = null;
        unregister(boot, "boot");
        unregister(removeWorld, "remove world");
        unregister(playerDisconnect, "player disconnect");
        unregister(playerReady, "player ready");
    }

    private void unregister(@Nullable EventRegistration<?, ?> registration, @Nonnull String eventName) {
        if (registration == null) {
            return;
        }
        try {
            registration.unregister();
        } catch (RuntimeException ex) {
            logWarning("Alec's Telemetry could not unregister shared runtime " + eventName + " event.", ex);
            handle.recordSelfError(
                    "runtime_event_unregister_failed",
                    ex,
                    Map.of(
                            "operation", "event_unregister",
                            "event", eventName
                    )
            );
        }
    }

    private void logWarning(@Nonnull String message, @Nonnull RuntimeException ex) {
        if (logger == null) {
            return;
        }
        try {
            logger.at(Level.WARNING).withCause(ex).log(message);
        } catch (RuntimeException ignored) {
        }
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        playerCounter.onPlayerReady(event);
        handle.onPlayerReady(event);
    }

    private void onBoot(@Nonnull BootEvent event) {
        handle.onBoot();
    }

    private void onPlayerDisconnected(@Nonnull PlayerDisconnectEvent event) {
        playerCounter.onPlayerDisconnect(event);
    }

    private void onWorldRemoved(@Nonnull RemoveWorldEvent event) {
        handle.onWorldRemoved(event);
    }
}
