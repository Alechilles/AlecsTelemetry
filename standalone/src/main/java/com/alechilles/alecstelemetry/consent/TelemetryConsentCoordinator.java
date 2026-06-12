package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.commands.TelemetryCommandRoot;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Opens the telemetry consent page for operators when newly discovered telemetry projects need review.
 */
public final class TelemetryConsentCoordinator {

    private final TelemetryRuntimeService runtimeService;
    private final HytaleLogger logger;
    private final Set<String> promptedKeys = ConcurrentHashMap.newKeySet();

    public TelemetryConsentCoordinator(@Nonnull TelemetryRuntimeService runtimeService,
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
        String promptKey = promptKey(unreviewed);
        if (!promptedKeys.add(promptKey)) {
            return;
        }
        if (!openConsentPage(playerEntityRef, store, playerRef, true)) {
            promptedKeys.remove(promptKey);
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

    private static boolean hasConsentAccess(@Nullable PlayerRef playerRef) {
        return playerRef != null
                && playerRef.isValid()
                && (playerRef.hasPermission(TelemetryCommandRoot.ROOT_PERMISSION, false)
                || isLocalSingleplayerOwner(playerRef));
    }

    private static boolean isLocalSingleplayerOwner(@Nonnull PlayerRef playerRef) {
        return SingleplayerModule.get() != null && SingleplayerModule.isOwner(playerRef);
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
