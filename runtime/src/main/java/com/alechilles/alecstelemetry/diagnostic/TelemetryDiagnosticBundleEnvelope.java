package com.alechilles.alecstelemetry.diagnostic;

import com.alechilles.alecstelemetry.api.TelemetryDiagnosticAttachment;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticBundle;
import com.alechilles.alecstelemetry.api.TelemetryDiagnosticDisposition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Serialized project diagnostic sent through the normal event ingest route.
 */
public record TelemetryDiagnosticBundleEnvelope(int schemaVersion,
                                                @Nonnull String eventType,
                                                @Nonnull String diagnosticId,
                                                @Nonnull String projectId,
                                                @Nonnull String capturedAtUtc,
                                                @Nonnull String source,
                                                @Nonnull String diagnosticKind,
                                                @Nonnull String title,
                                                @Nonnull String summary,
                                                @Nonnull String severity,
                                                @Nonnull String pluginIdentifier,
                                                @Nonnull String pluginVersion,
                                                @Nullable String sessionHash,
                                                @Nullable String serverHash,
                                                @Nonnull TelemetryDiagnosticDisposition disposition,
                                                @Nonnull Map<String, Object> attributes,
                                                @Nonnull List<TelemetryDiagnosticAttachment> attachments) {

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "diagnostic_bundle";

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    @Nonnull
    public static TelemetryDiagnosticBundleEnvelope create(@Nonnull String projectId,
                                                           @Nonnull String pluginIdentifier,
                                                           @Nonnull String pluginVersion,
                                                           @Nullable String sessionId,
                                                           @Nullable String serverId,
                                                           @Nonnull TelemetryDiagnosticBundle bundle) {
        return new TelemetryDiagnosticBundleEnvelope(
                SCHEMA_VERSION,
                EVENT_TYPE,
                bundle.diagnosticId(),
                projectId,
                bundle.capturedAtUtc(),
                bundle.source(),
                bundle.diagnosticKind(),
                bundle.title(),
                bundle.summary(),
                bundle.severity(),
                pluginIdentifier,
                pluginVersion,
                scopedHash(projectId, sessionId),
                scopedHash(projectId, serverId),
                bundle.disposition(),
                bundle.attributes(),
                bundle.attachments()
        );
    }

    @Nonnull
    public String toJson() {
        return GSON.toJson(this) + System.lineSeparator();
    }

    @Nullable
    private static String scopedHash(@Nonnull String projectId,
                                     @Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (projectId + ':' + value).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
