package com.alechilles.beacon.reports;

import com.alechilles.beacon.report.ManualReportEnvelope;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
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

/**
 * Player receipt page shown after a manual report is accepted locally.
 */
public final class TelemetryReportReceiptPage extends InteractiveCustomUIPage<TelemetryReportReceiptPage.ReceiptEventData> {

    public static final String UI_PATH = "TelemetryReportReceiptPage.ui";

    private static final String KEY_ACTION = "Action";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_REFRESH = "refresh";

    private final TelemetryCommandRuntime runtime;
    private final ManualReportEnvelope envelope;
    private final String status;

    public TelemetryReportReceiptPage(@Nonnull PlayerRef playerRef,
                                      @Nonnull TelemetryCommandRuntime runtime,
                                      @Nonnull ManualReportEnvelope envelope,
                                      @Nonnull String status) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ReceiptEventData.CODEC);
        this.runtime = runtime;
        this.envelope = envelope;
        this.status = status;
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

    private void refreshUi() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands);
        bindEvents(events);
        sendUpdate(commands, events, false);
    }

    private void render(@Nonnull UICommandBuilder commands) {
        commands.set("#TelemetryReportReceiptProject.Text", envelope.projectDisplayName());
        commands.set("#TelemetryReportReceiptKind.Text", envelope.reportKind());
        commands.set("#TelemetryReportReceiptStatus.Text", displayStatus());
        commands.set("#TelemetryReportReceiptId.Text", envelope.reportId());
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportReceiptRefresh",
                EventData.of(KEY_ACTION, ACTION_REFRESH),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TelemetryReportReceiptClose",
                EventData.of(KEY_ACTION, ACTION_CLOSE),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ReceiptEventData data) {
        switch (data.action) {
            case ACTION_REFRESH -> {
                runtime.requestFlush(envelope.projectId());
                refreshUi();
            }
            case ACTION_CLOSE -> close();
            default -> {
            }
        }
    }

    @Nonnull
    private String displayStatus() {
        String storedStatus = runtime.manualReportReceiptStatus(envelope.reportId());
        return switch (storedStatus) {
            case "uploaded" -> "Uploaded";
            case "review" -> "Waiting for server owner review";
            case "upload_failed" -> "Upload failed; queued for retry";
            default -> status;
        };
    }

    public static final class ReceiptEventData {
        public static final BuilderCodec<ReceiptEventData> CODEC = BuilderCodec.builder(
                        ReceiptEventData.class,
                        ReceiptEventData::new
                )
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .build();

        private String action = "";
    }
}
