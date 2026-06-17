package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Local view of the active coordinator bridge.
 *
 * <p>Implementations and reflective proxies must expose only JDK types so shaded runtime copies
 * loaded by different plugin classloaders can still interoperate.</p>
 */
public interface TelemetryCoordinatorBridge {

    @Nonnull
    String providerId();

    @Nonnull
    String origin();

    @Nonnull
    String runtimeVersion();

    int coordinatorProtocolVersion();

    @Nonnull
    String providerPluginIdentifier();

    @Nonnull
    String providerPluginVersion();

    @Nonnull
    String sourcePath();

    @Nonnull
    String sharedDataRoot();

    void activate();

    void deactivate();

    boolean isActive();

    default void start() {
    }

    default void shutdown() {
    }

    default boolean recordBreadcrumb(@Nonnull String projectId,
                                     @Nonnull String category,
                                     @Nonnull String detail) {
        return false;
    }

    default boolean recordError(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nullable Throwable throwable,
                                @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordLifecycle(@Nonnull String projectId,
                                    @Nonnull String eventName,
                                    int durationMs,
                                    boolean success,
                                    @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordPerformance(@Nonnull String projectId,
                                      @Nonnull String eventName,
                                      int durationMs,
                                      @Nullable Double metricValue,
                                      @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordUsage(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean recordStats(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull Map<String, Object> details) {
        return false;
    }

    default boolean captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        return false;
    }

    default boolean captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
        return false;
    }

    default boolean requestFlush(@Nullable String projectId) {
        return false;
    }

    default boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return false;
    }
}
