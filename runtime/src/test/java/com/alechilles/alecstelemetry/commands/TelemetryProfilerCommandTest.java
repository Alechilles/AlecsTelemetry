package com.alechilles.alecstelemetry.commands;

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

    private static TelemetryCommandRuntime runtime(SparkProfilerDiagnostics diagnostics,
                                                     List<SparkProfileSnapshot> history) {
        return runtime(diagnostics, history, new ArrayList<>());
    }

    private static TelemetryCommandRuntime runtime(SparkProfilerDiagnostics diagnostics,
                                                     List<SparkProfileSnapshot> history,
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
    }
}
