package com.alechilles.alecstelemetry.report;

import javax.annotation.Nonnull;

/**
 * Manual report attachment metadata.
 */
public final class ManualReportAttachment {

    private ManualReportAttachment() {
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
