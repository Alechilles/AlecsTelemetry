package com.alechilles.alecstelemetry.reports;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Opens player-facing manual report pages for registered telemetry projects.
 */
public final class TelemetryReportCoordinator {

    private final TelemetryRuntimeService runtimeService;
    private final HytaleLogger logger;

    public TelemetryReportCoordinator(@Nonnull TelemetryRuntimeService runtimeService,
                                      @Nullable HytaleLogger logger) {
        this.runtimeService = runtimeService;
        this.logger = logger;
    }

    public boolean openReportPage(@Nonnull String projectId,
                                  @Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull TelemetryReportOpenRequest request) {
        if (playerEntityRef == null || store == null || playerRef == null) {
            warn("Unable to open manual report page because player UI context is unavailable.", null);
            return false;
        }
        TelemetryProjectRegistration project = runtimeService.findManualReportProject(projectId);
        if (project == null || !runtimeService.isProjectEnabled(project.projectId()) || !project.descriptor().reports().enabled()) {
            warn("Unable to open manual report page for unavailable project " + projectId + ".", null);
            return false;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            warn("Unable to open manual report page because the player component is unavailable.", null);
            return false;
        }
        player.getPageManager().openCustomPage(
                playerEntityRef,
                store,
                new TelemetryReportPage(playerRef, runtimeService, project.projectId(), request)
        );
        return true;
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
