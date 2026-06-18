package com.alechilles.alecstelemetry.runtime.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryPlayerCounterTest {

    @Test
    void tracksUniquePlayers() {
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        UUID player = UUID.randomUUID();

        counter.markReady(player);
        counter.markReady(player);

        assertEquals(1, counter.onlinePlayers());
    }

    @Test
    void removesDisconnectedPlayers() {
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        UUID player = UUID.randomUUID();

        counter.markReady(player);
        counter.markDisconnected(player);

        assertEquals(0, counter.onlinePlayers());
    }
}
