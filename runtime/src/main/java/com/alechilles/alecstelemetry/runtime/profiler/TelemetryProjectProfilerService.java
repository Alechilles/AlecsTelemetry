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
    static final int MAX_RETAINED_PATH_ENTRIES = 500;
    static final int MAX_PROJECT_SUBSCRIPTIONS = 4;
    static final int MAX_GLOBAL_SUBSCRIPTIONS = 32;

    private static final long LISTENER_WARNING_INTERVAL_NANOS =
            TimeUnit.MINUTES.toNanos(1L);
    private static final String INITIALIZATION_DETAIL =
            "Profiler monitor diagnostics are not attached yet.";
    private static final String DISABLED_DETAIL =
            "Performance profiling is disabled for this project.";
    private static final String NO_PROJECT_DATA_DETAIL =
            "The latest Spark window contained no actionable project data.";
    private static final String CLOSED_DETAIL = "Profiler service is shut down.";
    private static final String UNKNOWN_MONITOR_STATE_DETAIL =
            "Profiler monitor returned an unknown state.";

    private final Supplier<List<TelemetryProjectRegistration>> projectsSupplier;
    private final Predicate<String> currentlyPerformanceEligible;
    private final String physicalRuntimeVersion;
    private final BiConsumer<Level, String> logSink;
    private final Consumer<ProfilerProjectAnalyzer.Analysis> beforeInsertHook;
    private final Runnable beforeDeliveryOfferHook;
    private final Object lock = new Object();
    private final Map<String, ProjectState> projects = new LinkedHashMap<>();
    private final Map<String, ProjectView> currentViews = new HashMap<>();
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();
    private final ArrayDeque<HistoryEntry> retainedHistory = new ArrayDeque<>();

    private ProfilerProjectOwnershipIndex ownership;
    private String ownershipKey = "";
    private int truncatedPrefixCount;
    private int retainedPathEntries;
    private Supplier<SparkProfilerDiagnostics> monitorDiagnostics;
    private long providerGeneration;
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
        this.projectsSupplier = Objects.requireNonNull(projects, "projects");
        this.currentlyPerformanceEligible = Objects.requireNonNull(
                currentlyPerformanceEligible,
                "currentlyPerformanceEligible"
        );
        this.physicalRuntimeVersion = requiredText(physicalRuntimeVersion);
        this.logSink = logSink;
        this.beforeInsertHook = Objects.requireNonNull(beforeInsertHook, "beforeInsertHook");
        this.beforeDeliveryOfferHook = Objects.requireNonNull(
                beforeDeliveryOfferHook,
                "beforeDeliveryOfferHook"
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
        synchronized (lock) {
            if (closed || !providerActive) {
                return;
            }
            capture = captureForPublishLocked();
        }
        if (capture.projects().isEmpty()) {
            return;
        }

        if (result.status() != SparkProfileReadResult.Status.COMPLETE
                || result.window() == null) {
            publishStatusOnly(capture, result);
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

        List<DeliveryTarget> deliveries = new ArrayList<>();
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || providerGeneration != capture.providerGeneration()) {
                return;
            }
            if (analysis.status() == ProfilerProjectAnalyzer.Analysis.Status.FAILED) {
                updateAnalysisFailureLocked(capture, analysis.detail());
                return;
            }

            Map<String, ProfilerProjectWindow> analyzed = new HashMap<>();
            for (ProfilerProjectWindow project : analysis.projects()) {
                analyzed.put(project.projectId(), project);
            }

            for (ProjectCapture projectCapture : capture.projects()) {
                ProjectState state = projects.get(projectCapture.projectId());
                if (!isInsertStillAuthorizedLocked(state, projectCapture)) {
                    continue;
                }
                ProfilerProjectWindow project = analyzed.get(projectCapture.projectId());
                if (project == null) {
                    state.lastState = TelemetryProfilerStatus.State.NO_DATA;
                    state.lastDetail = NO_PROJECT_DATA_DETAIL;
                    state.projectStatusAuthoritative = true;
                    state.lastSparkVersion = result.sparkVersion();
                    state.analysisDurationMillis = analysis.durationMillis();
                    state.omittedPathCount = Math.max(
                            state.omittedPathCount,
                            analysis.omittedPathCount()
                    );
                    continue;
                }
                TelemetryProfilerSnapshot snapshot = snapshotOf(project);
                int evictedPathCount = retainLocked(state, snapshot);
                long sequence = ++publicationSequence;
                state.lastState = TelemetryProfilerStatus.State.COMPLETE;
                state.lastDetail = "Latest Spark project profile window was summarized locally.";
                state.projectStatusAuthoritative = false;
                state.lastSparkVersion = snapshot.sparkVersion();
                state.analysisDurationMillis = analysis.durationMillis();
                state.omittedPathCount = Math.max(
                        state.omittedPathCount,
                        analysis.omittedPathCount() + evictedPathCount
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
        offerDeliveries(deliveries);
    }

    public void invalidate(@Nonnull String projectId) {
        synchronized (lock) {
            ProjectState state = projects.get(requiredText(projectId));
            if (state == null) {
                return;
            }
            state.authorizationGeneration++;
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

    public void refreshProjects() {
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
                    projects.remove(state.projectId);
                    currentViews.remove(state.projectId);
                    state.available = false;
                    state.authorizationGeneration++;
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

        }
        for (Subscription subscription : closeAfterUnlock) {
            subscription.stop();
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
        synchronized (lock) {
            if (closed || !providerActive) {
                return;
            }
            providerActive = false;
            providerGeneration++;
            for (ProjectState state : projects.values()) {
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
    }

    @Override
    public void close() {
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
    }

    private PublishCapture captureForPublishLocked() {
        List<ProjectCapture> captures = new ArrayList<>();
        for (ProjectState state : projects.values()) {
            if (!state.available || !observeEligibilityLocked(state)) {
                continue;
            }
            captures.add(new ProjectCapture(state.projectId, state.authorizationGeneration));
        }
        return new PublishCapture(providerGeneration, ownership, List.copyOf(captures));
    }

    private void publishStatusOnly(PublishCapture capture, SparkProfileReadResult result) {
        List<DeliveryTarget> ignored = List.of();
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
                && state.authorizationGeneration == capture.authorizationGeneration()
                && observeEligibilityLocked(state);
    }

    private int retainLocked(ProjectState state, TelemetryProfilerSnapshot snapshot) {
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

        while (retainedPathEntries > MAX_RETAINED_PATH_ENTRIES
                && !retainedHistory.isEmpty()) {
            HistoryEntry oldest = retainedHistory.removeFirst();
            if (oldest.state().history.remove(oldest)) {
                retainedPathEntries -= oldest.snapshot().paths().size();
                evictedPathCount += oldest.snapshot().paths().size();
            }
        }
        return evictedPathCount;
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

    private TelemetryProfilerSnapshot snapshotOf(ProfilerProjectWindow project) {
        List<TelemetryProfilerPathEvidence> paths = project.paths().stream()
                .limit(MAX_PATHS_PER_SNAPSHOT)
                .map(this::pathEvidenceOf)
                .toList();
        long observedAtMillis = Math.max(0L, System.currentTimeMillis());
        return new TelemetryProfilerSnapshot(
                project.projectId(),
                project.projectVersion(),
                project.sparkVersion(),
                project.windowKey(),
                observedAtMillis,
                project.selectedWorldThreadSelfMilliseconds(),
                project.qualificationRuleSet(),
                paths,
                TelemetryProfilerContext.unavailable()
        );
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
        StatusData base;
        Supplier<SparkProfilerDiagnostics> diagnosticsSupplier;
        synchronized (lock) {
            if (!state.available) {
                return TelemetryProfilerStatus.unavailable();
            }
            if (!observeEligibilityLocked(state)) {
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
        boolean projectStatusAuthoritative = base.projectStatusAuthoritative();
        boolean knownMonitorState = knownMonitorState(diagnostics.state());
        return new TelemetryProfilerStatus(
                projectStatusAuthoritative
                        ? base.state()
                        : statusState(diagnostics.state()),
                bounded(projectStatusAuthoritative
                        ? base.detail()
                        : knownMonitorState ? diagnostics.detail() : UNKNOWN_MONITOR_STATE_DETAIL),
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
                key.append(keyPart(prefix));
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
        Subscription accepted = null;
        synchronized (lock) {
            if (closed
                    || !providerActive
                    || state == null
                    || !state.available
                    || projects.get(state.projectId) != state
                    || !observeEligibilityLocked(state)) {
                return null;
            }
            if (state.subscriptionCount >= MAX_PROJECT_SUBSCRIPTIONS
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
            synchronized (lock) {
                if (!state.available || !observeEligibilityLocked(state) || state.history.isEmpty()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(state.history.getLast().snapshot());
            }
        }

        @Nonnull
        @Override
        public List<TelemetryProfilerSnapshot> history() {
            synchronized (lock) {
                if (!state.available || !observeEligibilityLocked(state) || state.history.isEmpty()) {
                    return List.of();
                }
                return state.history.stream().map(HistoryEntry::snapshot).toList();
            }
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
            synchronized (lock) {
                return !closed.get()
                        && !TelemetryProjectProfilerService.this.closed
                        && providerActive
                        && providerGeneration == delivery.providerGeneration()
                        && state.available
                        && state.authorizationGeneration == delivery.authorizationGeneration()
                        && observeEligibilityLocked(state);
            }
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

    private record ProjectCapture(String projectId, long authorizationGeneration) {
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
