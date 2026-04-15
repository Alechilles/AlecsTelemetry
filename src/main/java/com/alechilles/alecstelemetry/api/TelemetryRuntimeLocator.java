package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Static locator for optional dependency integrations.
 */
public final class TelemetryRuntimeLocator {

    private static final AtomicReference<TelemetryRuntimeApi> CURRENT = new AtomicReference<>();

    private TelemetryRuntimeLocator() {
    }

    public static void register(@Nonnull TelemetryRuntimeApi api) {
        CURRENT.set(api);
    }

    public static void clear() {
        CURRENT.set(null);
    }

    @Nullable
    public static TelemetryRuntimeApi tryGet() {
        return CURRENT.get();
    }
}
