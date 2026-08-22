package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.alechilles.alecstelemetry.runtime.profiler.ProfilerProjectSignal;
import com.alechilles.alecstelemetry.runtime.profiler.ProfilerSignalEngine;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerDiagnostics;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shows the local summaries produced by the optional Spark profiler monitor.
 */
public final class TelemetryProfilerCommand extends AbstractCommandCollection {
    private static final int MAX_TOP_ENTRIES = 10;
    private static final int MAX_PROJECT_TOP_ENTRIES = 5;
    private static final int MAX_SIGNAL_ENTRIES = 5;
    private static final int MAX_SIGNAL_WINDOWS = 5;
    private static final int MAX_HISTORY_ENTRIES = 10;
    private static final int MAX_PROJECT_ID_LENGTH = 120;
    private static final int MAX_PROJECT_MEASUREMENT_TEXT = 12;
    private static final int MAX_OUTPUT_TEXT = 320;

    public TelemetryProfilerCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("profiler", "Show local Spark profiler summaries.");
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        addSubCommand(new StatusCommand(runtime));
        addSubCommand(new TopCommand(runtime));
        addSubCommand(new HistoryCommand(runtime));
        addSubCommand(new SignalsCommand(runtime));
    }

    private static final class StatusCommand extends CommandBase {
        private final TelemetryCommandRuntime runtime;

        private StatusCommand(@Nonnull TelemetryCommandRuntime runtime) {
            super("status", "Show Spark profiler monitor status.");
            this.runtime = runtime;
            setPermissionGroups(TelemetryCommandPermissions.adminGroups());
            setAllowsExtraArguments(false);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                send(commandContext, null, "Telemetry profiler is unavailable.");
                return;
            }
            send(
                    commandContext,
                    runtime,
                    formatStatus(runtime.sparkProfilerDiagnostics())
            );
        }
    }

    private static final class TopCommand extends CommandBase {
        private final TelemetryCommandRuntime runtime;

        private TopCommand(@Nonnull TelemetryCommandRuntime runtime) {
            super("top", "Show the latest Spark WorldThread hot paths.");
            this.runtime = runtime;
            setPermissionGroups(TelemetryCommandPermissions.adminGroups());
            setAllowsExtraArguments(true);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                send(commandContext, null, "Telemetry profiler is unavailable.");
                return;
            }
            ProjectArgument projectArgument = projectArgument(commandContext, "top");
            if (!projectArgument.valid()) {
                send(commandContext, runtime, usage("top"));
                return;
            }
            if (projectArgument.projectId() != null) {
                executeProjectTop(commandContext, runtime, projectArgument.projectId());
                return;
            }
            SparkProfilerDiagnostics diagnostics = runtime.sparkProfilerDiagnostics();
            if (diagnostics.state() == SparkProfilerDiagnostics.State.DISABLED) {
                send(
                        commandContext,
                        runtime,
                        "Spark profiler top: disabled. " + bounded(diagnostics.detail())
                );
                return;
            }
            if (diagnostics.state() != SparkProfilerDiagnostics.State.COMPLETE
                    || diagnostics.hotPaths().isEmpty()) {
                send(
                        commandContext,
                        runtime,
                        "Spark profiler top: no actionable data (state="
                                + diagnostics.state().name()
                                + ", detail="
                                + bounded(diagnostics.detail())
                                + ")."
                );
                return;
            }

            List<SparkProfileSnapshot.HotPath> hotPaths = diagnostics.hotPaths();
            int count = Math.min(MAX_TOP_ENTRIES, hotPaths.size());
            send(
                    commandContext,
                    runtime,
                    "Spark profiler top: window=" + diagnostics.windowKey()
                            + ", sparkVersion=" + safeValue(diagnostics.sparkVersion())
                            + ", showing=" + count + " of " + hotPaths.size()
            );
            for (int index = 0; index < count; index++) {
                send(commandContext, runtime, formatHotPath(index + 1, hotPaths.get(index)));
            }
        }
    }

    private static final class HistoryCommand extends CommandBase {
        private final TelemetryCommandRuntime runtime;

        private HistoryCommand(@Nonnull TelemetryCommandRuntime runtime) {
            super("history", "Show retained Spark profiler windows.");
            this.runtime = runtime;
            setPermissionGroups(TelemetryCommandPermissions.adminGroups());
            setAllowsExtraArguments(true);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                send(commandContext, null, "Telemetry profiler is unavailable.");
                return;
            }
            ProjectArgument projectArgument = projectArgument(commandContext, "history");
            if (!projectArgument.valid()) {
                send(commandContext, runtime, usage("history"));
                return;
            }
            if (projectArgument.projectId() != null) {
                executeProjectHistory(commandContext, runtime, projectArgument.projectId());
                return;
            }
            List<SparkProfileSnapshot> history = runtime.sparkProfilerHistory();
            if (history == null || history.isEmpty()) {
                send(
                        commandContext,
                        runtime,
                        "Spark profiler history: no completed windows retained."
                );
                return;
            }

            int start = Math.max(0, history.size() - MAX_HISTORY_ENTRIES);
            int count = history.size() - start;
            send(
                    commandContext,
                    runtime,
                    "Spark profiler history: showing " + count + " of " + history.size()
                            + " retained windows."
            );
            for (int index = start; index < history.size(); index++) {
                send(commandContext, runtime, formatHistoryLine(history.get(index)));
            }
        }
    }

    private static final class SignalsCommand extends CommandBase {
        private final TelemetryCommandRuntime runtime;

        private SignalsCommand(@Nonnull TelemetryCommandRuntime runtime) {
            super("signals", "Show rolling Spark profiler hot-path signals for a project.");
            this.runtime = runtime;
            setPermissionGroups(TelemetryCommandPermissions.adminGroups());
            setAllowsExtraArguments(true);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                send(commandContext, null, "Telemetry profiler is unavailable.");
                return;
            }
            ProjectArgument projectArgument = projectArgument(commandContext, "signals");
            if (!projectArgument.valid() || projectArgument.projectId() == null) {
                send(commandContext, runtime, usageRequired("signals"));
                return;
            }

            String projectId = projectArgument.projectId();
            TelemetryProfilerView view = projectView(runtime, projectId);
            if (isUnavailable(view)) {
                send(commandContext, runtime,
                        "Spark profiler signals: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                                + " unavailable.");
                return;
            }

            List<TelemetryProfilerSnapshot> history;
            try {
                List<TelemetryProfilerSnapshot> source = view.history();
                history = source == null ? List.of() : List.copyOf(source);
            } catch (RuntimeException | LinkageError ignored) {
                send(commandContext, runtime,
                        "Spark profiler signals: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                                + " unavailable.");
                return;
            }
            if (history.isEmpty()) {
                send(commandContext, runtime, projectNoData("signals", projectId, view));
                return;
            }

            List<ProfilerProjectSignal> signals;
            try {
                signals = new ProfilerSignalEngine().signals(history);
            } catch (RuntimeException | LinkageError ignored) {
                send(commandContext, runtime,
                        "Spark profiler signals: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                                + " unavailable.");
                return;
            }
            if (signals == null || signals.isEmpty()) {
                send(commandContext, runtime,
                        "Spark profiler signals: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                                + " has no active signals.");
                return;
            }

            int count = Math.min(MAX_SIGNAL_ENTRIES, signals.size());
            send(commandContext, runtime,
                    "Spark profiler signals: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                            + ", ruleSet=" + ProfilerSignalEngine.RULE_SET
                            + ", showing=" + count + " of " + signals.size());
            for (int index = 0; index < count; index++) {
                ProfilerProjectSignal signal = signals.get(index);
                TelemetryProfilerPathEvidence path = signal.representative();
                send(commandContext, runtime, formatSignal(index + 1, signal));
                send(commandContext, runtime,
                        formatMethodEvidence("ownedMethod=",
                                path.ownedClassName(),
                                path.ownedMethodName(),
                                path.ownedMethodDescriptor()));
                if (path.attribution() == TelemetryProfilerAttribution.DOWNSTREAM
                        && (path.firstExternalClassName() != null
                        || path.firstExternalMethodName() != null
                        || path.firstExternalMethodDescriptor() != null)) {
                    send(commandContext, runtime,
                            formatMethodEvidence("externalMethod=",
                                    path.firstExternalClassName(),
                                    path.firstExternalMethodName(),
                                    path.firstExternalMethodDescriptor()));
                }
                if (!signal.recentCorrelationCounts().isEmpty()) {
                    send(commandContext, runtime,
                            formatCorrelations(signal.recentCorrelationCounts()));
                }
            }
        }
    }

    private static void executeProjectTop(@Nonnull CommandContext commandContext,
                                          @Nonnull TelemetryCommandRuntime runtime,
                                          @Nonnull String projectId) {
        TelemetryProfilerView view = projectView(runtime, projectId);
        if (isUnavailable(view)) {
            send(commandContext, runtime,
                    "Spark profiler top: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                            + " unavailable.");
            return;
        }

        Optional<TelemetryProfilerSnapshot> latest;
        try {
            latest = view.latest();
        } catch (RuntimeException | LinkageError ignored) {
            latest = Optional.empty();
        }
        if (latest == null || latest.isEmpty()) {
            send(commandContext, runtime, projectNoData("top", projectId, view));
            return;
        }

        TelemetryProfilerSnapshot snapshot = latest.get();
        TelemetryProfilerStatus status = projectStatus(view);
        List<TelemetryProfilerPathEvidence> paths = snapshot.paths();
        int count = Math.min(MAX_PROJECT_TOP_ENTRIES, paths.size());
        send(commandContext, runtime, bounded(String.format(
                Locale.ROOT,
                "Spark profiler top: project=%s, window=%d, sparkVersion=%s, showing=%d of %d, "
                        + "analysisDurationMs=%d, omittedPaths=%d, truncatedPrefixes=%d",
                bounded(projectId, MAX_PROJECT_ID_LENGTH),
                snapshot.windowKey(),
                safeValue(snapshot.sparkVersion()),
                count,
                paths.size(),
                status.analysisDurationMillis(),
                status.omittedPathCount(),
                status.truncatedPrefixCount()
        )));
        for (int index = 0; index < count; index++) {
            TelemetryProfilerPathEvidence path = paths.get(index);
            send(commandContext, runtime, formatProjectPath(index + 1, path));
            send(commandContext, runtime,
                    formatMethodEvidence("ownedMethod=",
                            path.ownedClassName(),
                            path.ownedMethodName(),
                            path.ownedMethodDescriptor()));
            if (path.attribution() == TelemetryProfilerAttribution.DOWNSTREAM
                    && (path.firstExternalClassName() != null
                    || path.firstExternalMethodName() != null
                    || path.firstExternalMethodDescriptor() != null)) {
                send(commandContext, runtime,
                        formatMethodEvidence("externalMethod=",
                                path.firstExternalClassName(),
                                path.firstExternalMethodName(),
                                path.firstExternalMethodDescriptor()));
            }
        }
    }

    private static void executeProjectHistory(@Nonnull CommandContext commandContext,
                                              @Nonnull TelemetryCommandRuntime runtime,
                                              @Nonnull String projectId) {
        TelemetryProfilerView view = projectView(runtime, projectId);
        if (isUnavailable(view)) {
            send(commandContext, runtime,
                    "Spark profiler history: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                            + " unavailable.");
            return;
        }

        List<TelemetryProfilerSnapshot> history;
        try {
            history = view.history();
        } catch (RuntimeException | LinkageError ignored) {
            history = List.of();
        }
        if (history == null || history.isEmpty()) {
            send(commandContext, runtime, projectNoData("history", projectId, view));
            return;
        }

        int start = Math.max(0, history.size() - MAX_HISTORY_ENTRIES);
        int count = history.size() - start;
        send(commandContext, runtime,
                "Spark profiler history: project=" + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                        + ", showing " + count + " of " + history.size()
                        + " retained windows.");
        for (int index = start; index < history.size(); index++) {
            send(commandContext, runtime, formatProjectHistoryLine(history.get(index)));
        }
    }

    @Nonnull
    private static TelemetryProfilerView projectView(@Nonnull TelemetryCommandRuntime runtime,
                                                     @Nonnull String projectId) {
        try {
            TelemetryProfilerView view = runtime.projectProfiler(projectId);
            return view == null ? TelemetryProfilerView.unavailable() : view;
        } catch (RuntimeException | LinkageError ignored) {
            return TelemetryProfilerView.unavailable();
        }
    }

    private static boolean isUnavailable(@Nullable TelemetryProfilerView view) {
        return projectStatus(view).state() == TelemetryProfilerStatus.State.UNAVAILABLE;
    }

    @Nonnull
    private static TelemetryProfilerStatus projectStatus(@Nullable TelemetryProfilerView view) {
        if (view == null) {
            return TelemetryProfilerStatus.unavailable();
        }
        try {
            TelemetryProfilerStatus status = view.status();
            return status == null ? TelemetryProfilerStatus.unavailable() : status;
        } catch (RuntimeException | LinkageError ignored) {
            return TelemetryProfilerStatus.unavailable();
        }
    }

    @Nonnull
    private static String projectNoData(@Nonnull String command,
                                        @Nonnull String projectId,
                                        @Nonnull TelemetryProfilerView view) {
        String state = projectStatus(view).state().name();
        return "Spark profiler " + command + ": project="
                + bounded(projectId, MAX_PROJECT_ID_LENGTH)
                + " has no completed data (state=" + state + ").";
    }

    @Nonnull
    private static String formatProjectPath(int rank,
                                            @Nonnull TelemetryProfilerPathEvidence path) {
        StringBuilder line = new StringBuilder()
                .append(rank)
                .append(". attribution=").append(path.attribution().name())
                .append(", sampledMs=").append(measurement(path.sampledMilliseconds())).append(" ms")
                .append(", worldThreadShare=").append(measurement(path.selectedWorldThreadSharePercent()))
                .append('%')
                .append(", qualification=").append(path.qualification().name())
                .append(", fingerprint=").append(bounded(path.fingerprint(), 32));
        return bounded(line.toString());
    }

    @Nonnull
    private static String formatProjectHistoryLine(@Nonnull TelemetryProfilerSnapshot snapshot) {
        return bounded(String.format(
                Locale.ROOT,
                "window=%d, sparkVersion=%s, observedAtMillis=%d, worldThreadSelfMs=%.2f, paths=%d",
                snapshot.windowKey(),
                safeValue(snapshot.sparkVersion()),
                snapshot.observedAtMillis(),
                snapshot.selectedWorldThreadSelfMilliseconds(),
                snapshot.paths().size()
        ));
    }

    @Nonnull
    private static String projectMethod(@Nullable String className,
                                        @Nullable String methodName,
                                        @Nullable String descriptor,
                                        int maxLength) {
        int tupleLength = Math.max(9, maxLength);
        String safeClass = methodComponent(className);
        String safeMethod = methodComponent(methodName);
        String safeDescriptor = methodComponent(descriptor);
        String complete = safeClass + "#" + safeMethod + safeDescriptor;
        if (complete.length() <= tupleLength) {
            return complete;
        }
        int componentBudget = tupleLength - 1;
        int classLength = componentBudget / 3;
        int methodLength = componentBudget / 3;
        int descriptorLength = componentBudget - classLength - methodLength;
        return boundedComponent(safeClass, classLength)
                + "#" + boundedComponent(safeMethod, methodLength)
                + boundedComponent(safeDescriptor, descriptorLength);
    }

    @Nonnull
    private static String formatMethodEvidence(@Nonnull String label,
                                                @Nullable String className,
                                                @Nullable String methodName,
                                                @Nullable String descriptor) {
        int tupleLength = MAX_OUTPUT_TEXT - label.length();
        return bounded(label + projectMethod(className, methodName, descriptor, tupleLength));
    }

    @Nonnull
    private static String boundedComponent(@Nullable String value, int maxLength) {
        String normalized = methodComponent(value);
        int limit = Math.max(1, maxLength);
        if (normalized.length() <= limit) {
            return normalized;
        }
        if (limit <= 3) {
            return normalized.substring(0, limit);
        }
        return normalized.substring(0, limit - 3) + "...";
    }

    @Nonnull
    private static String methodComponent(@Nullable String value) {
        return value == null || value.isBlank()
                ? "<unknown>"
                : value.replace('\n', ' ').replace('\r', ' ');
    }

    @Nonnull
    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Nonnull
    private static String measurement(double value) {
        return bounded(formatNumber(value), MAX_PROJECT_MEASUREMENT_TEXT);
    }

    @Nonnull
    private static String usage(@Nonnull String command) {
        return "Usage: /telemetry profiler " + command + " [project-id]";
    }

    @Nonnull
    private static String usageRequired(@Nonnull String command) {
        return "Usage: /telemetry profiler " + command + " <project-id>";
    }

    @Nonnull
    private static String formatSignal(int rank, @Nonnull ProfilerProjectSignal signal) {
        TelemetryProfilerPathEvidence path = signal.representative();
        return bounded(String.format(
                Locale.ROOT,
                "%d. severity=%s, qualification=%s, hits=%d/%d, medianShare=%s%%, "
                        + "totalSampledMs=%s, attribution=%s, fingerprint=%s",
                rank,
                signal.severity().name(),
                signal.qualification().name(),
                signal.qualifyingWindows(),
                MAX_SIGNAL_WINDOWS,
                measurement(signal.medianSharePercent()),
                measurement(signal.totalSampledMilliseconds()),
                path.attribution().name(),
                bounded(path.fingerprint(), 32)
        ));
    }

    @Nonnull
    private static String formatCorrelations(@Nonnull Map<String, Integer> counts) {
        StringBuilder line = new StringBuilder("correlations=");
        int added = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(5)
                .toList()) {
            if (added++ > 0) {
                line.append(',');
            }
            line.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return bounded(line.toString());
    }

    @Nonnull
    private static ProjectArgument projectArgument(@Nonnull CommandContext commandContext,
                                                   @Nonnull String subcommand) {
        String input = commandContext.getInputString();
        if (input == null || input.isBlank()) {
            return ProjectArgument.none();
        }
        String[] tokens = input.trim().split("\\s+");
        int subcommandIndex = -1;
        for (int index = 0; index < tokens.length; index++) {
            if (subcommand.equals(tokens[index])) {
                subcommandIndex = index;
                break;
            }
        }
        if (subcommandIndex < 0) {
            if (tokens.length != 1) {
                return ProjectArgument.invalid();
            }
            return normalizeProjectArgument(tokens[0]);
        }
        int argumentCount = tokens.length - subcommandIndex - 1;
        if (argumentCount == 0) {
            return ProjectArgument.none();
        }
        if (argumentCount != 1) {
            return ProjectArgument.invalid();
        }
        return normalizeProjectArgument(tokens[subcommandIndex + 1]);
    }

    @Nonnull
    private static ProjectArgument normalizeProjectArgument(@Nullable String value) {
        if (value == null) {
            return ProjectArgument.invalid();
        }
        String projectId = value.trim();
        if (projectId.isBlank() || projectId.length() > MAX_PROJECT_ID_LENGTH) {
            return ProjectArgument.invalid();
        }
        return new ProjectArgument(true, projectId);
    }

    private record ProjectArgument(boolean valid, @Nullable String projectId) {
        @Nonnull
        private static ProjectArgument none() {
            return new ProjectArgument(true, null);
        }

        @Nonnull
        private static ProjectArgument invalid() {
            return new ProjectArgument(false, null);
        }
    }

    private static void send(@Nonnull CommandContext commandContext,
                             @Nullable TelemetryCommandRuntime runtime,
                             @Nonnull String message) {
        TelemetryCommandSupport.send(commandContext, message);
        if (runtime == null) {
            return;
        }
        try {
            runtime.logProfilerCommandOutput(message);
        } catch (RuntimeException ignored) {
        }
    }

    @Nonnull
    static String formatStatus(@Nullable SparkProfilerDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Spark profiler: unavailable.";
        }
        StringBuilder status = new StringBuilder("Spark profiler: state=")
                .append(diagnostics.state().name())
                .append(", active=").append(diagnostics.active())
                .append(", circuitOpen=").append(diagnostics.circuitOpen())
                .append(", sparkVersion=").append(safeValue(diagnostics.sparkVersion()))
                .append(", nextCaptureAtMillis=").append(diagnostics.nextCaptureAtMillis())
                .append(", lastWindow=")
                .append(diagnostics.state() == SparkProfilerDiagnostics.State.COMPLETE
                        ? diagnostics.windowKey()
                        : "<none>")
                .append(", captureDurationMs=").append(diagnostics.captureDurationMillis())
                .append(", heapDeltaBytes=").append(diagnostics.heapDeltaBytes())
                .append(", threads=").append(diagnostics.threadCount())
                .append(", frames=").append(diagnostics.frameCount())
                .append(", hotPaths=").append(diagnostics.hotPaths().size());
        if (!diagnostics.detail().isBlank()) {
            status.append(", detail=").append(diagnostics.detail());
        }
        return bounded(status.toString());
    }

    @Nonnull
    static String formatHotPath(int rank, @Nonnull SparkProfileSnapshot.HotPath hotPath) {
        return bounded(String.format(
                Locale.ROOT,
                "%d. %.2f ms (%.2f%%) %s :: %s",
                rank,
                hotPath.selfMilliseconds(),
                hotPath.selfSharePercent(),
                safeValue(hotPath.source()),
                safeValue(hotPath.frame())
        ));
    }

    @Nonnull
    static String formatHistoryLine(@Nonnull SparkProfileSnapshot snapshot) {
        return bounded(String.format(
                Locale.ROOT,
                "window=%d, sparkVersion=%s, threads=%d/%d, frames=%d/%d, worldThreadSelfMs=%.2f, hotPaths=%d",
                snapshot.windowKey(),
                safeValue(snapshot.sparkVersion()),
                snapshot.selectedThreadCount(),
                snapshot.totalThreadCount(),
                snapshot.selectedFrameCount(),
                snapshot.totalFrameCount(),
                snapshot.totalSelfMilliseconds(),
                snapshot.hotPaths().size()
        ));
    }

    @Nonnull
    private static String safeValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        return bounded(value);
    }

    @Nonnull
    private static String bounded(@Nullable String value) {
        return bounded(value, MAX_OUTPUT_TEXT);
    }

    @Nonnull
    private static String bounded(@Nullable String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
