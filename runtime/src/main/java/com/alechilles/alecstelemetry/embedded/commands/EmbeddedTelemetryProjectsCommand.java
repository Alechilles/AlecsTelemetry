package com.alechilles.alecstelemetry.embedded.commands;

import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Lists telemetry projects visible to the embedded coordinator.
 */
final class EmbeddedTelemetryProjectsCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryProjectsCommand(@Nonnull EmbeddedTelemetryService service) {
        super("projects", "List registered telemetry projects.");
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
        List<TelemetryProjectRegistration> projects = service.commandProjects();
        if (projects.isEmpty()) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "No telemetry-enabled projects were discovered.");
            return;
        }

        for (TelemetryProjectRegistration project : projects) {
            EmbeddedTelemetryCommandSupport.send(
                    commandContext,
                    project.projectId()
                            + ": mode=" + project.runtimeMode()
                            + ", enabled=" + service.commandProjectEnabled(project.projectId())
                            + ", destination=" + project.destinationMode()
                            + ", pending=" + service.commandPendingReports(project.projectId())
                            + ", override=" + project.hasOverride()
            );
        }
    }
}
