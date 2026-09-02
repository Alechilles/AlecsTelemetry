package com.alechilles.beacon.commands;

import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

/**
 * Server profile and public listing commands.
 */
public final class TelemetryServerCommand extends AbstractCommandCollection {

    public TelemetryServerCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("server", "Manage Beacon server profile verification.");
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        addSubCommand(new TelemetryServerVerifyCommand(runtime));
    }
}
