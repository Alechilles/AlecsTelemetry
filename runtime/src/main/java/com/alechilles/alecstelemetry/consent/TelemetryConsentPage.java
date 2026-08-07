package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentRuntime;
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
    static final String PROJECT_ROW_UI_PATH = "TelemetryConsentProjectRow.ui";

    static final String ACTION_TOGGLE_ALL = TelemetryConsentUiContract.ACTION_TOGGLE_ALL;
    static final String ACTION_TOGGLE_GLOBAL_CATEGORY = TelemetryConsentUiContract.ACTION_TOGGLE_GLOBAL_CATEGORY;
    static final String ACTION_TOGGLE_PROJECT = TelemetryConsentUiContract.ACTION_TOGGLE_PROJECT;
    static final String ACTION_TOGGLE_CATEGORY = TelemetryConsentUiContract.ACTION_TOGGLE_CATEGORY;
    static final String ACTION_SHOW_PRIVACY_DISCLAIMER = TelemetryConsentUiContract.ACTION_SHOW_PRIVACY_DISCLAIMER;
    static final String ACTION_HIDE_PRIVACY_DISCLAIMER = TelemetryConsentUiContract.ACTION_HIDE_PRIVACY_DISCLAIMER;
    static final String ACTION_SAVE = TelemetryConsentUiContract.ACTION_SAVE;
    static final String ACTION_CLOSE = TelemetryConsentUiContract.ACTION_CLOSE;

    private static final String PRIVACY_DISCLAIMER_TEXT = String.join("\n\n",
            "Alec's Telemetry is designed to report anonymous usage data. Under its default configuration, and in officially sanctioned mods such as other mods in the Alec's mod line, it does not report personally identifiable information.",
            "However, Alec's Telemetry includes highly customizable reporting systems. Because third-party mod authors can configure those systems themselves, Alec cannot guarantee that every mod using Alec's Telemetry follows the intended privacy standards.",
            "Reporting non-optional personally identifiable information through Alec's Telemetry is against Alec's Telemetry policy. If you believe a mod author is using Alec's Telemetry to collect identifiable user information without a clear, optional choice, please report it directly to Alec at discord.gg/uP5bNTVSze and it will be promptly investigated."
    );
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
    private final TelemetryConsentRuntime runtimeService;
    private final boolean firstRun;
    private boolean privacyDisclaimerVisible;

    public TelemetryConsentPage(@Nonnull PlayerRef playerRef,
                                @Nonnull TelemetryConsentRuntime runtimeService,
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
        render(commands, events);
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
            case ACTION_SHOW_PRIVACY_DISCLAIMER -> privacyDisclaimerVisible = true;
            case ACTION_HIDE_PRIVACY_DISCLAIMER -> privacyDisclaimerVisible = false;
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
        render(commands, events);
        bindEvents(events);
        sendUpdate(commands, events, false);
    }

    private void render(@Nonnull UICommandBuilder commands,
                        @Nonnull UIEventBuilder events) {
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.consentDiagnostics());
        List<TelemetryConsentViewModel.ProjectRow> projects = viewModel.projects();

        commands.set("#TelemetryConsentSummary.Text", String.join("\n", viewModel.explanationLines()));
        commands.set("#TelemetryConsentAllEnabled.Value", allTelemetryEnabled(projects));
        for (String category : CATEGORIES) {
            boolean supported = categoryAnySupported(projects, category);
            commands.set(globalCategoryCheckSelector(category) + ".Value", categoryAllEnabled(projects, category));
            commands.set(globalCategoryCheckSelector(category) + ".Visible", supported);
            commands.set(globalCategoryToggleSelector(category) + ".Visible", supported);
            commands.set(globalCategoryUnsupportedSelector(category) + ".Visible", !supported);
        }
        commands.set("#TelemetryConsentPrimary.Text", firstRun ? "Save choices" : "Save");
        commands.set("#TelemetryConsentSecondary.Text", firstRun ? "Not now" : "Close");
        commands.set("#TelemetryConsentPrivacyDisclaimerPopup.Visible", privacyDisclaimerVisible);
        commands.set("#TelemetryConsentPrivacyDisclaimerBody.Text", PRIVACY_DISCLAIMER_TEXT);
        commands.set("#TelemetryConsentEmpty.Visible", projects.isEmpty());
        renderProjectRows(commands, events, projects);
    }

    static void renderProjectRows(@Nonnull UICommandBuilder commands,
                                  @Nonnull UIEventBuilder events,
                                  @Nonnull List<TelemetryConsentViewModel.ProjectRow> projects) {
        commands.clear("#TelemetryConsentRows");
        for (int index = 0; index < projects.size(); index++) {
            TelemetryConsentViewModel.ProjectRow project = projects.get(index);
            commands.append("#TelemetryConsentRows", PROJECT_ROW_UI_PATH);
            renderProject(commands, index, project);
            bindProjectEvents(events, index, project);
        }
    }

    private static void renderProject(@Nonnull UICommandBuilder commands,
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
        for (String category : CATEGORIES) {
            setCategory(commands, index, category, project.supported().categoryEnabled(category), consent.categoryEnabled(category));
        }
    }

    private static void setCategory(@Nonnull UICommandBuilder commands,
                                    int index,
                                    @Nonnull String category,
                                    boolean supported,
                                    boolean enabled) {
        String checkSelector = categoryCheckSelector(index, category);
        String toggleSelector = categoryToggleSelector(index, category);
        String unsupportedSelector = categoryUnsupportedSelector(index, category);
        commands.set(checkSelector + ".Value", supported && enabled);
        commands.set(checkSelector + ".Visible", supported);
        commands.set(toggleSelector + ".Visible", supported);
        commands.set(unsupportedSelector + ".Visible", !supported);
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.consentDiagnostics());
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
            if (!categoryAnySupported(projects, category)) {
                continue;
            }
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
                "#TelemetryConsentPrivacyDisclaimerButton",
                EventData.of(KEY_ACTION, ACTION_SHOW_PRIVACY_DISCLAIMER),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentPrivacyDisclaimerClose",
                EventData.of(KEY_ACTION, ACTION_HIDE_PRIVACY_DISCLAIMER),
                false
        );
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

    }

    private static void bindProjectEvents(@Nonnull UIEventBuilder events,
                                          int index,
                                          @Nonnull TelemetryConsentViewModel.ProjectRow project) {
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
            if (!project.supported().categoryEnabled(category)) {
                continue;
            }
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

    private void toggleAll(boolean enabled) {
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : runtimeService.consentDiagnostics().projects()) {
            TelemetryConsentSnapshot supported = project.supportedSnapshot();
            runtimeService.applyConsent(project.projectId(), new TelemetryConsentSnapshot(
                    enabled,
                    enabled && supported.crashEnabled(),
                    enabled && supported.errorEnabled(),
                    enabled && supported.lifecycleEnabled(),
                    enabled && supported.performanceEnabled(),
                    enabled && supported.usageEnabled(),
                    enabled && supported.statsEnabled(),
                    enabled && supported.breadcrumbsEnabled()
            ));
        }
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
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.consentDiagnostics());
        if (!categoryAnySupported(viewModel.projects(), category)) {
            return;
        }
        runtimeService.applyConsentCategoryToAll(category, enabled);
    }

    private void toggleCategory(@Nullable String projectId, @Nullable String category, boolean enabled) {
        TelemetryRuntimeDiagnostics.ProjectDiagnostics project = diagnostics(projectId);
        if (project == null || category == null || !project.supportedSnapshot().categoryEnabled(category)) {
            return;
        }
        TelemetryConsentSnapshot current = snapshot(project);
        TelemetryConsentSnapshot updated = current.withCategory(category, enabled);
        runtimeService.applyConsent(project.projectId(), updated);
    }

    private void saveAndClose() {
        for (TelemetryRuntimeDiagnostics.ProjectDiagnostics project : runtimeService.consentDiagnostics().projects()) {
            runtimeService.markConsentReviewed(project.projectId());
        }
        close();
    }

    @Nullable
    private TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics(@Nullable String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return runtimeService.consentProjectDiagnostics(projectId);
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
            TelemetryConsentSnapshot supported = project.supported();
            return consent.projectEnabled()
                    && (!supported.crashEnabled() || consent.crashEnabled())
                    && (!supported.errorEnabled() || consent.errorEnabled())
                    && (!supported.lifecycleEnabled() || consent.lifecycleEnabled())
                    && (!supported.performanceEnabled() || consent.performanceEnabled())
                    && (!supported.usageEnabled() || consent.usageEnabled())
                    && (!supported.statsEnabled() || consent.statsEnabled())
                    && (!supported.breadcrumbsEnabled() || consent.breadcrumbsEnabled());
        });
    }

    private static boolean categoryAllEnabled(@Nonnull List<TelemetryConsentViewModel.ProjectRow> projects,
                                              @Nonnull String category) {
        boolean anySupported = false;
        for (TelemetryConsentViewModel.ProjectRow project : projects) {
            if (!project.supported().categoryEnabled(category)) {
                continue;
            }
            anySupported = true;
            if (!project.consent().categoryEnabled(category)) {
                return false;
            }
        }
        return anySupported;
    }

    private static boolean categoryAnySupported(@Nonnull List<TelemetryConsentViewModel.ProjectRow> projects,
                                                @Nonnull String category) {
        return projects.stream().anyMatch(project -> project.supported().categoryEnabled(category));
    }

    @Nonnull
    private static String rowSelector(int index) {
        return "#TelemetryConsentRows[" + index + "]";
    }

    @Nonnull
    private static String projectCheckSelector(int index) {
        return rowSelector(index) + " #TelemetryConsentProjectEnabled";
    }

    @Nonnull
    static String projectToggleSelector(int index) {
        return TelemetryConsentUiContract.projectToggleSelector(index);
    }

    @Nonnull
    static String categoryCheckSelector(int index, @Nonnull String category) {
        return TelemetryConsentUiContract.categoryCheckSelector(index, category);
    }

    @Nonnull
    static String categoryToggleSelector(int index, @Nonnull String category) {
        return TelemetryConsentUiContract.categoryToggleSelector(index, category);
    }

    @Nonnull
    private static String categoryUnsupportedSelector(int index, @Nonnull String category) {
        return rowSelector(index) + " #TelemetryConsent" + selectorToken(category) + "Unsupported";
    }

    @Nonnull
    static String globalCategoryCheckSelector(@Nonnull String category) {
        return TelemetryConsentUiContract.globalCategoryCheckSelector(category);
    }

    @Nonnull
    static String globalCategoryToggleSelector(@Nonnull String category) {
        return TelemetryConsentUiContract.globalCategoryToggleSelector(category);
    }

    @Nonnull
    private static String globalCategoryUnsupportedSelector(@Nonnull String category) {
        return "#TelemetryConsent" + selectorToken(category) + "AllUnsupported";
    }

    @Nonnull
    static String selectorToken(@Nonnull String category) {
        return TelemetryConsentUiContract.selectorToken(category);
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
