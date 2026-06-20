package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

/**
 * Server profile and public listing commands.
 */
public final class TelemetryServerCommand extends AbstractCommandCollection {

    public TelemetryServerCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("server", "Manage Alec's Telemetry server profile verification.");
        setPermissionGroups("OP", "Admin", "Operator");
        addSubCommand(new TelemetryServerVerifyCommand(runtime));
    }
}
