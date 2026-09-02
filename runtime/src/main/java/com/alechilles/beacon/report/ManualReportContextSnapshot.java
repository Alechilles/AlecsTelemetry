package com.alechilles.beacon.report;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/**
 * Player/server context captured when a manual report is submitted.
 */
public record ManualReportContextSnapshot(@Nonnull String runtimeMode,
                                          boolean singleplayer,
                                          int onlinePlayerCount,
                                          @Nullable String worldName,
                                          boolean loadedModsIncluded,
                                          boolean diagnosticsIncluded) {

    public ManualReportContextSnapshot(@Nonnull String runtimeMode,
                                       int onlinePlayerCount,
                                       boolean loadedModsIncluded,
                                       boolean diagnosticsIncluded) {
        this(runtimeMode, "singleplayer".equalsIgnoreCase(runtimeMode), onlinePlayerCount, null, loadedModsIncluded, diagnosticsIncluded);
    }

    public ManualReportContextSnapshot {
        runtimeMode = runtimeMode == null || runtimeMode.isBlank() ? "unknown" : runtimeMode.trim();
        onlinePlayerCount = Math.max(0, onlinePlayerCount);
        worldName = worldName == null || worldName.isBlank() ? null : worldName.trim();
    }
}
