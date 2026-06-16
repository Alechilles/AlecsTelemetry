package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Opens the telemetry consent settings page.
 */
public final class TelemetryConsentCommand extends AbstractPlayerCommand {

    private final AlecsTelemetry plugin;

    public TelemetryConsentCommand(AlecsTelemetry plugin) {
        super("consent", "Open telemetry consent settings.");
        this.plugin = plugin;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(false);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TelemetryConsentCoordinator coordinator = plugin == null ? null : plugin.getConsentCoordinator();
        if (coordinator == null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry consent UI is unavailable.");
            return;
        }
        if (!coordinator.openConsentPage(ref, store, playerRef, false)) {
            TelemetryCommandSupport.send(commandContext, "Unable to open telemetry consent UI.");
        }
    }
}
