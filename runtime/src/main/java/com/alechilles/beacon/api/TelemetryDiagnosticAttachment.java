package com.alechilles.beacon.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One opaque attachment in a diagnostic bundle.
 */
public record TelemetryDiagnosticAttachment(@Nonnull String attachmentId,
                                            @Nonnull String kind,
                                            @Nonnull String fileName,
                                            @Nonnull String contentType,
                                            @Nonnull String contentEncoding,
                                            int byteCount,
                                            @Nonnull String sha256,
                                            @Nonnull String content) {

    public TelemetryDiagnosticAttachment {
        attachmentId = normalize(attachmentId, UUID.randomUUID().toString());
        kind = normalize(kind, "diagnostic_attachment");
        fileName = normalize(fileName, "diagnostic.dat");
        contentType = normalize(contentType, "application/octet-stream");
        contentEncoding = normalize(contentEncoding, "identity").toLowerCase(Locale.ROOT);
        byteCount = Math.max(0, byteCount);
        sha256 = normalize(sha256, "unknown").toLowerCase(Locale.ROOT);
        content = Objects.requireNonNullElse(content, "");
    }

    @Nonnull
    public static TelemetryDiagnosticAttachment binary(@Nonnull String attachmentId,
                                                       @Nonnull String kind,
                                                       @Nonnull String fileName,
                                                       @Nonnull String contentType,
                                                       @Nonnull byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content.clone();
        return new TelemetryDiagnosticAttachment(
                attachmentId,
                kind,
                fileName,
                contentType,
                "base64",
                bytes.length,
                sha256(bytes),
                Base64.getEncoder().encodeToString(bytes)
        );
    }

    @Nonnull
    public static TelemetryDiagnosticAttachment text(@Nonnull String attachmentId,
                                                     @Nonnull String kind,
                                                     @Nonnull String fileName,
                                                     @Nonnull String contentType,
                                                     @Nonnull String content) {
        String value = Objects.requireNonNullElse(content, "");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new TelemetryDiagnosticAttachment(
                attachmentId,
                kind,
                fileName,
                contentType,
                "identity",
                bytes.length,
                sha256(bytes),
                value
        );
    }

    @Nonnull
    private static String sha256(@Nonnull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    @Nonnull
    private static String normalize(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
