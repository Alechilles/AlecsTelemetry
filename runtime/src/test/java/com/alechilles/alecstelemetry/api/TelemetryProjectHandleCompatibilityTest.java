package com.alechilles.alecstelemetry.api;

import com.alechilles.alecstelemetry.api.internal.TelemetryProjectHandleImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeApiImpl;
import com.alechilles.alecstelemetry.api.internal.TelemetryRuntimeOperations;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.reports.TelemetryReportOpenRequest;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProjectHandleCompatibilityTest {

    @Test
    void nullableStringDetailCallsRemainSourceCompatible() {
        TelemetryProjectHandle handle = new NoopTelemetryProjectHandle();

        handle.recordUsage("usage_event", null);
        handle.recordStats("stats_event", null);
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

    @Test
    void runtimeBackedReportPageOpenDelegatesToRuntimeOperations() {
        FakeRuntimeOperations runtime = new FakeRuntimeOperations(true, true);
        runtime.openReportPageResult = true;
        TelemetryProjectHandle handle = new TelemetryProjectHandleImpl(runtime, "example-mod");
        TelemetryReportOpenRequest request = new TelemetryReportOpenRequest("issue", null, null);

        assertTrue(handle.openReportPage(null, null, null, request));
        assertEquals("example-mod", runtime.openedProjectId);
        assertSame(request, runtime.openedRequest);
    }

    @Test
    void runtimeApiEnabledFollowsGlobalRuntimeOperation() {
        TelemetryRuntimeApi disabledApi = new TelemetryRuntimeApiImpl(new FakeRuntimeOperations(false, true));
        TelemetryRuntimeApi enabledApi = new TelemetryRuntimeApiImpl(new FakeRuntimeOperations(true, false));

        assertFalse(disabledApi.isEnabled());
        assertTrue(enabledApi.isEnabled());
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

    private static final class FakeRuntimeOperations implements TelemetryRuntimeOperations {
        private final boolean enabled;
        private final boolean projectEnabled;
        private final TelemetryProjectRegistration project;
        private boolean openReportPageResult;
        private String openedProjectId;
        private TelemetryReportOpenRequest openedRequest;

        private FakeRuntimeOperations(boolean enabled, boolean projectEnabled) {
            this.enabled = enabled;
            this.projectEnabled = projectEnabled;
            this.project = project("example-mod", "Example Mod");
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Nonnull
        @Override
        public List<TelemetryProjectRegistration> projects() {
            return List.of(project);
        }

        @Nullable
        @Override
        public TelemetryProjectRegistration findProject(@Nonnull String projectId) {
            return project.projectId().equals(projectId) ? project : null;
        }

        @Override
        public boolean isProjectEnabled(@Nonnull String projectId) {
            return projectEnabled;
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            return false;
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return false;
        }

        @Override
        public boolean openReportPage(@Nonnull String projectId,
                                      @Nonnull Ref<EntityStore> playerEntityRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull TelemetryReportOpenRequest request) {
            openedProjectId = projectId;
            openedRequest = request;
            return openReportPageResult;
        }

        @Override
        public void recordBreadcrumb(@Nonnull String projectId, @Nonnull String category, @Nonnull String detail) {
        }

        @Override
        public void captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        }

        @Override
        public void captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        }

        @Override
        public void recordErrorWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable Throwable throwable,
                                           @Nullable TelemetryEventContext context) {
        }

        @Override
        public void recordLifecycleWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               int durationMs,
                                               boolean success,
                                               @Nullable TelemetryEventContext context) {
        }

        @Override
        public void recordPerformanceWithContext(@Nonnull String projectId,
                                                 @Nonnull String eventName,
                                                 int durationMs,
                                                 @Nullable Double metricValue,
                                                 @Nullable TelemetryEventContext context) {
        }

        @Override
        public void recordUsageWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable TelemetryEventContext context) {
        }

        @Override
        public void recordStatsWithContext(@Nonnull String projectId,
                                           @Nonnull String eventName,
                                           @Nullable TelemetryEventContext context) {
        }

        @Nonnull
        private static TelemetryProjectRegistration project(@Nonnull String projectId, @Nonnull String displayName) {
            TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                    "{\"projectId\":\"" + projectId + "\",\"displayName\":\"" + displayName + "\"}",
                    null
            );
            return new TelemetryProjectRegistration(descriptor, "Example:" + displayName, "1.0.0", null);
        }
    }
}
