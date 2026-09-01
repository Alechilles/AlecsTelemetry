package com.alechilles.beacon.commands;

import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Captures a manual test report without crashing the server.
 */
public final class TelemetryTestCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryTestCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("test", "Capture a manual Beacon test report. Usage: /beacon test <project-id> [detail]");
        this.runtime = runtime;
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext commandContext) {
        if (runtime == null) {
            TelemetryCommandSupport.send(commandContext, "Beacon runtime service is unavailable.");
            return;
        }
        String projectId = TelemetryCommandSupport.token(commandContext, 2);
        if (projectId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /beacon test <project-id> [detail]");
            return;
        }

        boolean captured = runtime.captureTestReport(projectId, TelemetryCommandSupport.remainder(commandContext, 3));
        TelemetryCommandSupport.send(
                commandContext,
                captured
                        ? "Beacon test report queued for " + projectId + "."
                        : "Unable to queue Beacon test report for " + projectId + "."
        );
    }
}
