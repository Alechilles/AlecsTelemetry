package com.alechilles.alecstelemetry.embedded;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Embedded telemetry handle owned by one consumer mod.
 */
public interface EmbeddedTelemetryHandle {

    @Nonnull
    String projectId();

    @Nonnull
    String displayName();

    boolean isEnabled();

    @Nullable
    String disabledReason();

    void start();

    void shutdown();

    void recordBreadcrumb(@Nonnull String category, @Nonnull String detail);

    void captureSetupFailure(@Nullable Throwable throwable);

    void captureStartFailure(@Nullable Throwable throwable);

    void captureExceptionalWorldRemoval(@Nullable World world, @Nullable RemoveWorldEvent.RemovalReason removalReason);

    boolean requestFlush();

    boolean captureTestReport(@Nullable String detail);
}
