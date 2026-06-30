package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Schedules a telemetry flush for all projects or one specific project.
 */
public final class TelemetryFlushCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryFlushCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("flush", "Flush pending telemetry reports. Optional: <project-id>");
        this.runtime = runtime;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext commandContext) {
        if (runtime == null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry runtime service is unavailable.");
            return;
        }
        String projectId = TelemetryCommandSupport.token(commandContext, 2);
        if (projectId != null && runtime.findProject(projectId) == null) {
            TelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        boolean scheduled = projectId == null
                ? runtime.requestFlush(null)
                : runtime.requestFlush(projectId);
        TelemetryCommandSupport.send(
                commandContext,
                scheduled
                        ? "Telemetry flush scheduled" + (projectId == null ? "." : " for " + projectId + ".")
                        : "Telemetry flush was not scheduled."
        );
    }
}
