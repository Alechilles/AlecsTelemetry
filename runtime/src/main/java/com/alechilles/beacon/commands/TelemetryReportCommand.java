package com.alechilles.beacon.commands;

import com.alechilles.beacon.reports.TelemetryReportProjectSelectPage;
import com.alechilles.beacon.reports.TelemetryReportOpenRequest;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Opens the player-facing manual report submission UI.
 */
public final class TelemetryReportCommand extends AbstractPlayerCommand {

    private final TelemetryCommandRuntime runtime;

    public TelemetryReportCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("report", "Open a telemetry issue or suggestion report.");
        this.runtime = runtime;
        setPermissionGroups("Player", "hytale:Admin", "OP", "Admin", "Operator");
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
        String reportKind = TelemetryCommandSupport.token(commandContext, 3);
        if (isReportKind(projectId) && reportKind == null) {
            reportKind = projectId;
            projectId = null;
        }
        if (reportKind == null) {
            reportKind = "issue";
        }
        if (!isReportKind(reportKind)) {
            sendUsage(commandContext);
            return;
        }

        if (projectId == null) {
            openProjectSelector(commandContext, store, ref, playerRef, runtime, reportKind);
            return;
        }

        boolean opened = runtime.openReportPage(
                projectId,
                ref,
                store,
                playerRef,
                new TelemetryReportOpenRequest(reportKind, null, null)
        );
        if (!opened) {
            TelemetryCommandSupport.send(commandContext, "Unable to open report UI for " + projectId + ".");
        }
    }

    private static void openProjectSelector(@Nonnull CommandContext commandContext,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull PlayerRef playerRef,
                                            @Nonnull TelemetryCommandRuntime runtime,
                                            @Nonnull String reportKind) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            TelemetryCommandSupport.send(commandContext, "Unable to open report project selector.");
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new TelemetryReportProjectSelectPage(playerRef, runtime, reportKind)
        );
    }

    private static boolean isReportKind(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return "issue".equals(normalized) || "suggestion".equals(normalized);
    }

    private static void sendUsage(@Nonnull CommandContext commandContext) {
        TelemetryCommandSupport.send(commandContext, "Usage: /telemetry report [project-id] [issue|suggestion]");
    }
}
