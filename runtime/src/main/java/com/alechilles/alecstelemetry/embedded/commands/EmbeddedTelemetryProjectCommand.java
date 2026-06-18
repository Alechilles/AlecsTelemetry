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
import java.util.stream.Collectors;

/**
 * Shows diagnostics for one embedded telemetry project.
 */
final class EmbeddedTelemetryProjectCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryProjectCommand(@Nonnull EmbeddedTelemetryService service) {
        super("project", "Show diagnostics for one telemetry project.");
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
        if (projectId == null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Usage: /telemetry project <project-id>");
            return;
        }

        TelemetryProjectRegistration project = service.commandProject(projectId);
        if (project == null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        EmbeddedTelemetryCommandSupport.send(commandContext, project.projectId() + " (" + project.displayName() + ")");
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                "enabled=" + service.commandProjectEnabled(project.projectId())
                        + ", mode=" + project.runtimeMode()
                        + ", destination=" + project.destinationMode()
                        + ", pending=" + service.commandPendingReports(project.projectId())
        );
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                "plugin=" + project.pluginIdentifier()
                        + "@" + project.pluginVersion()
                        + ", override=" + project.hasOverride()
        );
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                "packagePrefixes=" + project.packagePrefixes().stream().collect(Collectors.joining(", "))
        );
        if (project.sourcePath() != null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "sourcePath=" + project.sourcePath());
        }
    }
}
