package com.alechilles.alecstelemetry.reports;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
import com.alechilles.alecstelemetry.report.ManualReportKind;
import com.alechilles.alecstelemetry.report.ManualReportSubmission;
import com.alechilles.alecstelemetry.report.PlayerReportRuntimeContext;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-facing manual issue/suggestion report submission page.
 */
public final class TelemetryReportPage extends InteractiveCustomUIPage<TelemetryReportPage.ReportEventData> {

    public static final String UI_PATH = "TelemetryReportPage.ui";

    static final String ACTION_SET_KIND = "set_kind";
    static final String ACTION_SUBMIT = "submit";
    static final String ACTION_CANCEL = "cancel";
    static final String ACTION_TOGGLE_CURRENT_LOG = "toggle_current_log";
    static final String ACTION_TOGGLE_PREVIOUS_LOG = "toggle_previous_log";
    static final String ACTION_TOGGLE_LOADED_MODS = "toggle_loaded_mods";
    static final String ACTION_TOGGLE_DIAGNOSTICS = "toggle_diagnostics";
    static final String ACTION_UPDATE_TITLE = "update_title";
    static final String ACTION_UPDATE_DESCRIPTION = "update_description";
    static final String ACTION_UPDATE_CONTACT = "update_contact";
    static final String ACTION_UPDATE_FIELD = "update_field";

    private static final int MAX_CUSTOM_FIELDS = 8;
    private static final String KEY_ACTION = "Action";
    private static final String KEY_KIND = "Kind";
    private static final String KEY_TITLE = "Title";
    private static final String KEY_DESCRIPTION = "Description";
    private static final String KEY_CONTACT = "Contact";
    private static final String KEY_ENABLED = "Enabled";
    private static final String KEY_FIELD_INDEX = "FieldIndex";
    private static final String KEY_FIELD_VALUE = "FieldValue";

    private final TelemetryRuntimeService runtimeService;
    private final PlayerRef playerRef;
    private final String projectId;
    private String selectedKind;
    private String title;
    private String description;
    private String contact;
    private boolean includeCurrentServerLog;
    private boolean includePreviousServerLog;
    private boolean includeLoadedMods = true;
    private boolean includeDiagnostics = true;
    private Map<String, String> fieldInputs = Map.of();
    private List<String> validationErrors = List.of();

    public TelemetryReportPage(@Nonnull PlayerRef playerRef,
                               @Nonnull TelemetryRuntimeService runtimeService,
                               @Nonnull String projectId,
                               @Nonnull TelemetryReportOpenRequest request) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ReportEventData.CODEC);
        this.runtimeService = runtimeService;
        this.playerRef = playerRef;
        this.projectId = projectId;
        this.selectedKind = request.normalizedKind();
        this.title = request.initialTitle() == null ? "" : request.initialTitle();
        this.description = request.initialDescription() == null ? "" : request.initialDescription();
        this.contact = "";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        commands.append(UI_PATH);
        render(commands);
        bindEvents(events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ReportEventData data) {
        switch (data.action == null ? "" : data.action) {
            case ACTION_SET_KIND -> selectedKind = "suggestion".equalsIgnoreCase(data.kind) ? "suggestion" : "issue";
            case ACTION_TOGGLE_CURRENT_LOG -> includeCurrentServerLog = data.enabled;
            case ACTION_TOGGLE_PREVIOUS_LOG -> includePreviousServerLog = data.enabled;
            case ACTION_TOGGLE_LOADED_MODS -> includeLoadedMods = data.enabled;
            case ACTION_TOGGLE_DIAGNOSTICS -> includeDiagnostics = data.enabled;
            case ACTION_UPDATE_TITLE -> {
                title = normalizeInput(data.title);
                return;
            }
            case ACTION_UPDATE_DESCRIPTION -> {
                description = normalizeInput(data.description);
                return;
            }
            case ACTION_UPDATE_CONTACT -> {
                contact = normalizeInput(data.contact);
                return;
            }
            case ACTION_UPDATE_FIELD -> {
                updateFieldInput(data);
                return;
            }
            case ACTION_SUBMIT -> {
                submit(ref, store, data);
                return;
            }
            case ACTION_CANCEL -> {
                close();
                return;
            }
            default -> {
                return;
            }
        }
        refreshUi();
    }

    private void submit(@Nonnull Ref<EntityStore> ref,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull ReportEventData data) {
        ManualReportEnvelope.CreateResult result = runtimeService.submitManualReport(
                projectId,
                new ManualReportSubmission(
                        "suggestion".equals(selectedKind) ? ManualReportKind.SUGGESTION : ManualReportKind.ISSUE,
                        title,
                        description,
                        contact,
                        formValues(fieldInputs),
                        includeCurrentServerLog,
                        includePreviousServerLog,
                        includeLoadedMods,
                        includeDiagnostics,
                        true
                ),
                PlayerReportRuntimeContext.UNKNOWN
        );
        if (!result.accepted()) {
            validationErrors = result.validationErrors();
            refreshUi();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        ManualReportEnvelope envelope = result.envelope();
        String status = viewModel().manualReviewRequired() ? "Waiting for server owner review" : "Queued for upload";
        player.getPageManager().openCustomPage(
                ref,
                store,
                new TelemetryReportReceiptPage(playerRef, runtimeService, envelope, status)
        );
    }

    private void refreshUi() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands);
        bindEvents(events);
        sendUpdate(commands, events, false);
    }

    private void render(@Nonnull UICommandBuilder commands) {
        TelemetryReportViewModel viewModel = viewModel();
        selectedKind = viewModel.selectedKind();
        commands.set("#TelemetryReportProject.Text", viewModel.projectDisplayName());
        commands.set("#TelemetryReportKindSelector.Visible", viewModel.showKindSelector());
        commands.set("#TelemetryReportIssueSelected.Visible", "issue".equals(viewModel.selectedKind()));
        commands.set("#TelemetryReportSuggestionSelected.Visible", "suggestion".equals(viewModel.selectedKind()));
        commands.set("#TelemetryReportReviewWarning.Visible", viewModel.manualReviewRequired());
        commands.set("#TelemetryReportError.Text", String.join("\n", validationErrors));
        commands.set("#TelemetryReportError.Visible", !validationErrors.isEmpty());
        commands.set("#TelemetryReportTitle.Value", title);
        commands.set("#TelemetryReportDescription.Value", description);
        commands.set("#TelemetryReportContactRow.Visible", viewModel.showContact());
        commands.set("#TelemetryReportContact.Value", contact);
        commands.set("#TelemetryReportCurrentLogRow.Visible", viewModel.showCurrentServerLog());
        commands.set("#TelemetryReportCurrentLog.Value", includeCurrentServerLog && viewModel.showCurrentServerLog());
        commands.set("#TelemetryReportPreviousLogRow.Visible", viewModel.showPreviousServerLog());
        commands.set("#TelemetryReportPreviousLog.Value", includePreviousServerLog && viewModel.showPreviousServerLog());
        commands.set("#TelemetryReportLoadedModsRow.Visible", viewModel.showLoadedModList());
        commands.set("#TelemetryReportLoadedMods.Value", includeLoadedMods && viewModel.showLoadedModList());
        commands.set("#TelemetryReportDiagnosticsRow.Visible", viewModel.showDiagnostics());
        commands.set("#TelemetryReportDiagnostics.Value", includeDiagnostics && viewModel.showDiagnostics());
        commands.set("#TelemetryReportSubmit.Visible", viewModel.canSubmit());
        renderFields(commands, viewModel.fields());
    }

    private void renderFields(@Nonnull UICommandBuilder commands,
                              @Nonnull List<TelemetryReportViewModel.FieldRow> fields) {
        int visibleRows = Math.min(MAX_CUSTOM_FIELDS, fields.size());
        for (int index = 0; index < MAX_CUSTOM_FIELDS; index++) {
            String row = "#TelemetryReportFieldRow" + index;
            boolean visible = index < visibleRows;
            commands.set(row + ".Visible", visible);
            if (!visible) {
                continue;
            }
            TelemetryReportViewModel.FieldRow field = fields.get(index);
            commands.set(row + " #TelemetryReportFieldLabel.Text", field.label() + (field.required() ? " *" : ""));
            boolean dropdown = isDropdownField(field);
            commands.set(row + " #TelemetryReportFieldTextValue.Visible", !dropdown);
            commands.set(row + " #TelemetryReportFieldDropdownValue.Visible", dropdown);
            if (dropdown) {
                commands.set(row + " #TelemetryReportFieldDropdownValue.Entries", dropdownEntries(field.values()));
                commands.set(row + " #TelemetryReportFieldDropdownValue.Value", normalizeDropdownValue(fieldInputs.get(field.key()), field.values()));
            } else {
                commands.set(row + " #TelemetryReportFieldTextValue.Value", fieldInputs.getOrDefault(field.key(), ""));
                commands.set(row + " #TelemetryReportFieldTextValue.PlaceholderText", "");
                commands.set(row + " #TelemetryReportFieldTextValue.MaxLength", field.maxLength());
            }
        }
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportIssueButton",
                EventData.of(KEY_ACTION, ACTION_SET_KIND).append(KEY_KIND, "issue"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportSuggestionButton",
                EventData.of(KEY_ACTION, ACTION_SET_KIND).append(KEY_KIND, "suggestion"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryReportCurrentLog",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_CURRENT_LOG).append(KEY_ENABLED, "#TelemetryReportCurrentLog.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryReportPreviousLog",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_PREVIOUS_LOG).append(KEY_ENABLED, "#TelemetryReportPreviousLog.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryReportLoadedMods",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_LOADED_MODS).append(KEY_ENABLED, "#TelemetryReportLoadedMods.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryReportDiagnostics",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_DIAGNOSTICS).append(KEY_ENABLED, "#TelemetryReportDiagnostics.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportSubmit",
                EventData.of(KEY_ACTION, ACTION_SUBMIT),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportCancel",
                EventData.of(KEY_ACTION, ACTION_CANCEL),
                false
        );
        bindInputEvents(events);
    }

    private void bindInputEvents(@Nonnull UIEventBuilder events) {
        bindValueUpdate(events, "#TelemetryReportTitle", EventData.of(KEY_ACTION, ACTION_UPDATE_TITLE)
                .append(KEY_TITLE, "#TelemetryReportTitle.Value"));
        bindValueUpdate(events, "#TelemetryReportDescription", EventData.of(KEY_ACTION, ACTION_UPDATE_DESCRIPTION)
                .append(KEY_DESCRIPTION, "#TelemetryReportDescription.Value"));
        bindValueUpdate(events, "#TelemetryReportContact", EventData.of(KEY_ACTION, ACTION_UPDATE_CONTACT)
                .append(KEY_CONTACT, "#TelemetryReportContact.Value"));
        for (int index = 0; index < MAX_CUSTOM_FIELDS; index++) {
            String row = "#TelemetryReportFieldRow" + index;
            bindValueUpdate(
                    events,
                    row + " #TelemetryReportFieldTextValue",
                    EventData.of(KEY_ACTION, ACTION_UPDATE_FIELD)
                            .append(KEY_FIELD_INDEX, String.valueOf(index))
                            .append(KEY_FIELD_VALUE, row + " #TelemetryReportFieldTextValue.Value")
            );
            bindValueUpdate(
                    events,
                    row + " #TelemetryReportFieldDropdownValue",
                    EventData.of(KEY_ACTION, ACTION_UPDATE_FIELD)
                            .append(KEY_FIELD_INDEX, String.valueOf(index))
                            .append(KEY_FIELD_VALUE, row + " #TelemetryReportFieldDropdownValue.Value")
            );
        }
    }

    private static void bindValueUpdate(@Nonnull UIEventBuilder events,
                                        @Nonnull String selector,
                                        @Nonnull EventData eventData) {
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, eventData, false);
    }

    @Nonnull
    private TelemetryReportViewModel viewModel() {
        TelemetryProjectRegistration project = runtimeService.findManualReportProject(projectId);
        if (project == null) {
            throw new IllegalStateException("Unknown telemetry project: " + projectId);
        }
        return TelemetryReportViewModel.from(
                project,
                runtimeService.manualReportSettings(),
                new TelemetryReportOpenRequest(selectedKind, title, description)
        );
    }

    @Nonnull
    private Map<String, Object> formValues(@Nonnull Map<String, String> inputValues) {
        TelemetryProjectRegistration project = runtimeService.findManualReportProject(projectId);
        if (project == null) {
            return Map.of();
        }
        TelemetryProjectDescriptor.ManualReportForm form = "suggestion".equals(selectedKind)
                ? project.descriptor().reports().suggestion()
                : project.descriptor().reports().issue();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (TelemetryProjectDescriptor.ManualReportField field : form.fields()) {
            String value = inputValues.get(field.key());
            if (value != null && !value.isBlank()) {
                values.put(field.key(), isDropdownField(field) ? normalizeDropdownValue(value, field.values()) : value.trim());
                continue;
            }
            if (field.type() == TelemetryProjectDescriptor.ManualReportFieldType.ENUM && !field.values().isEmpty()) {
                values.put(field.key(), field.values().getFirst());
            }
        }
        return Map.copyOf(values);
    }

    @Nonnull
    private static String normalize(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void updateFieldInput(@Nonnull ReportEventData data) {
        int index = parseFieldIndex(data.fieldIndex);
        TelemetryReportViewModel viewModel = viewModel();
        if (index < 0 || index >= Math.min(MAX_CUSTOM_FIELDS, viewModel.fields().size())) {
            return;
        }
        TelemetryReportViewModel.FieldRow field = viewModel.fields().get(index);
        String value = normalizeNullable(data.fieldValue);
        LinkedHashMap<String, String> values = new LinkedHashMap<>(fieldInputs);
        if (value == null) {
            values.remove(field.key());
        } else if (isDropdownField(field)) {
            values.put(field.key(), normalizeDropdownValue(value, field.values()));
        } else {
            values.put(field.key(), value);
        }
        fieldInputs = Map.copyOf(values);
    }

    private static int parseFieldIndex(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static boolean isDropdownField(@Nonnull TelemetryReportViewModel.FieldRow field) {
        return "enum".equals(field.type()) && !field.values().isEmpty();
    }

    private static boolean isDropdownField(@Nonnull TelemetryProjectDescriptor.ManualReportField field) {
        return field.type() == TelemetryProjectDescriptor.ManualReportFieldType.ENUM && !field.values().isEmpty();
    }

    @Nonnull
    private static String normalizeDropdownValue(@Nullable String value,
                                                 @Nonnull List<String> options) {
        if (options.isEmpty()) {
            return "";
        }
        if (value != null) {
            for (String option : options) {
                if (option.equalsIgnoreCase(value.trim())) {
                    return option;
                }
            }
        }
        return options.getFirst();
    }

    @Nonnull
    private static List<DropdownEntryInfo> dropdownEntries(@Nonnull List<String> options) {
        ArrayList<DropdownEntryInfo> entries = new ArrayList<>(options.size());
        for (String option : options) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(option), option));
        }
        return entries;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static String normalizeInput(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static final class ReportEventData {
        public static final BuilderCodec<ReportEventData> CODEC = BuilderCodec.builder(
                        ReportEventData.class,
                        ReportEventData::new
                )
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>(KEY_KIND, Codec.STRING), (data, value) -> data.kind = value, data -> data.kind)
                .addField(new KeyedCodec<>(KEY_TITLE, Codec.STRING), (data, value) -> data.title = value, data -> data.title)
                .addField(new KeyedCodec<>(KEY_DESCRIPTION, Codec.STRING), (data, value) -> data.description = value, data -> data.description)
                .addField(new KeyedCodec<>(KEY_CONTACT, Codec.STRING), (data, value) -> data.contact = value, data -> data.contact)
                .addField(new KeyedCodec<>(KEY_ENABLED, Codec.BOOLEAN), (data, value) -> data.enabled = value, data -> data.enabled)
                .addField(new KeyedCodec<>(KEY_FIELD_INDEX, Codec.STRING), (data, value) -> data.fieldIndex = value, data -> data.fieldIndex)
                .addField(new KeyedCodec<>(KEY_FIELD_VALUE, Codec.STRING), (data, value) -> data.fieldValue = value, data -> data.fieldValue)
                .build();

        private String action = "";
        private String kind = "";
        private String title = "";
        private String description = "";
        private String contact = "";
        private boolean enabled;
        private String fieldIndex = "";
        private String fieldValue = "";
    }
}
