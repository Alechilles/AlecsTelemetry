package com.alechilles.beacon.coordinator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Metadata for one standalone or embedded telemetry runtime that can coordinate reporting.
 */
public record TelemetryRuntimeCandidate(@Nonnull String providerId,
                                        @Nonnull TelemetryRuntimeOrigin origin,
                                        @Nonnull String runtimeVersion,
                                        int coordinatorProtocolVersion,
                                        @Nonnull String providerPluginIdentifier,
                                        @Nonnull String providerPluginVersion,
                                        @Nonnull Path sourcePath,
                                        @Nonnull Path sharedDataRoot) {

    private static final Comparator<TelemetryRuntimeCandidate> WINNER_ORDER =
            Comparator.comparing(TelemetryRuntimeCandidate::versionKey)
                    .thenComparing(candidate -> candidate.origin() == TelemetryRuntimeOrigin.STANDALONE ? 1 : 0)
                    .thenComparing(
                            candidate -> normalize(candidate.providerPluginIdentifier()),
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            candidate -> normalize(candidate.sourcePath().toString()),
                            Comparator.reverseOrder()
                    );

    @Nullable
    public static TelemetryRuntimeCandidate selectWinner(@Nonnull List<TelemetryRuntimeCandidate> candidates,
                                                         int requiredProtocolVersion) {
        return candidates.stream()
                .filter(candidate -> candidate.coordinatorProtocolVersion() == requiredProtocolVersion)
                .max(WINNER_ORDER)
                .orElse(null);
    }

    @Nonnull
    private VersionKey versionKey() {
        return VersionKey.parse(runtimeVersion);
    }

    @Nonnull
    private static String normalize(@Nonnull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record VersionKey(int major, int minor, int patch, @Nonnull String suffix)
            implements Comparable<VersionKey> {

        @Nonnull
        static VersionKey parse(@Nonnull String raw) {
            String trimmed = raw.trim();
            String[] versionAndSuffix = trimmed.split("-", 2);
            String[] parts = versionAndSuffix[0].split("\\.");
            return new VersionKey(
                    parsePart(parts, 0),
                    parsePart(parts, 1),
                    parsePart(parts, 2),
                    versionAndSuffix.length > 1 ? versionAndSuffix[1] : ""
            );
        }

        private static int parsePart(@Nonnull String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            String numeric = parts[index].replaceAll("[^0-9]", "");
            if (numeric.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(numeric);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        @Override
        public int compareTo(@Nonnull VersionKey other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            int patchCompare = Integer.compare(patch, other.patch);
            if (patchCompare != 0) {
                return patchCompare;
            }
            if (suffix.isBlank() && !other.suffix.isBlank()) {
                return 1;
            }
            if (!suffix.isBlank() && other.suffix.isBlank()) {
                return -1;
            }
            return other.suffix.compareToIgnoreCase(suffix);
        }
    }
}
