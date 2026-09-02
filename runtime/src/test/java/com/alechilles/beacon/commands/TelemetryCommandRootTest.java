package com.alechilles.beacon.commands;

import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryCommandRootTest {

    @Test
    void rootPermissionDoesNotUseManifestDisplayName() {
        TelemetryCommandRoot root = new TelemetryCommandRoot(null);
        root.setOwner(new DisplayNameOwner());

        assertEquals("beacon", root.getName());
        assertTrue(root.getAliases().isEmpty(), "The production registrar must not expose /telemetry as an alias");
        assertEquals(TelemetryCommandRoot.ROOT_PERMISSION, root.getPermission());
        assertEquals("beacon.command.beacon", root.getPermission());
        assertEquals(
                "beacon.command.beacon.status",
                root.getSubCommands().get("status").getPermission()
        );
        assertEquals(
                "beacon.command.beacon.flush",
                root.getSubCommands().get("flush").getPermission()
        );
        assertEquals(
                "beacon.command.beacon.test",
                root.getSubCommands().get("test").getPermission()
        );
        assertEquals(
                "beacon.command.beacon.server",
                root.getSubCommands().get("server").getPermission()
        );
        assertEquals(
                "beacon.command.beacon.consent",
                root.getSubCommands().get("consent").getPermission()
        );
        assertEquals(
                "beacon.command.beacon.report",
                root.getSubCommands().get("report").getPermission()
        );
    }

    @Test
    void productionRegistrarRegistersBeaconWithoutTelemetryAlias() throws Exception {
        CapturingCommandRegistry registry = new CapturingCommandRegistry();
        TestPlugin.commandRegistry = registry;
        TestPlugin plugin = (TestPlugin) unsafe().allocateInstance(TestPlugin.class);

        Class<?> registrarType = Class.forName(
                "com.alechilles.beacon.runtime.host.TelemetryRuntimeCommandRegistrar"
        );
        Constructor<?> constructor = registrarType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object registrar = constructor.newInstance();
        Class<?> handleType = Class.forName(
                "com.alechilles.beacon.runtime.host.TelemetryRuntimeProviderHandle"
        );
        Object handle = unsafe().allocateInstance(handleType);

        Method initialize = registrarType.getDeclaredMethod(
                "initialize",
                JavaPlugin.class,
                handleType,
                Class.forName("com.hypixel.hytale.logger.HytaleLogger")
        );
        initialize.setAccessible(true);
        initialize.invoke(registrar, plugin, handle, null);

        Method register = registrarType.getDeclaredMethod("register");
        register.setAccessible(true);
        register.invoke(registrar);

        assertEquals(List.of("beacon"), registry.registeredCommands.stream()
                .map(AbstractCommand::getName)
                .toList());
        assertNotNull(registry.registeredCommands.getFirst());
        assertTrue(registry.registeredCommands.getFirst().getAliases().isEmpty());
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

    private static sun.misc.Unsafe unsafe() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class TestPlugin extends JavaPlugin {
        private static CommandRegistry commandRegistry;

        private TestPlugin() {
            super(null);
        }

        @Override
        public CommandRegistry getCommandRegistry() {
            return commandRegistry;
        }
    }

    private static final class CapturingCommandRegistry extends CommandRegistry {
        private final List<AbstractCommand> registeredCommands = new ArrayList<>();

        private CapturingCommandRegistry() {
            super(List.of(), () -> true, "test", null);
        }

        @Override
        public CommandRegistration registerCommand(AbstractCommand command) {
            registeredCommands.add(command);
            return new CommandRegistration(command, () -> true, () -> { });
        }
    }
}
