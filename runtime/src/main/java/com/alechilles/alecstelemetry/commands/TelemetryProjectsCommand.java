package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Lists registered telemetry projects and their effective routing state.
 */
public final class TelemetryProjectsCommand extends AbstractPlayerCommand {

    private final TelemetryCommandRuntime runtime;

    public TelemetryProjectsCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("projects", "List registered telemetry projects.");
        this.runtime = runtime;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
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
