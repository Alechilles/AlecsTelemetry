package com.alechilles.beacon.reports;

import com.alechilles.beacon.commands.TelemetryCommandSupport;
import com.alechilles.beacon.report.ManualReportEnvelope;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Server-owner review commands for player-submitted manual reports.
 */
public final class TelemetryReportReviewCommand extends CommandBase {

    private static final int MAX_LISTED_REPORTS = 20;

    private final TelemetryCommandRuntime runtime;

    public TelemetryReportReviewCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("reports", "Review player-submitted telemetry reports.");
        this.runtime = runtime;
        setPermissionGroups("hytale:Admin", "OP", "Admin", "Operator");
        setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext commandContext) {
        if (runtime == null) {
            TelemetryCommandSupport.send(commandContext, "Beacon runtime service is unavailable.");
            return;
        }
        String action = TelemetryCommandSupport.token(commandContext, 2);
        if (action == null) {
            sendUsage(commandContext);
            return;
        }
        switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "pending" -> listPending(commandContext, runtime);
            case "approve" -> approve(commandContext, runtime);
            case "reject" -> reject(commandContext, runtime);
            case "submitted" -> listSubmitted(commandContext, runtime);
            default -> sendUsage(commandContext);
        }
    }

    private static void listPending(@Nonnull CommandContext commandContext,
                                    @Nonnull TelemetryCommandRuntime runtime) {
        List<ManualReportEnvelope> reports = runtime.manualReportsForReview(MAX_LISTED_REPORTS);
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
                                @Nonnull TelemetryCommandRuntime runtime) {
        String reportId = TelemetryCommandSupport.token(commandContext, 3);
        if (reportId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /beacon reports approve <report-id>");
            return;
        }
        if (runtime.approveManualReport(reportId)) {
            TelemetryCommandSupport.send(commandContext, "Approved manual report " + reportId + " and queued it for upload.");
            return;
        }
        TelemetryCommandSupport.send(commandContext, "No reviewed manual report matched ID " + reportId + ".");
    }

    private static void reject(@Nonnull CommandContext commandContext,
                               @Nonnull TelemetryCommandRuntime runtime) {
        String reportId = TelemetryCommandSupport.token(commandContext, 3);
        if (reportId == null) {
            TelemetryCommandSupport.send(commandContext, "Usage: /beacon reports reject <report-id>");
            return;
        }
        if (runtime.rejectManualReport(reportId)) {
            TelemetryCommandSupport.send(commandContext, "Rejected manual report " + reportId + ".");
            return;
        }
        TelemetryCommandSupport.send(commandContext, "No reviewed manual report matched ID " + reportId + ".");
    }

    private static void listSubmitted(@Nonnull CommandContext commandContext,
                                      @Nonnull TelemetryCommandRuntime runtime) {
        List<String> lines = runtime.submittedManualReportAuditLines(MAX_LISTED_REPORTS);
        if (lines.isEmpty()) {
            TelemetryCommandSupport.send(commandContext, "No submitted manual report audit entries are available.");
            return;
        }
        for (String line : lines) {
            TelemetryCommandSupport.send(commandContext, line);
        }
    }

    private static void sendUsage(@Nonnull CommandContext commandContext) {
        TelemetryCommandSupport.send(commandContext, "Usage: /beacon reports <pending|approve|reject|submitted> [report-id]");
    }
}
