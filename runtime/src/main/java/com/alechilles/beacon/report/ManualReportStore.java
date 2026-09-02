package com.alechilles.beacon.report;

import com.alechilles.beacon.runtime.TelemetryDataPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Local filesystem store for manual report queue, review, rejection, and audit state.
 */
public final class ManualReportStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final TelemetryDataPaths paths;

    public ManualReportStore(@Nonnull TelemetryDataPaths paths) {
        this.paths = paths;
    }

    @Nonnull
    public Path savePending(@Nonnull ManualReportEnvelope envelope) {
        return write(paths.pendingManualReportsDirectory(envelope.projectId()), envelope);
    }

    @Nonnull
    public Path saveForReview(@Nonnull ManualReportEnvelope envelope) {
        return write(paths.reviewManualReportsDirectory(envelope.projectId()), envelope);
    }

    @Nonnull
    public Optional<Path> approveReviewed(@Nonnull String projectId, @Nonnull String reportId) {
        return moveReviewed(projectId, reportId, paths.pendingManualReportsDirectory(projectId));
    }

    @Nonnull
    public Optional<Path> rejectReviewed(@Nonnull String projectId, @Nonnull String reportId) {
        return moveReviewed(projectId, reportId, paths.rejectedManualReportsDirectory(projectId));
    }

    @Nonnull
    public List<PendingReport> listPendingReports(@Nonnull String projectId, int maxReports) {
        return listReports(paths.pendingManualReportsDirectory(projectId), maxReports);
    }

    @Nonnull
    public List<PendingReport> listReviewReports(@Nonnull String projectId, int maxReports) {
        return listReports(paths.reviewManualReportsDirectory(projectId), maxReports);
    }

    @Nonnull
    private static List<PendingReport> listReports(@Nonnull Path directory, int maxReports) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(Math.max(1, maxReports))
                    .map(path -> new PendingReport(path, readString(path)))
                    .filter(report -> report.payload() != null)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean delete(@Nonnull Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    public int pendingCount(@Nonnull String projectId) {
        Path directory = paths.pendingManualReportsDirectory(projectId);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files.filter(Files::isRegularFile).count();
        } catch (Exception ignored) {
            return 0;
        }
    }

    public void appendSubmittedAudit(@Nonnull ManualReportEnvelope envelope,
                                     @Nonnull String reviewState,
                                     boolean uploaded) {
        JsonObject audit = new JsonObject();
        audit.addProperty("reportId", envelope.reportId());
        audit.addProperty("projectId", envelope.projectId());
        audit.addProperty("reportKind", envelope.reportKind());
        audit.addProperty("submittedAtUtc", envelope.submittedAtUtc());
        audit.addProperty("reviewState", reviewState);
        audit.addProperty("uploaded", uploaded);
        try {
            Path logFile = paths.submittedManualReportsLog();
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    logFile,
                    COMPACT_GSON.toJson(audit) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(logFile)
                            ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.APPEND}
                            : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE_NEW}
            );
        } catch (Exception ignored) {
            // Audit logging should never break report submission.
        }
    }

    @Nonnull
    private Path write(@Nonnull Path directory, @Nonnull ManualReportEnvelope envelope) {
        try {
            Files.createDirectories(directory);
            Path path = directory.resolve(fileName(envelope));
            atomicWrite(path, envelope.toJson());
            return path;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to save manual report " + envelope.reportId(), ex);
        }
    }

    @Nonnull
    private Optional<Path> moveReviewed(@Nonnull String projectId,
                                        @Nonnull String reportId,
                                        @Nonnull Path destinationDirectory) {
        Path source = findReport(paths.reviewManualReportsDirectory(projectId), reportId);
        if (source == null) {
            return Optional.empty();
        }
        try {
            Files.createDirectories(destinationDirectory);
            Path destination = destinationDirectory.resolve(source.getFileName());
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(destination);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    private Path findReport(@Nonnull Path directory, @Nonnull String reportId) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(reportId))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private static String fileName(@Nonnull ManualReportEnvelope envelope) {
        return envelope.submittedAtUtc()
                .replace(':', '-')
                .replace('.', '-')
                + "-"
                + envelope.reportId()
                + ".json";
    }

    @Nullable
    private static String readString(@Nonnull Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void atomicWrite(@Nonnull Path path, @Nonnull String content) throws java.io.IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record PendingReport(@Nonnull Path path, @Nonnull String payload) {
    }
}
