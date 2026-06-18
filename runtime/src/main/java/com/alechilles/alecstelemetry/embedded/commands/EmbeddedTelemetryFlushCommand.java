package com.alechilles.alecstelemetry.embedded.commands;

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
 * Schedules an embedded telemetry flush.
 */
final class EmbeddedTelemetryFlushCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryFlushCommand(@Nonnull EmbeddedTelemetryService service) {
        super("flush", "Flush pending telemetry reports. Optional: <project-id>");
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
        String projectId = EmbeddedTelemetryCommandSupport.token(commandContext, 2);
        if (projectId != null && service.commandProject(projectId) == null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        boolean scheduled = service.commandFlush(projectId);
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                scheduled
                        ? "Telemetry flush scheduled" + (projectId == null ? "." : " for " + projectId + ".")
                        : "Telemetry flush was not scheduled."
        );
    }
}
