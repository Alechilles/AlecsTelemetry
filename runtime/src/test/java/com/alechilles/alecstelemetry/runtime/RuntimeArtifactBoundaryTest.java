package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimeArtifactBoundaryTest {

    @Test
    void runtimeClasspathContainsEmbeddedRuntimeButNotStandalonePlugin() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertNotNull(classLoader.getResource("com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class"));
        assertNotNull(classLoader.getResource("com/alechilles/alecstelemetry/core/TelemetryCoreEngine.class"));
        assertNull(classLoader.getResource("com/alechilles/alecstelemetry/AlecsTelemetry.class"));
        assertNull(classLoader.getResource("com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.class"));
        assertNull(classLoader.getResource("manifest.json"));
    }
}
