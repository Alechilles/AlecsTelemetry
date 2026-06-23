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
            StateDocument document = read(file);
            LinkedHashMap<String, ReviewedProjectDocument> reviewed = reviewedEntries(document);
            reviewed.put(key(project), ReviewedProjectDocument.from(project));
            document.reviewedProjects = new ArrayList<>(reviewed.values());
            write(file, document);
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
        Map<String, ReviewedProjectDocument> reviewed = reviewedEntries(read(file));
        ArrayList<TelemetryProjectRegistration> unreviewed = new ArrayList<>();
        for (TelemetryProjectRegistration project : projects) {
            if (!reviewed.containsKey(key(project))) {
                unreviewed.add(project);
            }
        }
        return List.copyOf(unreviewed);
    }

    public boolean isNoticeShown(@Nonnull Path file,
                                 @Nonnull String viewerKey,
                                 @Nonnull List<TelemetryProjectRegistration> projects) {
        return noticeEntries(read(file)).containsKey(noticeKey(viewerKey, projects));
    }

    public boolean markNoticeShown(@Nonnull Path file,
                                   @Nonnull String viewerKey,
                                   @Nonnull List<TelemetryProjectRegistration> projects) {
        try {
            StateDocument document = read(file);
            LinkedHashMap<String, NoticeDocument> notices = noticeEntries(document);
            notices.put(noticeKey(viewerKey, projects), NoticeDocument.from(viewerKey, projects));
            document.shownNotices = new ArrayList<>(notices.values());
            write(file, document);
            return true;
        } catch (Exception ex) {
            warn("Failed to save telemetry consent notice state to " + file, ex);
            return false;
        }
    }

    public boolean markFunnelEventRecorded(@Nonnull Path file,
                                           @Nonnull String viewerKey,
                                           @Nonnull String eventType,
                                           @Nonnull TelemetryProjectRegistration project) {
        try {
            StateDocument document = read(file);
            LinkedHashMap<String, FunnelEventDocument> events = funnelEventEntries(document);
            String key = funnelEventKey(viewerKey, eventType, project);
            if (events.containsKey(key)) {
                return false;
            }
            events.put(key, FunnelEventDocument.from(viewerKey, eventType, project));
            document.funnelEvents = new ArrayList<>(events.values());
            write(file, document);
            return true;
        } catch (Exception ex) {
            warn("Failed to save telemetry consent funnel state to " + file, ex);
            return false;
        }
    }

    @Nonnull
    private LinkedHashMap<String, ReviewedProjectDocument> reviewedEntries(@Nonnull Path file) {
        return reviewedEntries(read(file));
    }

    @Nonnull
    private LinkedHashMap<String, ReviewedProjectDocument> reviewedEntries(@Nonnull StateDocument document) {
        LinkedHashMap<String, ReviewedProjectDocument> reviewed = new LinkedHashMap<>();
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
    private LinkedHashMap<String, NoticeDocument> noticeEntries(@Nonnull StateDocument document) {
        LinkedHashMap<String, NoticeDocument> notices = new LinkedHashMap<>();
        if (document.shownNotices == null) {
            return notices;
        }
        for (NoticeDocument notice : document.shownNotices) {
            if (notice == null || notice.viewerKey == null || notice.promptKey == null) {
                continue;
            }
            notices.put(noticeKey(notice.viewerKey, notice.promptKey), notice);
        }
        return notices;
    }

    @Nonnull
    private LinkedHashMap<String, FunnelEventDocument> funnelEventEntries(@Nonnull StateDocument document) {
        LinkedHashMap<String, FunnelEventDocument> events = new LinkedHashMap<>();
        if (document.funnelEvents == null) {
            return events;
        }
        for (FunnelEventDocument event : document.funnelEvents) {
            if (event == null
                    || event.viewerKey == null
                    || event.eventType == null
                    || event.projectId == null
                    || event.pluginIdentifier == null
                    || event.pluginVersion == null) {
                continue;
            }
            events.put(funnelEventKey(event.viewerKey, event.eventType, event.projectId, event.pluginIdentifier, event.pluginVersion), event);
        }
        return events;
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

    private void write(@Nonnull Path file, @Nonnull StateDocument document) throws java.io.IOException {
        document.version = CURRENT_VERSION;
        if (document.reviewedProjects == null) {
            document.reviewedProjects = List.of();
        }
        if (document.shownNotices == null) {
            document.shownNotices = List.of();
        }
        if (document.funnelEvents == null) {
            document.funnelEvents = List.of();
        }

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

    @Nonnull
    private static String noticeKey(@Nonnull String viewerKey, @Nonnull List<TelemetryProjectRegistration> projects) {
        return noticeKey(viewerKey, promptKey(projects));
    }

    @Nonnull
    private static String noticeKey(@Nonnull String viewerKey, @Nonnull String promptKey) {
        return normalize(viewerKey) + "|" + promptKey;
    }

    @Nonnull
    private static String funnelEventKey(@Nonnull String viewerKey,
                                         @Nonnull String eventType,
                                         @Nonnull TelemetryProjectRegistration project) {
        return funnelEventKey(viewerKey, eventType, project.projectId(), project.pluginIdentifier(), project.pluginVersion());
    }

    @Nonnull
    private static String funnelEventKey(@Nonnull String viewerKey,
                                         @Nonnull String eventType,
                                         @Nonnull String projectId,
                                         @Nonnull String pluginIdentifier,
                                         @Nonnull String pluginVersion) {
        return normalize(viewerKey)
                + "|"
                + normalize(eventType)
                + "|"
                + key(projectId, pluginIdentifier, pluginVersion);
    }

    @Nonnull
    private static String promptKey(@Nonnull List<TelemetryProjectRegistration> projects) {
        return projects.stream()
                .map(TelemetryConsentStateStore::key)
                .sorted()
                .reduce((left, right) -> left + ";" + right)
                .orElse("none");
    }

    @Nonnull
    private static String normalize(@Nonnull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "unknown" : normalized;
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
        private List<NoticeDocument> shownNotices;
        private List<FunnelEventDocument> funnelEvents;
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

    private static final class NoticeDocument {
        private String viewerKey;
        private String promptKey;
        private List<ReviewedProjectDocument> projects;

        @Nonnull
        private static NoticeDocument from(@Nonnull String viewerKey,
                                           @Nonnull List<TelemetryProjectRegistration> projects) {
            NoticeDocument document = new NoticeDocument();
            document.viewerKey = normalize(viewerKey);
            document.promptKey = promptKey(projects);
            document.projects = projects.stream()
                    .map(ReviewedProjectDocument::from)
                    .toList();
            return document;
        }
    }

    private static final class FunnelEventDocument {
        private String viewerKey;
        private String eventType;
        private String projectId;
        private String pluginIdentifier;
        private String pluginVersion;

        @Nonnull
        private static FunnelEventDocument from(@Nonnull String viewerKey,
                                                @Nonnull String eventType,
                                                @Nonnull TelemetryProjectRegistration project) {
            FunnelEventDocument document = new FunnelEventDocument();
            document.viewerKey = normalize(viewerKey);
            document.eventType = normalize(eventType);
            document.projectId = project.projectId();
            document.pluginIdentifier = project.pluginIdentifier();
            document.pluginVersion = project.pluginVersion();
            return document;
        }
    }
}
