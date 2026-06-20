package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
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
 * Forces a stats heartbeat carrying the server claim token, then flushes it.
 */
public final class TelemetryServerVerifyCommand extends AbstractPlayerCommand {

    private final TelemetryCommandRuntime runtime;

    public TelemetryServerVerifyCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("verify", "Send the configured server claim token to ModStats.");
        this.runtime = runtime;
        setPermissionGroups("OP", "Admin", "Operator");
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
        TelemetryServerVerificationResult result = runtime.requestServerVerification();
        switch (result.status()) {
            case QUEUED -> TelemetryCommandSupport.send(commandContext, verificationQueuedMessage(result));
            case MISSING_CLAIM_TOKEN -> TelemetryCommandSupport.send(
                    commandContext,
                    "No server claim token is configured. Create or rotate a server profile in the ModStats portal, then add serverClaimToken to Settings/server-identity.json."
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
