package com.alechilles.beacon.report;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Player-submitted report kind.
 */
public enum ManualReportKind {
    ISSUE("issue"),
    SUGGESTION("suggestion");

    private final String key;

    ManualReportKind(@Nonnull String key) {
        this.key = key;
    }

    @Nonnull
    public String key() {
        return key;
    }

    @Nonnull
    public static ManualReportKind normalize(@Nullable ManualReportKind value) {
        return value == null ? ISSUE : value;
    }
}
