package com.alechilles.alecstelemetry.report;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable player-submitted issue or suggestion report.
 */
public record ManualReportEnvelope(int schemaVersion,
                                   @Nonnull String eventType,
                                   @Nonnull String reportId,
                                   @Nonnull String followUpTokenHash,
                                   @Nonnull String reportKind,
                                   @Nonnull String projectId,
                                   @Nonnull String projectDisplayName,
                                   @Nonnull String submittedAtUtc,
                                   @Nonnull String source,
                                   @Nonnull String sessionId,
                                   @Nonnull String serverId,
                                   @Nonnull String pluginIdentifier,
                                   @Nonnull String pluginVersion,
                                   @Nonnull String title,
                                   @Nonnull String description,
                                   @Nonnull Contact contact,
                                   @Nonnull Map<String, Object> formValues,
                                   @Nonnull List<ManualReportAttachment.Manifest> attachmentManifests,
                                   @Nonnull ManualReportContextSnapshot context,
                                   @Nonnull CrashReportEnvelope.EnvironmentSnapshot environment,
                                   @Nonnull CrashReportEnvelope.RuntimeMetadata runtime) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "manual_report";

    @Nonnull
    public static CreateResult create(@Nonnull TelemetryProjectRegistration project,
                                      @Nonnull TelemetryRuntimeSettings settings,
                                      @Nonnull ManualReportSubmission submission,
                                      @Nonnull ManualReportContextSnapshot context,
                                      @Nonnull List<ManualReportAttachment.Manifest> attachments,
                                      @Nonnull CrashReportEnvelope.EnvironmentSnapshot environment,
                                      @Nonnull CrashReportEnvelope.RuntimeMetadata runtime) {
        ArrayList<String> errors = new ArrayList<>();
        if (!settings.manualReports().enabled()) {
            errors.add("manual_reports_disabled");
        }

        TelemetryProjectDescriptor.ManualReportOptions reportOptions = project.descriptor().reports();
        if (!reportOptions.enabled()) {
            errors.add("project_reports_disabled");
        }

        TelemetryProjectDescriptor.ManualReportForm form = formFor(reportOptions, submission.kind());
        if (!form.enabled()) {
            errors.add("report_kind_disabled:" + submission.kind().key());
        }

        String title = truncate(normalizeNonBlank(submission.title(), ""), 120);
        if (title.isBlank()) {
            errors.add("title_required");
        }
        String description = truncate(normalizeNonBlank(submission.description(), ""), 4000);
        if (description.isBlank()) {
            errors.add("description_required");
        }

        Map<String, Object> formValues = sanitizeFormValues(form, submission.formValues(), errors);
        if (!errors.isEmpty()) {
            return new CreateResult(null, List.copyOf(errors));
        }

        String followUpToken = UUID.randomUUID().toString();
        ManualReportEnvelope envelope = new ManualReportEnvelope(
                SCHEMA_VERSION,
                EVENT_TYPE,
                UUID.randomUUID().toString(),
                shortHash(followUpToken),
                submission.kind().key(),
                normalizeNonBlank(project.projectId(), "unknown-project"),
                normalizeNonBlank(project.displayName(), project.projectId()),
                Instant.now().toString(),
                "player_ui",
                UUID.randomUUID().toString(),
                "unknown-server",
                normalizeNonBlank(project.pluginIdentifier(), "unknown"),
                normalizeNonBlank(project.pluginVersion(), "unknown"),
                title,
                description,
                contact(reportOptions, settings, submission.contact()),
                formValues,
                List.copyOf(attachments == null ? List.of() : attachments),
                context,
                environment.normalize(),
                runtime.normalize()
        );
        return new CreateResult(envelope, List.of());
    }

    @Nonnull
    public String toJson() {
        return GSON.toJson(this) + System.lineSeparator();
    }

    @Nonnull
    private static TelemetryProjectDescriptor.ManualReportForm formFor(@Nonnull TelemetryProjectDescriptor.ManualReportOptions options,
                                                                       @Nonnull ManualReportKind kind) {
        return kind == ManualReportKind.SUGGESTION ? options.suggestion() : options.issue();
    }

    @Nonnull
    private static Map<String, Object> sanitizeFormValues(@Nonnull TelemetryProjectDescriptor.ManualReportForm form,
                                                          @Nullable Map<String, Object> rawValues,
                                                          @Nonnull List<String> errors) {
        if (form.fields().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        Map<String, Object> safeValues = rawValues == null ? Map.of() : rawValues;
        for (TelemetryProjectDescriptor.ManualReportField field : form.fields()) {
            Object sanitizedValue = sanitizeFieldValue(field, safeValues.get(field.key()));
            if (sanitizedValue == null) {
                if (field.required()) {
                    Object rawValue = safeValues.get(field.key());
                    errors.add(rawValue == null ? "required_field:" + field.key() : "invalid_field:" + field.key());
                }
                continue;
            }
            sanitized.put(field.key(), sanitizedValue);
        }
        return Map.copyOf(sanitized);
    }

    @Nullable
    private static Object sanitizeFieldValue(@Nonnull TelemetryProjectDescriptor.ManualReportField field,
                                             @Nullable Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (field.type()) {
            case BOOLEAN -> rawValue instanceof Boolean ? rawValue : null;
            case NUMBER -> sanitizeNumber(field, rawValue);
            case ENUM -> sanitizeEnum(field, rawValue);
            case TEXT, STRING -> sanitizeString(field, rawValue);
        };
    }

    @Nullable
    private static Object sanitizeNumber(@Nonnull TelemetryProjectDescriptor.ManualReportField field,
                                         @Nonnull Object rawValue) {
        if (!(rawValue instanceof Number number)) {
            return null;
        }
        double value = number.doubleValue();
        if (field.min() != null && value < field.min()) {
            return null;
        }
        if (field.max() != null && value > field.max()) {
            return null;
        }
        return number;
    }

    @Nullable
    private static String sanitizeEnum(@Nonnull TelemetryProjectDescriptor.ManualReportField field,
                                       @Nonnull Object rawValue) {
        String normalized = sanitizeString(field, rawValue);
        if (normalized == null) {
            return null;
        }
        for (String value : field.values()) {
            if (value.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String sanitizeString(@Nonnull TelemetryProjectDescriptor.ManualReportField field,
                                         @Nonnull Object rawValue) {
        if (!(rawValue instanceof CharSequence text)) {
            return null;
        }
        String normalized = normalizeNullable(text.toString());
        if (normalized == null) {
            return null;
        }
        return truncate(normalized, field.maxLength());
    }

    @Nonnull
    private static Contact contact(@Nonnull TelemetryProjectDescriptor.ManualReportOptions reportOptions,
                                   @Nonnull TelemetryRuntimeSettings settings,
                                   @Nullable String rawContact) {
        String normalized = normalizeNullable(rawContact);
        if (normalized == null || !settings.manualReports().allowContact() || !reportOptions.contact().enabled()) {
            return new Contact(false, null);
        }
        return new Contact(true, truncate(normalized, reportOptions.contact().maxLength()));
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

    @Nonnull
    private static String truncate(@Nonnull String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Nonnull
    private static String shortHash(@Nonnull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(24);
            for (int i = 0; i < hash.length && out.length() < 24; i++) {
                out.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Contact(boolean provided, @Nullable String value) {
    }

    public record CreateResult(@Nullable ManualReportEnvelope envelope,
                               @Nonnull List<String> validationErrors) {
        public boolean accepted() {
            return envelope != null && validationErrors.isEmpty();
        }
    }
}
