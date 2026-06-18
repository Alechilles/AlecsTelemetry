package com.alechilles.alecstelemetry.embedded;

import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorRegistry;
import com.alechilles.alecstelemetry.coordinator.TelemetryCoordinatorService;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeCandidate;
import com.alechilles.alecstelemetry.coordinator.TelemetryRuntimeOrigin;
import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.crash.HttpCrashReportClient;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

/**
 * Bootstraps embedded telemetry for one owning mod.
 */
public final class EmbeddedTelemetryBootstrap {

    private static final String DESCRIPTOR_RESOURCE = "telemetry/project.json";
    private static final String FALLBACK_RUNTIME_VERSION = "0.1.3";

    private EmbeddedTelemetryBootstrap() {
    }

    @Nonnull
    public static EmbeddedTelemetryService bootstrap(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        TelemetryProjectDescriptor descriptor = loadDescriptor(plugin, logger);
        String pluginIdentifier = resolvePluginIdentifier(plugin);
        String displayName = descriptor == null
                ? fallbackDisplayName(plugin, pluginIdentifier)
                : descriptor.displayName();
        String projectId = descriptor == null
                ? slugify(displayName)
                : descriptor.projectId();

        if (descriptor == null) {
            return EmbeddedTelemetryService.disabled(
                    projectId,
                    displayName,
                    logger,
                    "No telemetry/project.json descriptor was found in the owning mod."
            );
        }
        if (!descriptor.isEmbeddedMode()) {
            return EmbeddedTelemetryService.disabled(
                    descriptor.projectId(),
                    descriptor.displayName(),
                    logger,
                    "Descriptor runtimeMode is '" + descriptor.runtimeMode() + "'; embedded bootstrap requires runtimeMode=embedded."
            );
        }

        TelemetryDataPaths dataPaths = TelemetryDataPaths.forEmbeddedOwner(plugin);
        TelemetryDataPaths sharedDataPaths = TelemetryDataPaths.forSharedCoordinator(plugin);
        TelemetryRuntimeSettings settings = TelemetryRuntimeSettings.load(dataPaths.settingsFile(), logger);
        TelemetryRuntimeSettings sharedSettings = TelemetryRuntimeSettings.load(sharedDataPaths.settingsFile(), logger);
        TelemetryProjectOverride override = new TelemetryProjectOverrideStore(logger)
                .load(dataPaths.projectOverrideFile(descriptor.projectId()));
        String pluginVersion = resolvePluginVersion(plugin);
        Path sourcePath = resolvePluginSourcePath(plugin);
        TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
                descriptor,
                pluginIdentifier,
                pluginVersion,
                sourcePath
        ).withOverride(override);
        EmbeddedTelemetryPlayerCounter playerCounter = new EmbeddedTelemetryPlayerCounter();
        registerPlayerCounter(plugin, playerCounter, logger);
        CrashReportClient client = new HttpCrashReportClient(sharedSettings.connectTimeoutMs(), sharedSettings.readTimeoutMs(), logger);
        TelemetryCoordinatorService coordinatorService = TelemetryCoordinatorService.discover(
                sharedDataPaths,
                client,
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                playerCounter::onlinePlayers
        );
        TelemetryRuntimeCandidate candidate = new TelemetryRuntimeCandidate(
                "embedded:" + pluginIdentifier,
                TelemetryRuntimeOrigin.EMBEDDED,
                resolveRuntimeVersion(),
                TelemetryCoordinatorRegistry.COORDINATOR_PROTOCOL_VERSION,
                pluginIdentifier,
                pluginVersion,
                sourcePath == null ? plugin.getDataDirectory().toAbsolutePath().normalize() : sourcePath,
                sharedDataPaths.runtimeRoot()
        );
        return new EmbeddedTelemetryService(
                settings,
                dataPaths,
                registration,
                List.of(new CrashReportEnvelope.LoadedModMetadata(pluginIdentifier, pluginVersion)),
                new HttpCrashReportClient(settings.connectTimeoutMs(), settings.readTimeoutMs(), logger),
                logger,
                HytaleServer.SCHEDULED_EXECUTOR,
                playerCounter,
                candidate,
                coordinatorService
        );
    }

    @Nonnull
    private static String resolveRuntimeVersion() {
        Package runtimePackage = EmbeddedTelemetryBootstrap.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_RUNTIME_VERSION
                : implementationVersion.trim();
    }

    private static void registerPlayerCounter(@Nonnull JavaPlugin plugin,
                                              @Nonnull EmbeddedTelemetryPlayerCounter playerCounter,
                                              @Nullable HytaleLogger logger) {
        if (plugin.getEventRegistry() == null) {
            return;
        }
        try {
            plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerCounter::onPlayerReady);
            plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, playerCounter::onPlayerDisconnect);
        } catch (RuntimeException ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Embedded telemetry stats player counter could not register player events."
                );
            }
        }
    }

    @Nullable
    private static TelemetryProjectDescriptor loadDescriptor(@Nonnull JavaPlugin plugin, @Nullable HytaleLogger logger) {
        try (InputStream stream = plugin.getClass().getClassLoader().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (stream == null) {
                return null;
            }
            String rawJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return TelemetryProjectDescriptor.fromJson(rawJson, buildFallbacks(plugin));
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log("Failed to load embedded telemetry descriptor from " + DESCRIPTOR_RESOURCE + ".");
            }
            return null;
        }
    }

    @Nonnull
    private static TelemetryProjectDescriptor.Fallbacks buildFallbacks(@Nonnull JavaPlugin plugin) {
        String pluginIdentifier = resolvePluginIdentifier(plugin);
        String displayName = fallbackDisplayName(plugin, pluginIdentifier);
        String packagePrefix = plugin.getClass().getPackageName();
        return new TelemetryProjectDescriptor.Fallbacks(
                slugify(displayName),
                displayName,
                pluginIdentifier,
                packagePrefix == null || packagePrefix.isBlank() ? List.of() : List.of(packagePrefix)
        );
    }

    @Nonnull
    private static String fallbackDisplayName(@Nonnull JavaPlugin plugin, @Nonnull String pluginIdentifier) {
        int separatorIndex = pluginIdentifier.indexOf(':');
        if (separatorIndex >= 0 && separatorIndex < pluginIdentifier.length() - 1) {
            return pluginIdentifier.substring(separatorIndex + 1).trim();
        }
        String packageName = plugin.getClass().getSimpleName();
        return packageName == null || packageName.isBlank() ? "Unknown Project" : packageName;
    }

    @Nonnull
    private static String resolvePluginIdentifier(@Nonnull JavaPlugin plugin) {
        PluginIdentifier identifier = plugin.getIdentifier();
        if (identifier != null) {
            return identifier.toString();
        }
        PluginManifest manifest = plugin.getManifest();
        if (manifest != null) {
            return new PluginIdentifier(manifest).toString();
        }
        return "unknown:unknown";
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

    @Nullable
    private static Path resolvePluginSourcePath(@Nonnull JavaPlugin plugin) {
        try {
            if (plugin.getFile() == null) {
                return null;
            }
            return plugin.getFile().toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private static String slugify(@Nonnull String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean previousDash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                previousDash = false;
            } else if (c >= 'A' && c <= 'Z') {
                out.append(Character.toLowerCase(c));
                previousDash = false;
            } else if (!previousDash) {
                out.append('-');
                previousDash = true;
            }
        }
        String slug = out.toString();
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isBlank() ? "unknown-project" : slug;
    }
}
