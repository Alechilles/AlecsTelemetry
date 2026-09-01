package com.alechilles.beacon.runtime.discovery;

import com.alechilles.beacon.consent.TelemetryConsentCapabilities;
import com.alechilles.beacon.consent.TelemetryConsentSnapshot;
import com.alechilles.beacon.consent.TelemetryConsentStateStore;
import com.alechilles.beacon.crash.CrashReportEnvelope;
import com.alechilles.beacon.project.TelemetryProjectCollisionDetector;
import com.alechilles.beacon.project.TelemetryProjectDiscovery;
import com.alechilles.beacon.project.TelemetryProjectOverride;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.alechilles.beacon.runtime.TelemetryProjectOverrideStore;
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
                discoveryResult.candidates(),
                activeLoadedMods
        );
        List<TelemetryProjectRegistration> activeConsentProjects = filterRegistrationsToLoadedMods(
                discoveryResult.candidates(),
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
        overrides = protectNewlySupportedCentralConsentOverrides(
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
        return TelemetryProjectDiscovery.electCandidates(filtered);
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
        return Map.copyOf(centralOverrides);
    }

    @Nonnull
    private Map<String, TelemetryProjectOverride> protectNewlySupportedCentralConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides,
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectOverrideStore store) {
        LinkedHashMap<String, TelemetryProjectOverride> protectedOverrides = new LinkedHashMap<>(overrides);
        TelemetryConsentStateStore consentStateStore = new TelemetryConsentStateStore(logger);
        for (TelemetryProjectRegistration project : projects) {
            if (project.isPassiveDescriptor()) {
                continue;
            }
            List<String> additions = consentStateStore.addedSupportedCategories(
                    dataPaths.consentStateFile(),
                    project
            );
            if (additions.isEmpty()) {
                continue;
            }
            String projectIdKey = project.projectId().toLowerCase(Locale.ROOT);
            java.nio.file.Path overrideFile = dataPaths.projectOverrideFile(project.projectId());
            TelemetryProjectOverride existing = protectedOverrides.get(projectIdKey);
            if (store.disableCategories(overrideFile, additions)) {
                TelemetryProjectOverride reloaded = store.load(overrideFile);
                if (reloaded != null) {
                    protectedOverrides.put(projectIdKey, reloaded);
                    continue;
                }
            }
            protectedOverrides.put(projectIdKey, disabledOverride(existing, additions));
            warnConsentProtectionFailure(project, overrideFile, additions);
        }
        return Map.copyOf(protectedOverrides);
    }

    @Nonnull
    private Map<String, TelemetryProjectOverride> cleanUnsupportedCentralConsentOverrides(
            @Nonnull List<TelemetryProjectRegistration> projects,
            @Nonnull Map<String, TelemetryProjectOverride> overrides,
            @Nonnull TelemetryDataPaths dataPaths,
            @Nonnull TelemetryProjectOverrideStore store) {
        LinkedHashMap<String, TelemetryProjectOverride> cleaned = new LinkedHashMap<>(overrides);
        for (TelemetryProjectRegistration project : projects) {
            if (project.isPassiveDescriptor()) {
                continue;
            }
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
        boolean hasUnsupportedDiagnostics = !supported.diagnosticsEnabled()
                && override.diagnostics() != null
                && override.diagnostics().enabled() != null;
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
                || hasUnsupportedDiagnostics
                || hasUnsupportedLifecycle
                || hasUnsupportedBreadcrumbs
                || hasUnsupportedPerformance
                || hasUnsupportedUsage
                || hasUnsupportedStats;
    }

    @Nonnull
    private static TelemetryProjectOverride disabledOverride(
            @Nullable TelemetryProjectOverride existing,
            @Nonnull List<String> categories) {
        TelemetryProjectOverride base = existing == null
                ? TelemetryProjectOverride.fromJson("{}")
                : existing;
        TelemetryProjectOverride.EventsOverride events = base.events();
        if (categories.contains("error") || categories.contains("lifecycle") || categories.contains("breadcrumbs")) {
            events = new TelemetryProjectOverride.EventsOverride(
                    categories.contains("error")
                            ? new TelemetryProjectOverride.EventTypeOverride(false)
                            : events == null ? null : events.errors(),
                    categories.contains("lifecycle")
                            ? new TelemetryProjectOverride.EventTypeOverride(false)
                            : events == null ? null : events.lifecycle(),
                    categories.contains("breadcrumbs")
                            ? new TelemetryProjectOverride.BreadcrumbsOverride(false)
                            : events == null ? null : events.breadcrumbs()
            );
        }
        return new TelemetryProjectOverride(
                base.enabled(),
                categories.contains("crash")
                        ? new TelemetryProjectOverride.CaptureOverride(false, false, false, false)
                        : base.capture(),
                base.destinationMode(),
                events,
                categories.contains("diagnostics")
                        ? new TelemetryProjectOverride.DiagnosticsOverride(false)
                        : base.diagnostics(),
                categories.contains("performance")
                        ? new TelemetryProjectOverride.PerformanceOverride(false, null, null)
                        : base.performance(),
                categories.contains("usage")
                        ? new TelemetryProjectOverride.UsageOverride(false, List.of())
                        : base.usage(),
                categories.contains("stats")
                        ? new TelemetryProjectOverride.StatsOverride(false, List.of())
                        : base.stats(),
                base.hosted(),
                base.customEndpoint()
        );
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

    private void warnConsentProtectionFailure(@Nonnull TelemetryProjectRegistration project,
                                              @Nonnull java.nio.file.Path overrideFile,
                                              @Nonnull List<String> categories) {
        if (logger == null) {
            return;
        }
        logger.at(Level.WARNING).log(
                "Failed to persist consent protection for "
                        + String.join(", ", categories)
                        + " on telemetry project "
                        + project.projectId()
                        + " at "
                        + overrideFile
                        + "; categories remain disabled for this runtime."
        );
    }
}
