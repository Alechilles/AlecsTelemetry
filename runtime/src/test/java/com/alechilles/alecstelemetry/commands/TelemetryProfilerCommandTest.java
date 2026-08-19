package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSubscription;
import com.alechilles.alecstelemetry.api.TelemetryProfilerView;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeDiagnostics;
import com.alechilles.alecstelemetry.runtime.host.TelemetryCommandRuntime;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfileSnapshot;
import com.alechilles.alecstelemetry.runtime.profiler.SparkProfilerDiagnostics;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryProfilerCommandTest {

    @Test
    void statusAndTopExplainWhenProfilerIsDisabled() throws Exception {
        TelemetryCommandRuntime runtime = runtime(
                SparkProfilerDiagnostics.disabled(),
                List.of()
        );
        TelemetryProfilerCommand profiler = new TelemetryProfilerCommand(runtime);

        TestCommandSender statusSender = execute(profiler, "status", "/telemetry profiler status");
        TestCommandSender topSender = execute(profiler, "top", "/telemetry profiler top");

        assertEquals(1, statusSender.messages.size());
        assertTrue(statusSender.text().contains("state=DISABLED"));
        assertTrue(statusSender.text().contains("Disabled in Settings/runtime.json."));
        assertEquals(1, topSender.messages.size());
        assertTrue(topSender.text().contains("disabled"));
        assertTrue(topSender.text().contains("Disabled in Settings/runtime.json."));
    }

    @Test
    void topExplainsWhenCompletedWindowHasNoActionableWorldThreadData() throws Exception {
        SparkProfilerDiagnostics diagnostics = new SparkProfilerDiagnostics(
                SparkProfilerDiagnostics.State.NO_ACTIONABLE_DATA,
                "1.10.172-beta7",
                "Completed Spark window had no Hytale WorldThread Java samples.",
                24L,
                1024L,
                0,
                4,
                19,
                List.of()
        );
        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime(diagnostics, List.of())),
                "top",
                "/telemetry profiler top"
        );

        assertEquals(1, sender.messages.size());
        assertTrue(sender.text().contains("no actionable"));
        assertTrue(sender.text().contains("WorldThread"));
    }

    @Test
    void topShowsBoundedWorldThreadRankingForActionableWindow() throws Exception {
        SparkProfileSnapshot.HotPath first = new SparkProfileSnapshot.HotPath(
                "ExampleMod", "com.example.TickSystem#tick()", 42.5, 68.0
        );
        SparkProfileSnapshot.HotPath second = new SparkProfileSnapshot.HotPath(
                "Hytale", "com.hypixel.World#step()", 20.0, 32.0
        );
        SparkProfilerDiagnostics diagnostics = new SparkProfilerDiagnostics(
                SparkProfilerDiagnostics.State.COMPLETE,
                "1.10.172-beta7",
                "Raw Spark profile data was not retained.",
                40L,
                2048L,
                17,
                2,
                12,
                List.of(first, second)
        );
        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime(diagnostics, List.of())),
                "top",
                "/telemetry profiler top"
        );

        assertEquals(3, sender.messages.size());
        assertTrue(sender.messages.get(0).getRawText().contains("window=17"));
        assertTrue(sender.messages.get(1).getRawText().contains("ExampleMod"));
        assertTrue(sender.messages.get(1).getRawText().contains("42.50 ms"));
        assertTrue(sender.messages.get(2).getRawText().contains("Hytale"));
        assertTrue(sender.messages.get(2).getRawText().contains("20.00 ms"));
    }

    @Test
    void profilerCommandsMirrorEveryReplyToTheRuntimeLog() throws Exception {
        SparkProfileSnapshot.HotPath hotPath = new SparkProfileSnapshot.HotPath(
                "ExampleMod", "com.example.TickSystem#tick()V", 42.5, 100.0
        );
        SparkProfileSnapshot snapshot = new SparkProfileSnapshot(
                "1.10.172-beta7", 17, 2, 1, 42.5, List.of(hotPath)
        );
        SparkProfilerDiagnostics diagnostics = new SparkProfilerDiagnostics(
                SparkProfilerDiagnostics.State.COMPLETE,
                "1.10.172-beta7",
                "Raw Spark profile data was not retained.",
                40L,
                2048L,
                17,
                2,
                1,
                List.of(hotPath)
        );
        List<String> logLines = new ArrayList<>();
        TelemetryProfilerCommand profiler = new TelemetryProfilerCommand(
                runtime(diagnostics, List.of(snapshot), logLines)
        );
        List<String> replies = new ArrayList<>();

        for (String subcommand : List.of("status", "top", "history")) {
            TestCommandSender sender = execute(
                    profiler,
                    subcommand,
                    "/telemetry profiler " + subcommand
            );
            sender.messages.stream().map(Message::getRawText).forEach(replies::add);
        }

        assertEquals(replies, logLines);
    }

    @Test
    void rootStatusIncludesCompactProfilerStateAndCommandHint() throws Exception {
        SparkProfilerDiagnostics diagnostics = new SparkProfilerDiagnostics(
                SparkProfilerDiagnostics.State.COMPLETE,
                "1.10.172-beta7",
                "complete",
                12L,
                34L,
                17,
                2,
                4,
                List.of()
        );
        TelemetryCommandRuntime runtime = runtime(diagnostics, List.of());
        TestCommandSender sender = new TestCommandSender();
        TelemetryStatusCommand command = new TelemetryStatusCommand(runtime);
        CommandContext context = new CommandContext(command, sender, "/telemetry status");

        executeCommand(command, context);

        assertTrue(sender.text().contains("Telemetry Spark profiler: state=COMPLETE"));
        assertTrue(sender.text().contains("/telemetry profiler status"));
    }

    @Test
    void historyOutputIsBoundedAndShowsNewestRetainedWindows() throws Exception {
        List<SparkProfileSnapshot> history = new ArrayList<>();
        for (int window = 1; window <= 20; window++) {
            history.add(new SparkProfileSnapshot(
                    "1.10.172-beta7",
                    window,
                    2,
                    1,
                    3.0,
                    List.of(new SparkProfileSnapshot.HotPath(
                            "ExampleMod", "com.example.Tick#tick()", 3.0, 100.0
                    ))
            ));
        }

        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime(
                        new SparkProfilerDiagnostics(
                                SparkProfilerDiagnostics.State.COMPLETE,
                                "1.10.172-beta7",
                                "complete",
                                1L,
                                1L,
                                20,
                                2,
                                1,
                                history.getLast().hotPaths()
                        ),
                        history
                )),
                "history",
                "/telemetry profiler history"
        );

        assertEquals(11, sender.messages.size());
        assertTrue(sender.messages.get(0).getRawText().contains("showing 10 of 20"));
        assertFalse(sender.messages.stream().anyMatch(message -> message.getRawText().startsWith("window=1,")));
        assertTrue(sender.text().contains("window=11"));
        assertTrue(sender.text().contains("window=20"));
    }

    @Test
    void projectTopShowsDirectAndDownstreamPathsAndMirrorsEveryLine() throws Exception {
        TelemetryProfilerSnapshot snapshot = snapshot(
                selfPath(40.0),
                downstreamPath(25.0)
        );
        List<String> logLines = new ArrayList<>();
        TelemetryCommandRuntime runtime = runtimeWithProjectSnapshot(
                "mod-a",
                snapshot,
                logLines
        );

        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime),
                "top",
                "/telemetry profiler top mod-a"
        );

        assertTrue(sender.text().contains("project=mod-a"));
        assertTrue(sender.text().contains("SELF"));
        assertTrue(sender.text().contains("DOWNSTREAM"));
        assertTrue(sender.text().contains("sampledMs=40.00"));
        assertTrue(sender.text().contains("worldThreadShare=40.00%"));
        assertTrue(sender.text().contains("ownedMethod=com.example.Mod#tick()V"));
        assertTrue(sender.text().contains("externalMethod=com.example.Other#call()V"));
        assertTrue(sender.text().contains("fingerprint=00112233445566778899aabbccddeeff"));
        assertTrue(sender.messages.stream().allMatch(message -> message.getRawText().length() <= 320));
        assertEquals(sender.rawLines(), logLines);
    }

    @Test
    void longProjectPathKeepsRequiredDownstreamFieldsAndFullFingerprintWithinLineBudget()
            throws Exception {
        String fingerprint = "ffeeddccbbaa99887766554433221100";
        TelemetryProfilerSnapshot snapshot = snapshot(longDownstreamPath(fingerprint));
        List<String> logLines = new ArrayList<>();
        TelemetryCommandRuntime runtime = runtimeWithProjectSnapshot(
                "mod-a",
                snapshot,
                logLines
        );

        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime),
                "top",
                "/telemetry profiler top mod-a"
        );

        String pathLine = sender.messages.stream()
                .map(Message::getRawText)
                .filter(line -> line.contains("DOWNSTREAM"))
                .findFirst()
                .orElseThrow();
        assertTrue(pathLine.contains("attribution=DOWNSTREAM"));
        assertTrue(pathLine.contains("sampledMs=25.00 ms"));
        assertTrue(pathLine.contains("worldThreadShare=25.00%"));
        assertTrue(pathLine.contains("ownedMethod="));
        assertTrue(pathLine.contains("externalMethod="));
        assertTrue(pathLine.contains("qualification=OBSERVED"));
        assertTrue(pathLine.contains("fingerprint=" + fingerprint));
        assertTrue(sender.messages.stream().allMatch(message -> message.getRawText().length() <= 320));
        assertEquals(sender.rawLines(), logLines);
    }

    @Test
    void unavailableProjectDoesNotLeakGlobalPaths() throws Exception {
        SparkProfileSnapshot.HotPath globalPath = new SparkProfileSnapshot.HotPath(
                "OtherMod", "com.example.OtherMod#tick()V", 12.0, 100.0
        );
        SparkProfilerDiagnostics diagnostics = new SparkProfilerDiagnostics(
                SparkProfilerDiagnostics.State.COMPLETE,
                "1.10.172-beta7",
                "complete",
                1L,
                1L,
                1,
                1,
                1,
                List.of(globalPath)
        );
        List<String> logLines = new ArrayList<>();
        TelemetryCommandRuntime runtime = runtimeWithProjectView(
                diagnostics,
                List.of(),
                "missing",
                TelemetryProfilerView.unavailable(),
                logLines
        );

        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime),
                "top",
                "/telemetry profiler top missing"
        );

        assertEquals(1, sender.messages.size());
        assertTrue(sender.text().contains("unavailable"));
        assertFalse(sender.text().contains("com.example.OtherMod"));
        assertEquals(sender.rawLines(), logLines);
    }

    @Test
    void projectHistoryShowsNewestTenRetainedSnapshotsInOldestToNewestOrder() throws Exception {
        List<TelemetryProfilerSnapshot> history = new ArrayList<>();
        for (int window = 1; window <= 20; window++) {
            history.add(snapshot(window));
        }
        List<String> logLines = new ArrayList<>();
        TelemetryCommandRuntime runtime = runtimeWithProjectView(
                SparkProfilerDiagnostics.disabled(),
                List.of(),
                "mod-a",
                projectView(history.getLast(), history),
                logLines
        );

        TestCommandSender sender = execute(
                new TelemetryProfilerCommand(runtime),
                "history",
                "/telemetry profiler history mod-a"
        );

        assertEquals(11, sender.messages.size());
        assertTrue(sender.messages.get(0).getRawText().contains("project=mod-a"));
        assertFalse(sender.text().contains("window=1,"));
        assertTrue(sender.text().contains("window=11,"));
        assertTrue(sender.text().contains("window=20,"));
        assertTrue(sender.messages.stream().allMatch(message -> message.getRawText().length() <= 320));
        assertEquals(sender.rawLines(), logLines);
    }

    @Test
    void projectProfilerRejectsMultipleOrOversizedProjectArgumentsWithOneUsageLine() throws Exception {
        TelemetryCommandRuntime runtime = runtimeWithProjectView(
                SparkProfilerDiagnostics.disabled(),
                List.of(),
                "mod-a",
                projectView(snapshot(1), List.of(snapshot(1))),
                new ArrayList<>()
        );
        TelemetryProfilerCommand profiler = new TelemetryProfilerCommand(runtime);

        TestCommandSender multiple = execute(
                profiler,
                "top",
                "/telemetry profiler top mod-a extra"
        );
        TestCommandSender oversized = execute(
                profiler,
                "history",
                "/telemetry profiler history " + "x".repeat(121)
        );

        assertEquals(1, multiple.messages.size());
        assertTrue(multiple.text().contains("Usage: /telemetry profiler top [project-id]"));
        assertEquals(1, oversized.messages.size());
        assertTrue(oversized.text().contains("Usage: /telemetry profiler history [project-id]"));
    }

    private static TelemetryCommandRuntime runtime(SparkProfilerDiagnostics diagnostics,
                                                     List<SparkProfileSnapshot> history) {
        return runtime(diagnostics, history, new ArrayList<>());
    }

    private static TelemetryCommandRuntime runtime(SparkProfilerDiagnostics diagnostics,
                                                     List<SparkProfileSnapshot> history,
                                                     List<String> logLines) {
        return runtimeWithProjectView(diagnostics, history, null, null, logLines);
    }

    private static TelemetryCommandRuntime runtimeWithProjectSnapshot(String projectId,
                                                                        TelemetryProfilerSnapshot snapshot,
                                                                        List<String> logLines) {
        return runtimeWithProjectView(
                SparkProfilerDiagnostics.disabled(),
                List.of(),
                projectId,
                projectView(snapshot, List.of(snapshot)),
                logLines
        );
    }

    private static TelemetryCommandRuntime runtimeWithProjectView(
            SparkProfilerDiagnostics diagnostics,
            List<SparkProfileSnapshot> history,
            String projectId,
            TelemetryProfilerView projectView,
            List<String> logLines) {
        return (TelemetryCommandRuntime) Proxy.newProxyInstance(
                TelemetryCommandRuntime.class.getClassLoader(),
                new Class<?>[]{TelemetryCommandRuntime.class},
                (proxy, method, args) -> {
                    if ("sparkProfilerDiagnostics".equals(method.getName())) {
                        return diagnostics;
                    }
                    if ("sparkProfilerHistory".equals(method.getName())) {
                        return history;
                    }
                    if ("logProfilerCommandOutput".equals(method.getName())) {
                        logLines.add((String) args[0]);
                        return null;
                    }
                    if ("projectProfiler".equals(method.getName())) {
                        if (projectId != null && projectId.equals(args[0])) {
                            return projectView;
                        }
                        return TelemetryProfilerView.unavailable();
                    }
                    if ("diagnostics".equals(method.getName())) {
                        return new TelemetryRuntimeDiagnostics(
                                false,
                                0,
                                0,
                                0,
                                false,
                                "<none>",
                                null,
                                List.of(),
                                List.of()
                        );
                    }
                    if ("activeCoordinatorProviderId".equals(method.getName())) {
                        return null;
                    }
                    if ("ownsActiveCoordinator".equals(method.getName())) {
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static TelemetryProfilerView projectView(TelemetryProfilerSnapshot latest,
                                                      List<TelemetryProfilerSnapshot> history) {
        return new TelemetryProfilerView() {
            @Override
            public TelemetryProfilerStatus status() {
                return new TelemetryProfilerStatus(
                        TelemetryProfilerStatus.State.COMPLETE,
                        "complete",
                        true,
                        false,
                        latest.sparkVersion(),
                        "runtime",
                        0L,
                        1L,
                        0,
                        0
                );
            }

            @Override
            public Optional<TelemetryProfilerSnapshot> latest() {
                return Optional.ofNullable(latest);
            }

            @Override
            public List<TelemetryProfilerSnapshot> history() {
                return List.copyOf(history);
            }

            @Override
            public TelemetryProfilerSubscription subscribe(
                    java.util.function.Consumer<? super TelemetryProfilerSnapshot> listener) {
                return TelemetryProfilerSubscription.closed();
            }
        };
    }

    private static TelemetryProfilerSnapshot snapshot(TelemetryProfilerPathEvidence... paths) {
        return new TelemetryProfilerSnapshot(
                "mod-a",
                "1.0.0",
                "1.10.172-beta7",
                17,
                1000L,
                100.0,
                "observed-v1",
                List.of(paths),
                TelemetryProfilerContext.unavailable()
        );
    }

    private static TelemetryProfilerSnapshot snapshot(int window) {
        return new TelemetryProfilerSnapshot(
                "mod-a",
                "1.0.0",
                "1.10.172-beta7",
                window,
                window,
                100.0,
                "observed-v1",
                List.of(selfPath(1.0)),
                TelemetryProfilerContext.unavailable()
        );
    }

    private static TelemetryProfilerPathEvidence selfPath(double sampledMilliseconds) {
        return new TelemetryProfilerPathEvidence(
                "00112233445566778899aabbccddeeff",
                TelemetryProfilerAttribution.SELF,
                "com.example.Mod",
                "tick",
                "()V",
                null,
                null,
                null,
                sampledMilliseconds,
                sampledMilliseconds,
                TelemetryProfilerQualification.OBSERVED,
                List.of("com.example.Mod#tick()V")
        );
    }

    private static TelemetryProfilerPathEvidence downstreamPath(double sampledMilliseconds) {
        return new TelemetryProfilerPathEvidence(
                "ffeeddccbbaa99887766554433221100",
                TelemetryProfilerAttribution.DOWNSTREAM,
                "com.example.Mod",
                "tick",
                "()V",
                "com.example.Other",
                "call",
                "()V",
                sampledMilliseconds,
                sampledMilliseconds,
                TelemetryProfilerQualification.OBSERVED,
                List.of("com.example.Mod#tick()V", "com.example.Other#call()V")
        );
    }

    private static TelemetryProfilerPathEvidence longDownstreamPath(String fingerprint) {
        return new TelemetryProfilerPathEvidence(
                fingerprint,
                TelemetryProfilerAttribution.DOWNSTREAM,
                "owned-class-".repeat(12),
                "owned-method-".repeat(12),
                "(owned-descriptor-".repeat(8) + ")V",
                "external-class-".repeat(12),
                "external-method-".repeat(12),
                "(external-descriptor-".repeat(8) + ")V",
                25.0,
                25.0,
                TelemetryProfilerQualification.OBSERVED,
                List.of()
        );
    }

    private static TestCommandSender execute(TelemetryProfilerCommand profiler,
                                              String subcommand,
                                              String input) throws Exception {
        AbstractCommand command = profiler.getSubCommands().get(subcommand);
        TestCommandSender sender = new TestCommandSender();
        CommandContext context = new CommandContext(profiler, sender, input);
        executeCommand(command, context);
        return sender;
    }

    private static void executeCommand(AbstractCommand command, CommandContext context) throws Exception {
        Method execute = executeMethod(command.getClass());
        try {
            CompletableFuture<?> future = (CompletableFuture<?>) execute.invoke(command, context);
            if (future != null) {
                future.join();
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static Method executeMethod(Class<?> commandClass) throws NoSuchMethodException {
        Class<?> current = commandClass;
        while (current != null) {
            try {
                Method execute = current.getDeclaredMethod("execute", CommandContext.class);
                execute.setAccessible(true);
                return execute;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException("execute");
    }

    private static final class TestCommandSender implements CommandSender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessage(Message message) {
            messages.add(message);
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public boolean hasPermission(String permission, boolean defaultValue) {
            return true;
        }

        @Override
        public String getUsername() {
            return "Console";
        }

        @Override
        public UUID getUuid() {
            return new UUID(0L, 0L);
        }

        private String text() {
            return messages.stream().map(Message::getRawText).reduce("", (a, b) -> a + "\n" + b);
        }

        private List<String> rawLines() {
            return messages.stream().map(Message::getRawText).toList();
        }
    }
}
