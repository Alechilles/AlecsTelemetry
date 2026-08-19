package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

record SparkProfileReadResult(@Nonnull Status status,
                              @Nullable String sparkVersion,
                              @Nullable SparkProfileSnapshot snapshot,
                              @Nullable SparkProfileWindow window,
                              @Nonnull String detail) {

    enum Status {
        COMPLETE,
        ABSENT,
        UNSUPPORTED_PLUGIN,
        UNSUPPORTED_VERSION,
        UNSUPPORTED_ARTIFACT,
        NOT_RUNNING,
        NOT_BACKGROUND,
        UNSUPPORTED_SAMPLER,
        NO_DATA,
        NO_ACTIONABLE_DATA,
        TOO_COMPLEX,
        INCOMPATIBLE
    }

    @Nonnull
    static SparkProfileReadResult complete(@Nonnull SparkProfileSnapshot snapshot,
                                           @Nonnull SparkProfileWindow window) {
        return new SparkProfileReadResult(
                Status.COMPLETE,
                snapshot.sparkVersion(),
                snapshot,
                window,
                "Latest completed Spark background window was summarized locally."
        );
    }

    /** Compatibility constructor for existing global-only profiler fixtures. */
    @Nonnull
    static SparkProfileReadResult complete(@Nonnull SparkProfileSnapshot snapshot) {
        return new SparkProfileReadResult(
                Status.COMPLETE,
                snapshot.sparkVersion(),
                snapshot,
                null,
                "Latest completed Spark background window was summarized locally."
        );
    }

    @Nonnull
    static SparkProfileReadResult of(@Nonnull Status status,
                                     @Nullable String sparkVersion,
                                     @Nonnull String detail) {
        return new SparkProfileReadResult(status, sparkVersion, null, null, detail);
    }

    @Nonnull
    static SparkProfileReadResult noData(@Nonnull String detail) {
        return of(Status.NO_DATA, null, detail);
    }

    @Nonnull
    static SparkProfileReadResult noActionableData(@Nonnull String sparkVersion,
                                                    @Nonnull String detail) {
        return of(Status.NO_ACTIONABLE_DATA, sparkVersion, detail);
    }

    @Nonnull
    static SparkProfileReadResult noActionableData(@Nonnull String detail) {
        return of(Status.NO_ACTIONABLE_DATA, null, detail);
    }
}

@FunctionalInterface
interface SparkProfileReader {
    @Nonnull
    SparkProfileReadResult read(@Nullable Object sparkPlugin, int maxEntries);
}
