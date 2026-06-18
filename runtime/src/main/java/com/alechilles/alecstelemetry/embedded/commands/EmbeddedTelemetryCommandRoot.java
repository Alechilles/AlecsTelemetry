package com.alechilles.alecstelemetry.embedded.commands;

import com.alechilles.alecstelemetry.consent.TelemetryConsentCoordinator;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

/**
 * Embedded-mode `/telemetry` command dispatcher.
 */
public final class EmbeddedTelemetryCommandRoot extends AbstractCommandCollection {

    public EmbeddedTelemetryCommandRoot(@Nonnull EmbeddedTelemetryService service) {
        super("telemetry", "Alec's Telemetry commands.");
        requirePermission(TelemetryConsentCoordinator.ROOT_PERMISSION);
        setPermissionGroups("OP", "Admin", "Operator");
        addSubCommand(new EmbeddedTelemetryStatusCommand(service));
        addSubCommand(new EmbeddedTelemetryProjectsCommand(service));
        addSubCommand(new EmbeddedTelemetryProjectCommand(service));
        addSubCommand(new EmbeddedTelemetryConsentCommand(service));
        addSubCommand(new EmbeddedTelemetryFlushCommand(service));
        addSubCommand(new EmbeddedTelemetryTestCommand(service));
    }
}
