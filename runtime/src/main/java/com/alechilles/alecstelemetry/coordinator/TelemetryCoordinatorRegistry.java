package com.alechilles.alecstelemetry.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM-wide coordinator registry shared by standalone and embedded telemetry copies.
 */
public final class TelemetryCoordinatorRegistry {

    public static final int COORDINATOR_PROTOCOL_VERSION = 3;
    public static final String REGISTRY_KEY = "com.alechilles.alecstelemetry.coordinator.registry.protocol.v3";
    public static final String LEGACY_PROTOCOL_TWO_REGISTRY_KEY = "com.alechilles.alecstelemetry.coordinator.registry.protocol.v2";
    public static final String LEGACY_PROTOCOL_ONE_REGISTRY_KEY = "com.alechilles.alecstelemetry.coordinator.registry.v1";

    private static final ConcurrentHashMap<String, Long> RECONCILED_REVISIONS = new ConcurrentHashMap<>();

    private TelemetryCoordinatorRegistry() {
    }

    public static void register(@Nonnull Object bridge) {
        TelemetryRuntimeCandidate candidate = candidateFrom(bridge);
        if (candidate == null) {
            return;
        }
        Object previous = registry().put(candidate.providerId(), bridge);
        RECONCILED_REVISIONS.remove(candidate.providerId());
        if (previous != null && previous != bridge) {
            ReflectiveBridge previousBridge = new ReflectiveBridge(previous, candidateFrom(previous));
            if (previousBridge.isActive()) {
                ReflectiveBridge replacementBridge = new ReflectiveBridge(bridge, candidate);
                if (!reconcileBeforeActivation(replacementBridge)) {
                    registry().put(candidate.providerId(), previous);
                    RECONCILED_REVISIONS.remove(candidate.providerId());
                    return;
                }
                previousBridge.shutdown();
                previousBridge.deactivate();
            }
        }
        elect();
    }

    public static void unregister(@Nonnull String providerId) {
        Object removed = registry().remove(providerId);
        RECONCILED_REVISIONS.remove(providerId);
        if (removed != null) {
            ReflectiveBridge bridge = new ReflectiveBridge(removed, candidateFrom(removed));
            if (bridge.isActive()) {
                bridge.shutdown();
                bridge.deactivate();
            }
        }
        elect();
    }

    @Nullable
    public static TelemetryCoordinatorBridge activeBridge() {
        TelemetryRuntimeCandidate winner = TelemetryRuntimeCandidate.selectWinner(
                candidates(),
                COORDINATOR_PROTOCOL_VERSION
        );
        if (winner == null) {
            return null;
        }
        Object bridge = registry().get(winner.providerId());
        if (bridge == null) {
            return null;
        }
        ReflectiveBridge active = new ReflectiveBridge(bridge, winner);
        if (reconcileBeforeActivation(active)) {
            return active;
        }
        for (Object value : registry().values()) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate == null) {
                continue;
            }
            ReflectiveBridge previous = new ReflectiveBridge(value, candidate);
            if (previous.isActive()) {
                return previous;
            }
        }
        return null;
    }

    public static void clearForTests() {
        ConcurrentHashMap<String, Object> existing = registry();
        for (Map.Entry<String, Object> entry : existing.entrySet()) {
            TelemetryRuntimeCandidate candidate = candidateFrom(entry.getValue());
            ReflectiveBridge bridge = new ReflectiveBridge(entry.getValue(), candidate);
            if (bridge.isActive()) {
                bridge.shutdown();
                bridge.deactivate();
            }
        }
        existing.clear();
        System.getProperties().remove(REGISTRY_KEY);
        System.getProperties().remove(LEGACY_PROTOCOL_TWO_REGISTRY_KEY);
        System.getProperties().remove(LEGACY_PROTOCOL_ONE_REGISTRY_KEY);
        RECONCILED_REVISIONS.clear();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> registry() {
        Properties properties = System.getProperties();
        Object existing = properties.get(REGISTRY_KEY);
        if (existing instanceof ConcurrentHashMap<?, ?> map) {
            return (ConcurrentHashMap<String, Object>) map;
        }
        ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<>();
        Object raced = properties.putIfAbsent(REGISTRY_KEY, created);
        return raced instanceof ConcurrentHashMap<?, ?> map
                ? (ConcurrentHashMap<String, Object>) map
                : created;
    }

    private static void elect() {
        TelemetryRuntimeCandidate winner = TelemetryRuntimeCandidate.selectWinner(
                candidates(),
                COORDINATOR_PROTOCOL_VERSION
        );
        List<Object> bridges = List.copyOf(registry().values());
        for (Object value : bridges) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate == null) {
                continue;
            }
            ReflectiveBridge bridge = new ReflectiveBridge(value, candidate);
            if (bridge.isActive()) {
                break;
            }
        }
        if (winner != null) {
            Object winningBridge = registry().get(winner.providerId());
            if (winningBridge == null) {
                return;
            }
            ReflectiveBridge replayTarget = new ReflectiveBridge(winningBridge, winner);
            if (!reconcileBeforeActivation(replayTarget)) {
                // Failed replay leaves the previous active provider and catalog intact.
                return;
            }
        }
        for (Object value : bridges) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate == null) {
                continue;
            }
            ReflectiveBridge bridge = new ReflectiveBridge(value, candidate);
            boolean shouldBeActive = winner != null && candidate.providerId().equals(winner.providerId());
            if (!shouldBeActive && bridge.isActive()) {
                bridge.shutdown();
                bridge.deactivate();
            }
        }
        if (winner == null) {
            return;
        }
        Object winningBridge = registry().get(winner.providerId());
        if (winningBridge == null) {
            return;
        }
        ReflectiveBridge bridge = new ReflectiveBridge(winningBridge, winner);
        if (!bridge.isActive()) {
            bridge.activate();
            bridge.start();
        }
    }

    private static boolean reconcileBeforeActivation(@Nonnull ReflectiveBridge bridge) {
        long revision = TelemetryProjectContributionRegistry.revision();
        Long previous = RECONCILED_REVISIONS.get(bridge.providerId());
        if (previous != null && previous == revision) {
            return true;
        }
        boolean reconciled = bridge.reconcileProjectContributions(
                revision,
                TelemetryProjectContributionRegistry.activeContributions()
        );
        if (reconciled) {
            RECONCILED_REVISIONS.put(bridge.providerId(), revision);
        }
        return reconciled;
    }

    @Nonnull
    private static List<TelemetryRuntimeCandidate> candidates() {
        ArrayList<TelemetryRuntimeCandidate> candidates = new ArrayList<>();
        for (Object value : registry().values()) {
            TelemetryRuntimeCandidate candidate = candidateFrom(value);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    @Nullable
    private static TelemetryRuntimeCandidate candidateFrom(@Nullable Object bridge) {
        if (bridge == null) {
            return null;
        }
        try {
            TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                    invokeString(bridge, "providerId"),
                    TelemetryRuntimeOrigin.valueOf(invokeString(bridge, "origin")),
                    invokeString(bridge, "runtimeVersion"),
                    invokeInt(bridge, "coordinatorProtocolVersion"),
                    invokeString(bridge, "providerPluginIdentifier"),
                    invokeString(bridge, "providerPluginVersion"),
                    Path.of(invokeString(bridge, "sourcePath")),
                    Path.of(invokeString(bridge, "sharedDataRoot"))
            );
            if (candidate.coordinatorProtocolVersion() >= COORDINATOR_PROTOCOL_VERSION
                    && !supportsProtocolThree(bridge)) {
                return null;
            }
            return candidate;
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static boolean supportsProtocolThree(@Nonnull Object bridge) {
        try {
            findMethod(bridge.getClass(), "reconcileProjectContributions", new Class<?>[]{long.class, List.class});
            findMethod(bridge.getClass(), "dispatchProjectContribution", new Class<?>[]{String.class, String.class, Map.class, Throwable.class});
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    @Nonnull
    private static String invokeString(@Nonnull Object target, @Nonnull String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        return value == null ? "" : value.toString();
    }

    private static int invokeInt(@Nonnull Object target, @Nonnull String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Nullable
    private static Object invoke(@Nonnull Object target,
                                 @Nonnull String methodName,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @Nullable
    private static Object invoke(@Nonnull Object target,
                                 @Nonnull String methodName) throws ReflectiveOperationException {
        return invoke(target, methodName, new Class<?>[0]);
    }

    @Nonnull
    private static Method findMethod(@Nonnull Class<?> type,
                                     @Nonnull String methodName,
                                     @Nonnull Class<?>[] parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return type.getMethod(methodName, parameterTypes);
    }

    private record ReflectiveBridge(@Nonnull Object delegate,
                                    @Nullable TelemetryRuntimeCandidate candidate) implements TelemetryCoordinatorBridge {

        @Override
        public String providerId() {
            return candidate == null ? "" : candidate.providerId();
        }

        @Override
        public String origin() {
            return candidate == null ? "" : candidate.origin().name();
        }

        @Override
        public String runtimeVersion() {
            return candidate == null ? "" : candidate.runtimeVersion();
        }

        @Override
        public int coordinatorProtocolVersion() {
            return candidate == null ? 0 : candidate.coordinatorProtocolVersion();
        }

        @Override
        public String providerPluginIdentifier() {
            return candidate == null ? "" : candidate.providerPluginIdentifier();
        }

        @Override
        public String providerPluginVersion() {
            return candidate == null ? "" : candidate.providerPluginVersion();
        }

        @Override
        public String sourcePath() {
            return candidate == null ? "" : candidate.sourcePath().toString();
        }

        @Override
        public String sharedDataRoot() {
            return candidate == null ? "" : candidate.sharedDataRoot().toString();
        }

        @Override
        public void activate() {
            invokeQuiet("activate", new Class<?>[0]);
        }

        @Override
        public void deactivate() {
            invokeQuiet("deactivate", new Class<?>[0]);
        }

        @Override
        public boolean isActive() {
            try {
                Object value = invoke(delegate, "isActive");
                return value instanceof Boolean active && active;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public void start() {
            invokeQuiet("start", new Class<?>[0]);
        }

        @Override
        public void shutdown() {
            invokeQuiet("shutdown", new Class<?>[0]);
        }

        @Override
        public boolean reconcileProjectContributions(long revision,
                                                     @Nonnull List<Map<String, Object>> contributions) {
            try {
                Object value = invoke(
                        delegate,
                        "reconcileProjectContributions",
                        new Class<?>[]{long.class, List.class},
                        revision,
                        contributions
                );
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                return false;
            }
        }

        @Nonnull
        @Override
        public Map<String, Object> dispatchProjectContribution(@Nonnull String token,
                                                               @Nonnull String operation,
                                                               @Nonnull Map<String, Object> payload,
                                                               @Nullable Throwable throwable) {
            try {
                Object value = invoke(
                        delegate,
                        "dispatchProjectContribution",
                        new Class<?>[]{String.class, String.class, Map.class, Throwable.class},
                        token,
                        operation,
                        payload,
                        throwable
                );
                if (value instanceof Map<?, ?> map) {
                    return stringObjectMap(map);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return TelemetryCoordinatorBridge.super.dispatchProjectContribution(token, operation, payload, throwable);
        }

        @Override
        public boolean isEnabled() {
            try {
                Object value = invoke(delegate, "isEnabled");
                return value instanceof Boolean enabled ? enabled : TelemetryCoordinatorBridge.super.isEnabled();
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.isEnabled();
            }
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> projectSummaries() {
            try {
                Object value = invoke(delegate, "projectSummaries");
                if (!(value instanceof List<?> list)) {
                    return TelemetryCoordinatorBridge.super.projectSummaries();
                }
                ArrayList<Map<String, Object>> projects = new ArrayList<>();
                for (Object project : list) {
                    if (project instanceof Map<?, ?> map) {
                        projects.add(stringObjectMap(map));
                    }
                }
                return List.copyOf(projects);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.projectSummaries();
            }
        }

        @Nonnull
        @Override
        public Map<String, Object> findProjectSummary(@Nonnull String projectId) {
            try {
                Object value = invoke(
                        delegate,
                        "findProjectSummary",
                        new Class<?>[]{String.class},
                        projectId
                );
                if (value instanceof Map<?, ?> map) {
                    return stringObjectMap(map);
                }
                return TelemetryCoordinatorBridge.super.findProjectSummary(projectId);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.findProjectSummary(projectId);
            }
        }

        @Override
        public boolean isProjectEnabled(@Nonnull String projectId) {
            return invokeBoolean(
                    "isProjectEnabled",
                    new Class<?>[]{String.class},
                    projectId
            );
        }

        @Override
        public boolean setProjectEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setProjectEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setCrashEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setCrashEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setErrorEventsEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setErrorEventsEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setLifecycleEventsEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setLifecycleEventsEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setPerformanceEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setPerformanceEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setUsageEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setUsageEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setStatsEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setStatsEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Override
        public boolean setBreadcrumbsEnabled(@Nonnull String projectId, boolean enabled) {
            return invokeBoolean(
                    "setBreadcrumbsEnabled",
                    new Class<?>[]{String.class, boolean.class},
                    projectId,
                    enabled
            );
        }

        @Nonnull
        @Override
        public Map<String, Object> consentDiagnostics() {
            try {
                Object value = invoke(delegate, "consentDiagnostics");
                if (value instanceof Map<?, ?> map) {
                    return stringObjectMap(map);
                }
                return TelemetryCoordinatorBridge.super.consentDiagnostics();
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.consentDiagnostics();
            }
        }

        @Nonnull
        @Override
        public Map<String, Object> consentProjectDiagnostics(@Nonnull String projectId) {
            try {
                Object value = invoke(
                        delegate,
                        "consentProjectDiagnostics",
                        new Class<?>[]{String.class},
                        projectId
                );
                if (value instanceof Map<?, ?> map) {
                    return stringObjectMap(map);
                }
                return TelemetryCoordinatorBridge.super.consentProjectDiagnostics(projectId);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.consentProjectDiagnostics(projectId);
            }
        }

        @Override
        public boolean applyConsentToAll(@Nonnull Map<String, Object> snapshot) {
            return invokeBoolean(
                    "applyConsentToAll",
                    new Class<?>[]{Map.class},
                    snapshot
            );
        }

        @Override
        public boolean applyConsentCategoryToAll(@Nonnull String category, boolean enabled) {
            return invokeBoolean(
                    "applyConsentCategoryToAll",
                    new Class<?>[]{String.class, boolean.class},
                    category,
                    enabled
            );
        }

        @Override
        public boolean applyConsent(@Nonnull String projectId, @Nonnull Map<String, Object> snapshot) {
            return invokeBoolean(
                    "applyConsent",
                    new Class<?>[]{String.class, Map.class},
                    projectId,
                    snapshot
            );
        }

        @Override
        public boolean markConsentReviewed(@Nonnull String projectId) {
            return invokeBoolean(
                    "markConsentReviewed",
                    new Class<?>[]{String.class},
                    projectId
            );
        }

        @Override
        public boolean recordBreadcrumb(@Nonnull String projectId,
                                        @Nonnull String category,
                                        @Nonnull String detail) {
            return invokeBoolean(
                    "recordBreadcrumb",
                    new Class<?>[]{String.class, String.class, String.class},
                    projectId,
                    category,
                    detail
            );
        }

        @Override
        public boolean recordBreadcrumb(@Nonnull String projectId,
                                        @Nonnull String category,
                                        @Nonnull String detail,
                                        @Nonnull Map<String, Object> context) {
            boolean handled = invokeBoolean(
                    "recordBreadcrumb",
                    new Class<?>[]{String.class, String.class, String.class, Map.class},
                    projectId,
                    category,
                    detail,
                    context
            );
            return handled || recordBreadcrumb(projectId, category, detail);
        }

        @Override
        public boolean recordError(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nullable Throwable throwable,
                                   @Nonnull Map<String, Object> details) {
            return invokeBoolean(
                    "recordError",
                    new Class<?>[]{String.class, String.class, Throwable.class, Map.class},
                    projectId,
                    eventName,
                    throwable,
                    details
            );
        }

        @Override
        public boolean recordLifecycle(@Nonnull String projectId,
                                       @Nonnull String eventName,
                                       int durationMs,
                                       boolean success,
                                       @Nonnull Map<String, Object> details) {
            return invokeBoolean(
                    "recordLifecycle",
                    new Class<?>[]{String.class, String.class, int.class, boolean.class, Map.class},
                    projectId,
                    eventName,
                    durationMs,
                    success,
                    details
            );
        }

        @Override
        public boolean recordPerformance(@Nonnull String projectId,
                                         @Nonnull String eventName,
                                         int durationMs,
                                         @Nullable Double metricValue,
                                         @Nonnull Map<String, Object> details) {
            return invokeBoolean(
                    "recordPerformance",
                    new Class<?>[]{String.class, String.class, int.class, Double.class, Map.class},
                    projectId,
                    eventName,
                    durationMs,
                    metricValue,
                    details
            );
        }

        @Override
        public boolean recordUsage(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            return invokeBoolean(
                    "recordUsage",
                    new Class<?>[]{String.class, String.class, Map.class},
                    projectId,
                    eventName,
                    details
            );
        }

        @Override
        public boolean recordStats(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nonnull Map<String, Object> details) {
            return invokeBoolean(
                    "recordStats",
                    new Class<?>[]{String.class, String.class, Map.class},
                    projectId,
                    eventName,
                    details
            );
        }

        @Override
        public boolean captureSetupFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            return invokeBoolean(
                    "captureSetupFailure",
                    new Class<?>[]{String.class, Throwable.class},
                    projectId,
                    throwable
            );
        }

        @Override
        public boolean captureStartFailure(@Nonnull String projectId, @Nullable Throwable throwable) {
            return invokeBoolean(
                    "captureStartFailure",
                    new Class<?>[]{String.class, Throwable.class},
                    projectId,
                    throwable
            );
        }

        @Override
        public boolean captureExceptionalWorldRemoval(@Nullable Throwable throwable,
                                                      @Nullable String worldName,
                                                      @Nullable String removalReason,
                                                      @Nullable String possibleFailureCause) {
            return invokeBoolean(
                    "captureExceptionalWorldRemoval",
                    new Class<?>[]{Throwable.class, String.class, String.class, String.class},
                    throwable,
                    worldName,
                    removalReason,
                    possibleFailureCause
            );
        }

        @Override
        public boolean requestFlush(@Nullable String projectId) {
            return invokeBoolean(
                    "requestFlush",
                    new Class<?>[]{String.class},
                    projectId
            );
        }

        @Override
        public boolean captureTestReport(@Nonnull String projectId, @Nullable String detail) {
            return invokeBoolean(
                    "captureTestReport",
                    new Class<?>[]{String.class, String.class},
                    projectId,
                    detail
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> submitManualReport(@Nonnull String projectId,
                                                       @Nonnull Map<String, Object> submission,
                                                       @Nonnull Map<String, Object> playerContext) {
            try {
                Object value = invoke(
                        delegate,
                        "submitManualReport",
                        new Class<?>[]{String.class, Map.class, Map.class},
                        projectId,
                        submission,
                        playerContext
                );
                if (value instanceof Map<?, ?> result) {
                    return (Map<String, Object>) result;
                }
                return TelemetryCoordinatorBridge.super.submitManualReport(projectId, submission, playerContext);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.submitManualReport(projectId, submission, playerContext);
            }
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> manualReportProjectSummaries() {
            try {
                Object value = invoke(delegate, "manualReportProjectSummaries");
                if (!(value instanceof List<?> list)) {
                    return TelemetryCoordinatorBridge.super.manualReportProjectSummaries();
                }
                ArrayList<Map<String, Object>> projects = new ArrayList<>();
                for (Object project : list) {
                    if (project instanceof Map<?, ?> map) {
                        projects.add(stringObjectMap(map));
                    }
                }
                return List.copyOf(projects);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.manualReportProjectSummaries();
            }
        }

        @Nonnull
        @Override
        public Map<String, Object> findManualReportProjectSummary(@Nonnull String projectId) {
            try {
                Object value = invoke(
                        delegate,
                        "findManualReportProjectSummary",
                        new Class<?>[]{String.class},
                        projectId
                );
                if (value instanceof Map<?, ?> map) {
                    return stringObjectMap(map);
                }
                return TelemetryCoordinatorBridge.super.findManualReportProjectSummary(projectId);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.findManualReportProjectSummary(projectId);
            }
        }

        @Nonnull
        @Override
        public String manualReportReceiptStatus(@Nonnull String reportId) {
            try {
                Object value = invoke(
                        delegate,
                        "manualReportReceiptStatus",
                        new Class<?>[]{String.class},
                        reportId
                );
                return value == null ? TelemetryCoordinatorBridge.super.manualReportReceiptStatus(reportId) : value.toString();
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.manualReportReceiptStatus(reportId);
            }
        }

        @Nonnull
        @Override
        public List<Map<String, Object>> manualReportsForReview(int maxReportsPerProject) {
            try {
                Object value = invoke(
                        delegate,
                        "manualReportsForReview",
                        new Class<?>[]{int.class},
                        maxReportsPerProject
                );
                if (!(value instanceof List<?> list)) {
                    return TelemetryCoordinatorBridge.super.manualReportsForReview(maxReportsPerProject);
                }
                ArrayList<Map<String, Object>> reports = new ArrayList<>();
                for (Object report : list) {
                    if (report instanceof Map<?, ?> map) {
                        reports.add(stringObjectMap(map));
                    }
                }
                return List.copyOf(reports);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.manualReportsForReview(maxReportsPerProject);
            }
        }

        @Override
        public boolean approveManualReport(@Nonnull String reportId) {
            return invokeBoolean(
                    "approveManualReport",
                    new Class<?>[]{String.class},
                    reportId
            );
        }

        @Override
        public boolean rejectManualReport(@Nonnull String reportId) {
            return invokeBoolean(
                    "rejectManualReport",
                    new Class<?>[]{String.class},
                    reportId
            );
        }

        @Nonnull
        @Override
        public List<String> submittedManualReportAuditLines(int maxLines) {
            try {
                Object value = invoke(
                        delegate,
                        "submittedManualReportAuditLines",
                        new Class<?>[]{int.class},
                        maxLines
                );
                if (!(value instanceof List<?> list)) {
                    return TelemetryCoordinatorBridge.super.submittedManualReportAuditLines(maxLines);
                }
                ArrayList<String> lines = new ArrayList<>();
                for (Object line : list) {
                    if (line != null) {
                        lines.add(line.toString());
                    }
                }
                return List.copyOf(lines);
            } catch (ReflectiveOperationException ex) {
                return TelemetryCoordinatorBridge.super.submittedManualReportAuditLines(maxLines);
            }
        }
        private boolean invokeBoolean(@Nonnull String methodName,
                                      @Nonnull Class<?>[] parameterTypes,
                                      Object... args) {
            try {
                Object value = invoke(delegate, methodName, parameterTypes, args);
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        private void invokeQuiet(@Nonnull String methodName, @Nonnull Class<?>[] parameterTypes, Object... args) {
            try {
                invoke(delegate, methodName, parameterTypes, args);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Nonnull
        private static Map<String, Object> stringObjectMap(@Nonnull Map<?, ?> raw) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                    values.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return Map.copyOf(values);
        }
    }
}
