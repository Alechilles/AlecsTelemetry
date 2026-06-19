package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryConsentRuntime;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.singleplayer.SingleplayerModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Opens the telemetry consent page for operators when newly discovered telemetry projects need review.
 */
public final class TelemetryConsentCoordinator {

    public static final String ROOT_PERMISSION = "telemetry.command.telemetry";
    public static final String FIRST_RUN_NOTICE = "One or more mods are using Alec's Telemetry to report anonymous statistics and/or crash, error, performance diagnostics. You may change your telemetry consent settings at any time using `/telemetry consent`.";
    static final String FIRST_RUN_NOTICE_COLOR = "#f0a33a";

    private final TelemetryConsentRuntime runtimeService;
    private final HytaleLogger logger;
    private final Set<String> promptedKeys = ConcurrentHashMap.newKeySet();

    public TelemetryConsentCoordinator(@Nonnull TelemetryConsentRuntime runtimeService,
                                       @Nullable HytaleLogger logger) {
        this.runtimeService = runtimeService;
        this.logger = logger;
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        List<TelemetryProjectRegistration> unreviewed = runtimeService.unreviewedConsentProjects();
        if (unreviewed.isEmpty()) {
            return;
        }
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (!hasConsentAccess(playerRef)) {
            return;
        }
        String viewerKey = viewerKey(playerRef);
        if (runtimeService.isConsentNoticeShown(viewerKey, unreviewed)) {
            return;
        }
        String promptKey = viewerKey + "|" + promptKey(unreviewed);
        if (!promptedKeys.add(promptKey)) {
            return;
        }
        if (!sendFirstRunNotice(playerRef)) {
            promptedKeys.remove(promptKey);
            return;
        }
        if (!runtimeService.markConsentNoticeShown(viewerKey, unreviewed)) {
            warn("Unable to save telemetry consent notice state.", null);
        }
    }

    public boolean openConsentPage(@Nonnull Ref<EntityStore> playerEntityRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef playerRef,
                                   boolean firstRun) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            warn("Unable to open telemetry consent page because the player component is unavailable.", null);
            return false;
        }
        player.getPageManager().openCustomPage(
                playerEntityRef,
                store,
                new TelemetryConsentPage(playerRef, runtimeService, firstRun)
        );
        return true;
    }

    private boolean sendFirstRunNotice(@Nonnull PlayerRef playerRef) {
        try {
            playerRef.sendMessage(Message.raw(FIRST_RUN_NOTICE).color(FIRST_RUN_NOTICE_COLOR));
            return true;
        } catch (Exception ex) {
            warn("Unable to send telemetry consent notice.", ex);
            return false;
        }
    }

    private static boolean hasConsentAccess(@Nullable PlayerRef playerRef) {
        return playerRef != null
                && playerRef.isValid()
                && (playerRef.hasPermission(ROOT_PERMISSION, false)
                || isLocalSingleplayerOwner(playerRef));
    }

    private static boolean isLocalSingleplayerOwner(@Nonnull PlayerRef playerRef) {
        return SingleplayerModule.get() != null && SingleplayerModule.isOwner(playerRef);
    }

    @Nonnull
    private static String viewerKey(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        return uuid == null ? "unknown-player" : uuid.toString();
    }

    @Nonnull
    private static String promptKey(@Nonnull List<TelemetryProjectRegistration> projects) {
        return projects.stream()
                .map(project -> project.projectId()
                        + "@"
                        + project.pluginIdentifier()
                        + "@"
                        + project.pluginVersion())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
    }

    private void warn(@Nonnull String message, @Nullable Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.at(Level.WARNING).log(message);
            return;
        }
        logger.at(Level.WARNING).withCause(throwable).log(message);
    }
}
