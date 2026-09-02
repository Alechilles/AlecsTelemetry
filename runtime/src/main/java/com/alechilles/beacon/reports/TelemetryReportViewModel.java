package com.alechilles.beacon.reports;

import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Render-ready state for the player manual report UI.
 */
public record TelemetryReportViewModel(@Nonnull String projectId,
                                       @Nonnull String projectDisplayName,
                                       @Nonnull String selectedKind,
                                       boolean issueEnabled,
                                       boolean suggestionEnabled,
                                       boolean showKindSelector,
                                       boolean showContact,
                                       boolean showCurrentServerLog,
                                       boolean showPreviousServerLog,
                                       boolean showLoadedModList,
                                       boolean showDiagnostics,
                                       boolean manualReviewRequired,
                                       @Nonnull List<FieldRow> fields) {

    @Nonnull
    public static TelemetryReportViewModel from(@Nonnull TelemetryProjectRegistration project,
                                                @Nonnull TelemetryRuntimeSettings.ManualReportSettings settings,
                                                @Nonnull TelemetryReportOpenRequest request) {
        TelemetryProjectDescriptor.ManualReportOptions reports = project.descriptor().reports();
        boolean issueEnabled = reports.enabled() && reports.issue().enabled();
        boolean suggestionEnabled = reports.enabled() && reports.suggestion().enabled();
        String requestedKind = request.normalizedKind();
        String selectedKind = "suggestion".equals(requestedKind) && suggestionEnabled ? "suggestion" : "issue";
        if ("issue".equals(selectedKind) && !issueEnabled && suggestionEnabled) {
            selectedKind = "suggestion";
        }
        TelemetryProjectDescriptor.ManualReportForm form = "suggestion".equals(selectedKind)
                ? reports.suggestion()
                : reports.issue();
        boolean attachmentsEnabled = reports.enabled() && settings.enabled();
        return new TelemetryReportViewModel(
                project.projectId(),
                project.displayName(),
                selectedKind,
                issueEnabled,
                suggestionEnabled,
                issueEnabled && suggestionEnabled,
                reports.enabled() && reports.contact().enabled() && settings.allowContact(),
                attachmentsEnabled && reports.attachments().currentServerLog() && settings.allowCurrentServerLog(),
                attachmentsEnabled && reports.attachments().previousServerLog() && settings.allowPreviousServerLog(),
                attachmentsEnabled && settings.allowLoadedModList(),
                attachmentsEnabled && settings.allowDiagnostics(),
                reports.enabled() && settings.manualReviewRequired(),
                form.fields().stream().map(FieldRow::from).toList()
        );
    }

    public boolean canSubmit() {
        return issueEnabled || suggestionEnabled;
    }

    public record FieldRow(@Nonnull String key,
                           @Nonnull String type,
                           @Nonnull String label,
                           boolean required,
                           @Nonnull List<String> values,
                           int maxLength) {

        @Nonnull
        private static FieldRow from(@Nonnull TelemetryProjectDescriptor.ManualReportField field) {
            return new FieldRow(
                    field.key(),
                    field.type().key(),
                    field.label(),
                    field.required(),
                    field.values(),
                    field.maxLength()
            );
        }
    }
}
