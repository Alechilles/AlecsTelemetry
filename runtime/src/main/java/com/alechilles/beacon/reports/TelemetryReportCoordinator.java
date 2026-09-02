package com.alechilles.beacon.reports;

import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
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

    private final TelemetryCommandRuntime runtime;
    private final HytaleLogger logger;

    public TelemetryReportCoordinator(@Nonnull TelemetryCommandRuntime runtime,
                                      @Nullable HytaleLogger logger) {
        this.runtime = runtime;
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
        TelemetryProjectRegistration project = runtime.findManualReportProject(projectId);
        if (project == null || !project.descriptor().reports().enabled()) {
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
                new TelemetryReportPage(playerRef, runtime, project.projectId(), request)
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
