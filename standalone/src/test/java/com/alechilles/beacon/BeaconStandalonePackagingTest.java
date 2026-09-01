package com.alechilles.beacon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeaconStandalonePackagingTest {

    @Test
    void creditorLifecycleApiIsAvailableToStandalonePlugin() throws Exception {
        Class<?> creditor = Class.forName("com.creditor.Creditor");

        assertNotNull(creditor.getMethod("setup", com.hypixel.hytale.server.core.plugin.JavaPlugin.class));
        assertNotNull(creditor.getMethod("start", com.hypixel.hytale.server.core.plugin.JavaPlugin.class));
    }

    @Test
    void beaconEntrypointIsLoadableFromBeaconNamespace() {
        assertDoesNotThrow(() -> Class.forName("com.alechilles.beacon.Beacon"));
    }
}
