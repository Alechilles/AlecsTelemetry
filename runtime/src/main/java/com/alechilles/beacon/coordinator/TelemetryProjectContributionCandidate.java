package com.alechilles.beacon.coordinator;

import com.hypixel.hytale.common.semver.Semver;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable, JDK-only metadata for one contribution candidate.
 *
 * <p>The bridge's candidate map is deliberately represented as strings, numbers, and booleans
 * so it can be shared by isolated plugin classloaders. Validation is kept here rather than in a
 * caller-specific DTO so every registry copy makes the same election decision.</p>
 */
public record TelemetryProjectContributionCandidate(
        int abiVersion,
        String projectId,
        String logicalPluginIdentifier,
        String logicalPluginVersion,
        String origin,
        String hostPluginIdentifier,
        String hostPluginVersion,
        String sourcePath,
        String descriptorJson,
        String descriptorHash
) {

    public static final int CONTRIBUTION_ABI_VERSION = 1;
    public static final String STANDALONE_ORIGIN = "STANDALONE";
    public static final String EMBEDDED_ORIGIN = "EMBEDDED";

    public TelemetryProjectContributionCandidate {
        projectId = text(projectId);
        logicalPluginIdentifier = text(logicalPluginIdentifier);
        logicalPluginVersion = text(logicalPluginVersion);
        origin = text(origin).toUpperCase(Locale.ROOT);
        hostPluginIdentifier = text(hostPluginIdentifier);
        hostPluginVersion = text(hostPluginVersion);
        sourcePath = normalizePath(sourcePath);
        descriptorJson = text(descriptorJson);
        descriptorHash = text(descriptorHash);
    }

    public TelemetryProjectContributionCandidate(int abiVersion,
                                                 String projectId,
                                                 String logicalPluginIdentifier,
                                                 String logicalPluginVersion,
                                                 String origin,
                                                 String hostPluginIdentifier,
                                                 String hostPluginVersion,
                                                 Path sourcePath,
                                                 String descriptorJson,
                                                 String descriptorHash) {
        this(
                abiVersion,
                projectId,
                logicalPluginIdentifier,
                logicalPluginVersion,
                origin,
                hostPluginIdentifier,
                hostPluginVersion,
                sourcePath == null ? null : sourcePath.toString(),
                descriptorJson,
                descriptorHash
        );
    }

    public TelemetryProjectContributionCandidate(int abiVersion,
                                                 String projectId,
                                                 String logicalPluginIdentifier,
                                                 String logicalPluginVersion,
                                                 TelemetryRuntimeOrigin origin,
                                                 String hostPluginIdentifier,
                                                 String hostPluginVersion,
                                                 Path sourcePath,
                                                 String descriptorJson,
                                                 String descriptorHash) {
        this(
                abiVersion,
                projectId,
                logicalPluginIdentifier,
                logicalPluginVersion,
                origin == null ? null : origin.name(),
                hostPluginIdentifier,
                hostPluginVersion,
                sourcePath,
                descriptorJson,
                descriptorHash
        );
    }

    /**
     * Parses a bridge-provided map. Missing or malformed values become invalid metadata instead
     * of escaping into the embedding plugin's lifecycle.
     */
    public static TelemetryProjectContributionCandidate fromMap(Map<?, ?> values) {
        if (values == null) {
            return invalid();
        }
        return new TelemetryProjectContributionCandidate(
                intValue(values.get("abiVersion"), 0),
                stringValue(values.get("projectId")),
                stringValue(values.get("logicalPluginIdentifier")),
                stringValue(values.get("logicalPluginVersion")),
                stringValue(values.get("origin")),
                stringValue(values.get("hostPluginIdentifier")),
                stringValue(values.get("hostPluginVersion")),
                stringValue(values.get("sourcePath")),
                stringValue(values.get("descriptorJson")),
                stringValue(values.get("descriptorHash"))
        );
    }

    /** Alias useful to reflective callers that treat candidate maps as an untyped payload. */
    public static TelemetryProjectContributionCandidate from(Map<?, ?> values) {
        return fromMap(values);
    }

    public static TelemetryProjectContributionCandidate invalid() {
        return new TelemetryProjectContributionCandidate(
                0, "", "", "", "", "", "", "", "", ""
        );
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("abiVersion", abiVersion);
        values.put("projectId", projectId);
        values.put("logicalPluginIdentifier", logicalPluginIdentifier);
        values.put("logicalPluginVersion", logicalPluginVersion);
        values.put("origin", origin);
        values.put("hostPluginIdentifier", hostPluginIdentifier);
        values.put("hostPluginVersion", hostPluginVersion);
        values.put("sourcePath", sourcePath);
        values.put("descriptorJson", descriptorJson);
        values.put("descriptorHash", descriptorHash);
        return Map.copyOf(values);
    }

    public Map<String, Object> candidate() {
        return toMap();
    }

    public String normalizedProjectId() {
        return projectId.toLowerCase(Locale.ROOT);
    }

    public String normalizedOwner() {
        return logicalPluginIdentifier.toLowerCase(Locale.ROOT);
    }

    public String normalizedHostPluginIdentifier() {
        return hostPluginIdentifier.toLowerCase(Locale.ROOT);
    }

    public String normalizedSourcePath() {
        return sourcePath;
    }

    public String normalizedDescriptorHash() {
        return descriptorHash;
    }

    public boolean compatible() {
        return abiVersion == CONTRIBUTION_ABI_VERSION;
    }

    public boolean isCompatible() {
        return compatible();
    }

    public boolean hasValidOrigin() {
        return STANDALONE_ORIGIN.equals(origin) || EMBEDDED_ORIGIN.equals(origin);
    }

    public boolean hasValidSemanticVersion() {
        try {
            Semver.fromString(logicalPluginVersion);
            return !logicalPluginVersion.isBlank();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public Semver semanticVersion() {
        return Semver.fromString(logicalPluginVersion);
    }

    /** Returns a bounded reason suitable for a cross-classloader diagnostic map. */
    public String invalidReason() {
        if (abiVersion == -1) {
            return "abi_mismatch";
        }
        if (!compatible()) {
            return "incompatible_abi";
        }
        if (projectId.isBlank()) {
            return "missing_project_id";
        }
        if (logicalPluginIdentifier.isBlank()) {
            return "missing_logical_owner";
        }
        if (logicalPluginVersion.isBlank() || !hasValidSemanticVersion()) {
            return "invalid_logical_version";
        }
        if (!hasValidOrigin()) {
            return "invalid_origin";
        }
        if (hostPluginIdentifier.isBlank()) {
            return "missing_host_plugin_identifier";
        }
        if (hostPluginVersion.isBlank()) {
            return "missing_host_plugin_version";
        }
        if (sourcePath.isBlank()) {
            return "missing_source_path";
        }
        if (descriptorJson.isBlank()) {
            return "missing_descriptor_json";
        }
        if (descriptorHash.isBlank()) {
            return "missing_descriptor_hash";
        }
        return "";
    }

    public boolean valid() {
        return invalidReason().isBlank();
    }

    /** Selects the highest valid candidate using the registry's deterministic order. */
    public static TelemetryProjectContributionCandidate selectWinner(
            List<TelemetryProjectContributionCandidate> candidates) {
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(TelemetryProjectContributionCandidate::valid)
                .max(Comparator
                        .comparing(TelemetryProjectContributionCandidate::semanticVersion)
                        .thenComparing(candidate -> originRank(candidate.origin()))
                        .thenComparing(
                                TelemetryProjectContributionCandidate::normalizedHostPluginIdentifier,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                TelemetryProjectContributionCandidate::normalizedSourcePath,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                TelemetryProjectContributionCandidate::normalizedDescriptorHash,
                                Comparator.reverseOrder()
                        ))
                .orElse(null);
    }

    private static int originRank(String origin) {
        return STANDALONE_ORIGIN.equals(origin) ? 1 : 0;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            try {
                return number.intValue();
            } catch (RuntimeException | LinkageError ignored) {
                return fallback;
            }
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (RuntimeException | LinkageError ignored) {
            return fallback;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return value.toString().trim();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePath(String value) {
        String trimmed = text(value).replace('\\', '/');
        if (trimmed.isBlank()) {
            return "";
        }
        try {
            return Path.of(trimmed).normalize().toString().replace('\\', '/');
        } catch (RuntimeException ignored) {
            return trimmed;
        }
    }
}
