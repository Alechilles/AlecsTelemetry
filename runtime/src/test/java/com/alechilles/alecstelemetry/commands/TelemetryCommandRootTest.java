package com.alechilles.alecstelemetry.commands;

import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void textAdminCommandsDoNotRequirePlayerSender() {
        TelemetryCommandRoot root = new TelemetryCommandRoot(null);

        assertConsoleSafe(root, "status");
        assertConsoleSafe(root, "projects");
        assertConsoleSafe(root, "project");
        assertConsoleSafe(root, "flush");
        assertConsoleSafe(root, "test");
        assertConsoleSafe(root, "reports");
        assertConsoleSafe(root.getSubCommands().get("server"), "verify");

        assertInstanceOf(AbstractPlayerCommand.class, root.getSubCommands().get("consent"));
        assertInstanceOf(AbstractPlayerCommand.class, root.getSubCommands().get("report"));
    }

    @Test
    void adminCommandIsAvailableToTheBuiltInAdminGroup() {
        TelemetryCommandRoot root = new TelemetryCommandRoot(null);

        assertTrue(root.getPermissionGroups().contains("hytale:Admin"));
    }

    private static void assertConsoleSafe(com.hypixel.hytale.server.core.command.system.AbstractCommand parent,
                                          String name) {
        assertFalse(
                parent.getSubCommands().get(name) instanceof AbstractPlayerCommand,
                name + " should accept console senders"
        );
    }

    private static final class DisplayNameOwner implements CommandOwner {
        @Override
        public String getName() {
            return "Alechilles:Alec's Telemetry!";
        }
    }
}
