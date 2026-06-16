package com.alechilles.alecstelemetry.consent;

/**
 * Effective project and category consent state for one telemetry project.
 */
public record TelemetryConsentSnapshot(boolean projectEnabled,
                                       boolean crashEnabled,
                                       boolean errorEnabled,
                                       boolean lifecycleEnabled,
                                       boolean performanceEnabled,
                                       boolean usageEnabled,
                                       boolean statsEnabled,
                                       boolean breadcrumbsEnabled) {
}
