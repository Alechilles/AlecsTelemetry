package com.alechilles.beacon.commands;

import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.alechilles.beacon.reports.TelemetryReportReviewCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

/**
 * Root `/telemetry` command dispatcher.
 */
public final class TelemetryCommandRoot extends AbstractCommandCollection {
    public static final String ROOT_PERMISSION = "telemetry.command.telemetry";

    public TelemetryCommandRoot(@Nonnull TelemetryCommandRuntime runtime) {
        super("telemetry", "Alec's Telemetry commands.");
        requirePermission(ROOT_PERMISSION);
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        addSubCommand(new TelemetryStatusCommand(runtime));
        addSubCommand(new TelemetryProjectsCommand(runtime));
        addSubCommand(new TelemetryProjectCommand(runtime));
        addSubCommand(new TelemetryConsentCommand(runtime));
        addSubCommand(new TelemetryReportCommand(runtime));
        addSubCommand(new TelemetryReportReviewCommand(runtime));
        addSubCommand(new TelemetryFlushCommand(runtime));
        addSubCommand(new TelemetryServerCommand(runtime));
        addSubCommand(new TelemetryTestCommand(runtime));
    }
}
