package com.alechilles.alecstelemetry.reports;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Small project picker shown by `/telemetry report` before opening a report form.
 */
public final class TelemetryReportProjectSelectPage
        extends InteractiveCustomUIPage<TelemetryReportProjectSelectPage.SelectEventData> {

    public static final String UI_PATH = "TelemetryReportProjectSelectPage.ui";

    static final String ACTION_SELECT_PROJECT = "select_project";
    static final String ACTION_CANCEL = "cancel";

    private static final int MAX_PROJECT_ROWS = 5;
    private static final String KEY_ACTION = "Action";
    private static final String KEY_PROJECT_ID = "ProjectId";

    private final TelemetryRuntimeService runtimeService;
    private final PlayerRef playerRef;
    private final String reportKind;

    public TelemetryReportProjectSelectPage(@Nonnull PlayerRef playerRef,
                                            @Nonnull TelemetryRuntimeService runtimeService,
                                            @Nonnull String reportKind) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SelectEventData.CODEC);
        this.playerRef = playerRef;
        this.runtimeService = runtimeService;
        this.reportKind = "suggestion".equalsIgnoreCase(reportKind) ? "suggestion" : "issue";
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
                                @Nonnull SelectEventData data) {
        switch (data.action == null ? "" : data.action) {
            case ACTION_SELECT_PROJECT -> openReportPage(ref, store, data.projectId);
            case ACTION_CANCEL -> close();
            default -> {
            }
        }
    }

    private void openReportPage(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String projectId) {
        if (runtimeService.findProject(projectId) == null) {
            refreshUi();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new TelemetryReportPage(playerRef, runtimeService, projectId, new TelemetryReportOpenRequest(reportKind, null, null))
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
        List<TelemetryProjectRegistration> projects = runtimeService.manualReportProjects();
        int visibleRows = Math.min(projects.size(), MAX_PROJECT_ROWS);
        commands.set("#TelemetryReportProjectSelectKind.Text", "suggestion".equals(reportKind) ? "Suggestion" : "Issue");
        commands.set("#TelemetryReportProjectSelectEmpty.Visible", projects.isEmpty());
        commands.set("#TelemetryReportProjectSelectOverflow.Visible", projects.size() > MAX_PROJECT_ROWS);
        commands.set("#TelemetryReportProjectSelectOverflow.Text", projects.size() > MAX_PROJECT_ROWS
                ? "Showing " + MAX_PROJECT_ROWS + " of " + projects.size() + " report-enabled projects."
                : "");
        for (int index = 0; index < MAX_PROJECT_ROWS; index++) {
            String row = rowSelector(index);
            boolean visible = index < visibleRows;
            commands.set(row + ".Visible", visible);
            if (!visible) {
                continue;
            }
            TelemetryProjectRegistration project = projects.get(index);
            commands.set(row + " #TelemetryReportProjectSelectName.Text", project.displayName());
            commands.set(row + " #TelemetryReportProjectSelectMeta.Text", project.projectId() + " | " + project.pluginIdentifier());
        }
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        List<TelemetryProjectRegistration> projects = runtimeService.manualReportProjects();
        int visibleRows = Math.min(projects.size(), MAX_PROJECT_ROWS);
        for (int index = 0; index < visibleRows; index++) {
            TelemetryProjectRegistration project = projects.get(index);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowSelector(index) + " #TelemetryReportProjectSelectOpen",
                    EventData.of(KEY_ACTION, ACTION_SELECT_PROJECT).append(KEY_PROJECT_ID, project.projectId()),
                    false
            );
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportProjectSelectCancel",
                EventData.of(KEY_ACTION, ACTION_CANCEL),
                false
        );
    }

    @Nonnull
    private static String rowSelector(int index) {
        return "#TelemetryReportProjectSelectRow" + index;
    }

    public static final class SelectEventData {
        public static final BuilderCodec<SelectEventData> CODEC = BuilderCodec.builder(
                        SelectEventData.class,
                        SelectEventData::new
                )
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .addField(new KeyedCodec<>(KEY_PROJECT_ID, Codec.STRING), (data, value) -> data.projectId = value, data -> data.projectId)
                .build();

        private String action = "";
        private String projectId = "";
    }
}
