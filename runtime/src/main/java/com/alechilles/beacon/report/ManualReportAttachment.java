package com.alechilles.beacon.report;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * Manual report attachment content plus metadata.
 */
public record ManualReportAttachment(@Nonnull Manifest manifest,
                                     @Nonnull String content) {

    public ManualReportAttachment {
        manifest = manifest == null
                ? new Manifest("unknown-attachment", "unknown", "attachment.dat", "application/octet-stream", 0, "unknown")
                : manifest;
        content = content == null ? "" : content;
    }

    @Nonnull
    public static ManualReportAttachment text(@Nonnull String kind,
                                              @Nonnull String fileName,
                                              @Nonnull String content) {
        String safeContent = content == null ? "" : content;
        byte[] bytes = safeContent.getBytes(StandardCharsets.UTF_8);
        return new ManualReportAttachment(
                new Manifest(
                        UUID.randomUUID().toString(),
                        kind,
                        fileName,
                        "text/plain",
                        bytes.length,
                        sha256(bytes)
                ),
                safeContent
        );
    }

    @Nonnull
    private static String sha256(@Nonnull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Manifest(@Nonnull String attachmentId,
                           @Nonnull String kind,
                           @Nonnull String fileName,
                           @Nonnull String contentType,
                           int byteCount,
                           @Nonnull String sha256) {

        public Manifest {
            attachmentId = normalize(attachmentId, "unknown-attachment");
            kind = normalize(kind, "unknown");
            fileName = normalize(fileName, "attachment.dat");
            contentType = normalize(contentType, "application/octet-stream");
            byteCount = Math.max(0, byteCount);
            sha256 = normalize(sha256, "unknown");
        }

        @Nonnull
        private static String normalize(String value, @Nonnull String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
