package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.util.List;

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
}
