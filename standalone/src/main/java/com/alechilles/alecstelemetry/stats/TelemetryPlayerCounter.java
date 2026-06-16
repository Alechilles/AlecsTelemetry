package com.alechilles.alecstelemetry.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks currently connected players for anonymous aggregate statistics.
 */
public final class TelemetryPlayerCounter {

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        markReady(playerRef);
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        markDisconnected(event.getPlayerRef());
    }

    public void markReady(@Nullable PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            markReady(playerRef.getUuid());
        }
    }

    public void markDisconnected(@Nullable PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            markDisconnected(playerRef.getUuid());
        }
    }

    public void markReady(@Nonnull UUID playerUuid) {
        activePlayers.add(playerUuid);
    }

    public void markDisconnected(@Nonnull UUID playerUuid) {
        activePlayers.remove(playerUuid);
    }

    public int onlinePlayers() {
        return activePlayers.size();
    }
}
