package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerDiagnostics;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Shows the local summaries produced by the optional Spark profiler monitor.
 */
public final class TelemetryProfilerCommand extends AbstractCommandCollection {
    private static final int MAX_TOP_ENTRIES = 10;
    private static final int MAX_HISTORY_ENTRIES = 10;
    private static final int MAX_OUTPUT_TEXT = 320;

    public TelemetryProfilerCommand(@Nonnull TelemetryCommandRuntime runtime) {
        super("profiler", "Show local Spark profiler summaries.");
        setPermissionGroups(TelemetryCommandPermissions.adminGroups());
        addSubCommand(new StatusCommand(runtime));
        addSubCommand(new TopCommand(runtime));
        addSubCommand(new HistoryCommand(runtime));
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
                TelemetryCommandSupport.send(commandContext, "Telemetry profiler is unavailable.");
                return;
            }
            TelemetryCommandSupport.send(
                    commandContext,
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
            setAllowsExtraArguments(false);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                TelemetryCommandSupport.send(commandContext, "Telemetry profiler is unavailable.");
                return;
            }
            SparkProfilerDiagnostics diagnostics = runtime.sparkProfilerDiagnostics();
            if (diagnostics.state() == SparkProfilerDiagnostics.State.DISABLED) {
                TelemetryCommandSupport.send(
                        commandContext,
                        "Spark profiler top: disabled. " + bounded(diagnostics.detail())
                );
                return;
            }
            if (diagnostics.state() != SparkProfilerDiagnostics.State.COMPLETE
                    || diagnostics.hotPaths().isEmpty()) {
                TelemetryCommandSupport.send(
                        commandContext,
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
            TelemetryCommandSupport.send(
                    commandContext,
                    "Spark profiler top: window=" + diagnostics.windowKey()
                            + ", sparkVersion=" + safeValue(diagnostics.sparkVersion())
                            + ", showing=" + count + " of " + hotPaths.size()
            );
            for (int index = 0; index < count; index++) {
                TelemetryCommandSupport.send(commandContext, formatHotPath(index + 1, hotPaths.get(index)));
            }
        }
    }

    private static final class HistoryCommand extends CommandBase {
        private final TelemetryCommandRuntime runtime;

        private HistoryCommand(@Nonnull TelemetryCommandRuntime runtime) {
            super("history", "Show retained Spark profiler windows.");
            this.runtime = runtime;
            setPermissionGroups(TelemetryCommandPermissions.adminGroups());
            setAllowsExtraArguments(false);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext commandContext) {
            if (runtime == null) {
                TelemetryCommandSupport.send(commandContext, "Telemetry profiler is unavailable.");
                return;
            }
            List<SparkProfileSnapshot> history = runtime.sparkProfilerHistory();
            if (history == null || history.isEmpty()) {
                TelemetryCommandSupport.send(
                        commandContext,
                        "Spark profiler history: no completed windows retained."
                );
                return;
            }

            int start = Math.max(0, history.size() - MAX_HISTORY_ENTRIES);
            int count = history.size() - start;
            TelemetryCommandSupport.send(
                    commandContext,
                    "Spark profiler history: showing " + count + " of " + history.size()
                            + " retained windows."
            );
            for (int index = start; index < history.size(); index++) {
                TelemetryCommandSupport.send(commandContext, formatHistoryLine(history.get(index)));
            }
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
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= MAX_OUTPUT_TEXT) {
            return normalized;
        }
        return normalized.substring(0, MAX_OUTPUT_TEXT - 3) + "...";
    }
}
