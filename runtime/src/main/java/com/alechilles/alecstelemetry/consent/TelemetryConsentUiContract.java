package com.alechilles.alecstelemetry.consent;

import javax.annotation.Nonnull;

final class TelemetryConsentUiContract {
    static final String ACTION_TOGGLE_ALL = "toggle_all";
    static final String ACTION_TOGGLE_GLOBAL_CATEGORY = "toggle_global_category";
    static final String ACTION_TOGGLE_PROJECT = "toggle_project";
    static final String ACTION_TOGGLE_CATEGORY = "toggle_category";
    static final String ACTION_SHOW_PRIVACY_DISCLAIMER = "show_privacy_disclaimer";
    static final String ACTION_HIDE_PRIVACY_DISCLAIMER = "hide_privacy_disclaimer";
    static final String ACTION_SAVE = "save";
    static final String ACTION_CLOSE = "close";

    private TelemetryConsentUiContract() {
    }

    @Nonnull
    static String projectToggleSelector(int index) {
        return rowSelector(index) + " #TelemetryConsentProjectToggleButton";
    }

    @Nonnull
    static String categoryCheckSelector(int index, @Nonnull String category) {
        return rowSelector(index) + " #TelemetryConsent" + selectorToken(category) + "Enabled";
    }

    @Nonnull
    static String categoryToggleSelector(int index, @Nonnull String category) {
        return rowSelector(index) + " #TelemetryConsent" + selectorToken(category) + "ToggleButton";
    }

    @Nonnull
    static String globalCategoryCheckSelector(@Nonnull String category) {
        return "#TelemetryConsent" + selectorToken(category) + "AllEnabled";
    }

    @Nonnull
    static String globalCategoryToggleSelector(@Nonnull String category) {
        return "#TelemetryConsent" + selectorToken(category) + "AllToggleButton";
    }

    @Nonnull
    static String selectorToken(@Nonnull String category) {
        if ("crash".equals(category)) {
            return "Capture";
        }
        return capitalize(category);
    }

    @Nonnull
    private static String rowSelector(int index) {
        return "#TelemetryConsentProjectRow" + index;
    }

    @Nonnull
    private static String capitalize(@Nonnull String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
