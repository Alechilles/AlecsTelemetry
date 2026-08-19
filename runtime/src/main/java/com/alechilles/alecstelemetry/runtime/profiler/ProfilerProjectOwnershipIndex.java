package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable mapping from profiler labels and classes to registered projects. */
final class ProfilerProjectOwnershipIndex {
    static final int MAX_PREFIXES_PER_PROJECT = 32;

    private final Map<String, List<Owner>> ownersByPluginIdentifier;
    private final List<PrefixOwner> prefixOwners;

    private ProfilerProjectOwnershipIndex(Map<String, List<Owner>> ownersByPluginIdentifier,
                                          List<PrefixOwner> prefixOwners) {
        this.ownersByPluginIdentifier = immutableOwnerMap(ownersByPluginIdentifier);
        this.prefixOwners = List.copyOf(prefixOwners);
    }

    static BuildResult build(List<TelemetryProjectRegistration> projects,
                             String physicalRuntimeVersion) {
        Objects.requireNonNull(projects, "projects");
        String runtimeVersion = normalizedValue(physicalRuntimeVersion);
        LinkedHashMap<String, LinkedHashMap<String, Owner>> ownersByIdentifier = new LinkedHashMap<>();
        ArrayList<PrefixOwner> prefixOwners = new ArrayList<>();
        int truncatedPrefixCount = 0;

        for (TelemetryProjectRegistration registration : projects) {
            if (registration == null) {
                continue;
            }
            Owner owner = ownerOf(registration, runtimeVersion);
            for (String identifier : aliasesOf(registration)) {
                String normalizedIdentifier = normalizeIdentifier(identifier);
                if (normalizedIdentifier == null) {
                    continue;
                }
                ownersByIdentifier
                        .computeIfAbsent(normalizedIdentifier, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(owner.projectId(), owner);
            }

            LinkedHashSet<String> normalizedPrefixes = new LinkedHashSet<>();
            for (String prefix : registration.packagePrefixes()) {
                String normalizedPrefix = normalizePrefix(prefix);
                if (normalizedPrefix != null) {
                    normalizedPrefixes.add(normalizedPrefix);
                }
            }
            int retained = 0;
            for (String prefix : normalizedPrefixes) {
                if (retained++ >= MAX_PREFIXES_PER_PROJECT) {
                    truncatedPrefixCount++;
                    continue;
                }
                prefixOwners.add(new PrefixOwner(prefix, owner));
            }
        }

        return new BuildResult(
                new ProfilerProjectOwnershipIndex(
                        flattenOwnersByIdentifier(ownersByIdentifier),
                        prefixOwners
                ),
                truncatedPrefixCount
        );
    }

    Optional<Owner> resolve(String sourceLabel, String className) {
        String normalizedSource = normalizeIdentifier(sourceLabel);
        if (normalizedSource != null) {
            List<Owner> exactMatches = ownersByPluginIdentifier.get(normalizedSource);
            if (exactMatches != null) {
                return uniqueProject(exactMatches);
            }
        }

        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        Owner best = null;
        int bestLength = -1;
        boolean ambiguous = false;
        for (PrefixOwner candidate : prefixOwners) {
            if (!matches(candidate.prefix(), className)) {
                continue;
            }
            int candidateLength = candidate.prefix().length();
            if (candidateLength > bestLength) {
                best = candidate.owner();
                bestLength = candidateLength;
                ambiguous = false;
            } else if (candidateLength == bestLength
                    && best != null
                    && !best.projectId().equals(candidate.owner().projectId())) {
                ambiguous = true;
            }
        }
        return best == null || ambiguous ? Optional.empty() : Optional.of(best);
    }

    private static Owner ownerOf(TelemetryProjectRegistration registration,
                                 String physicalRuntimeVersion) {
        String logicalVersion = registration.descriptor().projectVersion();
        if (logicalVersion == null || logicalVersion.isBlank()) {
            logicalVersion = registration.pluginVersion();
        }
        return new Owner(
                normalizedValue(registration.projectId()),
                normalizedValue(logicalVersion),
                normalizedValue(registration.pluginIdentifier()),
                physicalRuntimeVersion
        );
    }

    private static List<String> aliasesOf(TelemetryProjectRegistration registration) {
        ArrayList<String> aliases = new ArrayList<>(registration.ownerPluginIdentifiers().size() + 1);
        aliases.add(registration.pluginIdentifier());
        aliases.addAll(registration.ownerPluginIdentifiers());
        return aliases;
    }

    private static Map<String, List<Owner>> flattenOwnersByIdentifier(
            Map<String, LinkedHashMap<String, Owner>> ownersByIdentifier) {
        LinkedHashMap<String, List<Owner>> flattened = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, Owner>> entry : ownersByIdentifier.entrySet()) {
            flattened.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }
        return flattened;
    }

    private static Map<String, List<Owner>> immutableOwnerMap(Map<String, List<Owner>> source) {
        LinkedHashMap<String, List<Owner>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Owner>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Optional<Owner> uniqueProject(List<Owner> matches) {
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        Owner first = matches.getFirst();
        for (int index = 1; index < matches.size(); index++) {
            if (!first.projectId().equals(matches.get(index).projectId())) {
                return Optional.empty();
            }
        }
        return Optional.of(first);
    }

    private static boolean matches(String prefix, String className) {
        return className.equals(prefix) || className.startsWith(prefix + ".");
    }

    private static String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('/', '.');
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizedValue(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value.trim();
    }

    private record PrefixOwner(String prefix, Owner owner) {
    }

    record Owner(String projectId,
                 String logicalProjectVersion,
                 String pluginIdentifier,
                 String physicalRuntimeVersion) {
    }

    record BuildResult(ProfilerProjectOwnershipIndex index,
                       int truncatedPrefixCount) {
        BuildResult {
            Objects.requireNonNull(index, "index");
            if (truncatedPrefixCount < 0) {
                throw new IllegalArgumentException("truncatedPrefixCount must be non-negative");
            }
        }
    }
}
