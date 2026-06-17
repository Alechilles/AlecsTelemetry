package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
            try {
                PluginManager pluginManager = PluginManager.get();
                if (pluginManager == null) {
                    return safeFallback;
                }
                List<CrashReportEnvelope.LoadedModMetadata> activeMods = new ArrayList<>();
                for (PluginBase plugin : pluginManager.getPlugins()) {
                    if (plugin == null || !plugin.isEnabled()) {
                        continue;
                    }
                    String identifier = plugin.getIdentifier() == null ? null : plugin.getIdentifier().toString();
                    if (identifier == null || identifier.isBlank()) {
                        continue;
                    }
                    String version = plugin.getManifest() == null || plugin.getManifest().getVersion() == null
                            ? "unknown"
                            : plugin.getManifest().getVersion().toString();
                    activeMods.add(new CrashReportEnvelope.LoadedModMetadata(identifier, version));
                }
                return List.copyOf(activeMods);
            } catch (Throwable ex) {
                if (logger != null) {
                    logger.at(Level.WARNING).withCause(ex).log("Failed to snapshot active loaded mods for manual report context.");
                }
                return safeFallback;
            }
        };
    }
}
