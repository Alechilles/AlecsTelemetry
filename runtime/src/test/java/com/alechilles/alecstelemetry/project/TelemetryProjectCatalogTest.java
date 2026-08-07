package com.alechilles.alecstelemetry.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectCatalogTest {

    @Test
    void reconcilePublishesIndependentProjectsAndRetiresWithoutDroppingQueueRegistration() {
        TelemetryProjectRegistration host = registration("host", "Host", "com.example.host");
        TelemetryProjectRegistration library = registration("library", "Library", "com.example.library");
        TelemetryProjectCatalog catalog = new TelemetryProjectCatalog(List.of(host), List.of(host));

        assertTrue(catalog.reconcile(List.of(host, library)));
        assertNotNull(catalog.find("library"));
        assertEquals(List.of("host", "library"), catalog.snapshot().projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());

        assertTrue(catalog.reconcile(List.of(host)));
        assertEquals(List.of("host"), catalog.snapshot().projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertEquals(List.of("library"), catalog.snapshot().retiredProjects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
        assertEquals(List.of("host"), catalog.snapshot().manualReportProjects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
    }

    @Test
    void invalidReplacementLeavesPreviousProjectSnapshotWritable() {
        TelemetryProjectRegistration previous = registration("library", "Library", "com.example.library");
        TelemetryProjectCatalog catalog = new TelemetryProjectCatalog(List.of(previous), List.of(previous));
        TelemetryProjectRegistration invalid = new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson("{\"projectId\":\"\",\"displayName\":\"\"}", null),
                "Example:Library",
                "1.0.0",
                null
        );

        assertFalse(catalog.reconcile(List.of(invalid)));
        assertSame(previous, catalog.find("library"));
        assertEquals(List.of("library"), catalog.snapshot().projects().stream()
                .map(TelemetryProjectRegistration::projectId)
                .toList());
    }

    @Test
    void overlappingPrefixesAreMarkedAmbiguousForAutomaticCrashAttribution() {
        TelemetryProjectRegistration first = registration("first", "First", "com.example");
        TelemetryProjectRegistration second = registration("second", "Second", "com.example.second");
        TelemetryProjectCatalog catalog = new TelemetryProjectCatalog(List.of(first), List.of(first));

        assertTrue(catalog.reconcile(List.of(first, second)));
        assertTrue(catalog.snapshot().ambiguousPackagePrefixes().contains("com.example"));
        assertTrue(catalog.snapshot().ambiguousPackagePrefixes().contains("com.example.second"));
    }

    private static TelemetryProjectRegistration registration(String projectId,
                                                              String displayName,
                                                              String packagePrefix) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                "{\"projectId\":\"" + projectId + "\","
                        + "\"displayName\":\"" + displayName + "\","
                        + "\"ownerPluginIdentifiers\":[\"Example:" + displayName + "\"],"
                        + "\"packagePrefixes\":[\"" + packagePrefix + "\"]}",
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:" + displayName, "1.0.0", null);
    }
}
