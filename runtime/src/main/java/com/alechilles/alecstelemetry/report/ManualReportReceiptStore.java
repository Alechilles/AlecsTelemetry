package com.alechilles.alecstelemetry.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Local player receipt history for submitted manual reports.
 */
public final class ManualReportReceiptStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;

    @Nonnull
    public List<Receipt> loadAll(@Nonnull Path file) {
        ReceiptDocument document = read(file);
        if (document.receipts == null || document.receipts.isEmpty()) {
            return List.of();
        }
        ArrayList<Receipt> receipts = new ArrayList<>();
        for (Receipt receipt : document.receipts) {
            if (receipt != null && receipt.reportId() != null && !receipt.reportId().isBlank()) {
                receipts.add(receipt.normalize());
            }
        }
        return List.copyOf(receipts);
    }

    public boolean saveReceipt(@Nonnull Path file, @Nonnull Receipt receipt) {
        Receipt normalized = receipt.normalize();
        LinkedHashMap<String, Receipt> byReportId = new LinkedHashMap<>();
        for (Receipt existing : loadAll(file)) {
            byReportId.put(existing.reportId(), existing);
        }
        byReportId.put(normalized.reportId(), normalized);

        ReceiptDocument document = new ReceiptDocument();
        document.version = CURRENT_VERSION;
        document.receipts = new ArrayList<>(byReportId.values());
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nonnull
    private ReceiptDocument read(@Nonnull Path file) {
        if (!Files.isRegularFile(file)) {
            return new ReceiptDocument();
        }
        try {
            ReceiptDocument document = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ReceiptDocument.class);
            return document == null ? new ReceiptDocument() : document;
        } catch (Exception ignored) {
            return new ReceiptDocument();
        }
    }

    public record Receipt(@Nonnull String reportId,
                          @Nonnull String projectId,
                          @Nonnull String reportKind,
                          @Nonnull String title,
                          @Nonnull String submittedAtUtc,
                          @Nonnull String followUpToken,
                          @Nonnull String lastKnownStatus,
                          @Nullable String lastCheckedAtUtc) {

        @Nonnull
        Receipt normalize() {
            return new Receipt(
                    normalizeNonBlank(reportId, "unknown-report"),
                    normalizeNonBlank(projectId, "unknown-project"),
                    normalizeNonBlank(reportKind, "issue"),
                    normalizeNonBlank(title, "<untitled>"),
                    normalizeNonBlank(submittedAtUtc, ""),
                    normalizeNonBlank(followUpToken, ""),
                    normalizeNonBlank(lastKnownStatus, "queued"),
                    normalizeNullable(lastCheckedAtUtc)
            );
        }
    }

    private static final class ReceiptDocument {
        private int version = CURRENT_VERSION;
        private List<Receipt> receipts = List.of();
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static String normalizeNonBlank(@Nullable String value, @Nonnull String fallback) {
        String normalized = normalizeNullable(value);
        return normalized == null ? fallback : normalized;
    }
}
