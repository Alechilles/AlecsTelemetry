package com.alechilles.beacon.commands;

import com.alechilles.beacon.coordinator.TelemetryServerVerificationResult;
import com.alechilles.beacon.core.TelemetryCoreEngine;
import com.alechilles.beacon.runtime.host.TelemetryCommandRuntime;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServerVerifyCommandTest {

    @Test
    void consoleSenderCanRequestServerVerification() throws Exception {
        AtomicReference<String> requestedToken = new AtomicReference<>();
        TelemetryCommandRuntime runtime = runtimeRequestingVerification(requestedToken);
        TelemetryServerVerifyCommand command = new TelemetryServerVerifyCommand(runtime);
        TestCommandSender sender = new TestCommandSender();
        CommandContext context = new CommandContext(
                command,
                sender,
                "/telemetry server verify ms_claim_from_console"
        );

        executeCommand(command, context);

        assertEquals("ms_claim_from_console", requestedToken.get());
        assertEquals(1, sender.messages.size());
        assertEquals(
                "Server verification heartbeat sent. Check the ModStats portal for verification status.",
                sender.messages.getFirst().getRawText()
        );
    }

    @Test
    void queuedMessageReportsSuccessfulUploadAsSent() {
        String message = TelemetryServerVerifyCommand.verificationQueuedMessage(new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                1,
                new TelemetryCoreEngine.FlushSummary(1, 1, 0, null)
        ));

        assertEquals("Server verification heartbeat sent. Check the ModStats portal for verification status.", message);
    }

    @Test
    void queuedMessageDoesNotClaimSentWhenUploadFails() {
        String message = TelemetryServerVerifyCommand.verificationQueuedMessage(new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                1,
                new TelemetryCoreEngine.FlushSummary(1, 0, 1, "HTTP status 403")
        ));

        assertTrue(message.contains("could not be delivered"));
        assertTrue(message.contains("Attempted 1, sent 0, pending 1"));
        assertTrue(message.contains("HTTP status 403"));
    }

    @Test
    void claimTokenArgumentReadsVerifyKey() {
        assertEquals("ms_claim_from_command", TelemetryServerVerifyCommand.claimTokenArgument(
                "/telemetry server verify ms_claim_from_command"
        ));
    }

    private static TelemetryCommandRuntime runtimeRequestingVerification(AtomicReference<String> requestedToken) {
        return (TelemetryCommandRuntime) Proxy.newProxyInstance(
                TelemetryCommandRuntime.class.getClassLoader(),
                new Class<?>[]{TelemetryCommandRuntime.class},
                (proxy, method, args) -> {
                    if ("requestServerVerification".equals(method.getName())) {
                        requestedToken.set(args == null ? null : (String) args[0]);
                        return new TelemetryServerVerificationResult(
                                TelemetryServerVerificationResult.Status.QUEUED,
                                1,
                                new TelemetryCoreEngine.FlushSummary(1, 1, 0, null)
                        );
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
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
                Method method = current.getDeclaredMethod("execute", CommandContext.class);
                method.setAccessible(true);
                return method;
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
    }
}
