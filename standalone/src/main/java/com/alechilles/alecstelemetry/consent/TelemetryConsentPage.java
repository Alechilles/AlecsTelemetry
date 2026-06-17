package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Operator-facing page for first-run and manual telemetry consent changes.
 */
public final class TelemetryConsentPage extends InteractiveCustomUIPage<TelemetryConsentPage.ConsentEventData> {

    public static final String UI_PATH = "TelemetryConsentPage.ui";

    static final String ACTION_TOGGLE_ALL = "toggle_all";
    static final String ACTION_TOGGLE_GLOBAL_CATEGORY = "toggle_global_category";
    static final String ACTION_TOGGLE_PROJECT = "toggle_project";
    static final String ACTION_TOGGLE_CATEGORY = "toggle_category";
    static final String ACTION_SAVE = "save";
    static final String ACTION_CLOSE = "close";

    private static final int MAX_PROJECT_ROWS = 8;
    private static final String KEY_ACTION = "Action";
    private static final String KEY_PROJECT_ID = "ProjectId";
    private static final String KEY_CATEGORY = "Category";
    private static final String KEY_ENABLED = "Enabled";
    private static final String KEY_ENABLED_LITERAL = "EnabledLiteral";
    private static final String[] CATEGORIES = {
            "crash",
            "error",
            "lifecycle",
            "performance",
            "usage",
            "stats",
            "breadcrumbs"
    };
    private final TelemetryRuntimeService runtimeService;
    private final boolean firstRun;

    public TelemetryConsentPage(@Nonnull PlayerRef playerRef,
                                @Nonnull TelemetryRuntimeService runtimeService,
                                boolean firstRun) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConsentEventData.CODEC);
        this.runtimeService = runtimeService;
        this.firstRun = firstRun;
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
                                @Nonnull ConsentEventData data) {
        switch (data.action == null ? "" : data.action) {
            case ACTION_TOGGLE_ALL -> toggleAll(data.enabledValue());
            case ACTION_TOGGLE_GLOBAL_CATEGORY -> toggleGlobalCategory(data.category, data.enabledValue());
            case ACTION_TOGGLE_PROJECT -> toggleProject(data.projectId, data.enabledValue());
            case ACTION_TOGGLE_CATEGORY -> toggleCategory(data.projectId, data.category, data.enabledValue());
            case ACTION_SAVE -> {
                saveAndClose();
                return;
            }
            case ACTION_CLOSE -> {
                close();
                return;
            }
            default -> {
                return;
            }
        }
        refreshUi();
    }

    private void refreshUi() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands);
        bindEvents(events);
        sendUpdate(commands, events, false);
    }

    private void render(@Nonnull UICommandBuilder commands) {
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.diagnostics());
        List<TelemetryConsentViewModel.ProjectRow> projects = viewModel.projects();
        int visibleRows = Math.min(projects.size(), MAX_PROJECT_ROWS);

        commands.set("#TelemetryConsentSummary.Text", String.join("\n", viewModel.explanationLines()));
        commands.set("#TelemetryConsentAllEnabled.Value", allTelemetryEnabled(projects));
        for (String category : CATEGORIES) {
            commands.set(globalCategoryCheckSelector(category) + ".Value", categoryAllEnabled(projects, category));
        }
        commands.set("#TelemetryConsentPrimary.Text", firstRun ? "Save choices" : "Save");
        commands.set("#TelemetryConsentSecondary.Text", firstRun ? "Not now" : "Close");
        commands.set("#TelemetryConsentEmpty.Visible", projects.isEmpty());
        commands.set("#TelemetryConsentOverflow.Visible", projects.size() > MAX_PROJECT_ROWS);
        commands.set("#TelemetryConsentOverflow.Text", projects.size() > MAX_PROJECT_ROWS
                ? "Showing " + MAX_PROJECT_ROWS + " of " + projects.size() + " telemetry projects."
                : "");

        for (int index = 0; index < MAX_PROJECT_ROWS; index++) {
            boolean visible = index < visibleRows;
            String row = rowSelector(index);
            commands.set(row + ".Visible", visible);
            if (!visible) {
                continue;
            }
            renderProject(commands, index, projects.get(index));
        }
    }

    private void renderProject(@Nonnull UICommandBuilder commands,
                               int index,
                               @Nonnull TelemetryConsentViewModel.ProjectRow project) {
        TelemetryConsentSnapshot consent = project.consent();
        commands.set(rowSelector(index) + " #TelemetryConsentProjectName.Text", project.displayName());
        commands.set(rowSelector(index) + " #TelemetryConsentProjectMeta.Text", project.pluginVersion());
        if (project.hasIconTexture()) {
            commands.set(rowSelector(index) + " #TelemetryConsentProjectIconImage.Background", project.iconTexturePath());
            commands.set(rowSelector(index) + " #TelemetryConsentProjectIconPlaceholder.Visible", false);
        } else {
            commands.set(rowSelector(index) + " #TelemetryConsentProjectIconImage.Background", "#203a5a");
            commands.set(rowSelector(index) + " #TelemetryConsentProjectIconPlaceholder.Visible", true);
        }
        commands.set(projectCheckSelector(index) + ".Value", consent.projectEnabled());
        commands.set(categoryCheckSelector(index, "crash") + ".Value", consent.crashEnabled());
        commands.set(categoryCheckSelector(index, "error") + ".Value", consent.errorEnabled());
        commands.set(categoryCheckSelector(index, "lifecycle") + ".Value", consent.lifecycleEnabled());
        commands.set(categoryCheckSelector(index, "performance") + ".Value", consent.performanceEnabled());
        commands.set(categoryCheckSelector(index, "usage") + ".Value", consent.usageEnabled());
        commands.set(categoryCheckSelector(index, "stats") + ".Value", consent.statsEnabled());
        commands.set(categoryCheckSelector(index, "breadcrumbs") + ".Value", consent.breadcrumbsEnabled());
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.diagnostics());
        List<TelemetryConsentViewModel.ProjectRow> projects = viewModel.projects();

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryConsentAllEnabled",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_ALL).append(KEY_ENABLED, "#TelemetryConsentAllEnabled.Value"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentAllToggleButton",
                EventData.of(KEY_ACTION, ACTION_TOGGLE_ALL)
                        .append(KEY_ENABLED_LITERAL, Boolean.toString(!allTelemetryEnabled(projects))),
                false
        );
        for (String category : CATEGORIES) {
            String categoryCheck = globalCategoryCheckSelector(category);
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    categoryCheck,
                    EventData.of(KEY_ACTION, ACTION_TOGGLE_GLOBAL_CATEGORY)
                            .append(KEY_CATEGORY, category)
                            .append(KEY_ENABLED, categoryCheck + ".Value"),
                    false
            );
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    globalCategoryToggleSelector(category),
                    EventData.of(KEY_ACTION, ACTION_TOGGLE_GLOBAL_CATEGORY)
                            .append(KEY_CATEGORY, category)
                            .append(KEY_ENABLED_LITERAL, Boolean.toString(!categoryAllEnabled(projects, category))),
                    false
            );
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentPrimary",
                EventData.of(KEY_ACTION, ACTION_SAVE),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentSecondary",
                EventData.of(KEY_ACTION, ACTION_CLOSE),
                false
        );

        int visibleRows = Math.min(projects.size(), MAX_PROJECT_ROWS);
        for (int index = 0; index < visibleRows; index++) {
            TelemetryConsentViewModel.ProjectRow project = projects.get(index);
            String projectCheck = projectCheckSelector(index);
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    projectCheck,
                    EventData.of(KEY_ACTION, ACTION_TOGGLE_PROJECT)
                            .append(KEY_PROJECT_ID, project.projectId())
                            .append(KEY_ENABLED, projectCheck + ".Value"),
                    false
            );
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    projectToggleSelector(index),
                    EventData.of(KEY_ACTION, ACTION_TOGGLE_PROJECT)
                            .append(KEY_PROJECT_ID, project.projectId())
                            .append(KEY_ENABLED_LITERAL, Boolean.toString(!project.consent().projectEnabled())),
                    false
            );
            for (String category : CATEGORIES) {
                String categoryCheck = categoryCheckSelector(index, category);
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        categoryCheck,
                        EventData.of(KEY_ACTION, ACTION_TOGGLE_CATEGORY)
                                .append(KEY_PROJECT_ID, project.projectId())
                                .append(KEY_CATEGORY, category)
                                .append(KEY_ENABLED, categoryCheck + ".Value"),
                        false
                );
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        categoryToggleSelector(index, category),
                        EventData.of(KEY_ACTION, ACTION_TOGGLE_CATEGORY)
                                .append(KEY_PROJECT_ID, project.projectId())
                                .append(KEY_CATEGORY, category)
                                .append(KEY_ENABLED_LITERAL, Boolean.toString(!project.consent().categoryEnabled(category))),
                        false
                );
            }
        }
    }

    private void toggleAll(boolean enabled) {
        runtimeService.applyConsentToAll(new TelemetryConsentSnapshot(
                enabled,
                enabled,
                enabled,
                enabled,
                enabled,
                enabled,
                enabled,
                enabled
        ));
    }

    private void toggleProject(@Nullable String projectId, boolean enabled) {
        TelemetryRuntimeDiagnostics.ProjectDiagnostics project = diagnostics(projectId);
        if (project == null) {
            return;
        }
        TelemetryConsentSnapshot current = snapshot(project);
        runtimeService.applyConsent(
                project.projectId(),
                new TelemetryConsentSnapshot(
                        enabled,
                        current.crashEnabled(),
                        current.errorEnabled(),
                        current.lifecycleEnabled(),
                        current.performanceEnabled(),
                        current.usageEnabled(),
                        current.statsEnabled(),
                        current.breadcrumbsEnabled()
                )
        );
    }

    private void toggleGlobalCategory(@Nullable String category, boolean enabled) {
        if (category == null || category.isBlank()) {
            return;
        }
        runtimeService.applyConsentCategoryToAll(category, enabled);
    }

    private void toggleCategory(@Nullable String projectId, @Nullable String category, boolean enabled) {
        TelemetryRuntimeDiagnostics.ProjectDiagnostics project = diagnostics(projectId);
        if (project == null || category == null) {
            return;
        }
        TelemetryConsentSnapshot current = snapshot(project);
        TelemetryConsentSnapshot updated = current.withCategory(category, enabled);
        runtimeService.applyConsent(project.projectId(), updated);
    }

    private void saveAndClose() {
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : runtimeService.diagnostics().projects()) {
            runtimeService.markConsentReviewed(project.projectId());
        }
        close();
    }

    @Nullable
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics(@Nullable String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return runtimeService.projectDiagnostics(projectId);
    }

    @Nonnull
    private static TelemetryConsentSnapshot snapshot(@Nonnull TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        return new TelemetryConsentSnapshot(
                project.enabled(),
                project.crashEnabled(),
                project.errorEnabled(),
                project.lifecycleEnabled(),
                project.performanceEnabled(),
                project.usageEnabled(),
                project.statsEnabled(),
                project.breadcrumbsEnabled()
        );
    }

    private static boolean allTelemetryEnabled(@Nonnull List<TelemetryConsentViewModel.ProjectRow> projects) {
        return !projects.isEmpty() && projects.stream().allMatch(project -> {
            TelemetryConsentSnapshot consent = project.consent();
            return consent.projectEnabled()
                    && consent.crashEnabled()
                    && consent.errorEnabled()
                    && consent.lifecycleEnabled()
                    && consent.performanceEnabled()
                    && consent.usageEnabled()
                    && consent.statsEnabled()
                    && consent.breadcrumbsEnabled();
        });
    }

    private static boolean categoryAllEnabled(@Nonnull List<TelemetryConsentViewModel.ProjectRow> projects,
                                              @Nonnull String category) {
        return !projects.isEmpty() && projects.stream().allMatch(project -> project.consent().categoryEnabled(category));
    }

    @Nonnull
    private static String rowSelector(int index) {
        return "#TelemetryConsentProjectRow" + index;
    }

    @Nonnull
    private static String projectCheckSelector(int index) {
        return rowSelector(index) + " #TelemetryConsentProjectEnabled";
    }

    @Nonnull
    private static String projectToggleSelector(int index) {
        return rowSelector(index) + " #TelemetryConsentProjectToggleButton";
    }

    @Nonnull
    private static String categoryCheckSelector(int index, @Nonnull String category) {
        return rowSelector(index) + " #TelemetryConsent" + selectorToken(category) + "Enabled";
    }

    @Nonnull
    private static String categoryToggleSelector(int index, @Nonnull String category) {
        return rowSelector(index) + " #TelemetryConsent" + selectorToken(category) + "ToggleButton";
    }

    @Nonnull
    private static String globalCategoryCheckSelector(@Nonnull String category) {
        return "#TelemetryConsent" + selectorToken(category) + "AllEnabled";
    }

    @Nonnull
    private static String globalCategoryToggleSelector(@Nonnull String category) {
        return "#TelemetryConsent" + selectorToken(category) + "AllToggleButton";
    }

    @Nonnull
    private static String selectorToken(@Nonnull String category) {
        if ("crash".equals(category)) {
            return "Capture";
        }
        return capitalize(category);
    }

    @Nonnull
    private static String capitalize(@Nonnull String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    public static final class ConsentEventData {
        public static final BuilderCodec<ConsentEventData> CODEC = BuilderCodec.builder(
                        ConsentEventData.class,
                        ConsentEventData::new
                )
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>(KEY_PROJECT_ID, Codec.STRING), (data, value) -> data.projectId = value, data -> data.projectId)
                .addField(new KeyedCodec<>(KEY_CATEGORY, Codec.STRING), (data, value) -> data.category = value, data -> data.category)
                .addField(new KeyedCodec<>(KEY_ENABLED, Codec.BOOLEAN), (data, value) -> data.enabled = value, data -> data.enabled)
                .addField(new KeyedCodec<>(KEY_ENABLED_LITERAL, Codec.STRING), (data, value) -> data.enabledLiteral = value, data -> data.enabledLiteral)
                .build();

        private String action = "";
        private String projectId = "";
        private String category = "";
        private String enabledLiteral = "";
        private boolean enabled;

        private boolean enabledValue() {
            return enabledLiteral == null || enabledLiteral.isBlank()
                    ? enabled
                    : Boolean.parseBoolean(enabledLiteral);
        }
    }
}
