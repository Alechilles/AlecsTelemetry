package com.alechilles.alecstelemetry.report;

import javax.annotation.Nonnull;

/**
 * Player/server context captured when a manual report is submitted.
 */
public record ManualReportContextSnapshot(@Nonnull String runtimeMode,
                                          int onlinePlayerCount,
                                          boolean loadedModsIncluded,
                                          boolean diagnosticsIncluded) {

    public ManualReportContextSnapshot {
        runtimeMode = runtimeMode == null || runtimeMode.isBlank() ? "unknown" : runtimeMode.trim();
        onlinePlayerCount = Math.max(0, onlinePlayerCount);
    }
}
