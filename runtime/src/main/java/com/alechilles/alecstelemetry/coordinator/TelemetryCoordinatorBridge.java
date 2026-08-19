package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    /** Replays the complete immutable contribution snapshot before provider activation. */
    default boolean reconcileProjectContributions(long revision,
                                                  @Nonnull List<Map<String, Object>> contributions) {
        return contributions == null || contributions.isEmpty();
    }

    /** Routes a token-authorized contributor operation through the active provider. */
    @Nonnull
    default Map<String, Object> dispatchProjectContribution(@Nonnull String token,
                                                             @Nonnull String operation,
                                                             @Nonnull Map<String, Object> payload,
                                                             @Nullable Throwable throwable) {
        return Map.of("accepted", false, "reason", "contribution_dispatch_unavailable");
    }

    default boolean isEnabled() {
        return isActive();
    }

    @Nonnull
    default List<Map<String, Object>> projectSummaries() {
        return List.of();
    }

    /** Optional JDK-only profiler capability advertised by newer providers. */
    @Nonnull
    default List<String> capabilities() {
        return List.of();
    }

    /** Returns a JDK-only profiler status payload for one project. */
    @Nonnull
    default Map<String, Object> profilerStatus(@Nonnull String projectId) {
        return Map.of();
    }

    /** Returns a JDK-only latest profiler snapshot payload for one project. */
    @Nonnull
    default Map<String, Object> profilerLatest(@Nonnull String projectId) {
        return Map.of();
    }

    /** Returns JDK-only profiler history payloads for one project. */
    @Nonnull
    default List<Map<String, Object>> profilerHistory(@Nonnull String projectId) {
        return List.of();
    }

    /** Subscribes a JDK-only listener and returns an opaque provider-scoped ID. */
    @Nonnull
    default String subscribeProfiler(@Nonnull String projectId,
                                     @Nonnull Consumer<Map<String, Object>> listener) {
        return "";
    }

    /** Removes one provider-scoped profiler subscription. */
    default boolean unsubscribeProfiler(@Nonnull String subscriptionId) {
        return false;
    }

    @Nonnull
    default Map<String, Object> findProjectSummary(@Nonnull String projectId) {
        for (Map<String, Object> project : projectSummaries()) {
            Object id = project.get("projectId");
            if (id != null && id.toString().equalsIgnoreCase(projectId.trim())) {
                return project;
            }
        }
        return Map.of();
    }

    default boolean isProjectEnabled(@Nonnull String projectId) {
        return false;
    }

    default boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    default boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
        return false;
    }

    @Nonnull
    default Map<String, Object> consentDiagnostics() {
        return Map.of();
    }

    @Nonnull
    default Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
        Object projects = consentDiagnostics().get("projects");
        if (!(projects instanceof List<?> projectList)) {
            return Map.of();
        }
        for (Object rawProject : projectList) {
            if (!(rawProject instanceof Map<?, ?> project)) {
                continue;
            }
            Object id = project.get("projectId");
            if (id != null && id.toString().equalsIgnoreCase(projectId.trim())) {
                return copyStringObjectMap(project);
            }
        }
        return Map.of();
    }

    default boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
        return false;
    }

    default boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
        return false;
    }

    default boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
        return false;
    }

    default boolean markConsentReviewed(@Nonnull String projectId) {
        return false;
    }

    default boolean recordBreadcrumb(@Nonnull String projectId,
                                     @Nonnull String category,
                                     @Nonnull String detail) {
        return false;
    }

    default boolean recordBreadcrumb(@Nonnull String projectId,
                                     @Nonnull String category,
                                     @Nonnull String detail,
                                     @Nonnull Map<String, Object> context) {
        return recordBreadcrumb(projectId, category, detail);
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

    default boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable,
                                                   @Nullable String worldName,
                                                   @Nullable String removalReason,
                                                   @Nullable String possibleFailureCause) {
        return false;
    }

    default boolean requestFlush(@Nullable String projectId) {
        return false;
    }

    @Nonnull
    default TelemetryServerVerificationResult requestServerVerification() {
        return TelemetryServerVerificationResult.unavailable("active_coordinator_unavailable");
    }

    @Nonnull
    default TelemetryServerVerificationResult requestServerVerification(@Nullable String claimToken) {
        return requestServerVerification();
    }

    default boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
        return false;
    }

    @Nonnull
    default Map<String, Object> submitManualReport(@Nonnull String projectId,
                                                   @Nonnull Map<String, Object> submission,
                                                   @Nonnull Map<String, Object> playerContext) {
        return Map.of("accepted", false, "validationErrors", List.of("coordinator_manual_reports_unavailable"));
    }
    @Nonnull
    default List<Map<String, Object>> manualReportProjectSummaries() {
        return List.of();
    }

    @Nonnull
    default Map<String, Object> findManualReportProjectSummary(@Nonnull String projectId) {
        for (Map<String, Object> project : manualReportProjectSummaries()) {
            Object id = project.get("projectId");
            if (id != null && id.toString().equalsIgnoreCase(projectId.trim())) {
                return project;
            }
        }
        return Map.of();
    }

    @Nonnull
    default String manualReportReceiptStatus(@Nonnull String reportId) {
        return "unknown";
    }

    @Nonnull
    default List<Map<String, Object>> manualReportsForReview(int maxReportsPerProject) {
        return List.of();
    }

    default boolean approveManualReport(@Nonnull String reportId) {
        return false;
    }

    default boolean rejectManualReport(@Nonnull String reportId) {
        return false;
    }

    @Nonnull
    default List<String> submittedManualReportAuditLines(int maxLines) {
        return List.of();
    }

    @Nonnull
    private static Map<String, Object> copyStringObjectMap(@Nonnull Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            copy.put(entry.getKey().toString(), entry.getValue());
        }
        return Map.copyOf(copy);
    }
}
