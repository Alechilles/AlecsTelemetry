package com.alechilles.beacon.project;

import com.alechilles.beacon.crash.CrashReportEnvelope;
import com.hypixel.hytale.common.semver.Semver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers telemetry project descriptors from installed mod folders and archives.
 */
public final class TelemetryProjectDiscovery {

    public static final String DESCRIPTOR_PATH = "Server/Beacon/project.json";
    public static final String NAMESPACED_DESCRIPTOR_DIRECTORY = "META-INF/beacon/projects/";
    public static final String LEGACY_DESCRIPTOR_PATH = "Server/Telemetry/project.json";
    public static final String OLDER_LEGACY_DESCRIPTOR_PATH = "telemetry/project.json";
    private static final String LEGACY_NAMESPACED_DESCRIPTOR_DIRECTORY = "META-INF/alecs-telemetry/projects/";
    private static final String MANIFEST_PATH = "manifest.json";
    private static final int MAX_SKIPPED_REGISTRATION_WARNINGS = 64;

    private final HytaleLogger logger;

    public TelemetryProjectDiscovery(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public DiscoveryResult discover(@Nullable Path modsDirectory) {
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return new DiscoveryResult(List.of(), List.of(), List.of(), List.of());
        }
        return discover(List.of(modsDirectory));
    }

    @Nonnull
    public DiscoveryResult discover(@Nonnull Collection<Path> modsDirectories) {
        ArrayList<TelemetryProjectCandidate> candidates = new ArrayList<>();
        LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> loadedMods = new LinkedHashMap<>();
        ArrayList<String> skippedRegistrationWarnings = new ArrayList<>();
        for (Path modsDirectory : modsDirectories) {
            if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
                continue;
            }
            for (Path entry : sortedEntries(modsDirectory)) {
                try {
                    EntryData data = Files.isDirectory(entry)
                            ? readFolderEntry(entry, skippedRegistrationWarnings)
                            : readArchiveEntry(entry, skippedRegistrationWarnings);
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
                    for (DiscoveredRegistration registration : data.registrations()) {
                        candidates.add(new TelemetryProjectCandidate(registration));
                    }
                } catch (Exception ex) {
                    warn("Failed to inspect telemetry project entry " + entry, ex);
                }
            }
        }

        LinkedHashMap<String, List<TelemetryProjectCandidate>> candidatesByProject = new LinkedHashMap<>();
        for (TelemetryProjectCandidate candidate : candidates) {
            candidatesByProject.computeIfAbsent(
                    candidate.registration().projectId().toLowerCase(Locale.ROOT),
                    ignored -> new ArrayList<>())
                    .add(candidate);
        }

        LinkedHashMap<String, TelemetryProjectRegistration> registrations = new LinkedHashMap<>();
        ArrayList<TelemetryProjectRegistration> electedCandidates = new ArrayList<>();
        for (Map.Entry<String, List<TelemetryProjectCandidate>> item : candidatesByProject.entrySet()) {
            List<TelemetryProjectCandidate> projectCandidates = deduplicateCandidates(item.getValue());
            if (hasOwnerLineageConflict(projectCandidates)) {
                addSkippedWarning(
                        skippedRegistrationWarnings,
                        "Rejected telemetry project "
                                + projectCandidates.getFirst().registration().projectId()
                                + " because descriptors declare conflicting owner lineages."
                );
                continue;
            }
            projectCandidates = discardSameHostLegacyDuplicates(projectCandidates);
            for (TelemetryProjectCandidate candidate : projectCandidates) {
                electedCandidates.add(candidate.registration());
            }
            if (hasDescriptorDrift(projectCandidates)) {
                addSkippedWarning(
                        skippedRegistrationWarnings,
                        "Telemetry descriptor drift for project "
                                + projectCandidates.getFirst().registration().projectId()
                                + " at the same logical version."
                );
            }
            TelemetryProjectCandidate elected = elect(projectCandidates);
            registrations.put(item.getKey(), elected.registration());
        }

        return new DiscoveryResult(
                List.copyOf(registrations.values()),
                List.copyOf(registrations.values()),
                List.copyOf(loadedMods.values()),
                List.copyOf(skippedRegistrationWarnings),
                List.copyOf(electedCandidates)
        );
    }

    /** Elects one registration per project from a candidate set after host filtering. */
    @Nonnull
    public static List<TelemetryProjectRegistration> electCandidates(
            @Nonnull Collection<TelemetryProjectRegistration> registrations) {
        LinkedHashMap<String, List<TelemetryProjectCandidate>> candidatesByProject = new LinkedHashMap<>();
        for (TelemetryProjectRegistration registration : registrations) {
            if (registration == null) {
                continue;
            }
            candidatesByProject.computeIfAbsent(
                    registration.projectId().toLowerCase(Locale.ROOT),
                    ignored -> new ArrayList<>())
                    .add(new TelemetryProjectCandidate(registration));
        }

        ArrayList<TelemetryProjectRegistration> elected = new ArrayList<>();
        for (List<TelemetryProjectCandidate> projectCandidates : candidatesByProject.values()) {
            List<TelemetryProjectCandidate> deduplicated = deduplicateCandidates(projectCandidates);
            if (deduplicated.isEmpty() || hasOwnerLineageConflict(deduplicated)) {
                continue;
            }
            elected.add(elect(deduplicated).registration());
        }
        return List.copyOf(elected);
    }

    @Nonnull
    private List<Path> sortedEntries(@Nonnull Path modsDirectory) {
        ArrayList<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDirectory)) {
            for (Path entry : stream) {
                entries.add(entry);
            }
        } catch (Exception ex) {
            warn("Failed to scan mods directory for telemetry descriptors: " + modsDirectory, ex);
            return List.of();
        }

        entries.sort(Comparator.comparing(path -> {
            Path fileName = path.getFileName();
            return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
        }));
        return List.copyOf(entries);
    }

    @Nullable
    private EntryData readFolderEntry(@Nonnull Path folder,
                                      @Nonnull List<String> skippedRegistrationWarnings) {
        ModManifest manifest = readManifest(folder.resolve(MANIFEST_PATH));
        ArrayList<DiscoveredRegistration> registrations = new ArrayList<>();
        Path descriptorPath = descriptorPath(folder);
        if (Files.isRegularFile(descriptorPath)) {
            try {
                String rawDescriptor = Files.readString(descriptorPath, StandardCharsets.UTF_8);
                registrations.add(new DiscoveredRegistration(
                        toRegistration(rawDescriptor, manifest, folder),
                        isCanonicalDescriptorPath(folder, descriptorPath)
                ));
            } catch (Exception ex) {
                warn("Failed to read telemetry descriptor " + descriptorPath, ex);
            }
        }
        addPassiveFolderDescriptors(
                folder,
                manifest,
                NAMESPACED_DESCRIPTOR_DIRECTORY,
                true,
                registrations,
                skippedRegistrationWarnings
        );
        addPassiveFolderDescriptors(
                folder,
                manifest,
                LEGACY_NAMESPACED_DESCRIPTOR_DIRECTORY,
                false,
                registrations,
                skippedRegistrationWarnings
        );
        if (manifest == null && registrations.isEmpty()) {
            return null;
        }
        return new EntryData(List.copyOf(registrations), manifest);
    }

    @Nullable
    private EntryData readArchiveEntry(@Nonnull Path archive,
                                       @Nonnull List<String> skippedRegistrationWarnings) {
        String fileName = archive.getFileName() == null ? "" : archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ModManifest manifest = readManifest(zipFile, MANIFEST_PATH);
            ArrayList<DiscoveredRegistration> registrations = new ArrayList<>();
            ZipEntry descriptorEntry = descriptorEntry(zipFile);
            if (descriptorEntry != null) {
                try (InputStream stream = zipFile.getInputStream(descriptorEntry)) {
                    String rawDescriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    registrations.add(new DiscoveredRegistration(
                            toRegistration(rawDescriptor, manifest, archive),
                            isCanonicalDescriptorName(descriptorEntry.getName())
                    ));
                } catch (Exception ex) {
                    warn("Failed to read telemetry descriptor " + archive, ex);
                }
            }

            addPassiveArchiveDescriptors(
                    zipFile,
                    archive,
                    manifest,
                    NAMESPACED_DESCRIPTOR_DIRECTORY,
                    true,
                    registrations,
                    skippedRegistrationWarnings
            );
            addPassiveArchiveDescriptors(
                    zipFile,
                    archive,
                    manifest,
                    LEGACY_NAMESPACED_DESCRIPTOR_DIRECTORY,
                    false,
                    registrations,
                    skippedRegistrationWarnings
            );
            if (manifest == null && registrations.isEmpty()) {
                return null;
            }
            return new EntryData(List.copyOf(registrations), manifest);
        } catch (Exception ex) {
            warn("Failed to inspect telemetry archive " + archive, ex);
            return null;
        }
    }

    @Nonnull
    private static Path descriptorPath(@Nonnull Path folder) {
        for (String resourcePath : List.of(
                DESCRIPTOR_PATH,
                LEGACY_DESCRIPTOR_PATH,
                OLDER_LEGACY_DESCRIPTOR_PATH
        )) {
            Path candidate = folder.resolve(resourcePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return folder.resolve(DESCRIPTOR_PATH);
    }

    @Nullable
    private static ZipEntry descriptorEntry(@Nonnull ZipFile zipFile) {
        for (String resourcePath : List.of(
                DESCRIPTOR_PATH,
                LEGACY_DESCRIPTOR_PATH,
                OLDER_LEGACY_DESCRIPTOR_PATH
        )) {
            ZipEntry candidate = zipFile.getEntry(resourcePath);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private void addPassiveFolderDescriptors(@Nonnull Path folder,
                                             @Nullable ModManifest manifest,
                                             @Nonnull String resourceDirectory,
                                             boolean canonicalDescriptorPath,
                                             @Nonnull ArrayList<DiscoveredRegistration> registrations,
                                             @Nonnull List<String> skippedRegistrationWarnings) {
        ArrayList<Path> descriptors = namespacedDescriptorPaths(folder, resourceDirectory);
        descriptors.sort(Comparator.comparing(path -> path.getFileName() == null
                ? ""
                : path.getFileName().toString()));
        for (Path descriptorPath : descriptors) {
            try {
                String rawDescriptor = Files.readString(descriptorPath, StandardCharsets.UTF_8);
                TelemetryProjectRegistration registration = toPassiveRegistration(
                        rawDescriptor,
                        manifest,
                        folder,
                        descriptorPath.toString()
                );
                if (registration != null) {
                    registrations.add(new DiscoveredRegistration(registration, canonicalDescriptorPath));
                }
            } catch (Exception ex) {
                addSkippedWarning(
                        skippedRegistrationWarnings,
                        "Skipped passive telemetry descriptor " + descriptorPath + "."
                );
                warn("Failed to read passive telemetry descriptor " + descriptorPath, ex);
            }
        }
    }

    private void addPassiveArchiveDescriptors(@Nonnull ZipFile zipFile,
                                              @Nonnull Path archive,
                                              @Nullable ModManifest manifest,
                                              @Nonnull String resourceDirectory,
                                              boolean canonicalDescriptorPath,
                                              @Nonnull ArrayList<DiscoveredRegistration> registrations,
                                              @Nonnull List<String> skippedRegistrationWarnings) {
        ArrayList<ZipEntry> descriptors = namespacedDescriptorEntries(zipFile, resourceDirectory);
        descriptors.sort(Comparator.comparing(ZipEntry::getName));
        for (ZipEntry descriptorEntry : descriptors) {
            try (InputStream stream = zipFile.getInputStream(descriptorEntry)) {
                String rawDescriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                TelemetryProjectRegistration registration = toPassiveRegistration(
                        rawDescriptor,
                        manifest,
                        archive,
                        descriptorEntry.getName()
                );
                if (registration != null) {
                    registrations.add(new DiscoveredRegistration(registration, canonicalDescriptorPath));
                }
            } catch (Exception ex) {
                addSkippedWarning(
                        skippedRegistrationWarnings,
                        "Skipped passive telemetry descriptor " + archive + "!" + descriptorEntry.getName() + "."
                );
                warn("Failed to read passive telemetry descriptor " + archive + "!" + descriptorEntry.getName(), ex);
            }
        }
    }

    private static boolean isCanonicalDescriptorPath(@Nonnull Path folder, @Nonnull Path descriptorPath) {
        return folder.resolve(DESCRIPTOR_PATH).toAbsolutePath().normalize()
                .equals(descriptorPath.toAbsolutePath().normalize());
    }

    private static boolean isCanonicalDescriptorName(@Nonnull String descriptorName) {
        return DESCRIPTOR_PATH.equals(descriptorName);
    }

    @Nonnull
    private ArrayList<Path> namespacedDescriptorPaths(@Nonnull Path folder,
                                                      @Nonnull String resourceDirectory) {
        Path namespacedDirectory = folder.resolve(resourceDirectory);
        ArrayList<Path> descriptors = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(namespacedDirectory)) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate) && hasJsonSuffix(candidate.getFileName() == null
                        ? ""
                        : candidate.getFileName().toString())) {
                    descriptors.add(candidate);
                }
            }
        } catch (java.nio.file.NoSuchFileException ignored) {
            // A mod without the optional namespaced directory is expected.
        } catch (Exception ex) {
            warn("Failed to scan namespaced telemetry descriptors " + namespacedDirectory, ex);
        }
        return descriptors;
    }

    @Nonnull
    private static ArrayList<ZipEntry> namespacedDescriptorEntries(@Nonnull ZipFile zipFile,
                                                                    @Nonnull String resourceDirectory) {
        ArrayList<ZipEntry> descriptors = new ArrayList<>();
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry candidate = entries.nextElement();
            if (candidate.isDirectory()) {
                continue;
            }
            String name = candidate.getName();
            if (!name.startsWith(resourceDirectory)) {
                continue;
            }
            String remainder = name.substring(resourceDirectory.length());
            if (remainder.isBlank() || remainder.indexOf('/') >= 0 || !hasJsonSuffix(remainder)) {
                continue;
            }
            descriptors.add(candidate);
        }
        return descriptors;
    }

    private static boolean hasJsonSuffix(@Nonnull String resourceName) {
        return resourceName.toLowerCase(Locale.ROOT).endsWith(".json");
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

    @Nonnull
    private TelemetryProjectRegistration toPassiveRegistration(@Nonnull String rawDescriptor,
                                                                @Nullable ModManifest manifest,
                                                                @Nonnull Path sourcePath,
                                                                @Nonnull String resourceName) {
        if (manifest == null) {
            throw new IllegalArgumentException(
                    "Passive telemetry descriptor " + resourceName + " has no valid host manifest"
            );
        }
        JsonElement parsed = JsonParser.parseReader(new StringReader(rawDescriptor));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IllegalArgumentException("descriptor root must be an object");
        }
        JsonObject object = parsed.getAsJsonObject();
        String projectId = getString(object, "projectId");
        String projectVersion = getString(object, "projectVersion");
        String displayName = getString(object, "displayName");
        if (isBlank(projectId) || isBlank(projectVersion) || isBlank(displayName)) {
            throw new IllegalArgumentException(
                    "projectId, projectVersion, and displayName must be explicitly declared"
            );
        }
        if (!hasNonBlankOwner(object)) {
            throw new IllegalArgumentException("ownerPluginIdentifiers must contain a nonblank entry");
        }
        try {
            Semver.fromString(projectVersion.trim());
        } catch (RuntimeException | LinkageError ex) {
            throw new IllegalArgumentException("projectVersion must be a valid semantic version", ex);
        }

        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(rawDescriptor, null);
        if (!descriptor.stats().supported()
                || descriptor.stats().allowedEvents().stream().noneMatch("heartbeat"::equalsIgnoreCase)) {
            throw new IllegalArgumentException("stats.supported and stats.allowedEvents[heartbeat] are required");
        }
        return TelemetryProjectRegistration.passiveDescriptor(
                descriptor,
                sourcePath.toAbsolutePath().normalize(),
                manifest.identifier(),
                manifest.version()
        );
    }

    private static boolean hasNonBlankOwner(@Nonnull JsonObject object) {
        JsonElement ownersElement = object.get("ownerPluginIdentifiers");
        if (ownersElement == null || !ownersElement.isJsonArray()) {
            return false;
        }
        JsonArray owners = ownersElement.getAsJsonArray();
        for (JsonElement owner : owners) {
            if (owner != null && !owner.isJsonNull() && owner.isJsonPrimitive()) {
                try {
                    if (!isBlank(owner.getAsString())) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // Ignore malformed list elements and continue checking valid entries.
                }
            }
        }
        return false;
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

    @Nonnull
    private static List<TelemetryProjectCandidate> deduplicateCandidates(
            @Nonnull List<TelemetryProjectCandidate> candidates) {
        LinkedHashMap<String, TelemetryProjectCandidate> deduplicated = new LinkedHashMap<>();
        for (TelemetryProjectCandidate candidate : candidates) {
            TelemetryProjectRegistration registration = candidate.registration();
            String owner = registration.ownerPluginIdentifiers().isEmpty()
                    ? ""
                    : registration.ownerPluginIdentifiers().getFirst().toLowerCase(Locale.ROOT);
            String key = registration.projectId().toLowerCase(Locale.ROOT)
                    + '\u0000'
                    + candidate.logicalVersion().toLowerCase(Locale.ROOT)
                    + '\u0000'
                    + owner
                    + '\u0000'
                    + candidate.descriptorHash();
            TelemetryProjectCandidate existing = deduplicated.get(key);
            if (existing == null || comparePriority(candidate, existing) > 0) {
                deduplicated.put(key, candidate);
            }
        }
        return List.copyOf(deduplicated.values());
    }

    @Nonnull
    private static List<TelemetryProjectCandidate> discardSameHostLegacyDuplicates(
            @Nonnull List<TelemetryProjectCandidate> candidates) {
        if (candidates.size() < 2) {
            return candidates;
        }
        Set<String> canonicalHosts = new HashSet<>();
        for (TelemetryProjectCandidate candidate : candidates) {
            if (candidate.registration().isPassiveDescriptor() && candidate.canonicalDescriptorPath()) {
                canonicalHosts.add(candidate.sourcePath());
            }
        }
        if (canonicalHosts.isEmpty()) {
            return candidates;
        }

        ArrayList<TelemetryProjectCandidate> retained = new ArrayList<>(candidates.size());
        for (TelemetryProjectCandidate candidate : candidates) {
            if (!candidate.registration().isPassiveDescriptor()
                    || candidate.canonicalDescriptorPath()
                    || !canonicalHosts.contains(candidate.sourcePath())) {
                retained.add(candidate);
            }
        }
        return List.copyOf(retained);
    }

    private static boolean hasDescriptorDrift(@Nonnull List<TelemetryProjectCandidate> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            TelemetryProjectCandidate left = candidates.get(i);
            if (left.descriptorHash().isBlank()) {
                continue;
            }
            for (int j = i + 1; j < candidates.size(); j++) {
                TelemetryProjectCandidate right = candidates.get(j);
                if (right.descriptorHash().isBlank()
                        || left.descriptorHash().equals(right.descriptorHash())
                        || !sameLogicalVersion(left, right)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean hasOwnerLineageConflict(@Nonnull List<TelemetryProjectCandidate> candidates) {
        if (candidates.size() < 2) {
            return false;
        }
        String lineage = ownerLineage(candidates.getFirst().registration());
        for (int i = 1; i < candidates.size(); i++) {
            if (!lineage.equals(ownerLineage(candidates.get(i).registration()))) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String ownerLineage(@Nonnull TelemetryProjectRegistration registration) {
        if (!registration.ownerPluginIdentifiers().isEmpty()) {
            String owner = registration.ownerPluginIdentifiers().getFirst();
            if (owner != null && !owner.isBlank()) {
                return owner.trim().toLowerCase(Locale.ROOT);
            }
        }
        return registration.pluginIdentifier().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameLogicalVersion(@Nonnull TelemetryProjectCandidate left,
                                              @Nonnull TelemetryProjectCandidate right) {
        if (left.semanticVersion() != null && right.semanticVersion() != null) {
            return compareSemanticVersions(left.semanticVersion(), right.semanticVersion()) == 0;
        }
        return left.semanticVersion() == null
                && right.semanticVersion() == null
                && left.logicalVersion().equalsIgnoreCase(right.logicalVersion());
    }

    @Nonnull
    private static TelemetryProjectCandidate elect(@Nonnull List<TelemetryProjectCandidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Cannot elect from an empty candidate list");
        }
        TelemetryProjectCandidate elected = candidates.getFirst();
        for (int i = 1; i < candidates.size(); i++) {
            TelemetryProjectCandidate candidate = candidates.get(i);
            if (comparePriority(candidate, elected) > 0) {
                elected = candidate;
            }
        }
        return elected;
    }

    /** A positive result means the left candidate wins the deterministic election. */
    private static int comparePriority(@Nonnull TelemetryProjectCandidate left,
                                       @Nonnull TelemetryProjectCandidate right) {
        if (left.registration().isPassiveDescriptor() && right.registration().isPassiveDescriptor()) {
            int comparison = Boolean.compare(left.canonicalDescriptorPath(), right.canonicalDescriptorPath());
            if (comparison != 0) {
                return comparison;
            }
        }
        int comparison = compareSemanticVersions(left.semanticVersion(), right.semanticVersion());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(sourceRank(right.registration()), sourceRank(left.registration()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = lexicalAscendingPriority(left.hostIdentifier(), right.hostIdentifier());
        if (comparison != 0) {
            return comparison;
        }
        comparison = lexicalAscendingPriority(left.sourcePath(), right.sourcePath());
        if (comparison != 0) {
            return comparison;
        }
        return lexicalAscendingPriority(left.descriptorHash(), right.descriptorHash());
    }

    private static int compareSemanticVersions(@Nullable Semver left, @Nullable Semver right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return left.compareTo(right);
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    private static int sourceRank(@Nonnull TelemetryProjectRegistration registration) {
        return registration.source() == TelemetryProjectRegistrationSource.CONVENTIONAL ? 0 : 1;
    }

    /** A positive result means the lexically smaller value wins. */
    private static int lexicalAscendingPriority(@Nonnull String left, @Nonnull String right) {
        int comparison = right.compareToIgnoreCase(left);
        return comparison == 0 ? right.compareTo(left) : comparison;
    }

    private static void addSkippedWarning(@Nonnull List<String> warnings,
                                           @Nonnull String warning) {
        if (warnings.size() < MAX_SKIPPED_REGISTRATION_WARNINGS) {
            warnings.add(warning);
        }
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
                                  @Nonnull List<TelemetryProjectRegistration> consentProjects,
                                  @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                                  @Nonnull List<String> skippedRegistrationWarnings,
                                  @Nonnull List<TelemetryProjectRegistration> candidates) {

        public DiscoveryResult(@Nonnull List<TelemetryProjectRegistration> projects,
                               @Nonnull List<TelemetryProjectRegistration> consentProjects,
                               @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
                               @Nonnull List<String> skippedRegistrationWarnings) {
            this(projects, consentProjects, loadedMods, skippedRegistrationWarnings, projects);
        }
    }

    private record DiscoveredRegistration(@Nonnull TelemetryProjectRegistration registration,
                                          boolean canonicalDescriptorPath) {
    }

    private record EntryData(@Nonnull List<DiscoveredRegistration> registrations,
                             @Nullable ModManifest manifest) {
    }

    private record TelemetryProjectCandidate(@Nonnull TelemetryProjectRegistration registration,
                                              boolean canonicalDescriptorPath,
                                              @Nonnull String logicalVersion,
                                             @Nullable Semver semanticVersion,
                                             @Nonnull String hostIdentifier,
                                             @Nonnull String sourcePath,
                                             @Nonnull String descriptorHash) {

        private TelemetryProjectCandidate(@Nonnull DiscoveredRegistration discovered) {
            this(
                    discovered.registration(),
                    discovered.canonicalDescriptorPath()
            );
        }

        private TelemetryProjectCandidate(@Nonnull TelemetryProjectRegistration registration) {
            this(registration, false);
        }

        private TelemetryProjectCandidate(@Nonnull TelemetryProjectRegistration registration,
                                          boolean canonicalDescriptorPath) {
            this(
                    registration,
                    canonicalDescriptorPath,
                    logicalVersion(registration),
                    parseSemanticVersion(logicalVersion(registration)),
                    hostIdentifier(registration),
                    sourcePath(registration),
                    descriptorHash(registration)
            );
        }

        @Nonnull
        private static String logicalVersion(@Nonnull TelemetryProjectRegistration registration) {
            String declared = registration.descriptor().projectVersion();
            return isBlank(declared) ? registration.pluginVersion().trim() : declared.trim();
        }

        @Nullable
        private static Semver parseSemanticVersion(@Nonnull String version) {
            if (version.isBlank()) {
                return null;
            }
            try {
                return Semver.fromString(version);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nonnull
        private static String hostIdentifier(@Nonnull TelemetryProjectRegistration registration) {
            if (registration.isPassiveDescriptor()
                    && registration.hostPluginIdentifier() != null
                    && !registration.hostPluginIdentifier().isBlank()) {
                return registration.hostPluginIdentifier().trim();
            }
            return registration.pluginIdentifier().trim();
        }

        @Nonnull
        private static String sourcePath(@Nonnull TelemetryProjectRegistration registration) {
            return registration.sourcePath() == null
                    ? ""
                    : registration.sourcePath().toAbsolutePath().normalize().toString();
        }

        @Nonnull
        private static String descriptorHash(@Nonnull TelemetryProjectRegistration registration) {
            String hash = registration.declaredDescriptorHash();
            return hash == null ? registration.descriptor().canonicalHash() : hash.trim();
        }
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
