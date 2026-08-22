package com.alechilles.alecstelemetry.runtime.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparkProfileSnapshotTest {

    @Test
    void labelsUnknownHytaleFramesAsHytaleServer() {
        SparkProfileSnapshot.HotPath path = new SparkProfileSnapshot.HotPath(
                "<unknown>",
                "com.hypixel.hytale.component.Store#tickInternal()V",
                100.0,
                10.0
        );

        assertEquals("Hytale Server", path.source());
    }

    @Test
    void preservesDeclaredModSourceLabel() {
        SparkProfileSnapshot.HotPath path = new SparkProfileSnapshot.HotPath(
                "Alechilles:Alec's Tamework!",
                "com.alechilles.alecstamework.Example#tick()V",
                100.0,
                10.0
        );

        assertEquals("Alechilles:Alec's Tamework!", path.source());
    }
}
