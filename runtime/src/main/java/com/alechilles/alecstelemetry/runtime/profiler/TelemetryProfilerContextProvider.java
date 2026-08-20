package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Captures publication-time profiler context from independent, optional
 * runtime sources.
 */
public final class TelemetryProfilerContextProvider {
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 60_000L;

    private final IntSupplier playerCount;
    private final String hytaleVersion;
    private final SparkPublicMetricsAdapter sparkMetrics;
    private final TelemetryProfilerBreadcrumbCounter breadcrumbs;
    private final Predicate<String> breadcrumbEligible;
    private final LongSupplier clock;
    private final BiConsumer<Level, String> logSink;
    private final RefreshOperation refreshOperation;
    private final ClearOperation clearOperation;
    private final ClearAllOperation clearAllOperation;
    private final EnumMap<Source, Long> lastFailureLogMillis = new EnumMap<>(Source.class);

    public TelemetryProfilerContextProvider(
            @Nonnull IntSupplier playerCount,
            @Nullable String hytaleVersion,
            @Nonnull SparkPublicMetricsAdapter sparkMetrics,
            @Nonnull TelemetryProfilerBreadcrumbCounter breadcrumbs,
            @Nonnull Predicate<String> breadcrumbEligible,
            @Nonnull LongSupplier clock,
            @Nullable BiConsumer<Level, String> logSink) {
        this(
                playerCount,
                hytaleVersion,
                sparkMetrics,
                breadcrumbs,
                breadcrumbEligible,
                clock,
                logSink,
                breadcrumbs::refreshProjects,
                breadcrumbs::clear,
                breadcrumbs::clearAll
        );
    }

    TelemetryProfilerContextProvider(
            @Nonnull IntSupplier playerCount,
            @Nullable String hytaleVersion,
            @Nonnull SparkPublicMetricsAdapter sparkMetrics,
            @Nonnull TelemetryProfilerBreadcrumbCounter breadcrumbs,
            @Nonnull Predicate<String> breadcrumbEligible,
            @Nonnull LongSupplier clock,
            @Nullable BiConsumer<Level, String> logSink,
            @Nonnull RefreshOperation refreshOperation,
            @Nonnull ClearOperation clearOperation,
            @Nonnull ClearAllOperation clearAllOperation) {
        this.playerCount = Objects.requireNonNull(playerCount, "playerCount");
        this.hytaleVersion = normalizeVersion(hytaleVersion);
        this.sparkMetrics = Objects.requireNonNull(sparkMetrics, "sparkMetrics");
        this.breadcrumbs = Objects.requireNonNull(breadcrumbs, "breadcrumbs");
        this.breadcrumbEligible = Objects.requireNonNull(breadcrumbEligible, "breadcrumbEligible");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logSink = logSink;
        this.refreshOperation = Objects.requireNonNull(refreshOperation, "refreshOperation");
        this.clearOperation = Objects.requireNonNull(clearOperation, "clearOperation");
        this.clearAllOperation = Objects.requireNonNull(clearAllOperation, "clearAllOperation");
    }

    @Nonnull
    public static TelemetryProfilerContextProvider unavailable() {
        return new TelemetryProfilerContextProvider(
                () -> -1,
                null,
                new SparkPublicMetricsAdapter(() -> null),
                new TelemetryProfilerBreadcrumbCounter(),
                ignored -> false,
                System::currentTimeMillis,
                null
        );
    }

    @Nonnull
    public TelemetryProfilerContext snapshot(@Nullable String projectId) {
        long observedAtMillis = publicationTime();

        Integer currentPlayers = null;
        try {
            int supplied = playerCount.getAsInt();
            if (supplied >= 0) {
                currentPlayers = supplied;
            }
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.PLAYER_COUNT, failure, observedAtMillis);
        }

        SparkPublicMetrics metrics = SparkPublicMetrics.unavailable();
        try {
            SparkPublicMetrics supplied = sparkMetrics.snapshot();
            if (supplied != null) {
                metrics = supplied;
            }
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.SPARK_METRICS, failure, observedAtMillis);
        }

        Map<String, Integer> categoryCounts = Map.of();
        try {
            if (breadcrumbEligible.test(projectId)) {
                Map<String, Integer> supplied = breadcrumbs.snapshot(projectId);
                categoryCounts = supplied == null ? Map.of() : Map.copyOf(supplied);
            } else {
                clearProject(projectId, observedAtMillis);
            }
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            categoryCounts = Map.of();
            logFailure(Source.BREADCRUMBS, failure, observedAtMillis);
            clearProject(projectId, observedAtMillis);
        }

        return new TelemetryProfilerContext(
                observedAtMillis,
                hytaleVersion,
                currentPlayers,
                validMetric(metrics.tps()),
                validMetric(metrics.mspt()),
                categoryCounts
        );
    }

    public void refreshProjects(@Nonnull List<TelemetryProjectRegistration> projects) {
        refreshProjectsConfirmed(projects);
    }

    boolean refreshProjectsConfirmed(@Nonnull List<TelemetryProjectRegistration> projects) {
        try {
            Objects.requireNonNull(projects, "projects");
            refreshOperation.refresh(projects);
            return true;
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.BREADCRUMBS, failure);
            return false;
        }
    }

    public void recordBreadcrumb(@Nullable String projectId, @Nullable String category) {
        if (projectId == null || category == null) {
            return;
        }
        boolean eligible;
        try {
            eligible = breadcrumbEligible.test(projectId);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.BREADCRUMBS, failure);
            clearProject(projectId);
            return;
        }
        if (!eligible) {
            clearProject(projectId);
            return;
        }
        try {
            breadcrumbs.record(projectId, category);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.BREADCRUMBS, failure);
        }
    }

    public void clear(@Nullable String projectId) {
        clearConfirmed(projectId);
    }

    public void clearAll() {
        clearAllConfirmed();
    }

    boolean clearConfirmed(@Nullable String projectId) {
        return clearProjectConfirmed(projectId, safeNowForLog());
    }

    boolean clearAllConfirmed() {
        try {
            clearAllOperation.clearAll();
            return true;
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.BREADCRUMBS, failure);
            return false;
        }
    }

    private void clearProject(@Nullable String projectId) {
        clearConfirmed(projectId);
    }

    private void clearProject(@Nullable String projectId, long nowMillis) {
        clearProjectConfirmed(projectId, nowMillis);
    }

    private boolean clearProjectConfirmed(@Nullable String projectId, long nowMillis) {
        try {
            clearOperation.clear(projectId);
            return true;
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.BREADCRUMBS, failure, nowMillis);
            return false;
        }
    }

    private long publicationTime() {
        try {
            return Math.max(0L, clock.getAsLong());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            logFailure(Source.CLOCK, failure, 0L);
            return 0L;
        }
    }

    private long safeNowForLog() {
        try {
            return Math.max(0L, clock.getAsLong());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return 0L;
        }
    }

    private void logFailure(@Nonnull Source source, @Nonnull Throwable failure) {
        if (logSink == null) {
            return;
        }
        logFailure(source, failure, safeNowForLog());
    }

    @Nullable
    private static Double validMetric(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0d ? value : null;
    }

    @Nullable
    private static String normalizeVersion(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void logFailure(@Nonnull Source source,
                            @Nonnull Throwable failure,
                            long nowMillis) {
        if (logSink == null) {
            return;
        }
        synchronized (lastFailureLogMillis) {
            Long previous = lastFailureLogMillis.get(source);
            if (previous != null && nowMillis - previous < FAILURE_LOG_INTERVAL_MILLIS) {
                return;
            }
            lastFailureLogMillis.put(source, nowMillis);
        }
        try {
            logSink.accept(
                    Level.WARNING,
                    "Telemetry profiler context source " + source.label
                            + " failed safely: " + failureSummary(failure)
            );
        } catch (Throwable sinkFailure) {
            rethrowIfFatal(sinkFailure);
            // A diagnostic sink must not affect publication.
        }
    }

    @Nonnull
    private static String failureSummary(@Nonnull Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": " + message.trim();
    }

    private static void rethrowIfFatal(@Nonnull Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }

    private enum Source {
        CLOCK("clock"),
        PLAYER_COUNT("player count"),
        SPARK_METRICS("Spark metrics"),
        BREADCRUMBS("breadcrumbs");

        private final String label;

        Source(@Nonnull String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    interface RefreshOperation {
        void refresh(@Nonnull List<TelemetryProjectRegistration> projects);
    }

    @FunctionalInterface
    interface ClearOperation {
        void clear(@Nullable String projectId);
    }

    @FunctionalInterface
    interface ClearAllOperation {
        void clearAll();
    }
}
