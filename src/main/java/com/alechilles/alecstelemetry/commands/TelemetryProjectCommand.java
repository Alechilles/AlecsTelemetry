package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Shows detailed diagnostics for one telemetry project.
 */
public final class TelemetryProjectCommand extends AbstractPlayerCommand {

    private final AlecsTelemetry plugin;

    public TelemetryProjectCommand(@Nonnull AlecsTelemetry plugin) {
        super("project", "Show diagnostics for one telemetry project.");
        this.plugin = plugin;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TelemetryRuntimeService runtimeService = plugin.getRuntimeService();
        if (runtimeService == null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry runtime service is unavailable.");
            return;
        }
        String projectId = TelemetryCommandSupport.token(commandContext, 2);
        if (projectId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /telemetry project <project-id>");
            return;
        }

        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = runtimeService.projectDiagnostics(projectId);
        if (diagnostics == null) {
            TelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        TelemetryCommandSupport.send(
                commandContext,
                diagnostics.projectId() + " (" + diagnostics.displayName() + ")"
        );
        TelemetryCommandSupport.send(
                commandContext,
                "enabled=" + diagnostics.enabled()
                        + ", destination=" + diagnostics.destinationMode()
                        + ", endpoint=" + (diagnostics.endpoint() == null ? "<none>" : diagnostics.endpoint())
                        + ", pending=" + diagnostics.pendingReports()
        );
        TelemetryCommandSupport.send(
                commandContext,
                "plugin=" + diagnostics.pluginIdentifier()
                        + "@" + diagnostics.pluginVersion()
                        + ", override=" + diagnostics.overridePresent()
        );
        TelemetryCommandSupport.send(
                commandContext,
                "packagePrefixes=" + String.join(", ", diagnostics.packagePrefixes())
        );
        if (diagnostics.sourcePath() != null) {
            TelemetryCommandSupport.send(commandContext, "sourcePath=" + diagnostics.sourcePath());
        }
    }
}
