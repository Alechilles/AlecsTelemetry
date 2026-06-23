package com.alechilles.alecstelemetry.commands;

import com.hypixel.hytale.server.core.command.system.CommandOwner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryCommandRootTest {

    @Test
    void rootPermissionDoesNotUseManifestDisplayName() {
        TelemetryCommandRoot root = new TelemetryCommandRoot(null);
        root.setOwner(new DisplayNameOwner());

        assertEquals(TelemetryCommandRoot.ROOT_PERMISSION, root.getPermission());
        assertEquals(
                "telemetry.command.telemetry.status",
                root.getSubCommands().get("status").getPermission()
        );
        assertEquals(
                "telemetry.command.telemetry.flush",
                root.getSubCommands().get("flush").getPermission()
        );
        assertEquals(
                "telemetry.command.telemetry.test",
                root.getSubCommands().get("test").getPermission()
        );
        assertEquals(
                "telemetry.command.telemetry.server",
                root.getSubCommands().get("server").getPermission()
        );
        assertEquals(
                "telemetry.command.telemetry.consent",
                root.getSubCommands().get("consent").getPermission()
        );
        assertEquals(
                "telemetry.command.telemetry.report",
                root.getSubCommands().get("report").getPermission()
        );
    }

    private static final class DisplayNameOwner implements CommandOwner {
        @Override
        public String getName() {
            return "Alechilles:Alec's Telemetry!";
        }
    }
}
