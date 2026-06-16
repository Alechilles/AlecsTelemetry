package com.alechilles.alecstelemetry.reports;

import com.alechilles.alecstelemetry.AlecsTelemetry;
import com.alechilles.alecstelemetry.commands.TelemetryCommandSupport;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Server-owner review commands for player-submitted manual reports.
 */
public final class TelemetryReportReviewCommand extends AbstractPlayerCommand {

    private static final int MAX_LISTED_REPORTS = 20;

    private final AlecsTelemetry plugin;

    public TelemetryReportReviewCommand(@Nonnull AlecsTelemetry plugin) {
        super("reports", "Review player-submitted telemetry reports.");
        this.plugin = plugin;
        setPermissionGroups("OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TelemetryRuntimeService runtimeService = plugin.getRuntimeService();
        if (runtimeService == null) {
            TelemetryCommandSupport.send(commandContext, "Telemetry runtime service is unavailable.");
            return;
        }
        String action = TelemetryCommandSupport.token(commandContext, 2);
        if (action == null) {
            sendUsage(commandContext);
            return;
        }
        switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "pending" -> listPending(commandContext, runtimeService);
            case "approve" -> approve(commandContext, runtimeService);
            case "reject" -> reject(commandContext, runtimeService);
            case "submitted" -> listSubmitted(commandContext, runtimeService);
            default -> sendUsage(commandContext);
        }
    }

    private static void listPending(@Nonnull CommandContext commandContext,
                                    @Nonnull TelemetryRuntimeService runtimeService) {
        List<ManualReportEnvelope> reports = runtimeService.manualReportsForReview(MAX_LISTED_REPORTS);
        if (reports.isEmpty()) {
            TelemetryCommandSupport.send(commandContext, "No manual reports are waiting for review.");
            return;
        }
        for (ManualReportEnvelope report : reports) {
            TelemetryCommandSupport.send(
                    commandContext,
                    report.reportId()
                            + " | " + report.projectId()
                            + " | " + report.reportKind()
                            + " | " + report.title()
                            + " | attachments=" + report.attachmentManifests().size()
                            + " | submitted=" + report.submittedAtUtc()
            );
        }
    }

    private static void approve(@Nonnull CommandContext commandContext,
                                @Nonnull TelemetryRuntimeService runtimeService) {
        String reportId = TelemetryCommandSupport.token(commandContext, 3);
        if (reportId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /telemetry reports approve <report-id>");
            return;
        }
        if (runtimeService.approveManualReport(reportId)) {
            TelemetryCommandSupport.send(commandContext, "Approved manual report " + reportId + " and queued it for upload.");
            return;
        }
        TelemetryCommandSupport.send(commandContext, "No reviewed manual report matched ID " + reportId + ".");
    }

    private static void reject(@Nonnull CommandContext commandContext,
                               @Nonnull TelemetryRuntimeService runtimeService) {
        String reportId = TelemetryCommandSupport.token(commandContext, 3);
        if (reportId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /telemetry reports reject <report-id>");
            return;
        }
        if (runtimeService.rejectManualReport(reportId)) {
            TelemetryCommandSupport.send(commandContext, "Rejected manual report " + reportId + ".");
            return;
        }
        TelemetryCommandSupport.send(commandContext, "No reviewed manual report matched ID " + reportId + ".");
    }

    private static void listSubmitted(@Nonnull CommandContext commandContext,
                                      @Nonnull TelemetryRuntimeService runtimeService) {
        List<String> lines = runtimeService.submittedManualReportAuditLines(MAX_LISTED_REPORTS);
        if (lines.isEmpty()) {
            TelemetryCommandSupport.send(commandContext, "No submitted manual report audit entries are available.");
            return;
        }
        for (String line : lines) {
            TelemetryCommandSupport.send(commandContext, line);
        }
    }

    private static void sendUsage(@Nonnull CommandContext commandContext) {
        TelemetryCommandSupport.send(commandContext, "Usage: /telemetry reports <pending|approve|reject|submitted> [report-id]");
    }
}
