package com.alechilles.beacon.runtime.host;

import com.alechilles.beacon.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

public record TelemetryRuntimeBootstrapRequest(
        @Nonnull JavaPlugin plugin,
        @Nonnull TelemetryRuntimeOrigin origin,
        @Nonnull String providerPluginIdentifier,
        @Nonnull String providerPluginVersion,
        @Nonnull String runtimeVersion,
        @Nonnull Path sourcePath,
        @Nullable TelemetryProjectDescriptor embeddedDescriptor) {

    public boolean embedded() {
        return origin == TelemetryRuntimeOrigin.EMBEDDED;
    }

    public boolean standalone() {
        return origin == TelemetryRuntimeOrigin.STANDALONE;
    }
}
