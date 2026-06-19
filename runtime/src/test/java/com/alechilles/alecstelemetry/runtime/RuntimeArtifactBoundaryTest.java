package com.alechilles.alecstelemetry.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimeArtifactBoundaryTest {

    @Test
    void runtimeClasspathContainsSharedRuntimeButNotStandalonePlugin() {
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeHost.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/host/TelemetryRuntimeProviderHandle.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/runtime/discovery/TelemetryRuntimeDiscovery.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/api/TelemetryRuntimeLocator.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/reports/TelemetryReportCoordinator.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/core/TelemetryCoreEngine.class");
        assertContainsRuntimeClass("com/alechilles/alecstelemetry/consent/TelemetryConsentPage.class");
        assertContainsRuntimeClass("Common/UI/Custom/TelemetryConsentPage.ui");
        assertContainsRuntimeClass("Common/UI/Custom/AlecsTelemetryLogo.png");
        assertContainsRuntimeClass("Common/UI/Custom/TelemetryConsentHeader.png");

        assertDoesNotContainRuntimeClass("com/alechilles/alecstelemetry/AlecsTelemetry.class");
        assertDoesNotContainRuntimeClass("com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.class");
        assertDoesNotContainRuntimeClass("manifest.json");
    }

    private static void assertContainsRuntimeClass(String entryName) {
        assertNotNull(
                Thread.currentThread().getContextClassLoader().getResource(entryName),
                entryName + " should be packaged in runtime jar"
        );
    }

    private static void assertDoesNotContainRuntimeClass(String entryName) {
        assertNull(
                Thread.currentThread().getContextClassLoader().getResource(entryName),
                entryName + " should not be packaged in runtime jar"
        );
    }
}
