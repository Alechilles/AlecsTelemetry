package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

/**
 * Root `/telemetry` command dispatcher.
 */
public final class TelemetryCommandRoot extends AbstractCommandCollection {

    public TelemetryCommandRoot(@Nonnull AlecsTelemetry plugin) {
        super("telemetry", "Alec's Telemetry commands.");
        addSubCommand(new TelemetryStatusCommand(plugin));
        addSubCommand(new TelemetryProjectsCommand(plugin));
        addSubCommand(new TelemetryProjectCommand(plugin));
        addSubCommand(new TelemetryFlushCommand(plugin));
        addSubCommand(new TelemetryTestCommand(plugin));
    }
}
