package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Shows detailed diagnostics for one telemetry project.
 */
public final class TelemetryProjectCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryProjectCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("project", "Show diagnostics for one telemetry project.");
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
            TelemetryCommandSupport.send(commandContext, "Usage: /telemetry project <project-id>");
            return;
        }

        TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = runtime.projectDiagnostics(projectId);
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
                        + ", mode=" + diagnostics.runtimeMode()
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
