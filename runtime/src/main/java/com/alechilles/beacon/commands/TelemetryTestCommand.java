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
        super("test", "Capture a manual telemetry test report. Usage: /telemetry test <project-id> [detail]");
        this.runtime = runtime;
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext commandContext) {
        if (runtime == null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry runtime service is unavailable.");
            return;
        }
        String projectId = TelemetryCommandSupport.token(commandContext, 2);
        if (projectId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /telemetry test <project-id> [detail]");
            return;
        }

        boolean captured = runtime.captureTestReport(projectId, TelemetryCommandSupport.remainder(commandContext, 3));
        TelemetryCommandSupport.send(
                commandContext,
                captured
                        ? "Telemetry test report queued for " + projectId + "."
                        : "Unable to queue telemetry test report for " + projectId + "."
        );
    }
}
