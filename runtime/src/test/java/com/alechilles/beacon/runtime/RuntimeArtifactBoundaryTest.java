package com.alechilles.beacon.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimeArtifactBoundaryTest {

    @Test
    void runtimeClasspathContainsSharedRuntimeButNotStandalonePlugin() {
        assertContainsRuntimeClass("com/alechilles/beacon/embedded/EmbeddedTelemetryBootstrap.class");
        assertContainsRuntimeClass("com/alechilles/beacon/runtime/host/TelemetryRuntimeHost.class");
        assertContainsRuntimeClass("com/alechilles/beacon/runtime/host/TelemetryRuntimeProviderHandle.class");
        assertContainsRuntimeClass("com/alechilles/beacon/runtime/discovery/TelemetryRuntimeDiscovery.class");
        assertContainsRuntimeClass("com/alechilles/beacon/api/TelemetryRuntimeLocator.class");
        assertContainsRuntimeClass("com/alechilles/beacon/commands/TelemetryCommandRoot.class");
        assertContainsRuntimeClass("com/alechilles/beacon/reports/TelemetryReportCoordinator.class");
        assertContainsRuntimeClass("com/alechilles/beacon/core/TelemetryCoreEngine.class");
        assertContainsRuntimeClass("com/alechilles/beacon/consent/TelemetryConsentPage.class");
        assertContainsRuntimeClass("Common/UI/Custom/TelemetryConsentPage.ui");
        assertContainsRuntimeClass("Common/UI/Custom/BeaconLogo.png");
        assertContainsRuntimeClass("Common/UI/Custom/TelemetryConsentHeader.png");

        assertDoesNotContainRuntimeClass("com/alechilles/beacon/Beacon.class");
        assertDoesNotContainRuntimeClass("com/alechilles/beacon/runtime/TelemetryRuntimeService.class");
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
