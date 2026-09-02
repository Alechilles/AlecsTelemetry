package com.alechilles.beacon.commands;

import com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Shows high-level runtime status and registration warnings.
 */
public final class TelemetryStatusCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryStatusCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("status", "Show Beacon runtime status.");
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
        TelemetryCommandSupport.send(
                commandContext,
                "Beacon: enabled=" + diagnostics.enabled()
                        + ", projects=" + diagnostics.registeredProjects()
                        + ", loadedMods=" + diagnostics.loadedMods()
                        + ", pending=" + diagnostics.totalPendingReports()
                        + ", flushInProgress=" + diagnostics.flushInProgress()
                        + ", activeCoordinator=" + (runtime.activeCoordinatorProviderId() == null
                        ? "<none>"
                        : runtime.activeCoordinatorProviderId())
                        + ", standaloneOwnsRuntime=" + runtime.ownsActiveCoordinator()
        );
        TelemetryCommandSupport.send(commandContext, "Beacon last flush: " + diagnostics.lastFlushResult());
        if (diagnostics.modsDirectory() != null) {
            TelemetryCommandSupport.send(commandContext, "Beacon mods directory: " + diagnostics.modsDirectory());
        }
        if (!diagnostics.registrationWarnings().isEmpty()) {
            TelemetryCommandSupport.send(
                    commandContext,
                    "Beacon registration warnings=" + diagnostics.registrationWarnings().size()
                            + ". Use /beacon projects or /beacon project <id> for details."
            );
        }
    }
}
