package com.alechilles.beacon.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int MAX_CLAIM_TOKEN_LENGTH = 240;

    private TelemetryServerIdentity() {
    }

    @Nonnull
    public static String loadOrCreate(@Nonnull Path serverIdFile, @Nullable HytaleLogger logger) {
        return loadOrCreate(serverIdFile.getParent().resolve("server-identity.json"), serverIdFile, logger).serverId();
    }

    @Nonnull
    public static Identity loadOrCreate(@Nonnull Path serverIdentityFile,
                                        @Nullable Path legacyServerIdFile,
                                        @Nullable HytaleLogger logger) {
        try {
            Identity existing = readExistingIdentity(serverIdentityFile);
            if (existing != null) {
                return existing;
            }

            String claimToken = readExistingClaimToken(serverIdentityFile);
            String serverId = readExistingServerId(legacyServerIdFile);
            if (serverId == null) {
                serverId = UUID.randomUUID().toString();
            }
            Identity identity = new Identity(serverId, claimToken);
            writeIdentity(serverIdentityFile, identity);
            return identity;
        } catch (Exception ex) {
            String generated = UUID.randomUUID().toString();
            logWarning(logger, "Failed to persist telemetry server identity; using an in-memory id for this session.", ex);
            return new Identity(generated, null);
        }
    }

    @Nullable
    public static Identity saveClaimToken(@Nonnull Path serverIdentityFile,
                                          @Nullable Path legacyServerIdFile,
                                          @Nonnull String claimToken,
                                          @Nullable HytaleLogger logger) {
        String normalizedClaimToken = normalizeClaimToken(claimToken);
        if (normalizedClaimToken == null) {
            return null;
        }
        try {
            Identity current = loadOrCreate(serverIdentityFile, legacyServerIdFile, logger);
            Identity updated = new Identity(current.serverId(), normalizedClaimToken);
            writeIdentity(serverIdentityFile, updated);
            return updated;
        } catch (Exception ex) {
            logWarning(logger, "Failed to persist telemetry server claim token.", ex);
            return null;
        }
    }

    @Nullable
    private static Identity readExistingIdentity(@Nonnull Path serverIdentityFile) throws Exception {
        if (!Files.isRegularFile(serverIdentityFile)) {
            return null;
        }
        JsonObject document = readIdentityDocument(serverIdentityFile);
        if (document == null) {
            return null;
        }
        String serverId = normalizeServerId(readString(document, "serverId"));
        if (serverId == null) {
            return null;
        }
        return new Identity(serverId, normalizeClaimToken(readString(document, "serverClaimToken")));
    }

    @Nullable
    private static String readExistingClaimToken(@Nonnull Path serverIdentityFile) {
        try {
            JsonObject document = readIdentityDocument(serverIdentityFile);
            return document == null ? null : normalizeClaimToken(readString(document, "serverClaimToken"));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static JsonObject readIdentityDocument(@Nonnull Path serverIdentityFile) throws Exception {
        String raw = Files.readString(serverIdentityFile, StandardCharsets.UTF_8).trim();
        if (raw.isBlank()) {
            return null;
        }
        JsonElement parsed = JsonParser.parseString(raw);
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    @Nullable
    private static String readExistingServerId(@Nullable Path serverIdFile) throws Exception {
        if (serverIdFile == null || !Files.isRegularFile(serverIdFile)) {
            return null;
        }
        return normalizeServerId(Files.readString(serverIdFile, StandardCharsets.UTF_8));
    }

    private static void writeIdentity(@Nonnull Path serverIdentityFile, @Nonnull Identity identity) throws Exception {
        Files.createDirectories(serverIdentityFile.getParent());
        JsonObject document = new JsonObject();
        document.addProperty("serverId", identity.serverId());
        if (identity.serverClaimToken() != null) {
            document.addProperty("serverClaimToken", identity.serverClaimToken());
        }
        Files.writeString(serverIdentityFile, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    @Nullable
    private static String readString(@Nonnull JsonObject object, @Nonnull String property) {
        JsonElement value = object.get(property);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    @Nullable
    private static String normalizeServerId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static String normalizeClaimToken(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > MAX_CLAIM_TOKEN_LENGTH ? null : normalized;
    }

    private static void logWarning(@Nullable HytaleLogger logger,
                                   @Nonnull String message,
                                   @Nonnull Throwable throwable) {
        if (logger != null) {
            logger.at(Level.WARNING).withCause(throwable).log(message);
        }
    }

    public record Identity(@Nonnull String serverId, @Nullable String serverClaimToken) {
    }
}
