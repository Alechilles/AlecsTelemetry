package com.alechilles.alecstelemetry.runtime.host;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryRuntimeCommandRegistrar {
    private final AtomicBoolean registered = new AtomicBoolean(false);

    void register() {
        registered.set(true);
    }

    void unregister() {
        registered.set(false);
    }

    void onPlayerReady(@Nonnull PlayerReadyEvent event) {
    }

    boolean registered() {
        return registered.get();
    }
}
