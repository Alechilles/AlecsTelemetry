package com.alechilles.alecstelemetry.consent;

/**
 * Effective project and category consent state for one telemetry project.
 */
public record TelemetryConsentSnapshot(boolean projectEnabled,
                                       boolean crashEnabled,
                                       boolean errorEnabled,
                                       boolean diagnosticsEnabled,
                                       boolean lifecycleEnabled,
                                       boolean performanceEnabled,
                                       boolean usageEnabled,
                                       boolean statsEnabled,
                                       boolean breadcrumbsEnabled) {

    public TelemetryConsentSnapshot(boolean projectEnabled,
                                    boolean crashEnabled,
                                    boolean errorEnabled,
                                    boolean lifecycleEnabled,
                                    boolean performanceEnabled,
                                    boolean usageEnabled,
                                    boolean statsEnabled,
                                    boolean breadcrumbsEnabled) {
        this(projectEnabled,
                crashEnabled,
                errorEnabled,
                false,
                lifecycleEnabled,
                performanceEnabled,
                usageEnabled,
                statsEnabled,
                breadcrumbsEnabled);
    }

    public boolean categoryEnabled(String category) {
        return switch (category) {
            case "crash" -> crashEnabled;
            case "error" -> errorEnabled;
            case "diagnostics" -> diagnosticsEnabled;
            case "lifecycle" -> lifecycleEnabled;
            case "performance" -> performanceEnabled;
            case "usage" -> usageEnabled;
            case "stats" -> statsEnabled;
            case "breadcrumbs" -> breadcrumbsEnabled;
            default -> false;
        };
    }

    public TelemetryConsentSnapshot withCategory(String category, boolean enabled) {
        return switch (category) {
            case "crash" -> new TelemetryConsentSnapshot(projectEnabled, enabled, errorEnabled, diagnosticsEnabled, lifecycleEnabled, performanceEnabled, usageEnabled, statsEnabled, breadcrumbsEnabled);
            case "error" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, enabled, diagnosticsEnabled, lifecycleEnabled, performanceEnabled, usageEnabled, statsEnabled, breadcrumbsEnabled);
            case "diagnostics" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, enabled, lifecycleEnabled, performanceEnabled, usageEnabled, statsEnabled, breadcrumbsEnabled);
            case "lifecycle" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, diagnosticsEnabled, enabled, performanceEnabled, usageEnabled, statsEnabled, breadcrumbsEnabled);
            case "performance" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, diagnosticsEnabled, lifecycleEnabled, enabled, usageEnabled, statsEnabled, breadcrumbsEnabled);
            case "usage" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, diagnosticsEnabled, lifecycleEnabled, performanceEnabled, enabled, statsEnabled, breadcrumbsEnabled);
            case "stats" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, diagnosticsEnabled, lifecycleEnabled, performanceEnabled, usageEnabled, enabled, breadcrumbsEnabled);
            case "breadcrumbs" -> new TelemetryConsentSnapshot(projectEnabled, crashEnabled, errorEnabled, diagnosticsEnabled, lifecycleEnabled, performanceEnabled, usageEnabled, statsEnabled, enabled);
            default -> this;
        };
    }
}
