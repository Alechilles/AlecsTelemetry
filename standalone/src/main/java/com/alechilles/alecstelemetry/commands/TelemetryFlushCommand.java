package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
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
 * Schedules a telemetry flush for all projects or one specific project.
 */
public final class TelemetryFlushCommand extends AbstractPlayerCommand {

    private final AlecsTelemetry plugin;

    public TelemetryFlushCommand(@Nonnull AlecsTelemetry plugin) {
        super("flush", "Flush pending telemetry reports. Optional: <project-id>");
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
        if (projectId != null && runtimeService.findProject(projectId) == null) {
            TelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        boolean scheduled = projectId == null
                ? runtimeService.triggerFlushAsync()
                : runtimeService.triggerFlushAsync(projectId);
        TelemetryCommandSupport.send(
                commandContext,
                scheduled
                        ? "Telemetry flush scheduled" + (projectId == null ? "." : " for " + projectId + ".")
                        : "Telemetry flush was not scheduled."
        );
    }
}
