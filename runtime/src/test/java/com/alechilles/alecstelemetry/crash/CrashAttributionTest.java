package com.alechilles.alecstelemetry.crash;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashAttributionTest {

    @Test
    void attributesWhenStackContainsRegisteredPrefix() {
        RuntimeException throwable = new RuntimeException("boom");
        throwable.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        "com.example.telemetry.SomeSystem",
                        "tick",
                        "SomeSystem.java",
                        42
                )
        });

        CrashAttribution.AttributionResult result = CrashAttribution.classify(
                throwable,
                List.of("Example:Example Mod"),
                List.of("com.example.telemetry"),
                null
        );

        assertTrue(result.attributed());
        assertTrue(result.matchedStackPrefix());
        assertFalse(result.fingerprint().isBlank());
    }

    @Test
    void explicitCaptureForcesAttribution() {
        RuntimeException throwable = new RuntimeException("explicit");

        CrashAttribution.AttributionResult result = CrashAttribution.explicit(
                throwable,
                "Example:Example Mod",
                List.of("com.example.telemetry")
        );

        assertTrue(result.attributed());
        assertEquals("Example:Example Mod", result.identifiedPlugin());
        assertTrue(result.matchedPluginIdentifier());
    }
}
