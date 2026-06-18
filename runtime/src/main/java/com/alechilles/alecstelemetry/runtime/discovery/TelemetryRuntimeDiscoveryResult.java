package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record TelemetryRuntimeDiscoveryResult(
        @Nonnull List<TelemetryProjectRegistration> projects,
        @Nonnull List<TelemetryProjectRegistration> consentProjects,
        @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
        @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
        @Nonnull List<String> registrationWarnings) {

    public TelemetryRuntimeDiscoveryResult {
        projects = List.copyOf(projects);
        consentProjects = List.copyOf(consentProjects);
        loadedMods = List.copyOf(loadedMods);
        collisions = List.copyOf(collisions);
        registrationWarnings = List.copyOf(registrationWarnings);
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult withProviderRegistration(@Nonnull TelemetryProjectRegistration registration) {
        List<TelemetryProjectRegistration> resolvedProjects = mergeRegistration(projects, registration);
        List<TelemetryProjectRegistration> resolvedConsentProjects = mergeRegistration(consentProjects, registration);
        List<CrashReportEnvelope.LoadedModMetadata> resolvedLoadedMods = mergeLoadedMod(
                loadedMods,
                new CrashReportEnvelope.LoadedModMetadata(registration.pluginIdentifier(), registration.pluginVersion())
        );
        List<TelemetryProjectCollisionDetector.Collision> resolvedCollisions = TelemetryProjectCollisionDetector.detect(resolvedProjects);
        return new TelemetryRuntimeDiscoveryResult(
                resolvedProjects,
                resolvedConsentProjects,
                resolvedLoadedMods,
                resolvedCollisions,
                TelemetryRuntimeDiscovery.buildRegistrationWarnings(
                        resolvedCollisions,
                        warningsWithoutCollisionFormats(registrationWarnings, collisions)
                )
        );
    }

    @Nonnull
    private static List<String> warningsWithoutCollisionFormats(
            @Nonnull List<String> warnings,
            @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions) {
        Set<String> collisionFormats = new HashSet<>();
        for (TelemetryProjectCollisionDetector.Collision collision : collisions) {
            collisionFormats.add(collision.format());
        }
        ArrayList<String> filtered = new ArrayList<>(warnings.size());
        for (String warning : warnings) {
            if (!collisionFormats.contains(warning)) {
                filtered.add(warning);
            }
        }
        return List.copyOf(filtered);
    }

    @Nonnull
    private static List<TelemetryProjectRegistration> mergeRegistration(
            @Nonnull List<TelemetryProjectRegistration> registrations,
            @Nonnull TelemetryProjectRegistration registration) {
        LinkedHashMap<String, TelemetryProjectRegistration> merged = new LinkedHashMap<>();
        for (TelemetryProjectRegistration existing : registrations) {
            merged.put(existing.projectId().toLowerCase(Locale.ROOT), existing);
        }
        merged.put(registration.projectId().toLowerCase(Locale.ROOT), registration);
        return List.copyOf(merged.values());
    }

    @Nonnull
    private static List<CrashReportEnvelope.LoadedModMetadata> mergeLoadedMod(
            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods,
            @Nonnull CrashReportEnvelope.LoadedModMetadata loadedMod) {
        LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> merged = new LinkedHashMap<>();
        for (CrashReportEnvelope.LoadedModMetadata existing : loadedMods) {
            merged.put(existing.identifier().toLowerCase(Locale.ROOT), existing);
        }
        merged.put(loadedMod.identifier().toLowerCase(Locale.ROOT), loadedMod);
        return List.copyOf(merged.values());
    }
}
