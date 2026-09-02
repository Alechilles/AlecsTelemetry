package com.alechilles.beacon.commands;

import com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Lists registered Beacon projects and their effective routing state.
 */
public final class TelemetryProjectsCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryProjectsCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("projects", "List registered Beacon projects.");
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

        TelemetryRuntimeDiagnostics diagnostics = runtime.diagnostics();
        if (diagnostics.projects().isEmpty()) {
            TelemetryCommandSupport.send(commandContext, "No Beacon-enabled projects were discovered.");
            return;
        }

        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : diagnostics.projects()) {
            TelemetryCommandSupport.send(
                    commandContext,
                    project.projectId()
                            + ": mode=" + project.runtimeMode()
                            + ", enabled=" + project.enabled()
                            + ", destination=" + project.destinationMode()
                            + ", pending=" + project.pendingReports()
                            + ", override=" + project.overridePresent()
            );
        }
        if (!diagnostics.registrationWarnings().isEmpty()) {
            for (String warning : diagnostics.registrationWarnings()) {
                TelemetryCommandSupport.send(commandContext, "Warning: " + warning);
            }
        }
    }
}
