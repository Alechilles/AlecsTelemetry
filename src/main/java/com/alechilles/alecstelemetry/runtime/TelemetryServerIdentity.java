package com.alechilles.alecstelemetry.runtime;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stable per-install server identity used to correlate telemetry across sessions.
 */
public final class TelemetryServerIdentity {

    private TelemetryServerIdentity() {
    }

    @Nonnull
    public static String loadOrCreate(@Nonnull Path serverIdFile, @Nullable HytaleLogger logger) {
        try {
            String existing = readExisting(serverIdFile);
            if (existing != null) {
                return existing;
            }

            String generated = UUID.randomUUID().toString();
            Files.createDirectories(serverIdFile.getParent());
            Files.writeString(serverIdFile, generated + System.lineSeparator(), StandardCharsets.UTF_8);
            return generated;
        } catch (Exception ex) {
            String generated = UUID.randomUUID().toString();
            logWarning(logger, "Failed to persist telemetry server id; using an in-memory id for this session.", ex);
            return generated;
        }
    }

    @Nullable
    private static String readExisting(@Nonnull Path serverIdFile) throws Exception {
        if (!Files.isRegularFile(serverIdFile)) {
            return null;
        }
        String raw = Files.readString(serverIdFile, StandardCharsets.UTF_8).trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void logWarning(@Nullable HytaleLogger logger,
                                   @Nonnull String message,
                                   @Nonnull Throwable throwable) {
        if (logger != null) {
            logger.at(Level.WARNING).withCause(throwable).log(message);
        }
    }
}
