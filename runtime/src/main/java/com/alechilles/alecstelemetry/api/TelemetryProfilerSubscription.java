package com.alechilles.alecstelemetry.api;

/** Handle for one profiler snapshot subscription. */
@FunctionalInterface
public interface TelemetryProfilerSubscription extends AutoCloseable {
    @Override
    void close();

    static TelemetryProfilerSubscription closed() {
        return () -> {
        };
    }
}
