package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers telemetry project descriptors from installed mod folders and archives.
 */
public final class TelemetryProjectDiscovery {

    private static final String DESCRIPTOR_PATH = "telemetry/project.json";
    private static final String MANIFEST_PATH = "manifest.json";

    private final HytaleLogger logger;

    public TelemetryProjectDiscovery(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public DiscoveryResult discover(@Nullable Path modsDirectory) {
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return new DiscoveryResult(List.of(), List.of());
        }

        ArrayList<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
            for (Path entry : stream) {
                entries.add(entry);
            }
        } catch (Exception ex) {
            warn("Failed to scan mods directory for telemetry descriptors: " + modsDirectory, ex);
            return new DiscoveryResult(List.of(), List.of());
        }

        entries.sort(Comparator.comparing(path -> {
            Path fileName = path.getFileName();
            return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
        }));

        LinkedHashMap<String, TelemetryProjectRegistration> registrations = new LinkedHashMap<>();
        LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> loadedMods = new LinkedHashMap<>();
        for (Path entry : entries) {
            try {
                EntryData data = Files.isDirectory(entry) ? readFolderEntry(entry) : readArchiveEntry(entry);
                if (data == null) {
                    continue;
                }
                if (data.manifest() != null) {
                    String manifestId = data.manifest().identifier();
                    loadedMods.putIfAbsent(
                            manifestId.toLowerCase(Locale.ROOT),
                            new CrashReportEnvelope.LoadedModMetadata(manifestId, data.manifest().version())
                    );
                }
                if (data.registration() != null) {
                    String projectIdKey = data.registration().projectId().toLowerCase(Locale.ROOT);
                    if (registrations.containsKey(projectIdKey)) {
                        warn(
                                "Duplicate telemetry project id discovered; keeping first registration for "
                                        + data.registration().projectId()
                                        + ".",
                                null
                        );
                        continue;
                    }
                    registrations.put(projectIdKey, data.registration());
                }
            } catch (Exception ex) {
                warn("Failed to inspect telemetry project entry " + entry, ex);
            }
        }

        return new DiscoveryResult(List.copyOf(registrations.values()), List.copyOf(loadedMods.values()));
    }

    @Nullable
    private EntryData readFolderEntry(@Nonnull Path folder) {
        ModManifest manifest = readManifest(folder.resolve(MANIFEST_PATH));
        Path descriptorPath = folder.resolve("telemetry").resolve("project.json");
        if (!Files.isRegularFile(descriptorPath)) {
            return manifest == null ? null : new EntryData(null, manifest);
        }
        try {
            String rawDescriptor = Files.readString(descriptorPath, StandardCharsets.UTF_8);
            TelemetryProjectRegistration registration = toRegistration(rawDescriptor, manifest, folder);
            return new EntryData(registration, manifest);
        } catch (Exception ex) {
            warn("Failed to read telemetry descriptor " + descriptorPath, ex);
            return new EntryData(null, manifest);
        }
    }

    @Nullable
    private EntryData readArchiveEntry(@Nonnull Path archive) {
        String fileName = archive.getFileName() == null ? "" : archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ModManifest manifest = readManifest(zipFile, MANIFEST_PATH);
            ZipEntry descriptorEntry = zipFile.getEntry(DESCRIPTOR_PATH);
            if (descriptorEntry == null) {
                return manifest == null ? null : new EntryData(null, manifest);
            }
            try (InputStream stream = zipFile.getInputStream(descriptorEntry)) {
                String rawDescriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                TelemetryProjectRegistration registration = toRegistration(rawDescriptor, manifest, archive);
                return new EntryData(registration, manifest);
            }
        } catch (Exception ex) {
            warn("Failed to inspect telemetry archive " + archive, ex);
            return null;
        }
    }

    @Nonnull
    private TelemetryProjectRegistration toRegistration(@Nonnull String rawDescriptor,
                                                        @Nullable ModManifest manifest,
                                                        @Nonnull Path sourcePath) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                rawDescriptor,
                manifest == null
                        ? null
                        : new TelemetryProjectDescriptor.Fallbacks(
                        slugify(manifest.name()),
                        manifest.name(),
                        manifest.identifier(),
                        manifest.defaultPackagePrefixes()
                )
        );

        String pluginIdentifier = manifest == null ? firstOrUnknown(descriptor.ownerPluginIdentifiers()) : manifest.identifier();
        String pluginVersion = manifest == null ? "unknown" : manifest.version();
        return new TelemetryProjectRegistration(descriptor, pluginIdentifier, pluginVersion, sourcePath.toAbsolutePath().normalize());
    }

    @Nullable
    private ModManifest readManifest(@Nonnull Path manifestPath) {
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            return parseManifest(reader);
        } catch (Exception ex) {
            warn("Failed to read mod manifest " + manifestPath, ex);
            return null;
        }
    }

    @Nullable
    private ModManifest readManifest(@Nonnull ZipFile zipFile, @Nonnull String entryName) {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        try (InputStream stream = zipFile.getInputStream(entry);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parseManifest(reader);
        } catch (Exception ex) {
            warn("Failed to read archive manifest from " + zipFile.getName(), ex);
            return null;
        }
    }

    @Nullable
    private ModManifest parseManifest(@Nonnull Reader reader) {
        JsonElement parsed = JsonParser.parseReader(reader);
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }
        JsonObject object = parsed.getAsJsonObject();
        String group = getString(object, "Group");
        String name = getString(object, "Name");
        String version = getString(object, "Version");
        String mainClass = getString(object, "Main");
        if (isBlank(group) || isBlank(name) || isBlank(version)) {
            return null;
        }
        return new ModManifest(group.trim(), name.trim(), version.trim(), mainClass == null ? null : mainClass.trim());
    }

    @Nonnull
    private static String firstOrUnknown(@Nonnull List<String> values) {
        return values.isEmpty() ? "unknown" : values.getFirst();
    }

    @Nullable
    private static String getString(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key)) {
            return null;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
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

    private void warn(@Nonnull String message, @Nullable Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.at(Level.WARNING).log(message);
            return;
        }
        logger.at(Level.WARNING).withCause(throwable).log(message);
    }

    /**
     * Descriptor discovery output.
     */
    public record DiscoveryResult(@Nonnull List<TelemetryProjectRegistration> projects,
                                  @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
    }

    private record EntryData(@Nullable TelemetryProjectRegistration registration,
                             @Nullable ModManifest manifest) {
    }

    private record ModManifest(@Nonnull String group,
                               @Nonnull String name,
                               @Nonnull String version,
                               @Nullable String mainClass) {
        @Nonnull
        private String identifier() {
            return group + ":" + name;
        }

        @Nonnull
        private List<String> defaultPackagePrefixes() {
            String prefix = mainPackagePrefix();
            return prefix == null ? List.of() : List.of(prefix);
        }

        @Nullable
        private String mainPackagePrefix() {
            if (mainClass == null || mainClass.isBlank()) {
                return null;
            }
            int dotIndex = mainClass.lastIndexOf('.');
            if (dotIndex <= 0) {
                return null;
            }
            return mainClass.substring(0, dotIndex).trim();
        }
    }
}
