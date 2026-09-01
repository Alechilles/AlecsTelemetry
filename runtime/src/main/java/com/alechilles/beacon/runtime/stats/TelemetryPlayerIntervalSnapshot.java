package com.alechilles.beacon.runtime.stats;

public record TelemetryPlayerIntervalSnapshot(int currentPlayers,
                                              int maxPlayersSinceLastReport,
                                              double avgPlayersSinceLastReport) {
    public static TelemetryPlayerIntervalSnapshot single(int playersOnline) {
        int normalized = Math.max(0, playersOnline);
        return new TelemetryPlayerIntervalSnapshot(normalized, normalized, normalized);
    }
}
