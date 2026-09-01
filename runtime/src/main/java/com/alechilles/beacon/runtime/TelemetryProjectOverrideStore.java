package com.alechilles.beacon.runtime;

import com.alechilles.beacon.consent.TelemetryConsentSnapshot;
import com.alechilles.beacon.project.TelemetryProjectOverride;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads optional project override files stored by the telemetry runtime.
 */
public final class TelemetryProjectOverrideStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final HytaleLogger logger;

    public TelemetryProjectOverrideStore(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public Map<String, TelemetryProjectOverride> loadAll(@Nonnull Path directory) {
        LinkedHashMap<String, TelemetryProjectOverride> overrides = new LinkedHashMap<>();
        try {
            Files.createDirectories(directory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
                for (Path file : stream) {
                    String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                    String projectId = projectIdFromFileName(fileName);
                    if (projectId.isBlank()) {
                        continue;
                    }
                    String rawJson = Files.readString(file, StandardCharsets.UTF_8);
                    TelemetryProjectOverride override = TelemetryProjectOverride.fromJson(rawJson);
                    if (override.hasAnyValue()) {
                        overrides.put(projectId.toLowerCase(Locale.ROOT), override);
                    }
                }
            }
        } catch (Exception ex) {
            warn("Failed to load telemetry project overrides from " + directory, ex);
        }
        return Map.copyOf(overrides);
    }

    @Nullable
    public TelemetryProjectOverride load(@Nonnull Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String rawJson = Files.readString(file, StandardCharsets.UTF_8);
            TelemetryProjectOverride override = TelemetryProjectOverride.fromJson(rawJson);
            return override.hasAnyValue() ? override : null;
        } catch (Exception ex) {
            warn("Failed to load telemetry project override from " + file, ex);
            return null;
        }
    }

    public boolean saveProjectEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> root.addProperty("enabled", enabled));
    }

    public boolean saveCrashEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> {
            JsonObject capture = object(root, "capture");
            capture.addProperty("uncaughtExceptions", enabled);
            capture.addProperty("setupFailures", enabled);
            capture.addProperty("startFailures", enabled);
            capture.addProperty("exceptionalWorldRemovals", enabled);
        });
    }

    public boolean saveBreadcrumbsEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> {
            JsonObject events = object(root, "events");
            JsonObject breadcrumbs = object(events, "breadcrumbs");
            breadcrumbs.addProperty("enabled", enabled);
        });
    }

    public boolean saveErrorEventsEnabled(@Nonnull Path file, boolean enabled) {
        return updateEventTypeEnabled(file, "errors", enabled);
    }

    public boolean saveDiagnosticsEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> object(root, "diagnostics").addProperty("enabled", enabled));
    }

    public boolean saveLifecycleEventsEnabled(@Nonnull Path file, boolean enabled) {
        return updateEventTypeEnabled(file, "lifecycle", enabled);
    }

    public boolean savePerformanceEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> object(root, "performance").addProperty("enabled", enabled));
    }

    public boolean saveUsageEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> object(root, "usage").addProperty("enabled", enabled));
    }

    public boolean saveStatsEnabled(@Nonnull Path file, boolean enabled) {
        return update(file, root -> object(root, "stats").addProperty("enabled", enabled));
    }

    /**
     * Persists a protective opt-out for only the newly exposed categories,
     * preserving all unrelated project choices already present in the file.
     */
    public boolean disableCategories(@Nonnull Path file, @Nonnull Collection<String> categories) {
        return update(file, root -> {
            for (String rawCategory : categories) {
                if (rawCategory == null || rawCategory.isBlank()) {
                    continue;
                }
                switch (rawCategory.trim().toLowerCase(Locale.ROOT)) {
                    case "crash" -> {
                        JsonObject capture = object(root, "capture");
                        capture.addProperty("uncaughtExceptions", false);
                        capture.addProperty("setupFailures", false);
                        capture.addProperty("startFailures", false);
                        capture.addProperty("exceptionalWorldRemovals", false);
                    }
                    case "error" -> updateEventTypeEnabled(root, "errors", false);
                    case "diagnostics" -> object(root, "diagnostics").addProperty("enabled", false);
                    case "lifecycle", "breadcrumbs" -> updateEventTypeEnabled(
                            root,
                            rawCategory.trim().toLowerCase(Locale.ROOT),
                            false
                    );
                    case "performance", "usage", "stats" -> object(root, rawCategory.trim().toLowerCase(Locale.ROOT))
                            .addProperty("enabled", false);
                    default -> {
                        // Ignore unknown labels so future categories do not
                        // overwrite unrelated user settings.
                    }
                }
            }
        });
    }

    public boolean saveConsentSnapshot(@Nonnull Path file,
                                       @Nonnull TelemetryConsentSnapshot snapshot,
                                       @Nonnull TelemetryConsentSnapshot supported) {
        return update(file, root -> {
            root.addProperty("enabled", snapshot.projectEnabled());
            if (supported.crashEnabled()) {
                JsonObject capture = object(root, "capture");
                capture.addProperty("uncaughtExceptions", snapshot.crashEnabled());
                capture.addProperty("setupFailures", snapshot.crashEnabled());
                capture.addProperty("startFailures", snapshot.crashEnabled());
                capture.addProperty("exceptionalWorldRemovals", snapshot.crashEnabled());
            } else {
                root.remove("capture");
            }
            updateEventType(root, "errors", supported.errorEnabled(), snapshot.errorEnabled());
            updateCategory(root, "diagnostics", supported.diagnosticsEnabled(), snapshot.diagnosticsEnabled());
            updateEventType(root, "lifecycle", supported.lifecycleEnabled(), snapshot.lifecycleEnabled());
            updateEventType(root, "breadcrumbs", supported.breadcrumbsEnabled(), snapshot.breadcrumbsEnabled());
            updateCategory(root, "performance", supported.performanceEnabled(), snapshot.performanceEnabled());
            updateCategory(root, "usage", supported.usageEnabled(), snapshot.usageEnabled());
            updateCategory(root, "stats", supported.statsEnabled(), snapshot.statsEnabled());
        });
    }

    public boolean removeUnsupportedConsentValues(@Nonnull Path file, @Nonnull TelemetryConsentSnapshot supported) {
        return update(file, root -> {
            if (!supported.crashEnabled()) {
                root.remove("capture");
            }
            removeUnsupportedEventType(root, "errors", supported.errorEnabled());
            if (!supported.diagnosticsEnabled()) {
                root.remove("diagnostics");
            }
            removeUnsupportedEventType(root, "lifecycle", supported.lifecycleEnabled());
            removeUnsupportedEventType(root, "breadcrumbs", supported.breadcrumbsEnabled());
            if (!supported.performanceEnabled()) {
                root.remove("performance");
            }
            if (!supported.usageEnabled()) {
                root.remove("usage");
            }
            if (!supported.statsEnabled()) {
                root.remove("stats");
            }
        });
    }

    @Nonnull
    private static String projectIdFromFileName(@Nonnull String fileName) {
        String trimmed = fileName.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return "";
        }
        return trimmed.substring(0, trimmed.length() - 5).trim();
    }

    private boolean update(@Nonnull Path file, @Nonnull OverrideMutation mutation) {
        try {
            JsonObject root = readObject(file);
            mutation.apply(root);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String serialized = GSON.toJson(root) + System.lineSeparator();
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, serialized, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            warn("Failed to save telemetry project override to " + file, ex);
            return false;
        }
    }

    private boolean updateEventTypeEnabled(@Nonnull Path file, @Nonnull String eventType, boolean enabled) {
        return update(file, root -> {
            updateEventTypeEnabled(root, eventType, enabled);
        });
    }

    private static void updateEventTypeEnabled(@Nonnull JsonObject root,
                                               @Nonnull String eventType,
                                               boolean enabled) {
        JsonObject events = object(root, "events");
        JsonObject eventObject = object(events, eventType);
        eventObject.addProperty("enabled", enabled);
    }

    private static void updateEventType(@Nonnull JsonObject root,
                                        @Nonnull String eventType,
                                        boolean supported,
                                        boolean enabled) {
        if (!supported) {
            removeUnsupportedEventType(root, eventType, supported);
            return;
        }
        JsonObject events = object(root, "events");
        JsonObject eventObject = object(events, eventType);
        eventObject.addProperty("enabled", enabled);
    }

    private static void removeUnsupportedEventType(@Nonnull JsonObject root,
                                                   @Nonnull String eventType,
                                                   boolean supported) {
        if (supported) {
            return;
        }
        JsonElement eventsElement = root.get("events");
        if (eventsElement != null && eventsElement.isJsonObject()) {
            JsonObject events = eventsElement.getAsJsonObject();
            events.remove(eventType);
            if (events.isEmpty()) {
                root.remove("events");
            }
        }
    }

    private static void updateCategory(@Nonnull JsonObject root,
                                       @Nonnull String category,
                                       boolean supported,
                                       boolean enabled) {
        if (!supported) {
            root.remove(category);
            return;
        }
        object(root, category).addProperty("enabled", enabled);
    }

    @Nonnull
    private JsonObject readObject(@Nonnull Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            warn("Failed to parse telemetry project override from " + file + "; replacing with a valid override file.", ex);
            return new JsonObject();
        }
    }

    @Nonnull
    private static JsonObject object(@Nonnull JsonObject parent, @Nonnull String key) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private void warn(@Nonnull String message, @Nullable Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.at(Level.WARNING).log(message);
            return;
        }
        logger.at(Level.WARNING).withCause(throwable).log(message);
    }

    private interface OverrideMutation {
        void apply(@Nonnull JsonObject root);
    }
}
