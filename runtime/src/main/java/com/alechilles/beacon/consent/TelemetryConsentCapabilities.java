package com.alechilles.beacon.consent;

import com.alechilles.beacon.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Shared projection of the telemetry categories a project supports.
 */
public final class TelemetryConsentCapabilities {

    private static final List<String> CATEGORY_ORDER = List.of(
            "crash",
            "error",
            "diagnostics",
            "lifecycle",
            "performance",
            "usage",
            "stats",
            "breadcrumbs"
    );

    private TelemetryConsentCapabilities() {
    }

    @Nonnull
    public static TelemetryConsentSnapshot supportedSnapshot(@Nonnull TelemetryProjectRegistration project) {
        return new TelemetryConsentSnapshot(
                true,
                project.descriptor().capture().supportsAnySource(),
                project.descriptor().events().errors().supported(),
                project.descriptor().diagnostics().supported(),
                project.descriptor().events().lifecycle().supported(),
                project.descriptor().performance().supported(),
                project.descriptor().usage().supported(),
                project.descriptor().stats().supported(),
                project.descriptor().events().breadcrumbs().supported()
        );
    }

    @Nonnull
    public static List<String> supportedCategoryNames(@Nonnull TelemetryProjectRegistration project) {
        TelemetryConsentSnapshot supported = supportedSnapshot(project);
        return CATEGORY_ORDER.stream()
                .filter(supported::categoryEnabled)
                .toList();
    }

    @Nonnull
    public static String fingerprint(@Nonnull TelemetryProjectRegistration project) {
        return String.join(";", supportedCategoryNames(project));
    }
}
