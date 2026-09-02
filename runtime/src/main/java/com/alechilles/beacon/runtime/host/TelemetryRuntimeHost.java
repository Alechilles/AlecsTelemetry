package com.alechilles.beacon.runtime.host;

import com.alechilles.beacon.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class TelemetryRuntimeHost {
    private static final String FALLBACK_RUNTIME_VERSION = "unknown";
    private static final String RUNTIME_MAVEN_PROPERTIES =
            "META-INF/maven/com.alechilles/beacon-runtime/pom.properties";

    private TelemetryRuntimeHost() {
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapStandalone(@Nonnull JavaPlugin plugin) {
        return bootstrap(new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.STANDALONE,
                resolvePluginIdentifier(plugin, "Alechilles:Beacon"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                null
        ));
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle bootstrapEmbedded(@Nonnull JavaPlugin plugin,
                                                              @Nonnull TelemetryProjectDescriptor descriptor) {
        return TelemetryEmbeddedProviderPool.acquire(plugin, descriptor);
    }

    @Nonnull
    public static TelemetryRuntimeHostHandle acquireEmbedded(@Nonnull JavaPlugin plugin,
                                                             @Nullable TelemetryProjectDescriptor descriptor) {
        return TelemetryEmbeddedProviderPool.acquire(plugin, descriptor);
    }

    @Nonnull
    static TelemetryRuntimeHostHandle bootstrapDirectEmbedded(@Nonnull JavaPlugin plugin,
                                                              @Nullable TelemetryProjectDescriptor descriptor) {
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

    @Nullable
    static TelemetryProjectRegistration embeddedRegistration(@Nonnull JavaPlugin plugin,
                                                              @Nonnull TelemetryProjectDescriptor descriptor) {
        TelemetryRuntimeBootstrapRequest request = new TelemetryRuntimeBootstrapRequest(
                plugin,
                TelemetryRuntimeOrigin.EMBEDDED,
                resolvePluginIdentifier(plugin, "unknown:unknown"),
                resolvePluginVersion(plugin),
                resolveRuntimeVersion(),
                resolvePluginSourcePath(plugin),
                descriptor
        );
        return TelemetryRuntimeProviderHandle.providerRegistration(
                request,
                TelemetryDataPaths.forSharedCoordinator(plugin),
                plugin.getLogger()
        );
    }

    @Nonnull
    static String runtimeVersionForPool() {
        return resolveRuntimeVersion();
    }

    @Nonnull
    static TelemetryRuntimeHostHandle bootstrap(@Nonnull TelemetryRuntimeBootstrapRequest request) {
        return TelemetryRuntimeProviderHandle.create(request);
    }

    @Nonnull
    private static String resolveRuntimeVersion() {
        String mavenVersion = resolveRuntimeVersionFromMavenProperties();
        if (mavenVersion != null) {
            return mavenVersion;
        }
        Package runtimePackage = TelemetryRuntimeHost.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_RUNTIME_VERSION
                : implementationVersion.trim();
    }

    @Nullable
    private static String resolveRuntimeVersionFromMavenProperties() {
        ClassLoader loader = TelemetryRuntimeHost.class.getClassLoader();
        InputStream stream = loader == null
                ? ClassLoader.getSystemResourceAsStream(RUNTIME_MAVEN_PROPERTIES)
                : loader.getResourceAsStream(RUNTIME_MAVEN_PROPERTIES);
        if (stream == null) {
            return null;
        }
        try (InputStream closeable = stream) {
            Properties properties = new Properties();
            properties.load(closeable);
            String version = properties.getProperty("version");
            return version == null || version.isBlank() ? null : version.trim();
        } catch (IOException ignored) {
            return null;
        }
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
