package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Forces a stats heartbeat carrying the server claim token, then flushes it.
 */
public final class TelemetryServerVerifyCommand extends CommandBase {

    private final TelemetryCommandRuntime runtime;

    public TelemetryServerVerifyCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("verify", "Send a server claim token to ModStats. Usage: /telemetry server verify [key]");
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
        TelemetryServerVerificationResult result = runtime.requestServerVerification(claimTokenArgument(commandContext.getInputString()));
        switch (result.status()) {
            case QUEUED -> TelemetryCommandSupport.send(commandContext, verificationQueuedMessage(result));
            case MISSING_CLAIM_TOKEN -> TelemetryCommandSupport.send(
                    commandContext,
                    "No server claim token is configured. Create or rotate a server profile in the ModStats portal, then run /telemetry server verify <key>."
            );
            case NO_STATS_PROJECTS -> TelemetryCommandSupport.send(
                    commandContext,
                    "No stats-enabled telemetry projects are available to verify this server."
            );
            case UNAVAILABLE -> TelemetryCommandSupport.send(
                    commandContext,
                    "Server verification is unavailable from this telemetry runtime."
            );
        }
    }

    @Nullable
    static String claimTokenArgument(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length <= 3) {
            return null;
        }
        String token = tokens[3].trim();
        return token.isBlank() ? null : token;
    }

    @Nonnull
    static String verificationQueuedMessage(@Nonnull TelemetryServerVerificationResult result) {
        TelemetryCoreEngine.FlushSummary summary = result.flushSummary();
        if (summary.uploaded() > 0) {
            return "Server verification heartbeat sent. Check the ModStats portal for verification status.";
        }
        if (summary.attempted() == 0) {
            return "Server verification heartbeat was queued but no upload was attempted. Pending telemetry: "
                    + summary.pendingAfter() + ".";
        }
        String failure = summary.lastFailure() == null || summary.lastFailure().isBlank()
                ? "unknown upload failure"
                : summary.lastFailure();
        return "Server verification heartbeat could not be delivered yet. Attempted "
                + summary.attempted()
                + ", sent "
                + summary.uploaded()
                + ", pending "
                + summary.pendingAfter()
                + ". Last failure: "
                + failure;
    }
}
