package com.alechilles.alecstelemetry.embedded;

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
 * Tracks connected players for embedded anonymous stats heartbeats.
 */
final class EmbeddedTelemetryPlayerCounter {

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        markReady(playerRef);
    }

    void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        markDisconnected(event.getPlayerRef());
    }

    void markReady(@Nullable PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            markReady(playerRef.getUuid());
        }
    }

    void markDisconnected(@Nullable PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            markDisconnected(playerRef.getUuid());
        }
    }

    void markReady(@Nonnull UUID playerUuid) {
        activePlayers.add(playerUuid);
    }

    void markDisconnected(@Nonnull UUID playerUuid) {
        activePlayers.remove(playerUuid);
    }

    int onlinePlayers() {
        return activePlayers.size();
    }
}
