package com.alechilles.alecstelemetry.api;

import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryProjectHandleCompatibilityTest {

    @Test
    void nullableStringDetailCallsRemainSourceCompatible() {
        TelemetryProjectHandle handle = new NoopTelemetryProjectHandle();

        handle.recordUsage("usage_event", null);
        handle.recordStats("stats_event", null);
        handle.recordSimpleStatChart("language", "English");
        handle.recordNumericStatChart("active_npcs", 12);
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
        handle.recordStatsWithContext("stats_event", TelemetryEventContext.stats().detail("playersOnline", 3).build());
        handle.recordErrorWithContext("error_event", null, context);
        handle.recordLifecycleWithContext("lifecycle_event", 0, true, context);
        handle.recordPerformanceWithContext("performance_event", 0, null, context);
    }

    @Test
    void reportPageOpenRequestNormalizesKind() {
        assertEquals("issue", new TelemetryReportOpenRequest("bug", " title ", " desc ").normalizedKind());
        assertEquals("suggestion", new TelemetryReportOpenRequest("Suggestion", null, null).normalizedKind());
    }

    @Test
    void defaultReportPageOpenReturnsFalseWhenRuntimeIsUnavailable() {
        TelemetryProjectHandle handle = new NoopTelemetryProjectHandle();

        assertFalse(handle.openReportPage(
                null,
                null,
                null,
                new TelemetryReportOpenRequest("issue", null, null)
        ));
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
        public void recordStats(@Nonnull String eventName, @Nullable String detail) {
        }

        @Override
        public boolean requestFlush() {
            return false;
        }
    }
}
