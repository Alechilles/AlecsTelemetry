package com.alechilles.beacon.embedded;

import com.alechilles.beacon.commands.TelemetryCommandRoot;
import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectDiscovery;
import com.alechilles.beacon.project.TelemetryProjectOverride;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.alechilles.beacon.runtime.TelemetryProjectOverrideStore;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;
import com.alechilles.beacon.runtime.host.TelemetryRuntimeHost;
import com.alechilles.beacon.runtime.host.TelemetryRuntimeHostHandle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bootstraps embedded telemetry for one owning mod.
 */
public final class EmbeddedTelemetryBootstrap {

    private EmbeddedTelemetryBootstrap() {
    }

    @Nonnull
    public static EmbeddedTelemetryService bootstrap(@Nonnull JavaPlugin plugin) {
        HytaleLogger logger = plugin.getLogger();
        DescriptorBytes descriptorBytes = loadConventionalDescriptor(plugin, logger);
        String pluginIdentifier = resolvePluginIdentifier(plugin);
        if (descriptorBytes == null) {
            String displayName = fallbackDisplayName(plugin, pluginIdentifier);
            return EmbeddedTelemetryService.disabled(
                    slugify(displayName),
                    displayName,
                    logger,
                    "No Server/Beacon/project.json descriptor was found in the owning mod."
            );
        }
        return bootstrapFromDescriptorBytes(
                plugin,
                descriptorBytes.content(),
                descriptorBytes.resource(),
                pluginIdentifier,
                resolvePluginVersion(plugin),
                buildFallbacks(plugin),
                false,
                logger,
                "No Server/Beacon/project.json descriptor was found in the owning mod."
        );
    }

    /**
     * Loads and prepares one logical project descriptor from the contributor's classloader.
     */
    @Nonnull
    public static EmbeddedTelemetryService contribute(@Nonnull JavaPlugin plugin,
                                                      @Nonnull TelemetryProjectContribution contribution) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(contribution, "contribution");
        HytaleLogger logger = plugin.getLogger();
        String logicalIdentifier = contribution.logicalPluginIdentifier();
        String fallbackDisplayName = fallbackDisplayName(logicalIdentifier);
        String fallbackProjectId = slugify(fallbackDisplayName);
        try {
            DescriptorBytes descriptorBytes = loadAnchoredDescriptor(contribution);
            if (descriptorBytes == null) {
                return disabledContribution(
                        fallbackProjectId,
                        fallbackDisplayName,
                        logger,
                        "No embedded telemetry descriptor was found at " + contribution.descriptorResource() + "."
                );
            }
            return bootstrapFromDescriptorBytes(
                    plugin,
                    descriptorBytes.content(),
                    descriptorBytes.resource(),
                    logicalIdentifier,
                    contribution.logicalPluginVersion(),
                    null,
                    true,
                    logger,
                    "Failed to load embedded telemetry descriptor from " + descriptorBytes.resource() + "."
            );
        } catch (RuntimeException ex) {
            return disabledContribution(
                    fallbackProjectId,
                    fallbackDisplayName,
                    logger,
                    "Failed to initialize embedded telemetry contribution '"
                            + logicalIdentifier + "': " + safeMessage(ex)
            );
        } catch (LinkageError error) {
            return disabledContribution(
                    fallbackProjectId,
                    fallbackDisplayName,
                    logger,
                    "Failed to initialize embedded telemetry contribution '"
                            + logicalIdentifier + "': " + safeMessage(error)
            );
        }
    }

    @Nullable
    static TelemetryProjectOverride loadProjectOverride(@Nonnull TelemetryDataPaths sharedDataPaths,
                                                        @Nonnull TelemetryDataPaths ownerDataPaths,
                                                        @Nullable HytaleLogger logger,
                                                        @Nonnull String projectId) {
        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(logger);
        TelemetryProjectOverride sharedOverride = store.load(sharedDataPaths.projectOverrideFile(projectId));
        if (sharedOverride != null) {
            return sharedOverride;
        }
        return store.load(ownerDataPaths.projectOverrideFile(projectId));
    }



    @Nonnull
    static TelemetryCommandRoot sharedCommandRoot(@Nonnull EmbeddedTelemetryService service) {
        return new TelemetryCommandRoot(service.commandRuntime());
    }



    @Nullable
    private static DescriptorBytes loadConventionalDescriptor(@Nonnull JavaPlugin plugin,
                                                              @Nullable HytaleLogger logger) {
        Path sourcePath = resolvePluginSourcePath(plugin);
        if (sourcePath != null && Files.exists(sourcePath)) {
            return loadConventionalDescriptorFromSource(sourcePath, logger);
        }
        return loadConventionalDescriptorFromClassLoader(plugin, logger);
    }

    @Nullable
    private static DescriptorBytes loadConventionalDescriptorFromSource(@Nonnull Path sourcePath,
                                                                        @Nullable HytaleLogger logger) {
        try {
            if (Files.isDirectory(sourcePath)) {
                for (String descriptorResource : conventionalDescriptorResources()) {
                    Path descriptorPath = sourcePath.resolve(descriptorResource);
                    if (Files.isRegularFile(descriptorPath)) {
                        return new DescriptorBytes(descriptorResource, Files.readAllBytes(descriptorPath));
                    }
                }
                return null;
            }
            try (ZipFile archive = new ZipFile(sourcePath.toFile())) {
                for (String descriptorResource : conventionalDescriptorResources()) {
                    ZipEntry descriptorEntry = archive.getEntry(descriptorResource);
                    if (descriptorEntry != null) {
                        try (InputStream stream = archive.getInputStream(descriptorEntry)) {
                            return new DescriptorBytes(descriptorResource, stream.readAllBytes());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Failed to load embedded telemetry descriptor from owning plugin " + sourcePath + "."
                );
            }
        }
        return null;
    }

    @Nullable
    private static DescriptorBytes loadConventionalDescriptorFromClassLoader(@Nonnull JavaPlugin plugin,
                                                                              @Nullable HytaleLogger logger) {
        for (String descriptorResource : conventionalDescriptorResources()) {
            InputStream rawStream = plugin.getClass().getClassLoader().getResourceAsStream(descriptorResource);
            try (InputStream stream = rawStream) {
                if (stream != null) {
                    return new DescriptorBytes(descriptorResource, stream.readAllBytes());
                }
            } catch (Exception ex) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(ex).log(
                            "Failed to load embedded telemetry descriptor from " + descriptorResource + "."
                    );
                }
                return null;
            }
        }
        return null;
    }

    @Nonnull
    private static List<String> conventionalDescriptorResources() {
        return List.of(
                TelemetryProjectDiscovery.DESCRIPTOR_PATH,
                TelemetryProjectDiscovery.LEGACY_DESCRIPTOR_PATH,
                TelemetryProjectDiscovery.OLDER_LEGACY_DESCRIPTOR_PATH
        );
    }

    @Nullable
    private static DescriptorBytes loadAnchoredDescriptor(@Nonnull TelemetryProjectContribution contribution) {
        Class<?> anchor = contribution.resourceAnchor();
        ClassLoader loader = anchor.getClassLoader();
        InputStream stream = loader == null
                ? anchor.getResourceAsStream("/" + contribution.descriptorResource())
                : loader.getResourceAsStream(contribution.descriptorResource());
        if (stream == null) {
            return null;
        }
        try (InputStream closeable = stream) {
            return new DescriptorBytes(contribution.descriptorResource(), closeable.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to read embedded telemetry descriptor " + contribution.descriptorResource(),
                    ex
            );
        }
    }

    @Nonnull
    private static EmbeddedTelemetryService bootstrapFromDescriptorBytes(@Nonnull JavaPlugin plugin,
                                                                          @Nonnull byte[] descriptorBytes,
                                                                          @Nonnull String descriptorResource,
                                                                          @Nonnull String logicalIdentifier,
                                                                          @Nonnull String logicalVersion,
                                                                          @Nullable TelemetryProjectDescriptor.Fallbacks fallbacks,
                                                                          boolean validateContribution,
                                                                          @Nullable HytaleLogger logger,
                                                                          @Nonnull String parseFailureReason) {
        TelemetryProjectDescriptor descriptor;
        try {
            descriptor = TelemetryProjectDescriptor.fromJson(
                    new String(descriptorBytes, StandardCharsets.UTF_8),
                    fallbacks
            );
        } catch (RuntimeException ex) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(ex).log(
                        "Failed to load embedded telemetry descriptor from " + descriptorResource + "."
                );
            }
            String fallbackDisplay = fallbacks == null
                    ? fallbackDisplayName(logicalIdentifier)
                    : fallbacks.displayName();
            return EmbeddedTelemetryService.disabled(
                    slugify(fallbackDisplay),
                    fallbackDisplay,
                    logger,
                    parseFailureReason
            );
        }

        if (validateContribution) {
            if (!hasExplicitDescriptorIdentity(descriptorBytes)) {
                return disabledContribution(
                        descriptor.projectId(),
                        descriptor.displayName(),
                        logger,
                        "Embedded telemetry descriptor " + descriptorResource
                                + " must declare nonblank projectId and displayName."
                );
            }
            if (descriptor.ownerPluginIdentifiers().stream()
                    .noneMatch(owner -> owner.equalsIgnoreCase(logicalIdentifier.trim()))) {
                return disabledContribution(
                        descriptor.projectId(),
                        descriptor.displayName(),
                        logger,
                        "Embedded telemetry descriptor " + descriptorResource
                                + " does not declare logical owner " + logicalIdentifier + "."
                );
            }
            if (descriptor.projectVersion() != null
                    && !descriptor.projectVersion().isBlank()
                    && !descriptor.projectVersion().equals(logicalVersion)) {
                return disabledContribution(
                        descriptor.projectId(),
                        descriptor.displayName(),
                        logger,
                        "descriptor_version_mismatch"
                );
            }
            try {
                Semver.fromString(logicalVersion);
            } catch (RuntimeException ex) {
                return disabledContribution(
                        descriptor.projectId(),
                        descriptor.displayName(),
                        logger,
                        "Embedded telemetry contribution " + logicalIdentifier
                                + " has invalid logical version '" + logicalVersion + "'."
                );
            }
        }

        TelemetryDataPaths ownerDataPaths = TelemetryDataPaths.forEmbeddedOwner(plugin);
        TelemetryDataPaths sharedDataPaths = TelemetryDataPaths.forSharedCoordinator(plugin);
        TelemetryRuntimeSettings sharedSettings = TelemetryRuntimeSettings.load(sharedDataPaths.settingsFile(), logger);
        TelemetryProjectOverride override = loadProjectOverride(sharedDataPaths, ownerDataPaths, logger, descriptor.projectId());
        Path sourcePath = resolvePluginSourcePath(plugin);
        String hostPluginIdentifier = resolvePluginIdentifier(plugin);
        String hostPluginVersion = resolvePluginVersion(plugin);
        String descriptorHash = descriptor.canonicalHash();
        TelemetryProjectRegistration registration = validateContribution
                ? TelemetryProjectRegistration.contribution(
                        descriptor,
                        logicalIdentifier,
                        logicalVersion,
                        sourcePath,
                        hostPluginIdentifier,
                        hostPluginVersion,
                        descriptorHash
                )
                : new TelemetryProjectRegistration(
                        descriptor,
                        logicalIdentifier,
                        logicalVersion,
                        sourcePath
                );
        registration = registration.withOverride(override);
        if (validateContribution && "custom".equalsIgnoreCase(registration.destinationMode())) {
            return disabledContribution(
                    descriptor.projectId(),
                    descriptor.displayName(),
                    logger,
                    "Contributed telemetry projects must use hosted destination mode in the 1.1.0 MVP; custom destination mode is disabled."
            );
        }
        TelemetryRuntimeHostHandle host = TelemetryRuntimeHost.acquireEmbedded(
                plugin,
                validateContribution ? null : descriptor
        );
        if (validateContribution) {
            return EmbeddedTelemetryService.fromContribution(
                    sharedSettings,
                    sharedDataPaths,
                    registration,
                    host,
                    hostPluginIdentifier,
                    hostPluginVersion,
                    descriptorHash,
                    logger
            );
        }
        return EmbeddedTelemetryService.fromHost(sharedSettings, sharedDataPaths, registration, host, logger);
    }

    @Nonnull
    private static EmbeddedTelemetryService disabledContribution(@Nonnull String projectId,
                                                                 @Nonnull String displayName,
                                                                 @Nullable HytaleLogger logger,
                                                                 @Nonnull String reason) {
        if (logger != null) {
            logger.at(Level.WARNING).log(reason);
        }
        return EmbeddedTelemetryService.disabled(projectId, displayName, logger, reason);
    }

    @Nonnull
    private static String fallbackDisplayName(@Nonnull String logicalIdentifier) {
        int separatorIndex = logicalIdentifier.indexOf(':');
        if (separatorIndex >= 0 && separatorIndex < logicalIdentifier.length() - 1) {
            return logicalIdentifier.substring(separatorIndex + 1).trim();
        }
        return logicalIdentifier;
    }

    @Nonnull
    private static String safeMessage(@Nonnull RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @Nonnull
    private static String safeMessage(@Nonnull LinkageError error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static boolean hasExplicitDescriptorIdentity(@Nonnull byte[] descriptorBytes) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(descriptorBytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject document = parsed.getAsJsonObject();
            return hasNonBlankString(document, "projectId")
                    && hasNonBlankString(document, "displayName");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasNonBlankString(@Nonnull JsonObject document, @Nonnull String key) {
        JsonElement value = document.get(key);
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && !value.getAsString().isBlank();
    }

    private record DescriptorBytes(@Nonnull String resource, @Nonnull byte[] content) {
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
    private static String sha256(@Nonnull String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(value & 0x0f, 16));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return Integer.toHexString(content.hashCode());
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
