package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TelemetryRuntimeHostTest {
    @Test
    void exposesStandaloneAndEmbeddedBootstrapMethods() throws Exception {
        Method standalone = TelemetryRuntimeHost.class.getMethod(
                "bootstrapStandalone",
                JavaPlugin.class
        );
        Method embedded = TelemetryRuntimeHost.class.getMethod(
                "bootstrapEmbedded",
                JavaPlugin.class,
                TelemetryProjectDescriptor.class
        );

        assertNotNull(standalone);
        assertNotNull(embedded);
        assertEquals(TelemetryRuntimeHostHandle.class, standalone.getReturnType());
        assertEquals(TelemetryRuntimeHostHandle.class, embedded.getReturnType());
    }
}
