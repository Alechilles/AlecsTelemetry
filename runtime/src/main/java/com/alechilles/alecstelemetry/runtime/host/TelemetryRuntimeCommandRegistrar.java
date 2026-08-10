package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class TelemetryRuntimeCommandRegistrar {
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private JavaPlugin plugin;
    private TelemetryRuntimeProviderHandle handle;
    private HytaleLogger logger;

    void initialize(@Nullable JavaPlugin plugin,
                    @Nonnull TelemetryRuntimeProviderHandle handle,
                    @Nullable HytaleLogger logger) {
        this.plugin = plugin;
        this.handle = handle;
        this.logger = logger;
    }

    void register() {
        if (registered.get()) {
            return;
        }
        if (plugin == null || handle == null) {
            registered.set(true);
            return;
        }
        if (plugin.getCommandRegistry() == null) {
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        try {
            if (plugin.getCommandRegistry().registerCommand(new TelemetryCommandRoot(handle)) == null) {
                registered.set(false);
                if (logger != null) {
                    logger.at(Level.WARNING).log(
                            "Telemetry runtime command registration was rejected; /telemetry is unavailable."
                    );
                }
                return;
            }
            if (logger != null) {
                logger.at(Level.INFO).log("Telemetry runtime registered /telemetry commands.");
            }
        } catch (RuntimeException ex) {
            registered.set(false);
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Telemetry runtime commands could not register. Another runtime may already own /telemetry."
                );
            }
            handle.recordSelfError(
                    "runtime_command_register_failed",
                    ex,
                    Map.of("operation", "command_register")
            );
        }
    }

    void unregister() {
        registered.set(false);
    }

    void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        register();
    }

    boolean registered() {
        return registered.get();
    }
}
