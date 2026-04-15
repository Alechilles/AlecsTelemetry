package com.alechilles.alecstelemetry.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared parsing and messaging helpers for `/telemetry` subcommands.
 */
final class TelemetryCommandSupport {

    private TelemetryCommandSupport() {
    }

    static void send(@Nonnull CommandContext commandContext, @Nonnull String message) {
        commandContext.sender().sendMessage(Message.raw(message));
    }

    @Nullable
    static String token(@Nonnull CommandContext commandContext, int tokenIndex) {
        String[] tokens = tokens(commandContext);
        if (tokens.length <= tokenIndex) {
            return null;
        }
        String token = tokens[tokenIndex].trim();
        return token.isBlank() ? null : token;
    }

    @Nullable
    static String remainder(@Nonnull CommandContext commandContext, int tokenIndex) {
        String[] tokens = tokens(commandContext);
        if (tokens.length <= tokenIndex) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = tokenIndex; i < tokens.length; i++) {
            if (tokens[i] == null || tokens[i].isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tokens[i].trim());
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    @Nonnull
    private static String[] tokens(@Nonnull CommandContext commandContext) {
        String input = commandContext.getInputString();
        if (input == null || input.isBlank()) {
            return new String[0];
        }
        return input.trim().split("\\s+");
    }
}
