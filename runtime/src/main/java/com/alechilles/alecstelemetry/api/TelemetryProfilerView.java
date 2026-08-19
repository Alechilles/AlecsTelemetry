package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Project-scoped view of the optional local profiler. */
public interface TelemetryProfilerView {
    String CAPABILITY = "profiler-api-v1";

    @Nonnull
    TelemetryProfilerStatus status();

    @Nonnull
    Optional<TelemetryProfilerSnapshot> latest();

    @Nonnull
    List<TelemetryProfilerSnapshot> history();

    @Nonnull
    TelemetryProfilerSubscription subscribe(
            @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener);

    @Nonnull
    static TelemetryProfilerView unavailable() {
        return Unavailable.INSTANCE;
    }

    enum Unavailable implements TelemetryProfilerView {
        INSTANCE;

        @Nonnull
        @Override
        public TelemetryProfilerStatus status() {
            return TelemetryProfilerStatus.unavailable();
        }

        @Nonnull
        @Override
        public Optional<TelemetryProfilerSnapshot> latest() {
            return Optional.empty();
        }

        @Nonnull
        @Override
        public List<TelemetryProfilerSnapshot> history() {
            return List.of();
        }

        @Nonnull
        @Override
        public TelemetryProfilerSubscription subscribe(
                @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener) {
            Objects.requireNonNull(listener, "listener");
            return TelemetryProfilerSubscription.closed();
        }
    }
}
