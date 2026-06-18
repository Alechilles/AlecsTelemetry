package com.alechilles.alecstelemetry.embedded.commands;

import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Embedded-mode placeholder for the standalone consent UI command.
 */
final class EmbeddedTelemetryConsentCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryConsentCommand(@Nonnull EmbeddedTelemetryService service) {
        super("consent", "Show telemetry consent information.");
        this.service = service;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(false);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TelemetryConsentCoordinator coordinator = new TelemetryConsentCoordinator(service, null);
        if (!coordinator.openConsentPage(ref, store, playerRef, false)) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Unable to open telemetry consent UI.");
        }
    }
}
