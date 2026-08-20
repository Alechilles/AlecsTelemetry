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
    private final PrefixNode prefixRoot;

    private ProfilerProjectOwnershipIndex(Map<String, List<Owner>> ownersByPluginIdentifier,
                                          PrefixNode prefixRoot) {
        this.ownersByPluginIdentifier = immutableOwnerMap(ownersByPluginIdentifier);
        this.prefixRoot = Objects.requireNonNull(prefixRoot, "prefixRoot");
    }

    static BuildResult build(List<TelemetryProjectRegistration> projects,
                             String physicalRuntimeVersion) {
        Objects.requireNonNull(projects, "projects");
        String runtimeVersion = normalizedValue(physicalRuntimeVersion);
        LinkedHashMap<String, LinkedHashMap<String, Owner>> ownersByIdentifier = new LinkedHashMap<>();
        MutablePrefixNode prefixRoot = new MutablePrefixNode();
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
                addPrefix(prefixRoot, prefix, owner);
            }
        }

        return new BuildResult(
                new ProfilerProjectOwnershipIndex(
                        flattenOwnersByIdentifier(ownersByIdentifier),
                        freezePrefixNode(prefixRoot)
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
        PrefixNode node = prefixRoot;
        for (int index = 0; index < className.length(); index++) {
            node = node.children().get(className.charAt(index));
            if (node == null) {
                break;
            }
            int candidateLength = index + 1;
            if (!node.owners().isEmpty()
                    && (candidateLength == className.length()
                    || className.charAt(candidateLength) == '.')) {
                for (Owner candidate : node.owners()) {
                    if (candidateLength > bestLength) {
                        best = candidate;
                        bestLength = candidateLength;
                        ambiguous = false;
                    } else if (candidateLength == bestLength
                            && best != null
                            && !best.projectId().equals(candidate.projectId())) {
                        ambiguous = true;
                    }
                }
            }
        }
        return best == null || ambiguous ? Optional.empty() : Optional.of(best);
    }

    private static void addPrefix(MutablePrefixNode root,
                                  String prefix,
                                  Owner owner) {
        MutablePrefixNode node = root;
        for (int index = 0; index < prefix.length(); index++) {
            node = node.children.computeIfAbsent(prefix.charAt(index), ignored -> new MutablePrefixNode());
        }
        node.owners.add(owner);
    }

    private static PrefixNode freezePrefixNode(MutablePrefixNode source) {
        LinkedHashMap<Character, PrefixNode> children = new LinkedHashMap<>();
        for (Map.Entry<Character, MutablePrefixNode> entry : source.children.entrySet()) {
            children.put(entry.getKey(), freezePrefixNode(entry.getValue()));
        }
        return new PrefixNode(children, source.owners);
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

    private static final class MutablePrefixNode {
        private final LinkedHashMap<Character, MutablePrefixNode> children = new LinkedHashMap<>();
        private final ArrayList<Owner> owners = new ArrayList<>();
    }

    private record PrefixNode(Map<Character, PrefixNode> children,
                              List<Owner> owners) {
        private PrefixNode {
            children = Collections.unmodifiableMap(new LinkedHashMap<>(children));
            owners = List.copyOf(owners);
        }
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
