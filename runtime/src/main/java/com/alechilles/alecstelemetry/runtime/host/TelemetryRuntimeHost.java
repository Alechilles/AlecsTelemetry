package com.alechilles.alecstelemetry.runtime.host;

import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public final class TelemetryRuntimeHost {
    private static final String FALLBACK_RUNTIME_VERSION = "0.1.3";

    private TelemetryRuntimeHost() {
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapStandalone(@Nonnull JavaPlugin plugin) {
        return bootstrap(new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.STANDALONE,
                resolvePluginIdentifier(plugin, "Alechilles:Alec's Telemetry!"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                null
        ));
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapEmbedded(@Nonnull JavaPlugin plugin,
                                                              @Nonnull TelemetryProjectDescriptor descriptor) {
        return bootstrap(new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.EMBEDDED,
                resolvePluginIdentifier(plugin, "unknown:unknown"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                descriptor
        ));
    }

    @Nonnull
    static TelemetryRuntimeHostHandle bootstrap(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        return TelemetryRuntimeProviderHandle.create(request);
    }

    @Nonnull
    private static String resolveRuntimeVersion() {
        Package runtimePackage = TelemetryRuntimeHost.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_RUNTIME_VERSION
                : implementationVersion.trim();
    }

    @Nonnull
    private static String resolvePluginIdentifier(@Nonnull JavaPlugin plugin, @Nonnull String fallback) {
        PluginIdentifier identifier = plugin.getIdentifier();
        if (identifier != null) {
            return identifier.toString();
        }
        PluginManifest manifest = plugin.getManifest();
        if (manifest != null) {
            return new PluginIdentifier(manifest).toString();
        }
        return fallback;
    }

    @Nonnull
    private static String resolvePluginVersion(@Nonnull JavaPlugin plugin) {
        PluginManifest manifest = plugin.getManifest();
        if (manifest == null) {
            return "unknown";
        }
        Semver version = manifest.getVersion();
        return version == null ? "unknown" : version.toString();
    }

    @Nonnull
    private static Path resolvePluginSourcePath(@Nonnull JavaPlugin plugin) {
        try {
            if (plugin.getFile() != null) {
                return plugin.getFile().toAbsolutePath().normalize();
            }
        } catch (RuntimeException ignored) {
        }
        return plugin.getDataDirectory().toAbsolutePath().normalize();
    }
}
