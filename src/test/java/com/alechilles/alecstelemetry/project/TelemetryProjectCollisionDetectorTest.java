package com.alechilles.alecstelemetry.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryProjectCollisionDetectorTest {

    @Test
    void detectsOverlappingPackagePrefixes() {
        TelemetryProjectRegistration first = new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "first-mod",
                          "displayName": "First Mod",
                          "ownerPluginIdentifiers": ["Example:First Mod"],
                          "packagePrefixes": ["com.example"]
                        }
                        """,
                        null
                ),
                "Example:First Mod",
                "1.0.0",
                null
        );
        TelemetryProjectRegistration second = new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "second-mod",
                          "displayName": "Second Mod",
                          "ownerPluginIdentifiers": ["Example:Second Mod"],
                          "packagePrefixes": ["com.example.second"]
                        }
                        """,
                        null
                ),
                "Example:Second Mod",
                "1.0.0",
                null
        );

        assertFalse(TelemetryProjectCollisionDetector.detect(List.of(first, second)).isEmpty());
    }
}
