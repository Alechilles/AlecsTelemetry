package com.alechilles.alecstelemetry.commands;

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
 * Captures a manual test report without crashing the server.
 */
public final class TelemetryTestCommand extends AbstractPlayerCommand {

    private final TelemetryCommandRuntime runtime;

    public TelemetryTestCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("test", "Capture a manual telemetry test report. Usage: /telemetry test <project-id> [detail]");
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
