package com.alechilles.beacon.report;

import com.alechilles.beacon.crash.CrashReportEnvelope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Runtime-provided context for a player-submitted manual report.
 */
public record PlayerReportRuntimeContext(boolean singleplayer,
                                         int onlinePlayerCount,
                                         @Nullable String worldName,
                                         @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {

    public static final PlayerReportRuntimeContext UNKNOWN =
            new PlayerReportRuntimeContext(false, 0, null, List.of());

    public PlayerReportRuntimeContext {
        onlinePlayerCount = Math.max(0, onlinePlayerCount);
        worldName = worldName == null || worldName.isBlank() ? null : worldName.trim();
        loadedMods = loadedMods == null ? List.of() : List.copyOf(loadedMods);
    }

    @Nonnull
    public ManualReportContextSnapshot toSnapshot(boolean loadedModsIncluded, boolean diagnosticsIncluded) {
        return new ManualReportContextSnapshot(
                singleplayer ? "singleplayer" : "multiplayer",
                singleplayer,
                onlinePlayerCount,
                worldName,
                loadedModsIncluded,
                diagnosticsIncluded
        );
    }
}
