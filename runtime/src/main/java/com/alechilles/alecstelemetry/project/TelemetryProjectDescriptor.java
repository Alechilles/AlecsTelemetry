package com.alechilles.alecstelemetry.project;

import com.alechilles.alecstelemetry.crash.CrashReportClient;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Declarative project registration loaded from `Server/Telemetry/project.json`.
 */
public record TelemetryProjectDescriptor(int schemaVersion,
                                         @Nonnull String projectId,
                                         @Nullable String projectVersion,
                                         @Nonnull String displayName,
                                         @Nonnull String runtimeMode,
                                         @Nonnull List<String> ownerPluginIdentifiers,
                                         @Nonnull List<String> packagePrefixes,
                                         @Nonnull CaptureOptions capture,
                                         @Nonnull EventOptions events,
                                         @Nonnull PerformanceOptions performance,
                                         @Nonnull UsageOptions usage,
                                         @Nonnull StatsOptions stats,
                                         @Nonnull UiOptions ui,
                                         @Nonnull ManualReportOptions reports,
                                         @Nonnull Defaults defaults,
                                         @Nonnull HostedDestination hosted,
                                         @Nonnull CustomEndpoint customEndpoint) {

    public TelemetryProjectDescriptor(int schemaVersion,
                                      @Nonnull String projectId,
                                      @Nonnull String displayName,
                                      @Nonnull String runtimeMode,
                                      @Nonnull List<String> ownerPluginIdentifiers,
                                      @Nonnull List<String> packagePrefixes,
                                      @Nonnull CaptureOptions capture,
                                      @Nonnull EventOptions events,
                                      @Nonnull PerformanceOptions performance,
                                      @Nonnull UsageOptions usage,
                                      @Nonnull StatsOptions stats,
                                      @Nonnull UiOptions ui,
                                      @Nonnull ManualReportOptions reports,
                                      @Nonnull Defaults defaults,
                                      @Nonnull HostedDestination hosted,
                                      @Nonnull CustomEndpoint customEndpoint) {
        this(schemaVersion,
                projectId,
                null,
                displayName,
                runtimeMode,
                ownerPluginIdentifiers,
                packagePrefixes,
                capture,
                events,
                performance,
                usage,
                stats,
                ui,
                reports,
                defaults,
                hosted,
                customEndpoint);
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String MODE_HOSTED = "hosted";
    private static final String MODE_CUSTOM = "custom";
    public static final String RUNTIME_MODE_DEPENDENCY = "dependency";
    public static final String RUNTIME_MODE_EMBEDDED = "embedded";
    public static final String PROJECT_KEY_HEADER = "X-Telemetry-Project-Key";

    @Nonnull
    public static TelemetryProjectDescriptor fromJson(@Nonnull String rawJson, @Nullable Fallbacks fallbacks) {
        Document parsed = GSON.fromJson(rawJson, Document.class);
        Document safe = parsed == null ? new Document() : parsed;

        String fallbackProjectId = fallbacks == null ? null : fallbacks.projectId();
        String fallbackDisplayName = fallbacks == null ? null : fallbacks.displayName();
        String fallbackOwnerPluginIdentifier = fallbacks == null ? null : fallbacks.ownerPluginIdentifier();
        List<String> fallbackPackagePrefixes = fallbacks == null ? List.of() : fallbacks.packagePrefixes();

        String displayName = chooseNonBlank(safe.displayName, fallbackDisplayName, "Unknown Project");
        String projectId = chooseNonBlank(safe.projectId, fallbackProjectId, slugify(displayName));
        String projectVersion = normalizeNullable(safe.projectVersion);

        List<String> ownerPluginIdentifiers = normalizeList(safe.ownerPluginIdentifiers);
        if (ownerPluginIdentifiers.isEmpty() && fallbackOwnerPluginIdentifier != null && !fallbackOwnerPluginIdentifier.isBlank()) {
            ownerPluginIdentifiers = List.of(fallbackOwnerPluginIdentifier.trim());
        }

        CaptureDocument captureDocument = choose(safe.telemetry == null ? null : safe.telemetry.crash, safe.capture);
        boolean captureSupported = categorySupported(captureDocument, captureDocument == null ? null : captureDocument.supported);
        CaptureOptions capture = captureDocument == null
                ? new CaptureOptions(false, false, false, false, false, false)
                : new CaptureOptions(
                captureSupported,
                categoryDefaultEnabled(captureSupported, captureDocument.defaultEnabled, captureDocument.enabled),
                boolOrDefault(captureDocument.uncaughtExceptions, false),
                boolOrDefault(captureDocument.setupFailures, false),
                boolOrDefault(captureDocument.startFailures, false),
                boolOrDefault(captureDocument.exceptionalWorldRemovals, false)
        );

        String destinationMode = normalizeMode(
                safe.defaults == null ? null : safe.defaults.destinationMode,
                safe.customEndpoint == null ? null : safe.customEndpoint.url
        );

        Defaults defaults = new Defaults(
                safe.defaults == null || safe.defaults.enabled == null || safe.defaults.enabled,
                destinationMode
        );

        PerformanceDocument performanceDocument = choose(safe.telemetry == null ? null : safe.telemetry.performance, safe.performance);
        boolean performanceSupported = categorySupported(performanceDocument, performanceDocument == null ? null : performanceDocument.supported);
        PerformanceOptions performance = performanceDocument == null
                ? new PerformanceOptions(false, false, 1.0d, 100, Map.of())
                : new PerformanceOptions(
                performanceSupported,
                categoryDefaultEnabled(performanceSupported, performanceDocument.defaultEnabled, performanceDocument.enabled),
                doubleOrDefault(performanceDocument.sampleRate, 1.0d, 0.0d, 1.0d),
                intOrDefault(performanceDocument.thresholdMs, 100, 1, 60000),
                normalizeDetailRules(performanceDocument.details)
        );

        UsageDocument usageDocument = choose(safe.telemetry == null ? null : safe.telemetry.usage, safe.usage);
        boolean usageSupported = categorySupported(usageDocument, usageDocument == null ? null : usageDocument.supported);
        UsageOptions usage = usageDocument == null
                ? new UsageOptions(false, false, List.of(), Map.of())
                : new UsageOptions(
                usageSupported,
                categoryDefaultEnabled(usageSupported, usageDocument.defaultEnabled, usageDocument.enabled),
                normalizeNonBlankList(usageDocument.allowedEvents, 120),
                normalizeDetailRules(usageDocument.details)
        );

        StatsDocument statsDocument = choose(safe.telemetry == null ? null : safe.telemetry.stats, safe.stats);
        boolean statsSupported = categorySupported(statsDocument, statsDocument == null ? null : statsDocument.supported);
        StatsOptions stats = statsDocument == null
                ? new StatsOptions(false, false, List.of(), Map.of())
                : new StatsOptions(
                statsSupported,
                categoryDefaultEnabled(statsSupported, statsDocument.defaultEnabled, statsDocument.enabled),
                normalizeNonBlankList(statsDocument.allowedEvents, 120),
                normalizeDetailRules(statsDocument.details)
        );

        UiOptions ui = safe.ui == null
                ? new UiOptions(null)
                : new UiOptions(normalizeUiTexturePath(safe.ui.iconTexturePath));

        ManualReportOptions reports = normalizeManualReports(choose(safe.telemetry == null ? null : safe.telemetry.reports, safe.reports));

        EventsDocument eventsDocument = choose(safe.telemetry == null ? null : safe.telemetry.events, safe.events);
        EventOptions events = eventsDocument == null
                ? EventOptions.defaults()
                : new EventOptions(
                normalizeEventType(eventsDocument.errors),
                normalizeEventType(eventsDocument.lifecycle),
                normalizeBreadcrumbs(eventsDocument.breadcrumbs)
        );

        HostedDestination hosted = new HostedDestination(
                normalizeNullable(safe.hosted == null ? null : safe.hosted.endpoint),
                normalizeNullable(safe.hosted == null ? null : safe.hosted.eventEndpoint),
                normalizeNullable(safe.hosted == null ? null : safe.hosted.projectKey),
                normalizeHeaders(safe.hosted == null ? null : safe.hosted.headers)
        );
        CustomEndpoint customEndpoint = new CustomEndpoint(
                normalizeNullable(safe.customEndpoint == null ? null : safe.customEndpoint.url),
                normalizeNullable(safe.customEndpoint == null ? null : safe.customEndpoint.eventUrl),
                normalizeHeaders(safe.customEndpoint == null ? null : safe.customEndpoint.headers)
        );

        List<String> packagePrefixes = normalizeList(safe.packagePrefixes);
        if (packagePrefixes.isEmpty() && fallbackPackagePrefixes != null && !fallbackPackagePrefixes.isEmpty()) {
            packagePrefixes = normalizeList(fallbackPackagePrefixes);
        }

        return new TelemetryProjectDescriptor(
                safe.schemaVersion == null || safe.schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : safe.schemaVersion,
                projectId,
                projectVersion,
                displayName,
                normalizeRuntimeMode(safe.runtimeMode),
                ownerPluginIdentifiers,
                packagePrefixes,
                capture,
                events,
                performance,
                usage,
                stats,
                ui,
                reports,
                defaults,
                hosted,
                customEndpoint
        );
    }

    @Nonnull
    public String toJson() {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", schemaVersion);
        document.put("projectId", projectId);
        if (projectVersion != null && !projectVersion.isBlank()) {
            document.put("projectVersion", projectVersion);
        }
        document.put("displayName", displayName);
        document.put("runtimeMode", runtimeMode);
        document.put("ownerPluginIdentifiers", ownerPluginIdentifiers);
        document.put("packagePrefixes", packagePrefixes);
        document.put("telemetry", Map.of(
                "crash", captureDocument(capture),
                "events", Map.of(
                "errors", eventTypeDocument(events.errors()),
                "lifecycle", eventTypeDocument(events.lifecycle()),
                "breadcrumbs", Map.of(
                        "supported", events.breadcrumbs().supported(),
                        "defaultEnabled", events.breadcrumbs().enabled(),
                        "automatic", events.breadcrumbs().automatic()
                )
        ),
                "performance", Map.of(
                "supported", performance.supported(),
                "defaultEnabled", performance.enabled(),
                "sampleRate", performance.sampleRate(),
                "thresholdMs", performance.thresholdMs(),
                "details", detailRulesDocument(performance.details())
        ),
                "usage", Map.of(
                "supported", usage.supported(),
                "defaultEnabled", usage.enabled(),
                "allowedEvents", usage.allowedEvents(),
                "details", detailRulesDocument(usage.details())
        ),
                "stats", Map.of(
                "supported", stats.supported(),
                "defaultEnabled", stats.enabled(),
                "allowedEvents", stats.allowedEvents(),
                "details", detailRulesDocument(stats.details())
        ),
                "reports", reportsDocument(reports)
        ));
        LinkedHashMap<String, Object> uiDocument = new LinkedHashMap<>();
        putDocumentValue(uiDocument, "iconTexturePath", ui.iconTexturePath());
        document.put("ui", uiDocument);
        document.put("defaults", Map.of(
                "enabled", defaults.enabled(),
                "destinationMode", defaults.destinationMode()
        ));
        LinkedHashMap<String, Object> hostedDocument = new LinkedHashMap<>();
        putDocumentValue(hostedDocument, "endpoint", hosted.endpoint());
        putDocumentValue(hostedDocument, "eventEndpoint", hosted.eventEndpoint());
        putDocumentValue(hostedDocument, "projectKey", hosted.projectKey());
        hostedDocument.put("headers", hosted.headers());
        document.put("hosted", hostedDocument);
        LinkedHashMap<String, Object> customDocument = new LinkedHashMap<>();
        putDocumentValue(customDocument, "url", customEndpoint.url());
        putDocumentValue(customDocument, "eventUrl", customEndpoint.eventUrl());
        customDocument.put("headers", customEndpoint.headers());
        document.put("customEndpoint", customDocument);
        return GSON.toJson(document);
    }

    @Nonnull
    public String canonicalHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toJson().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(Character.forDigit((part >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(part & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Nonnull
    public TelemetryProjectDescriptor statsOnly() {
        return new TelemetryProjectDescriptor(
                schemaVersion,
                projectId,
                projectVersion,
                displayName,
                runtimeMode,
                ownerPluginIdentifiers,
                packagePrefixes,
                new CaptureOptions(false, false, false, false, false, false),
                EventOptions.defaults(),
                new PerformanceOptions(false, false, 1.0d, 100, Map.of()),
                new UsageOptions(false, false, List.of(), Map.of()),
                stats,
                ui,
                normalizeManualReports(null),
                defaults,
                hosted,
                customEndpoint
        );
    }

    @Nonnull
    private static Map<String, Object> eventTypeDocument(@Nonnull EventTypeOptions options) {
        return Map.of(
                "supported", options.supported(),
                "defaultEnabled", options.enabled(),
                "details", detailRulesDocument(options.details())
        );
    }

    @Nonnull
    private static Map<String, Object> captureDocument(@Nonnull CaptureOptions options) {
        return Map.of(
                "supported", options.supported(),
                "defaultEnabled", options.enabled(),
                "uncaughtExceptions", options.uncaughtExceptions(),
                "setupFailures", options.setupFailures(),
                "startFailures", options.startFailures(),
                "exceptionalWorldRemovals", options.exceptionalWorldRemovals()
        );
    }

    @Nonnull
    private static Map<String, Object> reportsDocument(@Nonnull ManualReportOptions options) {
        return Map.of(
                "supported", options.supported(),
                "defaultEnabled", options.enabled(),
                "issue", manualReportFormDocument(options.issue()),
                "suggestion", manualReportFormDocument(options.suggestion()),
                "attachments", Map.of(
                        "currentServerLog", options.attachments().currentServerLog(),
                        "previousServerLog", options.attachments().previousServerLog(),
                        "maxBytes", options.attachments().maxBytes()
                ),
                "contact", Map.of(
                        "enabled", options.contact().enabled(),
                        "maxLength", options.contact().maxLength()
                ),
                "resolutionUpdates", Map.of("enabled", options.resolutionUpdates().enabled())
        );
    }

    @Nonnull
    private static Map<String, Object> manualReportFormDocument(@Nonnull ManualReportForm form) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("enabled", form.enabled());
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        for (ManualReportField field : form.fields()) {
            LinkedHashMap<String, Object> fieldDocument = new LinkedHashMap<>();
            fieldDocument.put("type", field.type().key());
            fieldDocument.put("label", field.label());
            fieldDocument.put("required", field.required());
            if (!field.values().isEmpty()) {
                fieldDocument.put("values", field.values());
            }
            fieldDocument.put("maxLength", field.maxLength());
            putDocumentValue(fieldDocument, "min", field.min());
            putDocumentValue(fieldDocument, "max", field.max());
            fields.put(field.key(), fieldDocument);
        }
        document.put("fields", fields);
        return Map.copyOf(document);
    }

    @Nonnull
    private static Map<String, Object> detailRulesDocument(@Nonnull Map<String, DetailRules> rules) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        for (Map.Entry<String, DetailRules> ruleEntry : rules.entrySet()) {
            LinkedHashMap<String, Object> ruleDocument = new LinkedHashMap<>();
            LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<String, DetailFieldRule> fieldEntry : ruleEntry.getValue().allowedFields().entrySet()) {
                fields.put(fieldEntry.getKey(), Map.of(
                        "type", fieldEntry.getValue().type(),
                        "maxLength", fieldEntry.getValue().maxLength()
                ));
            }
            ruleDocument.put("allowedFields", fields);
            document.put(ruleEntry.getKey(), ruleDocument);
        }
        return Map.copyOf(document);
    }

    private static void putDocumentValue(@Nonnull Map<String, Object> document,
                                         @Nonnull String key,
                                         @Nullable Object value) {
        if (value != null) {
            document.put(key, value);
        }
    }
    @Nullable
    public CrashReportClient.DeliveryTarget resolveDeliveryTarget(@Nonnull TelemetryRuntimeSettings settings) {
        if (!defaults.enabled()) {
            return null;
        }
        if (MODE_CUSTOM.equals(defaults.destinationMode())) {
            if (customEndpoint.url() == null || customEndpoint.url().isBlank()) {
                return null;
            }
            return new CrashReportClient.DeliveryTarget(customEndpoint.url(), customEndpoint.headers());
        }

        String endpoint = hosted.endpoint() == null || hosted.endpoint().isBlank()
                ? settings.hostedIngestEndpoint()
                : hosted.endpoint();
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(hosted.headers());
        if (hosted.projectKey() != null && !hosted.projectKey().isBlank()) {
            headers.put(PROJECT_KEY_HEADER, hosted.projectKey());
        }
        return new CrashReportClient.DeliveryTarget(endpoint, headers);
    }

    private static boolean boolOrDefault(@Nullable Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static boolean categorySupported(@Nullable Object document, @Nullable Boolean supported) {
        return document != null && boolOrDefault(supported, true);
    }

    private static boolean categoryDefaultEnabled(boolean supported,
                                                  @Nullable Boolean defaultEnabled,
                                                  @Nullable Boolean legacyEnabled) {
        if (!supported) {
            return false;
        }
        if (defaultEnabled != null) {
            return defaultEnabled;
        }
        return boolOrDefault(legacyEnabled, true);
    }

    @Nullable
    private static <T> T choose(@Nullable T preferred, @Nullable T fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static int intOrDefault(@Nullable Integer value, int fallback, int min, int max) {
        int safe = value == null ? fallback : value;
        return Math.max(min, Math.min(max, safe));
    }

    private static double doubleOrDefault(@Nullable Double value, double fallback, double min, double max) {
        double safe = value == null ? fallback : value;
        return Math.max(min, Math.min(max, safe));
    }

    @Nonnull
    private static String normalizeMode(@Nullable String rawMode, @Nullable String customUrl) {
        String normalized = normalizeNullable(rawMode);
        if (normalized == null) {
            return customUrl == null || customUrl.isBlank() ? MODE_HOSTED : MODE_CUSTOM;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return MODE_CUSTOM.equals(lower) ? MODE_CUSTOM : MODE_HOSTED;
    }

    @Nonnull
    private static String normalizeRuntimeMode(@Nullable String rawRuntimeMode) {
        String normalized = normalizeNullable(rawRuntimeMode);
        if (normalized == null) {
            return RUNTIME_MODE_DEPENDENCY;
        }
        return RUNTIME_MODE_EMBEDDED.equalsIgnoreCase(normalized)
                ? RUNTIME_MODE_EMBEDDED
                : RUNTIME_MODE_DEPENDENCY;
    }

    @Nonnull
    private static List<String> normalizeList(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            String safe = normalizeNullable(value);
            if (safe != null) {
                normalized.putIfAbsent(safe.toLowerCase(Locale.ROOT), safe);
            }
        }
        return List.copyOf(new ArrayList<>(normalized.values()));
    }

    @Nonnull
    private static List<String> normalizeNonBlankList(@Nullable List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            String safe = normalizeNullable(value);
            if (safe != null) {
                String trimmed = safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
                normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(normalized.values()));
    }

    @Nonnull
    private static Map<String, String> normalizeHeaders(@Nullable Map<String, String> rawHeaders) {
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawHeaders.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = normalizeNullable(entry.getKey());
            String value = normalizeNullable(entry.getValue());
            if (key != null && value != null) {
                normalized.put(key, value);
            }
        }
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static Map<String, DetailRules> normalizeDetailRules(@Nullable Map<String, DetailRulesDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, DetailRules> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, DetailRulesDocument> entry : documents.entrySet()) {
            if (entry == null) {
                continue;
            }
            String eventName = normalizeNullable(entry.getKey());
            DetailRules rules = normalizeDetailRule(entry.getValue());
            if (eventName != null && !rules.allowedFields().isEmpty()) {
                normalized.putIfAbsent(eventName.toLowerCase(Locale.ROOT), rules);
            }
        }
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static DetailRules normalizeDetailRule(@Nullable DetailRulesDocument document) {
        if (document == null || document.allowedFields == null || document.allowedFields.isEmpty()) {
            return new DetailRules(Map.of());
        }
        LinkedHashMap<String, DetailFieldRule> fields = new LinkedHashMap<>();
        for (Map.Entry<String, DetailFieldDocument> entry : document.allowedFields.entrySet()) {
            if (fields.size() >= 20 || entry == null) {
                break;
            }
            String key = normalizeNullable(entry.getKey());
            DetailFieldRule field = normalizeDetailField(entry.getValue());
            if (key != null && field != null) {
                fields.putIfAbsent(key, field);
            }
        }
        return new DetailRules(Map.copyOf(fields));
    }

    @Nullable
    private static DetailFieldRule normalizeDetailField(@Nullable DetailFieldDocument document) {
        if (document == null) {
            return null;
        }
        String type = normalizeNullable(document.type);
        if (type == null) {
            return null;
        }
        String normalizedType = type.toLowerCase(Locale.ROOT);
        if (!"string".equals(normalizedType)
                && !"number".equals(normalizedType)
                && !"boolean".equals(normalizedType)
                && !"enum".equals(normalizedType)) {
            return null;
        }
        List<String> values = "enum".equals(normalizedType)
                ? normalizeNonBlankList(document.values, 120)
                : List.of();
        if ("enum".equals(normalizedType) && values.isEmpty()) {
            return null;
        }
        return new DetailFieldRule(
                normalizedType,
                values,
                intOrDefault(document.maxLength, 120, 1, 500)
        );
    }

    @Nonnull
    private static ManualReportOptions normalizeManualReports(@Nullable ReportsDocument document) {
        boolean supported = categorySupported(document, document == null ? null : document.supported);
        boolean enabled = categoryDefaultEnabled(
                supported,
                document == null ? null : document.defaultEnabled,
                document == null ? null : document.enabled
        );
        return new ManualReportOptions(
                supported,
                enabled,
                normalizeManualReportForm(document == null ? null : document.issue, enabled),
                normalizeManualReportForm(document == null ? null : document.suggestion, enabled),
                normalizeManualReportAttachments(document == null ? null : document.attachments),
                normalizeManualReportContact(document == null ? null : document.contact),
                normalizeManualReportResolutionUpdates(document == null ? null : document.resolutionUpdates)
        );
    }

    @Nonnull
    private static EventTypeOptions normalizeEventType(@Nullable EventTypeDocument document) {
        boolean supported = categorySupported(document, document == null ? null : document.supported);
        return new EventTypeOptions(
                supported,
                categoryDefaultEnabled(
                        supported,
                        document == null ? null : document.defaultEnabled,
                        document == null ? null : document.enabled
                ),
                normalizeDetailRules(document == null ? null : document.details)
        );
    }

    @Nonnull
    private static BreadcrumbOptions normalizeBreadcrumbs(@Nullable BreadcrumbsDocument document) {
        boolean supported = categorySupported(document, document == null ? null : document.supported);
        return new BreadcrumbOptions(
                supported,
                categoryDefaultEnabled(
                        supported,
                        document == null ? null : document.defaultEnabled,
                        document == null ? null : document.enabled
                ),
                document != null && boolOrDefault(document.automatic, false)
        );
    }

    @Nonnull
    private static ManualReportForm normalizeManualReportForm(@Nullable ManualReportFormDocument document,
                                                              boolean parentEnabled) {
        boolean enabled = document == null ? parentEnabled : boolOrDefault(document.enabled, parentEnabled);
        return new ManualReportForm(enabled, normalizeManualReportFields(document == null ? null : document.fields));
    }

    @Nonnull
    private static List<ManualReportField> normalizeManualReportFields(@Nullable Map<String, ManualReportFieldDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        ArrayList<ManualReportField> fields = new ArrayList<>();
        for (Map.Entry<String, ManualReportFieldDocument> entry : documents.entrySet()) {
            if (fields.size() >= 20 || entry == null) {
                break;
            }
            String key = normalizeNullable(entry.getKey());
            ManualReportField field = normalizeManualReportField(key, entry.getValue());
            if (field != null) {
                fields.add(field);
            }
        }
        return List.copyOf(fields);
    }

    @Nullable
    private static ManualReportField normalizeManualReportField(@Nullable String key,
                                                                @Nullable ManualReportFieldDocument document) {
        if (key == null || document == null) {
            return null;
        }
        ManualReportFieldType type = ManualReportFieldType.from(document.type);
        if (type == null) {
            return null;
        }
        List<String> values = type == ManualReportFieldType.ENUM
                ? normalizeNonBlankList(document.values, 80)
                : List.of();
        if (type == ManualReportFieldType.ENUM && values.isEmpty()) {
            return null;
        }
        return new ManualReportField(
                key.length() <= 80 ? key : key.substring(0, 80),
                type,
                truncate(chooseNonBlank(document.label, key, key), 80),
                boolOrDefault(document.required, false),
                values.size() <= 20 ? values : values.subList(0, 20),
                manualReportMaxLength(type, document.maxLength),
                document.min,
                document.max
        );
    }

    private static int manualReportMaxLength(@Nonnull ManualReportFieldType type, @Nullable Integer rawMaxLength) {
        return switch (type) {
            case TEXT -> intOrDefault(rawMaxLength, 1000, 1, 4000);
            case STRING -> intOrDefault(rawMaxLength, 120, 1, 500);
            default -> intOrDefault(rawMaxLength, 120, 1, 500);
        };
    }

    @Nonnull
    private static ManualReportAttachmentOptions normalizeManualReportAttachments(@Nullable ManualReportAttachmentDocument document) {
        return new ManualReportAttachmentOptions(
                document != null && boolOrDefault(document.currentServerLog, false),
                document != null && boolOrDefault(document.previousServerLog, false),
                intOrDefault(document == null ? null : document.maxBytes, 262144, 0, 1048576)
        );
    }

    @Nonnull
    private static ManualReportContactOptions normalizeManualReportContact(@Nullable ManualReportContactDocument document) {
        return new ManualReportContactOptions(
                document != null && boolOrDefault(document.enabled, false),
                intOrDefault(document == null ? null : document.maxLength, 160, 1, 240)
        );
    }

    @Nonnull
    private static ManualReportResolutionOptions normalizeManualReportResolutionUpdates(@Nullable ManualReportResolutionDocument document) {
        return new ManualReportResolutionOptions(document != null && boolOrDefault(document.enabled, false));
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nullable
    private static String normalizeUiTexturePath(@Nullable String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null
                || normalized.startsWith("/")
                || normalized.startsWith("\\")
                || normalized.contains("\\")
                || normalized.contains(":")
                || normalized.contains("//")) {
            return null;
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return normalized;
    }

    @Nonnull
    private static Map<String, Object> sanitizeDetailMap(@Nonnull Map<String, DetailRules> rulesByEventName,
                                                         @Nonnull String eventName,
                                                         @Nullable Map<String, Object> rawDetails) {
        if (rulesByEventName.isEmpty() || rawDetails == null || rawDetails.isEmpty()) {
            return Map.of();
        }
        String normalizedEventName = normalizeNullable(eventName);
        if (normalizedEventName == null) {
            return Map.of();
        }
        DetailRules rules = rulesByEventName.get(normalizedEventName.toLowerCase(Locale.ROOT));
        if (rules == null || rules.allowedFields().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, DetailFieldRule> field : rules.allowedFields().entrySet()) {
            if (sanitized.size() >= 20) {
                break;
            }
            Object value = sanitizeDetailValue(field.getValue(), rawDetails.get(field.getKey()));
            if (value != null) {
                sanitized.put(field.getKey(), value);
            }
        }
        return Map.copyOf(sanitized);
    }

    @Nullable
    private static Object sanitizeDetailValue(@Nonnull DetailFieldRule rule, @Nullable Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rule.type()) {
            case "number" -> rawValue instanceof Number ? rawValue : null;
            case "boolean" -> rawValue instanceof Boolean ? rawValue : null;
            case "enum" -> sanitizeEnumValue(rule, rawValue);
            default -> sanitizeStringValue(rule, rawValue);
        };
    }

    @Nullable
    private static String sanitizeStringValue(@Nonnull DetailFieldRule rule, @Nonnull Object rawValue) {
        if (!(rawValue instanceof CharSequence text)) {
            return null;
        }
        String normalized = normalizeNullable(text.toString());
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= rule.maxLength() ? normalized : normalized.substring(0, rule.maxLength());
    }

    @Nonnull
    private static String truncate(@Nonnull String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Nullable
    private static String sanitizeEnumValue(@Nonnull DetailFieldRule rule, @Nonnull Object rawValue) {
        String normalized = sanitizeStringValue(rule, rawValue);
        if (normalized == null) {
            return null;
        }
        for (String allowed : rule.values()) {
            if (allowed.equalsIgnoreCase(normalized)) {
                return allowed;
            }
        }
        return null;
    }

    @Nonnull
    private static String chooseNonBlank(@Nullable String primary, @Nullable String fallback, @Nonnull String finalFallback) {
        String normalizedPrimary = normalizeNullable(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        String normalizedFallback = normalizeNullable(fallback);
        return normalizedFallback == null ? finalFallback : normalizedFallback;
    }

    @Nonnull
    private static String slugify(@Nonnull String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean previousDash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                previousDash = false;
            } else if (c >= 'A' && c <= 'Z') {
                out.append(Character.toLowerCase(c));
                previousDash = false;
            } else if (!previousDash) {
                out.append('-');
                previousDash = true;
            }
        }
        String slug = out.toString();
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isBlank() ? "unknown-project" : slug;
    }

    public boolean isDependencyMode() {
        return RUNTIME_MODE_DEPENDENCY.equals(runtimeMode);
    }

    public boolean isEmbeddedMode() {
        return RUNTIME_MODE_EMBEDDED.equals(runtimeMode);
    }

    /**
     * Capture settings for one project.
     */
    public record CaptureOptions(boolean supported,
                                 boolean enabled,
                                 boolean uncaughtExceptions,
                                 boolean setupFailures,
                                 boolean startFailures,
                                 boolean exceptionalWorldRemovals) {
        public CaptureOptions(boolean uncaughtExceptions,
                              boolean setupFailures,
                              boolean startFailures,
                              boolean exceptionalWorldRemovals) {
            this(uncaughtExceptions || setupFailures || startFailures || exceptionalWorldRemovals,
                    uncaughtExceptions || setupFailures || startFailures || exceptionalWorldRemovals,
                    uncaughtExceptions,
                    setupFailures,
                    startFailures,
                    exceptionalWorldRemovals);
        }

        public boolean capturesSource(@Nonnull String source) {
            return enabled() && supportsSource(source);
        }

        public boolean supportsSource(@Nonnull String source) {
            return supported() && switch (source) {
                case "uncaught_exception" -> uncaughtExceptions;
                case "plugin_setup_failure" -> setupFailures;
                case "plugin_start_failure" -> startFailures;
                case "exceptional_world_removal" -> exceptionalWorldRemovals;
                case "manual_test" -> true;
                default -> false;
            };
        }

        public boolean supportsAnySource() {
            return supported() && (uncaughtExceptions || setupFailures || startFailures || exceptionalWorldRemovals);
        }
    }

    /**
     * Default runtime behavior for one project.
     */
    public record Defaults(boolean enabled, @Nonnull String destinationMode) {
    }

    /**
     * Explicit controls for generic telemetry events.
     */
    public record EventOptions(@Nonnull EventTypeOptions errors,
                               @Nonnull EventTypeOptions lifecycle,
                               @Nonnull BreadcrumbOptions breadcrumbs) {

        @Nonnull
        public static EventOptions defaults() {
            return new EventOptions(
                    new EventTypeOptions(false, false, Map.of()),
                    new EventTypeOptions(false, false, Map.of()),
                    new BreadcrumbOptions(false, false, false)
            );
        }
    }

    public record EventTypeOptions(boolean supported,
                                   boolean enabled,
                                   @Nonnull Map<String, DetailRules> details) {
        public EventTypeOptions(boolean enabled, @Nonnull Map<String, DetailRules> details) {
            this(enabled, enabled, details);
        }

        @Nonnull
        public Map<String, Object> sanitizeDetails(@Nonnull String eventName, @Nullable Map<String, Object> rawDetails) {
            return sanitizeDetailMap(details, eventName, rawDetails);
        }
    }

    public record BreadcrumbOptions(boolean supported, boolean enabled, boolean automatic) {
        public BreadcrumbOptions(boolean enabled, boolean automatic) {
            this(enabled, enabled, automatic);
        }
    }

    /**
     * Performance telemetry defaults for one project.
     */
    public record PerformanceOptions(boolean supported,
                                     boolean enabled,
                                     double sampleRate,
                                     int thresholdMs,
                                     @Nonnull Map<String, DetailRules> details) {
        public PerformanceOptions(boolean enabled,
                                  double sampleRate,
                                  int thresholdMs,
                                  @Nonnull Map<String, DetailRules> details) {
            this(enabled, enabled, sampleRate, thresholdMs, details);
        }

        @Nonnull
        public Map<String, Object> sanitizeDetails(@Nonnull String eventName, @Nullable Map<String, Object> rawDetails) {
            return sanitizeDetailMap(details, eventName, rawDetails);
        }
    }

    /**
     * Usage telemetry defaults and allowlist for one project.
     */
    public record UsageOptions(boolean supported,
                               boolean enabled,
                               @Nonnull List<String> allowedEvents,
                               @Nonnull Map<String, DetailRules> details) {
        public UsageOptions(boolean enabled,
                            @Nonnull List<String> allowedEvents,
                            @Nonnull Map<String, DetailRules> details) {
            this(enabled, enabled, allowedEvents, details);
        }

        public boolean allows(@Nonnull String eventName) {
            String normalized = normalizeNullable(eventName);
            return enabled() && normalized != null && allowedEvents().stream().anyMatch(normalized::equalsIgnoreCase);
        }

        @Nonnull
        public Map<String, Object> sanitizeDetails(@Nonnull String eventName, @Nullable Map<String, Object> rawDetails) {
            return sanitizeDetailMap(details, eventName, rawDetails);
        }
    }

    /**
     * Anonymous aggregate statistics defaults and allowlist for one project.
     */
    public record StatsOptions(boolean supported,
                               boolean enabled,
                               @Nonnull List<String> allowedEvents,
                               @Nonnull Map<String, DetailRules> details) {
        public StatsOptions(boolean enabled,
                            @Nonnull List<String> allowedEvents,
                            @Nonnull Map<String, DetailRules> details) {
            this(enabled, enabled, allowedEvents, details);
        }

        private static final String EVENT_HEARTBEAT = "heartbeat";

        public boolean allows(@Nonnull String eventName) {
            String normalized = normalizeNullable(eventName);
            return enabled() && EVENT_HEARTBEAT.equalsIgnoreCase(normalized);
        }

        @Nonnull
        public Map<String, Object> sanitizeDetails(@Nonnull String eventName, @Nullable Map<String, Object> rawDetails) {
            Map<String, Object> sanitized = sanitizeDetailMap(details, eventName, rawDetails);
            if (rawDetails == null || rawDetails.isEmpty()) {
                return sanitized;
            }
            LinkedHashMap<String, Object> withCoreFields = new LinkedHashMap<>(sanitized);
            if (EVENT_HEARTBEAT.equalsIgnoreCase(eventName)) {
                Object playersOnline = rawDetails.get("playersOnline");
                if (playersOnline instanceof Number) {
                    withCoreFields.putIfAbsent("playersOnline", playersOnline);
                }
                Object maxPlayersSinceLastReport = rawDetails.get("maxPlayersSinceLastReport");
                if (maxPlayersSinceLastReport instanceof Number) {
                    withCoreFields.putIfAbsent("maxPlayersSinceLastReport", maxPlayersSinceLastReport);
                }
                Object avgPlayersSinceLastReport = rawDetails.get("avgPlayersSinceLastReport");
                if (avgPlayersSinceLastReport instanceof Number) {
                    withCoreFields.putIfAbsent("avgPlayersSinceLastReport", avgPlayersSinceLastReport);
                }
            }
            return Map.copyOf(withCoreFields);
        }
    }

    /**
     * Optional custom UI presentation hints for consent and diagnostics pages.
     */
    public record UiOptions(@Nullable String iconTexturePath) {
    }

    /**
     * Player-submitted issue and suggestion report configuration.
     */
    public record ManualReportOptions(boolean supported,
                                      boolean enabled,
                                      @Nonnull ManualReportForm issue,
                                      @Nonnull ManualReportForm suggestion,
                                      @Nonnull ManualReportAttachmentOptions attachments,
                                      @Nonnull ManualReportContactOptions contact,
                                      @Nonnull ManualReportResolutionOptions resolutionUpdates) {
        public ManualReportOptions(boolean enabled,
                                   @Nonnull ManualReportForm issue,
                                   @Nonnull ManualReportForm suggestion,
                                   @Nonnull ManualReportAttachmentOptions attachments,
                                   @Nonnull ManualReportContactOptions contact,
                                   @Nonnull ManualReportResolutionOptions resolutionUpdates) {
            this(enabled, enabled, issue, suggestion, attachments, contact, resolutionUpdates);
        }
    }

    public record ManualReportForm(boolean enabled,
                                   @Nonnull List<ManualReportField> fields) {
    }

    public record ManualReportField(@Nonnull String key,
                                    @Nonnull ManualReportFieldType type,
                                    @Nonnull String label,
                                    boolean required,
                                    @Nonnull List<String> values,
                                    int maxLength,
                                    @Nullable Double min,
                                    @Nullable Double max) {
    }

    public enum ManualReportFieldType {
        STRING("string"),
        TEXT("text"),
        BOOLEAN("boolean"),
        ENUM("enum"),
        NUMBER("number");

        private final String key;

        ManualReportFieldType(@Nonnull String key) {
            this.key = key;
        }

        @Nonnull
        public String key() {
            return key;
        }

        @Nullable
        static ManualReportFieldType from(@Nullable String value) {
            String normalized = normalizeNullable(value);
            if (normalized == null) {
                return null;
            }
            for (ManualReportFieldType type : values()) {
                if (type.key.equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }

    public record ManualReportAttachmentOptions(boolean currentServerLog,
                                                boolean previousServerLog,
                                                int maxBytes) {
    }

    public record ManualReportContactOptions(boolean enabled, int maxLength) {
    }

    public record ManualReportResolutionOptions(boolean enabled) {
    }

    public record DetailRules(@Nonnull Map<String, DetailFieldRule> allowedFields) {
    }

    public record DetailFieldRule(@Nonnull String type,
                                  @Nonnull List<String> values,
                                  int maxLength) {
    }

    /**
     * Hosted destination configuration.
     */
    public record HostedDestination(@Nullable String endpoint,
                                    @Nullable String eventEndpoint,
                                    @Nullable String projectKey,
                                    @Nonnull Map<String, String> headers) {
    }

    /**
     * Custom endpoint configuration.
     */
    public record CustomEndpoint(@Nullable String url,
                                 @Nullable String eventUrl,
                                 @Nonnull Map<String, String> headers) {
    }

    /**
     * Manifest-derived fallbacks used during descriptor normalization.
     */
    public record Fallbacks(@Nullable String projectId,
                            @Nullable String displayName,
                            @Nullable String ownerPluginIdentifier,
                            @Nonnull List<String> packagePrefixes) {
    }

    private static final class Document {
        private Integer schemaVersion;
        private String projectId;
        private String projectVersion;
        private String displayName;
        private String runtimeMode;
        private List<String> ownerPluginIdentifiers;
        private List<String> packagePrefixes;
        private TelemetryDocument telemetry;
        private CaptureDocument capture;
        private EventsDocument events;
        private PerformanceDocument performance;
        private UsageDocument usage;
        private StatsDocument stats;
        private UiDocument ui;
        private ReportsDocument reports;
        private DefaultsDocument defaults;
        private HostedDocument hosted;
        private CustomEndpointDocument customEndpoint;
    }

    private static final class TelemetryDocument {
        private CaptureDocument crash;
        private EventsDocument events;
        private PerformanceDocument performance;
        private UsageDocument usage;
        private StatsDocument stats;
        private ReportsDocument reports;
    }

    private static final class CaptureDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private Boolean uncaughtExceptions;
        private Boolean setupFailures;
        private Boolean startFailures;
        private Boolean exceptionalWorldRemovals;
    }

    private static final class DefaultsDocument {
        private Boolean enabled;
        private String destinationMode;
    }

    private static final class EventsDocument {
        private EventTypeDocument errors;
        private EventTypeDocument lifecycle;
        private BreadcrumbsDocument breadcrumbs;
    }

    private static final class EventTypeDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private Map<String, DetailRulesDocument> details;
    }

    private static final class BreadcrumbsDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private Boolean automatic;
    }

    private static final class PerformanceDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private Double sampleRate;
        private Integer thresholdMs;
        private Map<String, DetailRulesDocument> details;
    }

    private static final class UsageDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private List<String> allowedEvents;
        private Map<String, DetailRulesDocument> details;
    }

    private static final class StatsDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private List<String> allowedEvents;
        private Map<String, DetailRulesDocument> details;
    }

    private static final class UiDocument {
        private String iconTexturePath;
    }

    private static final class ReportsDocument {
        private Boolean supported;
        private Boolean defaultEnabled;
        private Boolean enabled;
        private ManualReportFormDocument issue;
        private ManualReportFormDocument suggestion;
        private ManualReportAttachmentDocument attachments;
        private ManualReportContactDocument contact;
        private ManualReportResolutionDocument resolutionUpdates;
    }

    private static final class ManualReportFormDocument {
        private Boolean enabled;
        private Map<String, ManualReportFieldDocument> fields;
    }

    private static final class ManualReportFieldDocument {
        private String type;
        private String label;
        private Boolean required;
        private List<String> values;
        private Integer maxLength;
        private Double min;
        private Double max;
    }

    private static final class ManualReportAttachmentDocument {
        private Boolean currentServerLog;
        private Boolean previousServerLog;
        private Integer maxBytes;
    }

    private static final class ManualReportContactDocument {
        private Boolean enabled;
        private Integer maxLength;
    }

    private static final class ManualReportResolutionDocument {
        private Boolean enabled;
    }

    private static final class DetailRulesDocument {
        private Map<String, DetailFieldDocument> allowedFields;
    }

    private static final class DetailFieldDocument {
        private String type;
        private List<String> values;
        private Integer maxLength;
    }

    private static final class HostedDocument {
        private String endpoint;
        private String eventEndpoint;
        private String projectKey;
        private Map<String, String> headers;
    }

    private static final class CustomEndpointDocument {
        private String url;
        private String eventUrl;
        private Map<String, String> headers;
    }
}
