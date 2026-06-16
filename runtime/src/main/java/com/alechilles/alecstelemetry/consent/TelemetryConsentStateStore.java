package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Tracks telemetry projects whose current installed version has already been reviewed by an operator.
 */
public final class TelemetryConsentStateStore {

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final HytaleLogger logger;

    public TelemetryConsentStateStore(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    public boolean isReviewed(@Nonnull Path file, @Nonnull TelemetryProjectRegistration project) {
        return reviewedEntries(file).containsKey(key(project));
    }

    public boolean markReviewed(@Nonnull Path file, @Nonnull TelemetryProjectRegistration project) {
        try {
            LinkedHashMap<String, ReviewedProjectDocument> reviewed = reviewedEntries(file);
            reviewed.put(key(project), ReviewedProjectDocument.from(project));
            write(file, reviewed.values());
            return true;
        } catch (Exception ex) {
            warn("Failed to save telemetry consent state to " + file, ex);
            return false;
        }
    }

    @Nonnull
    public List<TelemetryProjectRegistration> unreviewedProjects(
            @Nonnull Path file,
            @Nonnull List<TelemetryProjectRegistration> projects) {
        Map<String, ReviewedProjectDocument> reviewed = reviewedEntries(file);
        ArrayList<TelemetryProjectRegistration> unreviewed = new ArrayList<>();
        for (TelemetryProjectRegistration project : projects) {
            if (!reviewed.containsKey(key(project))) {
                unreviewed.add(project);
            }
        }
        return List.copyOf(unreviewed);
    }

    @Nonnull
    private LinkedHashMap<String, ReviewedProjectDocument> reviewedEntries(@Nonnull Path file) {
        LinkedHashMap<String, ReviewedProjectDocument> reviewed = new LinkedHashMap<>();
        StateDocument document = read(file);
        if (document.reviewedProjects == null) {
            return reviewed;
        }
        for (ReviewedProjectDocument entry : document.reviewedProjects) {
            if (entry == null || entry.projectId == null || entry.pluginIdentifier == null || entry.pluginVersion == null) {
                continue;
            }
            reviewed.put(key(entry.projectId, entry.pluginIdentifier, entry.pluginVersion), entry);
        }
        return reviewed;
    }

    @Nonnull
    private StateDocument read(@Nonnull Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return new StateDocument();
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new StateDocument();
            }
            StateDocument parsed = GSON.fromJson(raw, StateDocument.class);
            return parsed == null ? new StateDocument() : parsed;
        } catch (Exception ex) {
            warn("Failed to read telemetry consent state from " + file, ex);
            return new StateDocument();
        }
    }

    private void write(@Nonnull Path file, @Nonnull Iterable<ReviewedProjectDocument> entries) throws java.io.IOException {
        StateDocument document = new StateDocument();
        document.version = CURRENT_VERSION;
        ArrayList<ReviewedProjectDocument> reviewed = new ArrayList<>();
        for (ReviewedProjectDocument entry : entries) {
            reviewed.add(entry);
        }
        document.reviewedProjects = reviewed;

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String serialized = GSON.toJson(document) + System.lineSeparator();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, serialized, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Nonnull
    private static String key(@Nonnull TelemetryProjectRegistration project) {
        return key(project.projectId(), project.pluginIdentifier(), project.pluginVersion());
    }

    @Nonnull
    private static String key(@Nonnull String projectId, @Nonnull String pluginIdentifier, @Nonnull String pluginVersion) {
        return projectId.trim().toLowerCase(Locale.ROOT)
                + "|"
                + pluginIdentifier.trim().toLowerCase(Locale.ROOT)
                + "|"
                + pluginVersion.trim().toLowerCase(Locale.ROOT);
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

    private static final class StateDocument {
        private Integer version;
        private List<ReviewedProjectDocument> reviewedProjects;
    }

    private static final class ReviewedProjectDocument {
        private String projectId;
        private String pluginIdentifier;
        private String pluginVersion;

        @Nonnull
        private static ReviewedProjectDocument from(@Nonnull TelemetryProjectRegistration project) {
            ReviewedProjectDocument document = new ReviewedProjectDocument();
            document.projectId = project.projectId();
            document.pluginIdentifier = project.pluginIdentifier();
            document.pluginVersion = project.pluginVersion();
            return document;
        }
    }
}
