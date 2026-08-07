package com.alechilles.alecstelemetry.project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transactional, immutable view of the projects currently owned by a coordinator.
 *
 * <p>Reconciliation builds every derived view before publishing one new snapshot. A malformed
 * proposal therefore cannot replace a previously valid project. Retired registrations remain in
 * the snapshot solely for queue replay; callers that resolve projects or consent use the active
 * lists.</p>
 */
public final class TelemetryProjectCatalog {

    private final AtomicReference<Snapshot> current;
    private final Object reconcileLock = new Object();

    public TelemetryProjectCatalog(@Nonnull List<TelemetryProjectRegistration> projects,
                                   @Nonnull List<TelemetryProjectRegistration> manualReportProjects) {
        Snapshot initial = buildSnapshot(
                0L,
                projects,
                manualReportProjects,
                List.of()
        );
        if (initial == null) {
            throw new IllegalArgumentException("Initial telemetry project catalog is invalid");
        }
        this.current = new AtomicReference<>(initial);
    }

    /** Creates an empty catalog useful for a provider that starts before contributions exist. */
    public TelemetryProjectCatalog() {
        this(List.of(), List.of());
    }

    @Nonnull
    public Snapshot snapshot() {
        return current.get();
    }

    @Nullable
    public TelemetryProjectRegistration find(@Nonnull String projectId) {
        String normalized = normalize(projectId);
        if (normalized.isBlank()) {
            return null;
        }
        for (TelemetryProjectRegistration project : snapshot().projects()) {
            if (normalize(project.projectId()).equals(normalized)) {
                return project;
            }
        }
        return null;
    }

    public boolean isActive(@Nonnull String projectId) {
        return find(projectId) != null;
    }

    /** Publishes a complete project proposal, retaining the previous snapshot if it is invalid. */
    public boolean reconcile(@Nonnull List<TelemetryProjectRegistration> registrations) {
        return reconcile(registrations, registrations);
    }

    /** Publishes a runtime proposal plus consent/report-only registrations. */
    public boolean reconcile(@Nonnull List<TelemetryProjectRegistration> registrations,
                             @Nonnull List<TelemetryProjectRegistration> manualCandidates) {
        synchronized (reconcileLock) {
            Snapshot previous = current.get();
            ArrayList<TelemetryProjectRegistration> previousRetained = new ArrayList<>(previous.projects());
            previousRetained.addAll(previous.retiredProjects());
            Snapshot next = buildSnapshot(
                    previous.revision() + 1L,
                    registrations,
                    manualCandidates,
                    previousRetained
            );
            if (next == null) {
                return false;
            }
            current.set(next);
            return true;
        }
    }

    /** Retires one active project while preserving its registration for queued-envelope replay. */
    public boolean retire(@Nonnull String projectId) {
        String normalized = normalize(projectId);
        if (normalized.isBlank()) {
            return false;
        }
        synchronized (reconcileLock) {
            Snapshot previous = current.get();
            TelemetryProjectRegistration retired = null;
            ArrayList<TelemetryProjectRegistration> nextProjects = new ArrayList<>();
            for (TelemetryProjectRegistration project : previous.projects()) {
                if (normalize(project.projectId()).equals(normalized)) {
                    retired = project;
                } else {
                    nextProjects.add(project);
                }
            }
            if (retired == null) {
                return false;
            }
            ArrayList<TelemetryProjectRegistration> nextRetired = new ArrayList<>(previous.retiredProjects());
            nextRetired.removeIf(project -> normalize(project.projectId()).equals(normalized));
            nextRetired.add(retired);
            Snapshot next = buildSnapshot(
                    previous.revision() + 1L,
                    nextProjects,
                    nextProjects,
                    nextRetired
            );
            if (next == null) {
                return false;
            }
            current.set(next);
            return true;
        }
    }

    @Nullable
    private static Snapshot buildSnapshot(long revision,
                                          @Nonnull List<TelemetryProjectRegistration> registrations,
                                          @Nonnull List<TelemetryProjectRegistration> manualCandidates,
                                          @Nonnull List<TelemetryProjectRegistration> previousRetired) {
        LinkedHashMap<String, TelemetryProjectRegistration> activeById = new LinkedHashMap<>();
        for (TelemetryProjectRegistration registration : registrations) {
            if (!valid(registration)) {
                return null;
            }
            String normalized = normalize(registration.projectId());
            if (activeById.putIfAbsent(normalized, registration) != null) {
                return null;
            }
        }

        LinkedHashMap<String, TelemetryProjectRegistration> retiredById = new LinkedHashMap<>();
        for (TelemetryProjectRegistration retired : previousRetired) {
            if (valid(retired)) {
                retiredById.putIfAbsent(normalize(retired.projectId()), retired);
            }
        }
        // Rebuild the filtered copy so active replacements disappear from the retired view.
        LinkedHashMap<String, TelemetryProjectRegistration> filteredRetired = new LinkedHashMap<>();
        for (Map.Entry<String, TelemetryProjectRegistration> entry : retiredById.entrySet()) {
            if (!activeById.containsKey(entry.getKey())) {
                filteredRetired.put(entry.getKey(), entry.getValue());
            }
        }
        retiredById = filteredRetired;

        // A replacement of an active project retires the prior registration only when the
        // project disappeared; callers that need queue replay pass that prior registration in
        // previousRetired and the engine retains stores by project ID.
        List<TelemetryProjectRegistration> active = List.copyOf(activeById.values());
        LinkedHashMap<String, TelemetryProjectRegistration> manualById = new LinkedHashMap<>();
        for (TelemetryProjectRegistration registration : manualCandidates) {
            if (!valid(registration)) {
                return null;
            }
            String normalized = normalize(registration.projectId());
            manualById.putIfAbsent(normalized, activeById.getOrDefault(normalized, registration));
        }
        for (TelemetryProjectRegistration registration : active) {
            if (registration.descriptor().reports().enabled()) {
                manualById.putIfAbsent(normalize(registration.projectId()), registration);
            }
        }
        List<TelemetryProjectRegistration> manual = List.copyOf(manualById.values());
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(active);
        Set<String> ambiguousPrefixes = TelemetryProjectCollisionDetector.ambiguousPackagePrefixes(active);
        return new Snapshot(
                revision,
                active,
                manual,
                List.copyOf(retiredById.values()),
                collisions,
                ambiguousPrefixes
        );
    }

    private static boolean valid(@Nullable TelemetryProjectRegistration registration) {
        if (registration == null || registration.descriptor() == null) {
            return false;
        }
        if (normalize(registration.projectId()).isBlank()
                || registration.displayName() == null
                || registration.displayName().trim().isBlank()
                || "unknown-project".equals(normalize(registration.projectId()))
                || "unknown project".equalsIgnoreCase(registration.displayName().trim())
                || registration.pluginIdentifier() == null
                || registration.pluginIdentifier().trim().isBlank()
                || registration.pluginVersion() == null
                || registration.pluginVersion().trim().isBlank()) {
            return false;
        }
        return registration.ownerPluginIdentifiers() != null
                && registration.packagePrefixes() != null;
    }

    @Nonnull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Immutable catalog publication. */
    public record Snapshot(long revision,
                           @Nonnull List<TelemetryProjectRegistration> projects,
                           @Nonnull List<TelemetryProjectRegistration> manualReportProjects,
                           @Nonnull List<TelemetryProjectRegistration> retiredProjects,
                           @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
                           @Nonnull Set<String> ambiguousPackagePrefixes) {

        public Snapshot {
            projects = List.copyOf(projects);
            manualReportProjects = List.copyOf(manualReportProjects);
            retiredProjects = List.copyOf(retiredProjects);
            collisions = List.copyOf(collisions);
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String prefix : ambiguousPackagePrefixes) {
                if (prefix != null && !prefix.isBlank()) {
                    normalized.add(prefix.trim().toLowerCase(Locale.ROOT));
                }
            }
            ambiguousPackagePrefixes = Collections.unmodifiableSet(normalized);
        }
    }
}
