package com.alechilles.alecstelemetry.runtime.discovery;

import com.alechilles.alecstelemetry.consent.TelemetryConsentCapabilities;
import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectCollisionDetector;
import com.alechilles.alecstelemetry.project.TelemetryProjectDiscovery;
import com.alechilles.alecstelemetry.project.TelemetryProjectOverride;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;
import com.alechilles.alecstelemetry.runtime.TelemetryProjectOverrideStore;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class TelemetryRuntimeDiscovery {

    private final HytaleLogger logger;

    public TelemetryRuntimeDiscovery(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult discoverActive(@Nonnull TelemetryDataPaths dataPaths) {
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        return discoverActive(
                dataPaths,
                discoveryResult,
                TelemetryLoadedModSnapshotProvider.hytalePluginManager(discoveryResult.loadedMods(), logger)
        );
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult discoverActive(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryLoadedModSnapshotProvider snapshotProvider) {
        TelemetryProjectDiscovery.DiscoveryResult discoveryResult = new TelemetryProjectDiscovery(logger)
                .discover(dataPaths.descriptorDirectories());
        return discoverActive(dataPaths, discoveryResult, snapshotProvider);
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult discoverActive(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectDiscovery.DiscoveryResult discoveryResult) {
        return discoverActive(dataPaths, discoveryResult, TelemetryLoadedModSnapshotProvider.fixed(discoveryResult.loadedMods()));
    }

    @Nonnull
    public TelemetryRuntimeDiscoveryResult discoverActive(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectDiscovery.DiscoveryResult discoveryResult,
            @Nonnull TelemetryLoadedModSnapshotProvider snapshotProvider) {
        List<CrashReportEnvelope.LoadedModMetadata> activeLoadedMods = snapshotProvider.snapshotLoadedMods();
        List<TelemetryProjectRegistration> activeProjects = filterRegistrationsToLoadedMods(
                discoveryResult.projects(),
                activeLoadedMods
        );
        List<TelemetryProjectRegistration> activeConsentProjects = filterRegistrationsToLoadedMods(
                discoveryResult.consentProjects(),
                activeLoadedMods
        );
        TelemetryProjectOverrideStore overrideStore = new TelemetryProjectOverrideStore(logger);
        Map<String, TelemetryProjectOverride> overrides = overrideStore
                .loadAll(dataPaths.projectSettingsDirectory());
        overrides = cleanUnsupportedCentralConsentOverrides(
                activeConsentProjects,
                overrides,
                dataPaths,
                overrideStore
        );
        Map<String, TelemetryProjectOverride> consentOverrides = loadConsentOverrides(
                activeConsentProjects,
                overrides,
                dataPaths
        );
        List<TelemetryProjectRegistration> resolvedProjects = applyOverrides(activeProjects, overrides);
        List<TelemetryProjectRegistration> resolvedConsentProjects = applyOverrides(activeConsentProjects, consentOverrides);
        List<TelemetryProjectCollisionDetector.Collision> collisions = TelemetryProjectCollisionDetector.detect(resolvedProjects);
        List<String> registrationWarnings = buildRegistrationWarnings(collisions, discoveryResult.skippedRegistrationWarnings());
        logRegistrationWarnings(registrationWarnings);
        return new TelemetryRuntimeDiscoveryResult(
                resolvedProjects,
                resolvedConsentProjects,
                activeLoadedMods,
                collisions,
                registrationWarnings
        );
    }

    @Nonnull
    public static List<TelemetryProjectRegistration> filterRegistrationsToLoadedMods(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        if (projects.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> loadedIdentifiers = new LinkedHashMap<>();
        for (CrashReportEnvelope.LoadedModMetadata loadedMod : loadedMods) {
            if (loadedMod == null) {
                continue;
            }
            addIdentifierVariants(loadedIdentifiers, loadedMod.identifier());
        }
        if (loadedIdentifiers.isEmpty()) {
            return List.of();
        }

        ArrayList<TelemetryProjectRegistration> filtered = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            if (matchesLoadedMod(project, loadedIdentifiers)) {
                filtered.add(project);
            }
        }
        return List.copyOf(filtered);
    }

    @Nonnull
    public static List<TelemetryProjectRegistration> applyOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides) {
        ArrayList<TelemetryProjectRegistration> resolved = new ArrayList<>(projects.size());
        for (TelemetryProjectRegistration project : projects) {
            resolved.add(project.withOverride(overrides.get(project.projectId().toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(resolved);
    }

    private Map<String, TelemetryProjectOverride> loadConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> centralOverrides,
            @Nonnull TelemetryDataPaths dataPaths) {
        LinkedHashMap<String, TelemetryProjectOverride> resolved = new LinkedHashMap<>(centralOverrides);
        TelemetryProjectOverrideStore store = new TelemetryProjectOverrideStore(logger);
        for (TelemetryProjectRegistration project : projects) {
            String projectIdKey = project.projectId().toLowerCase(Locale.ROOT);
            if (resolved.containsKey(projectIdKey)) {
                continue;
            }
            java.nio.file.Path embeddedOverrideFile = embeddedProjectOverrideFile(dataPaths, project);
            if (embeddedOverrideFile == null) {
                continue;
            }
            TelemetryProjectOverride override = store.load(embeddedOverrideFile);
            if (override != null) {
                resolved.put(projectIdKey, override);
            }
        }
        return Map.copyOf(resolved);
    }

    @Nonnull
    private Map<String, TelemetryProjectOverride> cleanUnsupportedCentralConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides,
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectOverrideStore store) {
        LinkedHashMap<String, TelemetryProjectOverride> cleaned = new LinkedHashMap<>(overrides);
        for (TelemetryProjectRegistration project : projects) {
            String projectIdKey = project.projectId().toLowerCase(Locale.ROOT);
            TelemetryProjectOverride override = cleaned.get(projectIdKey);
            if (override == null || !hasUnsupportedConsentValues(project, override)) {
                continue;
            }
            java.nio.file.Path overrideFile = dataPaths.projectOverrideFile(project.projectId());
            if (store.removeUnsupportedConsentValues(
                    overrideFile,
                    TelemetryConsentCapabilities.supportedSnapshot(project)
            )) {
                TelemetryProjectOverride reloaded = store.load(overrideFile);
                if (reloaded == null) {
                    cleaned.remove(projectIdKey);
                } else {
                    cleaned.put(projectIdKey, reloaded);
                }
            }
        }
        return Map.copyOf(cleaned);
    }

    private static boolean hasUnsupportedConsentValues(@Nonnull TelemetryProjectRegistration project,
                                                       @Nonnull TelemetryProjectOverride override) {
        TelemetryConsentSnapshot supported = TelemetryConsentCapabilities.supportedSnapshot(project);
        boolean hasUnsupportedCapture = !supported.crashEnabled()
                && override.capture() != null
                && override.capture().hasAnyValue();
        boolean hasUnsupportedErrors = !supported.errorEnabled()
                && override.events() != null
                && override.events().errors() != null
                && override.events().errors().enabled() != null;
        boolean hasUnsupportedLifecycle = !supported.lifecycleEnabled()
                && override.events() != null
                && override.events().lifecycle() != null
                && override.events().lifecycle().enabled() != null;
        boolean hasUnsupportedBreadcrumbs = !supported.breadcrumbsEnabled()
                && override.events() != null
                && override.events().breadcrumbs() != null
                && override.events().breadcrumbs().enabled() != null;
        boolean hasUnsupportedPerformance = !supported.performanceEnabled()
                && override.performance() != null
                && override.performance().enabled() != null;
        boolean hasUnsupportedUsage = !supported.usageEnabled()
                && override.usage() != null
                && override.usage().enabled() != null;
        boolean hasUnsupportedStats = !supported.statsEnabled()
                && override.stats() != null
                && override.stats().enabled() != null;
        return hasUnsupportedCapture
                || hasUnsupportedErrors
                || hasUnsupportedLifecycle
                || hasUnsupportedBreadcrumbs
                || hasUnsupportedPerformance
                || hasUnsupportedUsage
                || hasUnsupportedStats;
    }

    private static boolean matchesLoadedMod(@Nonnull TelemetryProjectRegistration project,
                                            @Nonnull Map<String, Boolean> loadedIdentifiers) {
        if (project.isPassiveDescriptor()) {
            return containsIdentifierVariant(loadedIdentifiers, project.hostPluginIdentifier());
        }
        if (containsIdentifierVariant(loadedIdentifiers, project.pluginIdentifier())) {
            return true;
        }
        if (containsIdentifierVariant(loadedIdentifiers, project.displayName())) {
            return true;
        }
        for (String ownerPluginIdentifier : project.ownerPluginIdentifiers()) {
            if (containsIdentifierVariant(loadedIdentifiers, ownerPluginIdentifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifierVariant(@Nonnull Map<String, Boolean> loadedIdentifiers,
                                                     @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        LinkedHashMap<String, Boolean> variants = new LinkedHashMap<>();
        addIdentifierVariants(variants, identifier);
        for (String variant : variants.keySet()) {
            if (loadedIdentifiers.containsKey(variant)) {
                return true;
            }
        }
        return false;
    }

    private static void addIdentifierVariants(@Nonnull Map<String, Boolean> identifiers,
                                              @Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String normalized = identifier.trim();
        identifiers.put(normalized.toLowerCase(Locale.ROOT), true);
        int separator = normalized.indexOf(':');
        if (separator >= 0 && separator + 1 < normalized.length()) {
            identifiers.put(normalized.substring(separator + 1).trim().toLowerCase(Locale.ROOT), true);
        }
    }

    @Nullable
    private static java.nio.file.Path embeddedProjectOverrideFile(
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectRegistration project) {
        return dataPaths.legacyEmbeddedProjectOverrideFile(project.pluginIdentifier(), project.projectId());
    }

    @Nonnull
    public static List<String> buildRegistrationWarnings(
            @Nonnull List<TelemetryProjectCollisionDetector.Collision> collisions,
            @Nonnull List<String> skippedWarnings) {
        ArrayList<String> warnings = new ArrayList<>(skippedWarnings.size() + collisions.size());
        warnings.addAll(skippedWarnings);
        for (TelemetryProjectCollisionDetector.Collision collision : collisions) {
            warnings.add(collision.format());
        }
        return List.copyOf(warnings);
    }

    private void logRegistrationWarnings(@Nonnull List<String> registrationWarnings) {
        if (logger == null || registrationWarnings.isEmpty()) {
            return;
        }
        for (String warning : registrationWarnings) {
            Level level = warning.contains("embedded telemetry project") ? Level.INFO : Level.WARNING;
            logger.at(level).log("Telemetry registration note: " + warning);
        }
    }
}
