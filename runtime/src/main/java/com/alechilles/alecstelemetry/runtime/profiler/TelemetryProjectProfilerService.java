package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Publishes bounded, local project profiler snapshots and isolated listeners.
 *
 * <p>The service retains only public immutable values. Spark's decoded window
 * is consumed during {@link #publish(SparkProfileReadResult)} and never enters
 * a field, mailbox, or public value.</p>
 */
public final class TelemetryProjectProfilerService implements AutoCloseable {
    static final int MAX_PATHS_PER_SNAPSHOT = 5;
    static final int MAX_HISTORY_PER_PROJECT = 10;
    static final int MAX_RETAINED_SNAPSHOTS = 500;
    static final int MAX_RETAINED_PATH_ENTRIES = 500;
    static final int MAX_PROJECT_SUBSCRIPTIONS = 4;
    static final int MAX_GLOBAL_SUBSCRIPTIONS = 32;

    private static final long LISTENER_WARNING_INTERVAL_NANOS =
            TimeUnit.MINUTES.toNanos(1L);
    private static final long HISTORY_WARNING_INTERVAL_NANOS =
            TimeUnit.MINUTES.toNanos(1L);
    private static final String INITIALIZATION_DETAIL =
            "Profiler monitor diagnostics are not attached yet.";
    private static final String DISABLED_DETAIL =
            "Performance profiling is disabled for this project.";
    private static final String NO_USABLE_SAMPLES_DETAIL =
            "The latest Spark window contained no usable selected WorldThread samples.";
    private static final String EMPTY_PROJECT_DETAIL =
            "Latest Spark project profile window completed with no owned paths.";
    private static final String CLOSED_DETAIL = "Profiler service is shut down.";
    private static final String UNKNOWN_MONITOR_STATE_DETAIL =
            "Profiler monitor returned an unknown state.";

    private final Supplier<List<TelemetryProjectRegistration>> projectsSupplier;
    private final Predicate<String> currentlyPerformanceEligible;
    private final String physicalRuntimeVersion;
    private final BiConsumer<Level, String> logSink;
    private final ProfilerSignalEngine signalEngine;
    private final TelemetryProfilerContextProvider contextProvider;
    private final Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook;
    private final Runnable beforeDeliveryOfferHook;
    private final Runnable beforeContextDrainHook;
    private final Object lock = new Object();
    private final Object contextLifecycleLock = new Object();
    private final Map<String, ProjectState> projects = new LinkedHashMap<>();
    private final Map<String, ProjectView> currentViews = new HashMap<>();
    private final Map<String, ContextClearRequest> pendingContextClears = new HashMap<>();
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();
    private final ArrayDeque<HistoryEntry> retainedHistory = new ArrayDeque<>();

    private ProfilerProjectOwnershipIndex ownership;
    private String ownershipKey = "";
    private int truncatedPrefixCount;
    private int retainedPathEntries;
    private long lastHistoryWarningNanos;
    private Supplier<SparkProfilerDiagnostics> monitorDiagnostics;
    private long providerGeneration;
    private long contextRefreshGeneration;
    private ContextRefreshRequest pendingContextRefresh;
    private long publicationSequence;
    private boolean providerActive;
    private boolean closed;

    public TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink) {
        this(
                projects,
                currentlyPerformanceEligible,
                physicalRuntimeVersion,
                logSink,
                new ProfilerSignalEngine(),
                TelemetryProfilerContextProvider.unavailable(),
                ignored -> {
                },
                () -> {
                }
        );
    }

    TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook) {
        this(
                projects,
                currentlyPerformanceEligible,
                physicalRuntimeVersion,
                logSink,
                new ProfilerSignalEngine(),
                TelemetryProfilerContextProvider.unavailable(),
                beforeInsertHook,
                () -> {
                }
        );
    }

    TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook,
            @Nonnull Runnable beforeDeliveryOfferHook) {
        this(
                projects,
                currentlyPerformanceEligible,
                physicalRuntimeVersion,
                logSink,
                new ProfilerSignalEngine(),
                TelemetryProfilerContextProvider.unavailable(),
                beforeInsertHook,
                beforeDeliveryOfferHook
        );
    }

    public TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull ProfilerSignalEngine signalEngine,
            @Nonnull TelemetryProfilerContextProvider contextProvider) {
        this(
                projects,
                currentlyPerformanceEligible,
                physicalRuntimeVersion,
                logSink,
                signalEngine,
                contextProvider,
                ignored -> {
                },
                () -> {
                }
        );
    }

    TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull ProfilerSignalEngine signalEngine,
            @Nonnull TelemetryProfilerContextProvider contextProvider,
            @Nonnull Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook,
            @Nonnull Runnable beforeDeliveryOfferHook) {
        this(
                projects,
                currentlyPerformanceEligible,
                physicalRuntimeVersion,
                logSink,
                signalEngine,
                contextProvider,
                beforeInsertHook,
                beforeDeliveryOfferHook,
                () -> {
                }
        );
    }

    TelemetryProjectProfilerService(
            @Nonnull Supplier<List<TelemetryProjectRegistration>> projects,
            @Nonnull Predicate<String> currentlyPerformanceEligible,
            @Nonnull String physicalRuntimeVersion,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull ProfilerSignalEngine signalEngine,
            @Nonnull TelemetryProfilerContextProvider contextProvider,
            @Nonnull Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook,
            @Nonnull Runnable beforeDeliveryOfferHook,
            @Nonnull Runnable beforeContextDrainHook) {
        this.projectsSupplier = Objects.requireNonNull(projects, "projects");
        this.currentlyPerformanceEligible = Objects.requireNonNull(
                currentlyPerformanceEligible,
                "currentlyPerformanceEligible"
        );
        this.physicalRuntimeVersion = requiredText(physicalRuntimeVersion);
        this.logSink = logSink;
        this.signalEngine = Objects.requireNonNull(signalEngine, "signalEngine");
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.beforeInsertHook = Objects.requireNonNull(beforeInsertHook, "beforeInsertHook");
        this.beforeDeliveryOfferHook = Objects.requireNonNull(
                beforeDeliveryOfferHook,
                "beforeDeliveryOfferHook"
        );
        this.beforeContextDrainHook = Objects.requireNonNull(
                beforeContextDrainHook,
                "beforeContextDrainHook"
        );
        this.ownership = ProfilerProjectOwnershipIndex.build(
                List.of(),
                this.physicalRuntimeVersion
        ).index();
        refreshProjects();
    }

    @Nonnull
    public TelemetryProfilerView view(@Nonnull String projectId) {
        String normalizedProjectId = requiredText(projectId);
        synchronized (lock) {
            ProjectView current = currentViews.get(normalizedProjectId);
            if (current != null) {
                return current;
            }
            ProjectState state = projects.get(normalizedProjectId);
            if (state == null || !state.available) {
                return new ProjectView(new ProjectState(normalizedProjectId));
            }
            ProjectView created = new ProjectView(state);
            currentViews.put(normalizedProjectId, created);
            return created;
        }
    }

    public void publish(@Nullable SparkProfileReadResult result) {
        if (result == null) {
            return;
        }

        PublishCapture capture;
        boolean recapturedAfterDrain = false;
        while (true) {
            synchronized (lock) {
                if (closed || !providerActive) {
                    return;
                }
                capture = captureForPublishLocked();
            }
            if (recapturedAfterDrain) {
                break;
            }
            ContextDrainResult drainResult = drainPendingContextClears();
            if (drainResult == ContextDrainResult.BLOCKED) {
                break;
            }
            if (drainResult == ContextDrainResult.PROGRESSED
                    || capture.projects().isEmpty()) {
                recapturedAfterDrain = true;
                continue;
            }
            break;
        }
        if (capture.projects().isEmpty()) {
            return;
        }

        if (result.status() != SparkProfileReadResult.Status.COMPLETE) {
            publishStatusOnly(capture, result);
            return;
        }
        if (result.window() == null) {
            publishNoData(capture, result);
            return;
        }
        if (!hasUsableSelectedSamples(result.window())) {
            publishNoData(capture, result);
            return;
        }

        ProfilerProjectAnalyzer.Analysis analysis = ProfilerProjectAnalyzer.analyze(
                result.window(),
                capture.ownership(),
                this::isCurrentlyEligible,
                MAX_PATHS_PER_SNAPSHOT
        );
        try {
            beforeInsertHook.accept(analysis);
        } catch (RuntimeException failure) {
            publishAnalysisFailure(capture, failure);
            return;
        }

        if (!providerCaptureStillCurrent(capture)) {
            return;
        }

        if (analysis.status() == ProfilerProjectAnalyzer.Analysis.Status.FAILED) {
            synchronized (lock) {
                if (closed
                        || !providerActive
                        || providerGeneration != capture.providerGeneration()) {
                    return;
                }
                updateAnalysisFailureLocked(capture, analysis.detail());
            }
            drainPendingContextClears();
            return;
        }

        Map<String, ProfilerProjectWindow> analyzed = new HashMap<>();
        for (ProfilerProjectWindow project : analysis.projects()) {
            analyzed.put(project.projectId(), project);
        }

        List<PreparedSnapshot> preparedSnapshots = new ArrayList<>();
        for (ProjectCapture projectCapture : capture.projects()) {
            if (!projectCaptureStillAuthorized(projectCapture)) {
                continue;
            }
            ProfilerProjectWindow project = analyzed.get(projectCapture.projectId());
            List<TelemetryProfilerPathEvidence> currentPaths = project == null
                    ? List.of()
                    : project.paths().stream().map(this::pathEvidenceOf).toList();

            List<TelemetryProfilerPathEvidence> qualifiedPaths = currentPaths;
            try {
                qualifiedPaths = signalEngine.evaluate(
                        projectCapture.previousWindows(),
                        currentPaths
                ).currentPaths();
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                log(Level.WARNING, "Profiler signal evaluation failed: " + bounded(failure.toString()));
            }

            TelemetryProfilerContext context;
            try {
                context = contextProvider.snapshot(projectCapture.projectId());
                if (context == null) {
                    throw new IllegalStateException("context provider returned null");
                }
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                log(Level.WARNING, "Profiler context publication failed: " + bounded(failure.toString()));
                context = TelemetryProfilerContext.unavailable();
            }

            SparkProfileWindow window = result.window();
            String projectVersion = project == null
                    ? projectCapture.projectVersion()
                    : project.projectVersion();
            String sparkVersion = project == null ? window.sparkVersion() : project.sparkVersion();
            int windowKey = project == null ? window.windowKey() : project.windowKey();
            double selectedMilliseconds = project == null
                    ? window.totalSelfMilliseconds()
                    : project.selectedWorldThreadSelfMilliseconds();
            preparedSnapshots.add(new PreparedSnapshot(
                    projectCapture,
                    new TelemetryProfilerSnapshot(
                            projectCapture.projectId(),
                            projectVersion,
                            sparkVersion,
                            windowKey,
                            Math.max(0L, System.currentTimeMillis()),
                            selectedMilliseconds,
                            ProfilerSignalEngine.RULE_SET,
                            qualifiedPaths,
                            context
                    )
            ));
        }

        List<DeliveryTarget> deliveries = new ArrayList<>();
        boolean historyEvicted = false;
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || providerGeneration != capture.providerGeneration()) {
                return;
            }
            for (PreparedSnapshot prepared : preparedSnapshots) {
                ProjectCapture projectCapture = prepared.capture();
                ProjectState state = projects.get(projectCapture.projectId());
                if (!isInsertStillAuthorizedLocked(state, projectCapture)) {
                    continue;
                }
                TelemetryProfilerSnapshot snapshot = prepared.snapshot();
                RetentionResult retention = retainLocked(state, snapshot);
                historyEvicted |= retention.globalEviction();
                long sequence = ++publicationSequence;
                state.lastState = TelemetryProfilerStatus.State.COMPLETE;
                state.lastDetail = snapshot.paths().isEmpty()
                        ? EMPTY_PROJECT_DETAIL
                        : "Latest Spark project profile window was summarized locally.";
                state.projectStatusAuthoritative = snapshot.paths().isEmpty();
                state.lastSparkVersion = snapshot.sparkVersion();
                state.analysisDurationMillis = analysis.durationMillis();
                state.omittedPathCount = Math.max(
                        state.omittedPathCount,
                        analysis.omittedPathCount() + retention.evictedPathCount()
                );
                for (Subscription subscription : state.subscriptions) {
                    deliveries.add(new DeliveryTarget(
                            subscription,
                            new Delivery(
                                    snapshot,
                                    capture.providerGeneration(),
                                    projectCapture.authorizationGeneration(),
                                    sequence
                            )
                    ));
                }
            }
        }
        drainPendingContextClears();
        if (historyEvicted) {
            logHistoryEvictionIfNeeded();
        }
        offerDeliveries(deliveries);
    }

    private void publishNoData(PublishCapture capture, SparkProfileReadResult result) {
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || providerGeneration != capture.providerGeneration()) {
                return;
            }
            for (ProjectCapture projectCapture : capture.projects()) {
                ProjectState state = projects.get(projectCapture.projectId());
                if (!isInsertStillAuthorizedLocked(state, projectCapture)) {
                    continue;
                }
                state.lastState = TelemetryProfilerStatus.State.NO_DATA;
                state.lastDetail = NO_USABLE_SAMPLES_DETAIL;
                state.projectStatusAuthoritative = true;
                state.lastSparkVersion = result.sparkVersion();
                state.analysisDurationMillis = 0L;
            }
        }
        drainPendingContextClears();
    }

    public void invalidate(@Nonnull String projectId) {
        String normalizedProjectId = requiredText(projectId);
        synchronized (contextLifecycleLock) {
            synchronized (lock) {
                ProjectState state = projects.get(normalizedProjectId);
                if (state == null) {
                    return;
                }
                state.authorizationGeneration++;
                markContextClearPendingLocked(state);
                clearHistoryLocked(state);
                drainMailboxesLocked(state);
                boolean eligible = eligibleLocked(state.projectId);
                state.lastState = eligible
                        ? TelemetryProfilerStatus.State.READY
                        : TelemetryProfilerStatus.State.DISABLED;
                state.lastDetail = eligible ? INITIALIZATION_DETAIL : DISABLED_DETAIL;
                state.lastEligibility = eligible;
                state.projectStatusAuthoritative = false;
                state.lastSparkVersion = null;
                state.analysisDurationMillis = 0L;
                state.omittedPathCount = 0;
            }
            drainPendingContextClears();
        }
    }

    public void refreshProjects() {
        synchronized (contextLifecycleLock) {
            List<TelemetryProjectRegistration> supplied;
            try {
                supplied = projectsSupplier.get();
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                log(Level.WARNING, "Profiler project refresh failed: " + bounded(failure.toString()));
                return;
            }
            List<TelemetryProjectRegistration> registrations = normalizeRegistrations(supplied);
            String nextOwnershipKey = ownershipKey(registrations);
            List<Subscription> closeAfterUnlock = new ArrayList<>();
            long refreshGeneration;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                if (!nextOwnershipKey.equals(ownershipKey)) {
                    ProfilerProjectOwnershipIndex.BuildResult nextOwnership = ProfilerProjectOwnershipIndex.build(
                            registrations,
                            physicalRuntimeVersion
                    );
                    ownership = nextOwnership.index();
                    ownershipKey = nextOwnershipKey;
                    truncatedPrefixCount = nextOwnership.truncatedPrefixCount();
                }

                Map<String, TelemetryProjectRegistration> incoming = new LinkedHashMap<>();
                for (TelemetryProjectRegistration registration : registrations) {
                    incoming.putIfAbsent(registration.projectId(), registration);
                }

                for (ProjectState state : new ArrayList<>(projects.values())) {
                    TelemetryProjectRegistration registration = incoming.get(state.projectId);
                    if (registration == null) {
                        state.authorizationGeneration++;
                        markContextClearPendingLocked(state);
                        projects.remove(state.projectId);
                        currentViews.remove(state.projectId);
                        state.available = false;
                        clearHistoryLocked(state);
                        drainMailboxesLocked(state);
                        state.lastState = TelemetryProfilerStatus.State.UNAVAILABLE;
                        state.lastDetail = "Project registration is no longer available.";
                        state.lastSparkVersion = null;
                        state.analysisDurationMillis = 0L;
                        state.omittedPathCount = 0;
                        state.projectStatusAuthoritative = false;
                        closeAfterUnlock.addAll(state.subscriptions);
                        continue;
                    }

                    if (!sameRegistration(state.registration, registration)) {
                        state.registration = registration;
                        state.authorizationGeneration++;
                        markContextClearPendingLocked(state);
                        clearHistoryLocked(state);
                        drainMailboxesLocked(state);
                        state.lastSparkVersion = null;
                        state.analysisDurationMillis = 0L;
                        state.omittedPathCount = 0;
                        state.projectStatusAuthoritative = false;
                    } else {
                        state.registration = registration;
                    }

                    boolean eligible = eligibleLocked(state.projectId);
                    if (state.lastEligibility && !eligible) {
                        state.authorizationGeneration++;
                        markContextClearPendingLocked(state);
                        clearHistoryLocked(state);
                        drainMailboxesLocked(state);
                        state.lastSparkVersion = null;
                        state.analysisDurationMillis = 0L;
                        state.omittedPathCount = 0;
                        state.projectStatusAuthoritative = false;
                    }
                    state.lastEligibility = eligible;
                    state.available = true;
                    if (!eligible) {
                        state.lastState = TelemetryProfilerStatus.State.DISABLED;
                        state.lastDetail = DISABLED_DETAIL;
                    } else if (state.lastState == TelemetryProfilerStatus.State.DISABLED
                            || state.lastState == TelemetryProfilerStatus.State.UNAVAILABLE) {
                        state.lastState = TelemetryProfilerStatus.State.READY;
                        state.lastDetail = INITIALIZATION_DETAIL;
                    }
                }

                for (TelemetryProjectRegistration registration : incoming.values()) {
                    if (projects.containsKey(registration.projectId())) {
                        continue;
                    }
                    ProjectState state = new ProjectState(registration.projectId(), registration);
                    state.lastEligibility = eligibleLocked(state.projectId);
                    state.lastState = state.lastEligibility
                            ? TelemetryProfilerStatus.State.READY
                            : TelemetryProfilerStatus.State.DISABLED;
                    state.lastDetail = state.lastEligibility ? INITIALIZATION_DETAIL : DISABLED_DETAIL;
                    state.projectStatusAuthoritative = false;
                    projects.put(state.projectId, state);
                    currentViews.put(state.projectId, new ProjectView(state));
                }

                refreshGeneration = ++contextRefreshGeneration;
                pendingContextRefresh = new ContextRefreshRequest(
                        refreshGeneration,
                        registrations
                );
            }

            boolean refreshConfirmed = refreshContextProjectsSafely(registrations);
            if (refreshConfirmed) {
                acknowledgeContextRefresh(refreshGeneration);
            }
            for (Subscription subscription : closeAfterUnlock) {
                subscription.stop();
            }
            if (refreshConfirmed) {
                drainPendingContextClears();
            }
        }
    }

    public void attachMonitorDiagnostics(@Nonnull Supplier<SparkProfilerDiagnostics> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        boolean duplicate;
        synchronized (lock) {
            duplicate = monitorDiagnostics != null;
            if (!duplicate) {
                monitorDiagnostics = diagnostics;
            }
        }
        if (duplicate) {
            log(Level.WARNING, "Profiler monitor diagnostics were already attached.");
        }
    }

    public void activateProvider() {
        synchronized (lock) {
            if (closed || providerActive) {
                return;
            }
            providerActive = true;
            providerGeneration++;
        }
    }

    public void deactivateProvider() {
        synchronized (contextLifecycleLock) {
            synchronized (lock) {
                if (closed || !providerActive) {
                    return;
                }
                providerActive = false;
                providerGeneration++;
                for (ProjectState state : projects.values()) {
                    state.authorizationGeneration++;
                    markContextClearPendingLocked(state);
                    clearHistoryLocked(state);
                    drainMailboxesLocked(state);
                    boolean eligible = eligibleLocked(state.projectId);
                    state.lastState = eligible
                            ? TelemetryProfilerStatus.State.READY
                            : TelemetryProfilerStatus.State.DISABLED;
                    state.lastDetail = eligible ? INITIALIZATION_DETAIL : DISABLED_DETAIL;
                    state.lastEligibility = eligible;
                    state.projectStatusAuthoritative = false;
                    state.lastSparkVersion = null;
                    state.analysisDurationMillis = 0L;
                    state.omittedPathCount = 0;
                }
            }
            List<ContextClearRequest> clearRequests;
            synchronized (lock) {
                clearRequests = List.copyOf(pendingContextClears.values());
            }
            if (clearAllContextSafely()) {
                acknowledgeContextClears(clearRequests);
            }
        }
    }

    @Override
    public void close() {
        synchronized (contextLifecycleLock) {
            List<Subscription> toClose;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                providerActive = false;
                providerGeneration++;
                toClose = List.copyOf(subscriptions);
                for (ProjectState state : projects.values()) {
                    state.available = false;
                    clearHistoryLocked(state);
                    state.lastState = TelemetryProfilerStatus.State.SHUTDOWN;
                    state.lastDetail = CLOSED_DETAIL;
                    state.projectStatusAuthoritative = false;
                }
            }
            for (Subscription subscription : toClose) {
                subscription.stop();
            }
            clearAllContextSafely();
        }
    }

    private PublishCapture captureForPublishLocked() {
        if (pendingContextRefresh != null) {
            return new PublishCapture(providerGeneration, ownership, List.of());
        }
        List<ProjectCapture> captures = new ArrayList<>();
        for (ProjectState state : projects.values()) {
            if (!state.available || contextAccessBlockedLocked(state)) {
                continue;
            }
            if (!observeEligibilityLocked(state)
                    || contextAccessBlockedLocked(state)) {
                continue;
            }
            int start = Math.max(0, state.history.size() - (ProfilerSignalEngine.MAX_WINDOWS - 1));
            List<TelemetryProfilerSnapshot> previousWindows = state.history.stream()
                    .skip(start)
                    .map(HistoryEntry::snapshot)
                    .toList();
            captures.add(new ProjectCapture(
                    state.projectId,
                    state.authorizationGeneration,
                    logicalProjectVersion(state.registration),
                    previousWindows
            ));
        }
        return new PublishCapture(providerGeneration, ownership, List.copyOf(captures));
    }

    private void publishStatusOnly(PublishCapture capture, SparkProfileReadResult result) {
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || providerGeneration != capture.providerGeneration()) {
                return;
            }
            TelemetryProfilerStatus.State state = statusState(result.status());
            for (ProjectCapture projectCapture : capture.projects()) {
                ProjectState project = projects.get(projectCapture.projectId());
                if (!isInsertStillAuthorizedLocked(project, projectCapture)) {
                    continue;
                }
                project.lastState = state;
                project.lastDetail = bounded(result.detail());
                project.lastSparkVersion = result.sparkVersion();
                project.analysisDurationMillis = 0L;
                project.projectStatusAuthoritative = state == TelemetryProfilerStatus.State.NO_DATA;
            }
        }
        drainPendingContextClears();
    }

    private void publishAnalysisFailure(PublishCapture capture, RuntimeException failure) {
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || providerGeneration != capture.providerGeneration()) {
                return;
            }
            updateAnalysisFailureLocked(capture, bounded(failure.toString()));
        }
        drainPendingContextClears();
    }

    private void updateAnalysisFailureLocked(PublishCapture capture, String detail) {
        for (ProjectCapture projectCapture : capture.projects()) {
            ProjectState state = projects.get(projectCapture.projectId());
            if (!isInsertStillAuthorizedLocked(state, projectCapture)) {
                continue;
            }
            state.lastState = TelemetryProfilerStatus.State.FAILED;
            state.lastDetail = bounded(detail);
            state.lastSparkVersion = null;
            state.projectStatusAuthoritative = true;
        }
    }

    private boolean isInsertStillAuthorizedLocked(ProjectState state, ProjectCapture capture) {
        return state != null
                && state.available
                && pendingContextRefresh == null
                && !pendingContextClears.containsKey(state.projectId)
                && state.authorizationGeneration == capture.authorizationGeneration()
                && observeEligibilityLocked(state);
    }

    private boolean contextAccessBlockedLocked(ProjectState state) {
        return pendingContextRefresh != null
                || pendingContextClears.containsKey(state.projectId);
    }

    private void markContextClearPendingLocked(ProjectState state) {
        pendingContextClears.put(
                state.projectId,
                new ContextClearRequest(state, state.authorizationGeneration)
        );
    }

    private boolean providerCaptureStillCurrent(PublishCapture capture) {
        synchronized (lock) {
            return !closed
                    && providerActive
                    && providerGeneration == capture.providerGeneration();
        }
    }

    private boolean projectCaptureStillAuthorized(ProjectCapture capture) {
        synchronized (lock) {
            return isInsertStillAuthorizedLocked(
                    projects.get(capture.projectId()),
                    capture
            );
        }
    }

    private static boolean hasUsableSelectedSamples(SparkProfileWindow window) {
        return window.selectedThreadCount() > 0
                && window.selectedFrameCount() > 0
                && window.totalSelfMilliseconds() > 0.0d;
    }

    @Nonnull
    private static String logicalProjectVersion(@Nullable TelemetryProjectRegistration registration) {
        if (registration == null) {
            return "<unknown>";
        }
        String projectVersion = registration.descriptor().projectVersion();
        if (projectVersion == null || projectVersion.isBlank()) {
            projectVersion = registration.pluginVersion();
        }
        return requiredText(projectVersion);
    }

    private boolean refreshContextProjectsSafely(List<TelemetryProjectRegistration> registrations) {
        try {
            if (!contextProvider.refreshProjectsConfirmed(registrations)) {
                log(Level.WARNING, "Profiler context project refresh was not confirmed.");
                return false;
            }
            return true;
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            log(Level.WARNING, "Profiler context project refresh failed: " + bounded(failure.toString()));
            return false;
        }
    }

    private ContextDrainResult drainPendingContextClears() {
        beforeContextDrainHook.run();
        boolean hadPending;
        synchronized (lock) {
            hadPending = pendingContextRefresh != null || !pendingContextClears.isEmpty();
        }
        if (!hadPending) {
            return ContextDrainResult.NO_WORK;
        }

        synchronized (contextLifecycleLock) {
            boolean progressed = false;
            ContextRefreshRequest refreshRequest;
            synchronized (lock) {
                refreshRequest = pendingContextRefresh;
            }
            if (refreshRequest != null) {
                if (!refreshContextProjectsSafely(refreshRequest.registrations())) {
                    return ContextDrainResult.BLOCKED;
                }
                acknowledgeContextRefresh(refreshRequest.generation());
                progressed = true;
            }

            List<ContextClearRequest> requests;
            synchronized (lock) {
                requests = List.copyOf(pendingContextClears.values());
            }
            if (requests.isEmpty()) {
                return progressed
                        ? ContextDrainResult.PROGRESSED
                        : ContextDrainResult.NO_WORK;
            }

            List<ContextClearRequest> confirmed = new ArrayList<>();
            for (ContextClearRequest request : requests) {
                if (clearContextSafely(request.state().projectId)) {
                    confirmed.add(request);
                    progressed = true;
                }
            }
            acknowledgeContextClears(confirmed);
            synchronized (lock) {
                if (pendingContextRefresh != null || !pendingContextClears.isEmpty()) {
                    return progressed
                            ? ContextDrainResult.PROGRESSED
                            : ContextDrainResult.BLOCKED;
                }
                return progressed
                        ? ContextDrainResult.PROGRESSED
                        : ContextDrainResult.NO_WORK;
            }
        }
    }

    private void acknowledgeContextRefresh(long generation) {
        synchronized (lock) {
            if (pendingContextRefresh != null
                    && pendingContextRefresh.generation() == generation) {
                pendingContextRefresh = null;
            }
        }
    }

    private void acknowledgeContextClears(List<ContextClearRequest> confirmed) {
        synchronized (lock) {
            for (ContextClearRequest request : confirmed) {
                ContextClearRequest pending = pendingContextClears.get(request.state().projectId);
                if (pending != null
                        && pending.state() == request.state()
                        && pending.authorizationGeneration() == request.authorizationGeneration()) {
                    pendingContextClears.remove(request.state().projectId);
                }
            }
        }
    }

    private boolean clearContextSafely(String projectId) {
        return contextProvider.clearConfirmed(projectId);
    }

    private boolean clearAllContextSafely() {
        return contextProvider.clearAllConfirmed();
    }

    private void logHistoryEvictionIfNeeded() {
        boolean shouldLog;
        synchronized (lock) {
            long now = System.nanoTime();
            shouldLog = lastHistoryWarningNanos == 0L
                    || now - lastHistoryWarningNanos >= HISTORY_WARNING_INTERVAL_NANOS;
            if (shouldLog) {
                lastHistoryWarningNanos = now;
            }
        }
        if (shouldLog) {
            log(Level.WARNING, "Profiler history retention limit evicted oldest evidence.");
        }
    }

    private RetentionResult retainLocked(ProjectState state, TelemetryProfilerSnapshot snapshot) {
        int evictedPathCount = 0;
        while (state.history.size() >= MAX_HISTORY_PER_PROJECT) {
            HistoryEntry oldest = state.history.removeFirst();
            retainedHistory.remove(oldest);
            retainedPathEntries -= oldest.snapshot().paths().size();
            evictedPathCount += oldest.snapshot().paths().size();
        }
        HistoryEntry retained = new HistoryEntry(state, snapshot);
        state.history.addLast(retained);
        retainedHistory.addLast(retained);
        retainedPathEntries += snapshot.paths().size();

        boolean globalEviction = false;
        while ((retainedHistory.size() > MAX_RETAINED_SNAPSHOTS
                || retainedPathEntries > MAX_RETAINED_PATH_ENTRIES)
                && !retainedHistory.isEmpty()) {
            HistoryEntry oldest = retainedHistory.removeFirst();
            if (oldest.state().history.remove(oldest)) {
                retainedPathEntries -= oldest.snapshot().paths().size();
                evictedPathCount += oldest.snapshot().paths().size();
                globalEviction = true;
            }
        }
        return new RetentionResult(evictedPathCount, globalEviction);
    }

    private void clearHistoryLocked(ProjectState state) {
        while (!state.history.isEmpty()) {
            HistoryEntry entry = state.history.removeFirst();
            retainedHistory.remove(entry);
            retainedPathEntries -= entry.snapshot().paths().size();
        }
        state.omittedPathCount = 0;
    }

    private void drainMailboxesLocked(ProjectState state) {
        for (Subscription subscription : state.subscriptions) {
            subscription.clearMailbox();
        }
    }

    private void offerDeliveries(List<DeliveryTarget> deliveries) {
        for (DeliveryTarget target : deliveries) {
            beforeDeliveryOfferHook.run();
            target.subscription().offer(target.delivery());
        }
    }

    private TelemetryProfilerPathEvidence pathEvidenceOf(ProfilerProjectWindow.Path path) {
        return new TelemetryProfilerPathEvidence(
                path.fingerprint(),
                path.attribution(),
                path.ownedClassName(),
                path.ownedMethodName(),
                path.ownedMethodDescriptor(),
                path.firstExternalClassName(),
                path.firstExternalMethodName(),
                path.firstExternalMethodDescriptor(),
                path.sampledMilliseconds(),
                path.selectedWorldThreadSharePercent(),
                path.qualification(),
                path.representativePath()
        );
    }

    private boolean isCurrentlyEligible(String projectId) {
        try {
            return currentlyPerformanceEligible.test(projectId);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return false;
        }
    }

    private boolean eligibleLocked(String projectId) {
        return isCurrentlyEligible(projectId);
    }

    private boolean observeEligibilityLocked(ProjectState state) {
        boolean eligible = eligibleLocked(state.projectId);
        if (state.available && state.lastEligibility && !eligible) {
            state.authorizationGeneration++;
            markContextClearPendingLocked(state);
            clearHistoryLocked(state);
            drainMailboxesLocked(state);
            state.lastState = TelemetryProfilerStatus.State.DISABLED;
            state.lastDetail = DISABLED_DETAIL;
            state.lastSparkVersion = null;
            state.analysisDurationMillis = 0L;
            state.projectStatusAuthoritative = false;
        }
        state.lastEligibility = eligible;
        return eligible;
    }

    @Nonnull
    private TelemetryProfilerStatus statusFor(ProjectState state) {
        StatusData base = null;
        Supplier<SparkProfilerDiagnostics> diagnosticsSupplier = null;
        boolean disabled = false;
        synchronized (lock) {
            if (!state.available) {
                return TelemetryProfilerStatus.unavailable();
            }
            if (contextAccessBlockedLocked(state)
                    || !observeEligibilityLocked(state)) {
                disabled = true;
            } else {
                base = new StatusData(
                        state.lastState,
                        state.lastDetail,
                        providerActive,
                        false,
                        state.lastSparkVersion,
                        state.analysisDurationMillis,
                        state.omittedPathCount,
                        truncatedPrefixCount,
                        state.projectStatusAuthoritative
                );
                diagnosticsSupplier = monitorDiagnostics;
            }
        }
        if (disabled) {
            drainPendingContextClears();
            return new TelemetryProfilerStatus(
                    TelemetryProfilerStatus.State.DISABLED,
                    DISABLED_DETAIL,
                    false,
                    false,
                    null,
                    null,
                    0L,
                    0L,
                    0,
                    0
            );
        }

        SparkProfilerDiagnostics diagnostics = null;
        if (diagnosticsSupplier != null) {
            try {
                diagnostics = diagnosticsSupplier.get();
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                diagnostics = SparkProfilerDiagnostics.simple(
                        SparkProfilerDiagnostics.State.FAILED,
                        null,
                        "Profiler monitor diagnostics failed."
                );
            }
        }
        if (diagnostics == null) {
            return new TelemetryProfilerStatus(
                    base.projectStatusAuthoritative()
                            ? base.state()
                            : TelemetryProfilerStatus.State.FAILED,
                    bounded(base.projectStatusAuthoritative()
                            ? base.detail()
                            : UNKNOWN_MONITOR_STATE_DETAIL),
                    base.active(),
                    base.circuitOpen(),
                    base.projectStatusAuthoritative() ? base.sparkVersion() : null,
                    physicalRuntimeVersion,
                    0L,
                    base.analysisDurationMillis(),
                    base.omittedPathCount(),
                    base.truncatedPrefixCount()
            );
        }
        boolean knownMonitorState = knownMonitorState(diagnostics.state());
        if (!knownMonitorState) {
            return new TelemetryProfilerStatus(
                    TelemetryProfilerStatus.State.FAILED,
                    UNKNOWN_MONITOR_STATE_DETAIL,
                    diagnostics.active(),
                    diagnostics.circuitOpen(),
                    null,
                    physicalRuntimeVersion,
                    Math.max(0L, diagnostics.nextCaptureAtMillis()),
                    base.analysisDurationMillis(),
                    base.omittedPathCount(),
                    base.truncatedPrefixCount()
            );
        }
        boolean projectStatusAuthoritative = base.projectStatusAuthoritative()
                && !monitorOverridesAuthoritativeProjectStatus(diagnostics.state());
        return new TelemetryProfilerStatus(
                projectStatusAuthoritative
                        ? base.state()
                        : statusState(diagnostics.state()),
                bounded(projectStatusAuthoritative
                        ? base.detail()
                        : diagnostics.detail()),
                diagnostics.active(),
                diagnostics.circuitOpen(),
                projectStatusAuthoritative ? base.sparkVersion() : diagnostics.sparkVersion(),
                physicalRuntimeVersion,
                Math.max(0L, diagnostics.nextCaptureAtMillis()),
                base.analysisDurationMillis(),
                base.omittedPathCount(),
                base.truncatedPrefixCount()
        );
    }

    @Nonnull
    private static TelemetryProfilerStatus.State statusState(
            @Nullable SparkProfileReadResult.Status state) {
        if (state == null) {
            return TelemetryProfilerStatus.State.FAILED;
        }
        return switch (state) {
            case COMPLETE -> TelemetryProfilerStatus.State.COMPLETE;
            case ABSENT -> TelemetryProfilerStatus.State.ABSENT;
            case NOT_RUNNING, NOT_BACKGROUND -> TelemetryProfilerStatus.State.NOT_RUNNING;
            case UNSUPPORTED_PLUGIN, UNSUPPORTED_VERSION, UNSUPPORTED_ARTIFACT,
                    UNSUPPORTED_SAMPLER -> TelemetryProfilerStatus.State.UNSUPPORTED;
            case NO_DATA, NO_ACTIONABLE_DATA -> TelemetryProfilerStatus.State.NO_DATA;
            case TOO_COMPLEX -> TelemetryProfilerStatus.State.NO_DATA;
            case INCOMPATIBLE -> TelemetryProfilerStatus.State.UNSUPPORTED;
            default -> TelemetryProfilerStatus.State.FAILED;
        };
    }

    @Nonnull
    private static TelemetryProfilerStatus.State statusState(
            @Nullable SparkProfilerDiagnostics.State state) {
        if (state == null) {
            return TelemetryProfilerStatus.State.FAILED;
        }
        return switch (state) {
            case DISABLED -> TelemetryProfilerStatus.State.DISABLED;
            case READY -> TelemetryProfilerStatus.State.READY;
            case WAITING -> TelemetryProfilerStatus.State.WAITING;
            case CAPTURING -> TelemetryProfilerStatus.State.CAPTURING;
            case COMPLETE -> TelemetryProfilerStatus.State.COMPLETE;
            case ABSENT -> TelemetryProfilerStatus.State.ABSENT;
            case NOT_OWNER -> TelemetryProfilerStatus.State.NOT_OWNER;
            case NOT_RUNNING -> TelemetryProfilerStatus.State.NOT_RUNNING;
            case UNSUPPORTED, INCOMPATIBLE -> TelemetryProfilerStatus.State.UNSUPPORTED;
            case NO_DATA, NO_ACTIONABLE_DATA, TOO_COMPLEX -> TelemetryProfilerStatus.State.NO_DATA;
            case LOW_HEAP -> TelemetryProfilerStatus.State.LOW_HEAP;
            case TIMED_OUT -> TelemetryProfilerStatus.State.TIMED_OUT;
            case FAILED -> TelemetryProfilerStatus.State.FAILED;
            case SHUTDOWN -> TelemetryProfilerStatus.State.SHUTDOWN;
            default -> TelemetryProfilerStatus.State.FAILED;
        };
    }

    private static boolean knownMonitorState(@Nullable SparkProfilerDiagnostics.State state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case DISABLED, READY, WAITING, CAPTURING, COMPLETE, ABSENT, NOT_OWNER,
                    NOT_RUNNING, UNSUPPORTED, INCOMPATIBLE, NO_DATA, NO_ACTIONABLE_DATA,
                    TOO_COMPLEX, LOW_HEAP, TIMED_OUT, FAILED, SHUTDOWN -> true;
            default -> false;
        };
    }

    private static boolean monitorOverridesAuthoritativeProjectStatus(
            @Nullable SparkProfilerDiagnostics.State state) {
        if (state == null) {
            return true;
        }
        return switch (state) {
            case READY, WAITING, CAPTURING, COMPLETE -> false;
            default -> true;
        };
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> normalizeRegistrations(
            @Nullable List<TelemetryProjectRegistration> supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return List.of();
        }
        ArrayList<TelemetryProjectRegistration> normalized = new ArrayList<>(supplied.size());
        Set<String> seen = new LinkedHashSet<>();
        for (TelemetryProjectRegistration registration : supplied) {
            if (registration == null || registration.projectId() == null) {
                continue;
            }
            String projectId = requiredText(registration.projectId());
            if (seen.add(projectId)) {
                normalized.add(registration);
            }
        }
        return List.copyOf(normalized);
    }

    @Nonnull
    private static String ownershipKey(List<TelemetryProjectRegistration> registrations) {
        StringBuilder key = new StringBuilder();
        for (TelemetryProjectRegistration registration : registrations) {
            key.append(keyPart(registration.projectId()))
                    .append(keyPart(registration.descriptor().projectVersion()))
                    .append(keyPart(registration.pluginIdentifier()))
                    .append(keyPart(registration.pluginVersion()));
            for (String identifier : registration.ownerPluginIdentifiers()) {
                key.append(keyPart(identifier));
            }
            for (String prefix : registration.packagePrefixes()) {
                key.append(profilerPrefixKeyPart(prefix));
            }
            key.append(';');
        }
        return key.toString();
    }

    private static boolean sameRegistration(@Nullable TelemetryProjectRegistration left,
                                            @Nonnull TelemetryProjectRegistration right) {
        if (left == null) {
            return false;
        }
        return ownershipKey(List.of(left)).equals(ownershipKey(List.of(right)));
    }

    @Nonnull
    private static String keyPart(@Nullable String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.length() + ":" + normalized + "|";
    }

    @Nonnull
    private static String profilerPrefixKeyPart(@Nullable String value) {
        if (value != null && value.length() > ProfilerProjectOwnershipIndex.MAX_PREFIX_LENGTH) {
            return "P!overlong|";
        }
        return keyPart(value);
    }

    @Nonnull
    private static String requiredText(@Nullable String value) {
        return value == null || value.isBlank() ? "<unknown>" : value.trim();
    }

    @Nonnull
    private static String bounded(@Nullable String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(240, value.length()));
        for (int index = 0; index < value.length() && safe.length() < 240; index++) {
            char character = value.charAt(index);
            safe.append(Character.isISOControl(character) ? ' ' : character);
        }
        return safe.toString().trim();
    }

    private void log(Level level, String message) {
        if (logSink == null) {
            return;
        }
        try {
            logSink.accept(level, bounded(message));
        } catch (RuntimeException ignored) {
            // A diagnostic sink must not affect profiler publication.
        }
    }

    private void logListenerFailure(Subscription subscription, RuntimeException failure) {
        boolean shouldLog;
        synchronized (lock) {
            long now = System.nanoTime();
            shouldLog = subscription.lastWarningNanos == 0L
                    || now - subscription.lastWarningNanos >= LISTENER_WARNING_INTERVAL_NANOS;
            if (shouldLog) {
                subscription.lastWarningNanos = now;
            }
        }
        if (shouldLog) {
            log(Level.WARNING, "Profiler listener failed: " + bounded(failure.toString()));
        }
    }

    private void releaseSubscription(Subscription subscription) {
        synchronized (lock) {
            if (!subscription.permitReleased) {
                subscription.permitReleased = true;
                subscriptions.remove(subscription);
                subscription.state.subscriptions.remove(subscription);
                if (subscription.state.subscriptionCount > 0) {
                    subscription.state.subscriptionCount--;
                }
            }
        }
    }

    @Nullable
    public TelemetryProfilerSubscription subscribeAccepted(
            @Nonnull String projectId,
            @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        ProjectState state;
        synchronized (lock) {
            state = projects.get(requiredText(projectId));
        }
        return subscribeAccepted(state, listener);
    }

    @Nullable
    private TelemetryProfilerSubscription subscribeAccepted(
            @Nullable ProjectState state,
            @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        boolean limitReached = false;
        boolean drainContext = false;
        Subscription accepted = null;
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || state == null
                    || !state.available
                    || projects.get(state.projectId) != state) {
                return null;
            }
            if (contextAccessBlockedLocked(state)
                    || !observeEligibilityLocked(state)) {
                drainContext = true;
            } else if (state.subscriptionCount >= MAX_PROJECT_SUBSCRIPTIONS
                    || subscriptions.size() >= MAX_GLOBAL_SUBSCRIPTIONS) {
                limitReached = true;
            } else {
                accepted = new Subscription(state, listener);
                state.subscriptions.add(accepted);
                state.subscriptionCount++;
                subscriptions.add(accepted);
                accepted.start();
            }
        }
        if (drainContext) {
            drainPendingContextClears();
            return null;
        }
        if (limitReached) {
            log(Level.WARNING, "Profiler listener subscription limit reached for "
                    + bounded(state.projectId));
            return null;
        }
        return accepted;
    }

    private final class ProjectView implements TelemetryProfilerView {
        private final ProjectState state;

        private ProjectView(ProjectState state) {
            this.state = state;
        }

        @Nonnull
        @Override
        public TelemetryProfilerStatus status() {
            return statusFor(state);
        }

        @Nonnull
        @Override
        public java.util.Optional<TelemetryProfilerSnapshot> latest() {
            java.util.Optional<TelemetryProfilerSnapshot> latest = java.util.Optional.empty();
            boolean drainContext = false;
            synchronized (lock) {
                if (!state.available) {
                    return latest;
                }
                if (contextAccessBlockedLocked(state)
                        || !observeEligibilityLocked(state)) {
                    drainContext = true;
                } else if (!state.history.isEmpty()) {
                    latest = java.util.Optional.of(state.history.getLast().snapshot());
                }
            }
            if (drainContext) {
                drainPendingContextClears();
            }
            return latest;
        }

        @Nonnull
        @Override
        public List<TelemetryProfilerSnapshot> history() {
            List<TelemetryProfilerSnapshot> history = List.of();
            boolean drainContext = false;
            synchronized (lock) {
                if (!state.available) {
                    return history;
                }
                if (contextAccessBlockedLocked(state)
                        || !observeEligibilityLocked(state)) {
                    drainContext = true;
                } else if (!state.history.isEmpty()) {
                    history = state.history.stream().map(HistoryEntry::snapshot).toList();
                }
            }
            if (drainContext) {
                drainPendingContextClears();
            }
            return history;
        }

        @Nonnull
        @Override
        public TelemetryProfilerSubscription subscribe(
                @Nonnull Consumer<? super TelemetryProfilerSnapshot> listener) {
            TelemetryProfilerSubscription accepted = TelemetryProjectProfilerService.this
                    .subscribeAccepted(state, listener);
            return accepted == null ? TelemetryProfilerSubscription.closed() : accepted;
        }
    }

    private final class Subscription implements TelemetryProfilerSubscription {
        private final ProjectState state;
        private final Consumer<? super TelemetryProfilerSnapshot> listener;
        private final ArrayBlockingQueue<Delivery> mailbox = new ArrayBlockingQueue<>(1);
        private final Object mailboxLock = new Object();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Thread worker;
        private volatile long lastWarningNanos;
        private long newestOfferedSequence;
        private boolean permitReleased;

        private Subscription(ProjectState state,
                             Consumer<? super TelemetryProfilerSnapshot> listener) {
            this.state = state;
            this.listener = listener;
        }

        private void start() {
            Thread created = new Thread(this::run);
            created.setDaemon(true);
            worker = created;
            created.start();
        }

        @Override
        public void close() {
            stop();
        }

        private void stop() {
            synchronized (mailboxLock) {
                closed.set(true);
                mailbox.clear();
            }
            Thread current = worker;
            if (current != null) {
                current.interrupt();
            }
        }

        private void clearMailbox() {
            synchronized (mailboxLock) {
                mailbox.clear();
            }
        }

        private void offer(Delivery delivery) {
            synchronized (mailboxLock) {
                if (closed.get() || delivery.sequence() <= newestOfferedSequence) {
                    return;
                }
                Delivery queued = mailbox.peek();
                if (queued != null && queued.sequence() >= delivery.sequence()) {
                    return;
                }
                if (!mailbox.offer(delivery)) {
                    mailbox.poll();
                    mailbox.offer(delivery);
                }
                newestOfferedSequence = delivery.sequence();
            }
        }

        private void run() {
            try {
                while (!closed.get()) {
                    Delivery delivery;
                    try {
                        delivery = mailbox.take();
                    } catch (InterruptedException interrupted) {
                        if (closed.get()) {
                            break;
                        }
                        continue;
                    }
                    if (closed.get() || !deliveryCurrent(delivery)) {
                        continue;
                    }
                    try {
                        listener.accept(delivery.snapshot());
                    } catch (RuntimeException failure) {
                        logListenerFailure(this, failure);
                    }
                }
            } finally {
                releaseSubscription(this);
            }
        }

        private boolean deliveryCurrent(Delivery delivery) {
            boolean current;
            synchronized (lock) {
                current = !closed.get()
                        && !TelemetryProjectProfilerService.this.closed
                        && providerActive
                        && providerGeneration == delivery.providerGeneration()
                        && state.available
                        && state.authorizationGeneration == delivery.authorizationGeneration()
                        && !contextAccessBlockedLocked(state)
                        && observeEligibilityLocked(state);
            }
            if (!current) {
                drainPendingContextClears();
            }
            return current;
        }
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }

    private record PublishCapture(long providerGeneration,
                                  ProfilerProjectOwnershipIndex ownership,
                                  List<ProjectCapture> projects) {
    }

    private record ContextClearRequest(ProjectState state, long authorizationGeneration) {
    }

    private record ContextRefreshRequest(long generation,
                                         List<TelemetryProjectRegistration> registrations) {
        private ContextRefreshRequest {
            registrations = List.copyOf(registrations);
        }
    }

    private enum ContextDrainResult {
        NO_WORK,
        PROGRESSED,
        BLOCKED
    }

    private record ProjectCapture(String projectId,
                                  long authorizationGeneration,
                                  String projectVersion,
                                  List<TelemetryProfilerSnapshot> previousWindows) {
        private ProjectCapture {
            projectId = requiredText(projectId);
            projectVersion = requiredText(projectVersion);
            previousWindows = List.copyOf(previousWindows);
        }
    }

    private record PreparedSnapshot(ProjectCapture capture,
                                    TelemetryProfilerSnapshot snapshot) {
    }

    private record RetentionResult(int evictedPathCount, boolean globalEviction) {
    }

    private record DeliveryTarget(Subscription subscription, Delivery delivery) {
    }

    private record Delivery(TelemetryProfilerSnapshot snapshot,
                            long providerGeneration,
                            long authorizationGeneration,
                            long sequence) {
    }

    private record StatusData(TelemetryProfilerStatus.State state,
                              String detail,
                              boolean active,
                              boolean circuitOpen,
                              String sparkVersion,
                              long analysisDurationMillis,
                              int omittedPathCount,
                              int truncatedPrefixCount,
                              boolean projectStatusAuthoritative) {
    }

    private record HistoryEntry(ProjectState state, TelemetryProfilerSnapshot snapshot) {
    }

    private final class ProjectState {
        private final String projectId;
        private final ArrayDeque<HistoryEntry> history = new ArrayDeque<>();
        private final Set<Subscription> subscriptions = new LinkedHashSet<>();
        private TelemetryProjectRegistration registration;
        private boolean available;
        private boolean lastEligibility;
        private long authorizationGeneration;
        private int subscriptionCount;
        private TelemetryProfilerStatus.State lastState = TelemetryProfilerStatus.State.UNAVAILABLE;
        private String lastDetail = "";
        private String lastSparkVersion;
        private long analysisDurationMillis;
        private int omittedPathCount;
        private boolean projectStatusAuthoritative;

        private ProjectState(String projectId) {
            this.projectId = projectId;
        }

        private ProjectState(String projectId, TelemetryProjectRegistration registration) {
            this.projectId = projectId;
            this.registration = registration;
            this.available = true;
        }
    }
}
