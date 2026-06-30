package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Lists registered telemetry projects and their effective routing state.
 */
public final class TelemetryProjectsCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryProjectsCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("projects", "List registered telemetry projects.");
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

        TelemetryRuntimeDiagnostics diagnostics = runtime.diagnostics();
        if (diagnostics.projects().isEmpty()) {
            TelemetryCommandSupport.send(commandContext, "No telemetry-enabled projects were discovered.");
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
