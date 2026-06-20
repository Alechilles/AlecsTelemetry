package com.alechilles.alecstelemetry.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServerIdentityTest {

    @TempDir
    Path tempDir;

    @Test
    void createsJsonIdentityFile() throws Exception {
        Path identityFile = tempDir.resolve("Settings").resolve("server-identity.json");

        TelemetryServerIdentity.Identity identity = TelemetryServerIdentity.loadOrCreate(identityFile, null, null);

        assertNotNull(UUID.fromString(identity.serverId()));
        assertTrue(Files.isRegularFile(identityFile));
        JsonObject document = JsonParser.parseString(Files.readString(identityFile)).getAsJsonObject();
        assertEquals(identity.serverId(), document.get("serverId").getAsString());
    }

    @Test
    void migratesLegacyServerIdIntoJsonIdentity() throws Exception {
        Path settingsDirectory = tempDir.resolve("Settings");
        Path legacyFile = settingsDirectory.resolve("server-id.txt");
        Path identityFile = settingsDirectory.resolve("server-identity.json");
        String legacyServerId = "550e8400-e29b-41d4-a716-446655440000";
        Files.createDirectories(settingsDirectory);
        Files.writeString(legacyFile, legacyServerId);

        TelemetryServerIdentity.Identity identity = TelemetryServerIdentity.loadOrCreate(identityFile, legacyFile, null);

        assertEquals(legacyServerId, identity.serverId());
        JsonObject document = JsonParser.parseString(Files.readString(identityFile)).getAsJsonObject();
        assertEquals(legacyServerId, document.get("serverId").getAsString());
    }

    @Test
    void loadsClaimTokenFromJsonIdentity() throws Exception {
        Path settingsDirectory = tempDir.resolve("Settings");
        Path identityFile = settingsDirectory.resolve("server-identity.json");
        Files.createDirectories(settingsDirectory);
        Files.writeString(identityFile, """
                {
                  "serverId": "550e8400-e29b-41d4-a716-446655440000",
                  "serverClaimToken": "ms_claim_test"
                }
                """);

        TelemetryServerIdentity.Identity identity = TelemetryServerIdentity.loadOrCreate(identityFile, settingsDirectory.resolve("server-id.txt"), null);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", identity.serverId());
        assertEquals("ms_claim_test", identity.serverClaimToken());
    }
}
