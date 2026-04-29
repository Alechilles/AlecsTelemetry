package com.alechilles.alecstelemetry.project;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Detects registration overlaps that can make crash attribution ambiguous.
 */
public final class TelemetryProjectCollisionDetector {

    private TelemetryProjectCollisionDetector() {
    }

    @Nonnull
    public static List<Collision> detect(@Nonnull List<TelemetryProjectRegistration> projects) {
        LinkedHashMap<String, Collision> collisions = new LinkedHashMap<>();
        for (int i = 0; i < projects.size(); i++) {
            TelemetryProjectRegistration left = projects.get(i);
            for (int j = i + 1; j < projects.size(); j++) {
                TelemetryProjectRegistration right = projects.get(j);
                addPluginIdentifierCollisions(collisions, left, right);
                addPackagePrefixCollisions(collisions, left, right);
            }
        }
        return List.copyOf(new ArrayList<>(collisions.values()));
    }

    private static void addPluginIdentifierCollisions(@Nonnull LinkedHashMap<String, Collision> collisions,
                                                      @Nonnull TelemetryProjectRegistration left,
                                                      @Nonnull TelemetryProjectRegistration right) {
        for (String leftId : left.ownerPluginIdentifiers()) {
            for (String rightId : right.ownerPluginIdentifiers()) {
                if (leftId.equalsIgnoreCase(rightId)) {
                    add(
                            collisions,
                            left.projectId(),
                            right.projectId(),
                            "Shared plugin identifier '" + leftId + "' can make ownership ambiguous."
                    );
                }
            }
        }
    }

    private static void addPackagePrefixCollisions(@Nonnull LinkedHashMap<String, Collision> collisions,
                                                   @Nonnull TelemetryProjectRegistration left,
                                                   @Nonnull TelemetryProjectRegistration right) {
        for (String leftPrefix : left.packagePrefixes()) {
            for (String rightPrefix : right.packagePrefixes()) {
                if (overlaps(leftPrefix, rightPrefix)) {
                    add(
                            collisions,
                            left.projectId(),
                            right.projectId(),
                            "Overlapping package prefixes '" + leftPrefix + "' and '" + rightPrefix + "' may duplicate reports."
                    );
                }
            }
        }
    }

    private static boolean overlaps(@Nonnull String left, @Nonnull String right) {
        String safeLeft = left.trim().toLowerCase(Locale.ROOT);
        String safeRight = right.trim().toLowerCase(Locale.ROOT);
        if (safeLeft.isBlank() || safeRight.isBlank()) {
            return false;
        }
        return safeLeft.equals(safeRight)
                || safeLeft.startsWith(safeRight + ".")
                || safeRight.startsWith(safeLeft + ".");
    }

    private static void add(@Nonnull LinkedHashMap<String, Collision> collisions,
                            @Nonnull String leftProjectId,
                            @Nonnull String rightProjectId,
                            @Nonnull String reason) {
        String key = leftProjectId.toLowerCase(Locale.ROOT) + "|"
                + rightProjectId.toLowerCase(Locale.ROOT) + "|"
                + reason.toLowerCase(Locale.ROOT);
        collisions.putIfAbsent(key, new Collision(leftProjectId, rightProjectId, reason));
    }

    /**
     * One discovered registration collision.
     */
    public record Collision(@Nonnull String leftProjectId,
                            @Nonnull String rightProjectId,
                            @Nonnull String reason) {
        @Nonnull
        public String format() {
            return leftProjectId + " <-> " + rightProjectId + ": " + reason;
        }
    }
}
