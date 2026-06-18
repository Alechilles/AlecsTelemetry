package com.alechilles.alecstelemetry.embedded.commands;

import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryDiagnostics;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

final class EmbeddedTelemetryStatusCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryStatusCommand(@Nonnull EmbeddedTelemetryService service) {
        super("status", "Show Alec's Telemetry runtime status.");
        this.service = service;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        EmbeddedTelemetryDiagnostics diagnostics = service.diagnostics();
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                "Telemetry: embedded=true"
                        + ", enabled=" + diagnostics.enabled()
                        + ", projects=" + service.commandProjects().size()
                        + ", pending=" + service.commandPendingReports(null)
                        + ", flushInProgress=" + diagnostics.flushInProgress()
                        + ", activeCoordinator=" + (service.activeCoordinatorProviderId() == null
                        ? "<none>"
                        : service.activeCoordinatorProviderId())
                        + ", ownsRuntime=" + service.ownsActiveCoordinator()
        );
        EmbeddedTelemetryCommandSupport.send(commandContext, "Telemetry last flush: " + service.commandLastFlushResult());
    }
}
