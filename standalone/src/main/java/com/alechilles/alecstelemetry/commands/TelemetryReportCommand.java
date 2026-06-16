package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.alechilles.alecstelemetry.reports.TelemetryReportProjectSelectPage;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
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

    private final AlecsTelemetry plugin;

    public TelemetryReportCommand(@Nonnull AlecsTelemetry plugin) {
        super("report", "Open a telemetry issue or suggestion report.");
        this.plugin = plugin;
        setPermissionGroups("Player", "OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TelemetryRuntimeService runtimeService = plugin == null ? null : plugin.getRuntimeService();
        if (runtimeService == null) {
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
            openProjectSelector(commandContext, store, ref, playerRef, runtimeService, reportKind);
            return;
        }

        boolean opened = runtimeService.openReportPage(
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
                                            @Nonnull TelemetryRuntimeService runtimeService,
                                            @Nonnull String reportKind) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            TelemetryCommandSupport.send(commandContext, "Unable to open report project selector.");
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new TelemetryReportProjectSelectPage(playerRef, runtimeService, reportKind)
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
