package com.alechilles.alecstelemetry.embedded.commands;

import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Captures an embedded-mode test report.
 */
final class EmbeddedTelemetryTestCommand extends AbstractPlayerCommand {

    private final EmbeddedTelemetryService service;

    EmbeddedTelemetryTestCommand(@Nonnull EmbeddedTelemetryService service) {
        super("test", "Capture a manual telemetry test report. Usage: /telemetry test <project-id> [detail]");
        this.service = service;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String projectId = EmbeddedTelemetryCommandSupport.token(commandContext, 2);
        if (projectId == null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Usage: /telemetry test <project-id> [detail]");
            return;
        }
        if (service.commandProject(projectId) == null) {
            EmbeddedTelemetryCommandSupport.send(commandContext, "Unknown telemetry project: " + projectId);
            return;
        }

        boolean captured = service.commandCaptureTestReport(
                projectId,
                EmbeddedTelemetryCommandSupport.remainder(commandContext, 3)
        );
        EmbeddedTelemetryCommandSupport.send(
                commandContext,
                captured
                        ? "Telemetry test report queued for " + projectId + "."
                        : "Unable to queue telemetry test report for " + projectId + "."
        );
    }
}
