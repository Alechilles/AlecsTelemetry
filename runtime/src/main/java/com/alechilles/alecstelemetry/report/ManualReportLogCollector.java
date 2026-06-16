package com.alechilles.alecstelemetry.report;

import com.alechilles.alecstelemetry.runtime.TelemetryDataPaths;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort server log collector for player-approved report attachments.
 */
public final class ManualReportLogCollector {

    @Nonnull
    public Optional<ManualReportAttachment> collectCurrentServerLog(@Nonnull TelemetryDataPaths paths, int maxBytes) {
        return collect(firstExisting(candidateCurrentLogs(paths)), "current_server_log", "current-server-log.txt", maxBytes);
    }

    @Nonnull
    public Optional<ManualReportAttachment> collectPreviousServerLog(@Nonnull TelemetryDataPaths paths, int maxBytes) {
        return collect(firstExisting(candidatePreviousLogs(paths)), "previous_server_log", "previous-server-log.txt", maxBytes);
    }

    @Nonnull
    private static Optional<ManualReportAttachment> collect(@Nullable Path path,
                                                            @Nonnull String kind,
                                                            @Nonnull String fileName,
                                                            int maxBytes) {
        if (path == null || maxBytes <= 0) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            int start = Math.max(0, bytes.length - maxBytes);
            String raw = new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
            return Optional.of(ManualReportAttachment.text(kind, fileName, ManualReportRedactor.redact(raw)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Nonnull
    private static List<Path> candidateCurrentLogs(@Nonnull TelemetryDataPaths paths) {
        Path root = paths.runtimeRoot();
        Path parent = root.getParent() == null ? root : root.getParent();
        return List.of(
                root.resolve("latest.log"),
                root.resolve("server.log"),
                root.resolve("logs").resolve("latest.log"),
                root.resolve("logs").resolve("server.log"),
                parent.resolve("latest.log"),
                parent.resolve("server.log"),
                parent.resolve("logs").resolve("latest.log"),
                parent.resolve("logs").resolve("server.log")
        );
    }

    @Nonnull
    private static List<Path> candidatePreviousLogs(@Nonnull TelemetryDataPaths paths) {
        Path root = paths.runtimeRoot();
        Path parent = root.getParent() == null ? root : root.getParent();
        return List.of(
                root.resolve("previous.log"),
                root.resolve("latest.log.1"),
                root.resolve("logs").resolve("previous.log"),
                root.resolve("logs").resolve("latest.log.1"),
                parent.resolve("previous.log"),
                parent.resolve("latest.log.1"),
                parent.resolve("logs").resolve("previous.log"),
                parent.resolve("logs").resolve("latest.log.1")
        );
    }

    @Nullable
    private static Path firstExisting(@Nonnull List<Path> candidates) {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
