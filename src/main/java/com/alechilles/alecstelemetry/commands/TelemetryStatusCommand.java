package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
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
 * Shows high-level runtime status and registration warnings.
 */
public final class TelemetryStatusCommand extends AbstractPlayerCommand {

    private final AlecsTelemetry plugin;

    public TelemetryStatusCommand(@Nonnull AlecsTelemetry plugin) {
        super("status", "Show Alec's Telemetry runtime status.");
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

        TelemetryRuntimeDiagnostics diagnostics = runtimeService.diagnostics();
        TelemetryCommandSupport.send(
                commandContext,
                "Telemetry: enabled=" + diagnostics.enabled()
                        + ", projects=" + diagnostics.registeredProjects()
                        + ", loadedMods=" + diagnostics.loadedMods()
                        + ", pending=" + diagnostics.totalPendingReports()
                        + ", flushInProgress=" + diagnostics.flushInProgress()
        );
        TelemetryCommandSupport.send(commandContext, "Telemetry last flush: " + diagnostics.lastFlushResult());
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
