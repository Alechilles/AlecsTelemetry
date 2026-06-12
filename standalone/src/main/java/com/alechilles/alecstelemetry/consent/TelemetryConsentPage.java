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

/**
 * Operator-facing page for first-run and manual telemetry consent changes.
 */
public final class TelemetryConsentPage extends InteractiveCustomUIPage<TelemetryConsentPage.ConsentEventData> {

    static final String ACTION_TOGGLE_ALL = "toggle_all";
    static final String ACTION_TOGGLE_PROJECT = "toggle_project";
    static final String ACTION_TOGGLE_CATEGORY = "toggle_category";
    static final String ACTION_SAVE = "save";
    static final String ACTION_CLOSE = "close";

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
        TelemetryConsentViewModel viewModel = TelemetryConsentViewModel.from(runtimeService.diagnostics());
        commands.appendInline("AlecsTelemetryConsentPage", inlinePage(viewModel));
        commands.set("#TelemetryConsentTitle.Text", "Alec's Telemetry");
        commands.set("#TelemetryConsentSummary.Text", String.join("\n", viewModel.explanationLines()));
        commands.set("#TelemetryConsentAllEnabled.Value", viewModel.allEnabled());
        commands.setObject("#TelemetryConsentProjects.Value", viewModel.projects());
        commands.set("#TelemetryConsentPrimary.Text", firstRun ? "Save choices" : "Save");
        commands.set("#TelemetryConsentSecondary.Text", firstRun ? "Not now" : "Close");
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TelemetryConsentAllEnabled",
                EventData.of("Action", ACTION_TOGGLE_ALL).append("Enabled", "#TelemetryConsentAllEnabled.Value")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentPrimary",
                EventData.of("Action", ACTION_SAVE)
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryConsentSecondary",
                EventData.of("Action", ACTION_CLOSE)
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ConsentEventData data) {
        switch (data.action == null ? "" : data.action) {
            case ACTION_TOGGLE_ALL -> toggleAll(data.enabled);
            case ACTION_TOGGLE_PROJECT -> toggleProject(data.projectId, data.enabled);
            case ACTION_TOGGLE_CATEGORY -> toggleCategory(data.projectId, data.category, data.enabled);
            case ACTION_SAVE -> saveAndClose();
            case ACTION_CLOSE -> close();
            default -> {
                return;
            }
        }
        sendUpdate();
    }

    private void toggleAll(boolean enabled) {
        runtimeService.applyConsentToAll(new TelemetryConsentSnapshot(
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
                        current.breadcrumbsEnabled()
                )
        );
    }

    private void toggleCategory(@Nullable String projectId, @Nullable String category, boolean enabled) {
        TelemetryRuntimeDiagnostics.ProjectDiagnostics project = diagnostics(projectId);
        if (project == null || category == null) {
            return;
        }
        TelemetryConsentSnapshot current = snapshot(project);
        TelemetryConsentSnapshot updated = switch (category) {
            case "crash" -> new TelemetryConsentSnapshot(current.projectEnabled(), enabled, current.errorEnabled(), current.lifecycleEnabled(), current.performanceEnabled(), current.usageEnabled(), current.breadcrumbsEnabled());
            case "error" -> new TelemetryConsentSnapshot(current.projectEnabled(), current.crashEnabled(), enabled, current.lifecycleEnabled(), current.performanceEnabled(), current.usageEnabled(), current.breadcrumbsEnabled());
            case "lifecycle" -> new TelemetryConsentSnapshot(current.projectEnabled(), current.crashEnabled(), current.errorEnabled(), enabled, current.performanceEnabled(), current.usageEnabled(), current.breadcrumbsEnabled());
            case "performance" -> new TelemetryConsentSnapshot(current.projectEnabled(), current.crashEnabled(), current.errorEnabled(), current.lifecycleEnabled(), enabled, current.usageEnabled(), current.breadcrumbsEnabled());
            case "usage" -> new TelemetryConsentSnapshot(current.projectEnabled(), current.crashEnabled(), current.errorEnabled(), current.lifecycleEnabled(), current.performanceEnabled(), enabled, current.breadcrumbsEnabled());
            case "breadcrumbs" -> new TelemetryConsentSnapshot(current.projectEnabled(), current.crashEnabled(), current.errorEnabled(), current.lifecycleEnabled(), current.performanceEnabled(), current.usageEnabled(), enabled);
            default -> current;
        };
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
                project.breadcrumbsEnabled()
        );
    }

    @Nonnull
    private static String inlinePage(@Nonnull TelemetryConsentViewModel viewModel) {
        StringBuilder builder = new StringBuilder();
        builder.append("TelemetryConsentPage\n");
        builder.append("AllEnabled=").append(viewModel.allEnabled()).append('\n');
        for (TelemetryConsentViewModel.ProjectRow project : viewModel.projects()) {
            builder.append(project.projectId())
                    .append('|')
                    .append(project.displayName())
                    .append('|')
                    .append(project.consent().projectEnabled())
                    .append('\n');
        }
        return builder.toString();
    }

    public static final class ConsentEventData {
        public static final BuilderCodec<ConsentEventData> CODEC = BuilderCodec.builder(
                        ConsentEventData.class,
                        ConsentEventData::new
                )
                .addField(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>("ProjectId", Codec.STRING), (data, value) -> data.projectId = value, data -> data.projectId)
                .addField(new KeyedCodec<>("Category", Codec.STRING), (data, value) -> data.category = value, data -> data.category)
                .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (data, value) -> data.enabled = value, data -> data.enabled)
                .build();

        private String action = "";
        private String projectId = "";
        private String category = "";
        private boolean enabled;
    }
}
