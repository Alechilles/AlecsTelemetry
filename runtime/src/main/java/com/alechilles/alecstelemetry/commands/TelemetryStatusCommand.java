package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Shows high-level runtime status and registration warnings.
 */
public final class TelemetryStatusCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryStatusCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("status", "Show Alec's Telemetry runtime status.");
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

        TelemetryRuntimeDiagnostics diagnostics = runtime.diagnostics();
        TelemetryCommandSupport.send(
                commandContext,
                "Telemetry: enabled=" + diagnostics.enabled()
                        + ", projects=" + diagnostics.registeredProjects()
                        + ", loadedMods=" + diagnostics.loadedMods()
                        + ", pending=" + diagnostics.totalPendingReports()
                        + ", flushInProgress=" + diagnostics.flushInProgress()
                        + ", activeCoordinator=" + (runtime.activeCoordinatorProviderId() == null
                        ? "<none>"
                        : runtime.activeCoordinatorProviderId())
                        + ", standaloneOwnsRuntime=" + runtime.ownsActiveCoordinator()
        );
        TelemetryCommandSupport.send(commandContext, "Telemetry last flush: " + diagnostics.lastFlushResult());
        TelemetryCommandSupport.send(
                commandContext,
                "Telemetry Spark profiler: " + runtime.sparkProfilerDiagnostics().commandSummary()
                        + ". Use /telemetry profiler status, /telemetry profiler top, /telemetry profiler history, or /telemetry profiler signals <project-id> for detail."
        );
        if (diagnostics.modsDirectory() != null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry mods directory: " + diagnostics.modsDirectory());
        }
        if (!diagnostics.registrationWarnings().isEmpty()) {
            TelemetryCommandSupport.send(
                    commandContext,
                    "Telemetry registration warnings=" + diagnostics.registrationWarnings().size()
                            + ". Use /telemetry projects or /telemetry project <id> for details."
            );
        }
    }
}
