package com.alechilles.alecstelemetry.api;

import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class TelemetryProjectHandleCompatibilityTest {

    @Test
    void nullableStringDetailCallsRemainSourceCompatible() {
        TelemetryProjectHandle handle = new NoopTelemetryProjectHandle();

        handle.recordUsage("usage_event", null);
        handle.recordError("error_event", null, null);
        handle.recordLifecycle("lifecycle_event", 0, true, null);
        handle.recordPerformance("performance_event", 0, null, null);
    }

    @Test
    void typedContextUsesNonAmbiguousMethods() {
        TelemetryProjectHandle handle = new NoopTelemetryProjectHandle();
        TelemetryEventContext context = TelemetryEventContext.usage()
                .detail("source", "settings_ui")
                .build();

        handle.recordUsageWithContext("usage_event", context);
        handle.recordErrorWithContext("error_event", null, context);
        handle.recordLifecycleWithContext("lifecycle_event", 0, true, context);
        handle.recordPerformanceWithContext("performance_event", 0, null, context);
    }

    private static final class NoopTelemetryProjectHandle implements TelemetryProjectHandle {
        @Nonnull
        @Override
        public String projectId() {
            return "test";
        }

        @Nonnull
        @Override
        public String displayName() {
            return "Test";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void recordBreadcrumb(@Nonnull String category, @Nonnull String detail) {
        }

        @Override
        public void captureSetupFailure(@Nullable Throwable throwable) {
        }

        @Override
        public void captureStartFailure(@Nullable Throwable throwable) {
        }

        @Override
        public void recordError(@Nonnull String eventName, @Nullable Throwable throwable, @Nullable String detail) {
        }

        @Override
        public void recordLifecycle(@Nonnull String eventName, int durationMs, boolean success, @Nullable String detail) {
        }

        @Override
        public void recordPerformance(@Nonnull String eventName, int durationMs, @Nullable Double metricValue, @Nullable String detail) {
        }

        @Override
        public void recordUsage(@Nonnull String eventName, @Nullable String detail) {
        }

        @Override
        public boolean requestFlush() {
            return false;
        }
    }
}
