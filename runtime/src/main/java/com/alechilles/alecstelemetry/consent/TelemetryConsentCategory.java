package com.alechilles.alecstelemetry.consent;

/**
 * Operator-facing telemetry categories exposed by the consent UI.
 */
public enum TelemetryConsentCategory {
    CRASH("crash"),
    ERROR("error"),
    LIFECYCLE("lifecycle"),
    PERFORMANCE("performance"),
    USAGE("usage"),
    BREADCRUMBS("breadcrumbs");

    private final String key;

    TelemetryConsentCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
