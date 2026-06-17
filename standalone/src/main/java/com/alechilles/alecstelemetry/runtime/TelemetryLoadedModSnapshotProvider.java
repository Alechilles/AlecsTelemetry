package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

@FunctionalInterface
public interface TelemetryLoadedModSnapshotProvider {

    @Nonnull
    List<CrashReportEnvelope.LoadedModMetadata> snapshotLoadedMods();

    @Nonnull
    static TelemetryLoadedModSnapshotProvider fixed(@Nonnull List<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        List<CrashReportEnvelope.LoadedModMetadata> safeLoadedMods = List.copyOf(loadedMods);
        return () -> safeLoadedMods;
    }

    @Nonnull
    static TelemetryLoadedModSnapshotProvider hytalePluginManager(@Nonnull List<CrashReportEnvelope.LoadedModMetadata> fallback,
                                                                  @Nullable HytaleLogger logger) {
        List<CrashReportEnvelope.LoadedModMetadata> safeFallback = List.copyOf(fallback);
        return () -> {
            LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> activeMods = new LinkedHashMap<>();
            boolean runtimeSnapshotAvailable = false;
            try {
                PluginManager pluginManager = PluginManager.get();
                if (pluginManager != null) {
                    runtimeSnapshotAvailable = true;
                    for (PluginBase plugin : pluginManager.getPlugins()) {
                        if (plugin == null || !plugin.isEnabled()) {
                            continue;
                        }
                        String identifier = plugin.getIdentifier() == null ? null : plugin.getIdentifier().toString();
                        String version = plugin.getManifest() == null || plugin.getManifest().getVersion() == null
                                ? "unknown"
                                : plugin.getManifest().getVersion().toString();
                        addReportableMod(activeMods, identifier, version);
                    }
                }
            } catch (Throwable ex) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(ex).log("Failed to snapshot active loaded plugin mods for manual report context.");
                }
            }

            try {
                AssetModule assetModule = AssetModule.get();
                if (assetModule != null) {
                    runtimeSnapshotAvailable = true;
                    for (AssetPack pack : assetModule.getAssetPacks()) {
                        if (pack == null || pack.isCoreMod()) {
                            continue;
                        }
                        String identifier = pack.getName();
                        String version = pack.getManifest() == null || pack.getManifest().getVersion() == null
                                ? "unknown"
                                : pack.getManifest().getVersion().toString();
                        addReportableMod(activeMods, identifier, version);
                    }
                }
            } catch (Throwable ex) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(ex).log("Failed to snapshot active loaded asset packs for manual report context.");
                }
            }

            if (!runtimeSnapshotAvailable && activeMods.isEmpty()) {
                return safeFallback;
            }
            return List.copyOf(activeMods.values());
        };
    }

    @Nonnull
    static List<CrashReportEnvelope.LoadedModMetadata> reportableLoadedMods(
            @Nonnull Collection<CrashReportEnvelope.LoadedModMetadata> loadedMods) {
        LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> reportableMods = new LinkedHashMap<>();
        for (CrashReportEnvelope.LoadedModMetadata mod : loadedMods) {
            if (mod == null) {
                continue;
            }
            addReportableMod(reportableMods, mod.identifier(), mod.version());
        }
        return List.copyOf(reportableMods.values());
    }

    static boolean isReportableModIdentifier(@Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String normalized = identifier.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !lower.startsWith("hytale:")
                && !"hytale".equals(lower)
                && !lower.endsWith("_generatedpatches");
    }

    private static void addReportableMod(@Nonnull LinkedHashMap<String, CrashReportEnvelope.LoadedModMetadata> mods,
                                         @Nullable String identifier,
                                         @Nullable String version) {
        if (!isReportableModIdentifier(identifier)) {
            return;
        }
        String normalizedIdentifier = identifier.trim();
        String normalizedVersion = version == null || version.isBlank() ? "unknown" : version.trim();
        mods.putIfAbsent(
                normalizedIdentifier.toLowerCase(Locale.ROOT),
                new CrashReportEnvelope.LoadedModMetadata(normalizedIdentifier, normalizedVersion)
        );
    }
}
