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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
        LinkedHashMap<String, List<String>> additionsByProject = new LinkedHashMap<>();
        for (TelemetryProjectRegistration project : unreviewed) {
            List<String> additions = runtimeService.addedConsentCategories(project.projectId());
            if (!additions.isEmpty()) {
                additionsByProject.put(project.projectId().trim().toLowerCase(Locale.ROOT), additions);
            }
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
        String notice = additionsByProject.isEmpty()
                ? FIRST_RUN_NOTICE
                : reviewNotice(unreviewed, additionsByProject);
        if (!sendNotice(playerRef, notice)) {
            promptedKeys.remove(promptKey);
            return;
        }
        if (!runtimeService.markConsentNoticeShown(viewerKey, unreviewed)) {
            warn("Unable to save telemetry consent notice state.", null);
            return;
        }
        runtimeService.recordConsentPromptShown(viewerKey, unreviewed);
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
        String viewerKey = viewerKey(playerRef);
        List<TelemetryProjectRegistration> promptedFirstReviewProjects = firstReviewPromptedProjects(viewerKey);
        player.getPageManager().openCustomPage(
                playerEntityRef,
                store,
                new TelemetryConsentPage(playerRef, runtimeService, firstRun)
        );
        if (!promptedFirstReviewProjects.isEmpty()) {
            runtimeService.recordConsentUiOpened(viewerKey, promptedFirstReviewProjects);
        }
        return true;
    }

    private boolean sendNotice(@Nonnull PlayerRef playerRef, @Nonnull String notice) {
        try {
            playerRef.sendMessage(Message.raw(notice).color(FIRST_RUN_NOTICE_COLOR));
            return true;
        } catch (Exception ex) {
            warn("Unable to send telemetry consent notice.", ex);
            return false;
        }
    }

    @Nonnull
    static String reviewNotice(@Nonnull List<TelemetryProjectRegistration> projects,
                               @Nonnull Map<String, List<String>> additionsByProject) {
        ArrayList<String> projectNotices = new ArrayList<>();
        for (TelemetryProjectRegistration project : projects) {
            List<String> additions = additionsByProject.get(project.projectId().trim().toLowerCase(Locale.ROOT));
            if (additions == null || additions.isEmpty()) {
                continue;
            }
            ArrayList<String> labels = new ArrayList<>();
            for (String category : additions) {
                String label = categoryLabel(category);
                if (label != null && !labels.contains(label)) {
                    labels.add(label);
                }
            }
            if (!labels.isEmpty()) {
                projectNotices.add(project.displayName()
                        + " added telemetry options: "
                        + joinLabels(labels)
                        + ".");
            }
        }
        if (projectNotices.isEmpty()) {
            return FIRST_RUN_NOTICE;
        }
        return String.join(" ", projectNotices)
                + " These remain disabled until reviewed. Run `/telemetry consent`.";
    }

    @Nullable
    private static String categoryLabel(@Nonnull String category) {
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "crash" -> "Crash";
            case "error" -> "Errors";
            case "lifecycle" -> "Lifecycle";
            case "performance" -> "Performance";
            case "usage" -> "Usage";
            case "stats" -> "Stats";
            case "breadcrumbs" -> "Breadcrumbs";
            default -> null;
        };
    }

    @Nonnull
    private static String joinLabels(@Nonnull List<String> labels) {
        if (labels.size() == 1) {
            return labels.getFirst();
        }
        if (labels.size() == 2) {
            return labels.get(0) + " and " + labels.get(1);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1))
                + ", and "
                + labels.getLast();
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
    private List<TelemetryProjectRegistration> firstReviewPromptedProjects(@Nonnull String viewerKey) {
        List<TelemetryProjectRegistration> unreviewed = runtimeService.unreviewedConsentProjects();
        if (unreviewed.isEmpty() || !runtimeService.isConsentNoticeShown(viewerKey, unreviewed)) {
            return List.of();
        }
        return unreviewed;
    }

    @Nonnull
    private static String viewerKey(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        return uuid == null ? "unknown-player" : uuid.toString();
    }

    @Nonnull
    static String promptKey(@Nonnull List<TelemetryProjectRegistration> projects) {
        return projects.stream()
                .map(project -> project.projectId()
                        + "@"
                        + project.pluginIdentifier()
                        + "@"
                        + project.pluginVersion()
                        + "#"
                        + TelemetryConsentCapabilities.fingerprint(project))
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
