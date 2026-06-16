package com.alechilles.alecstelemetry.reports;

import com.alechilles.alecstelemetry.report.ManualReportEnvelope;
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

    private final ManualReportEnvelope envelope;
    private final String status;

    public TelemetryReportReceiptPage(@Nonnull PlayerRef playerRef,
                                      @Nonnull ManualReportEnvelope envelope,
                                      @Nonnull String status) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ReceiptEventData.CODEC);
        this.envelope = envelope;
        this.status = status;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        commands.append(UI_PATH);
        commands.set("#TelemetryReportReceiptProject.Text", envelope.projectDisplayName());
        commands.set("#TelemetryReportReceiptKind.Text", envelope.reportKind());
        commands.set("#TelemetryReportReceiptStatus.Text", status);
        commands.set("#TelemetryReportReceiptId.Text", envelope.reportId());
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
        if (ACTION_CLOSE.equals(data.action)) {
            close();
        }
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
