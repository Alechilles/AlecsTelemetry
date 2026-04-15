package com.alechilles.alecstelemetry.crash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Serializable crash telemetry payload persisted to local storage.
 */
public record CrashReportEnvelope(int schemaVersion,
                                  @Nonnull String eventType,
                                  @Nonnull String reportId,
                                  @Nonnull String projectId,
                                  @Nonnull String projectDisplayName,
                                  @Nonnull String source,
                                  @Nonnull String fingerprint,
                                  @Nonnull String capturedAtUtc,
                                  @Nonnull String lastCapturedAtUtc,
                                  int occurrenceCount,
                                  @Nonnull String pluginIdentifier,
                                  @Nonnull String pluginVersion,
                                  @Nonnull String threadName,
                                  @Nullable String worldName,
                                  @Nullable String worldRemovalReason,
                                  @Nullable String worldFailurePluginIdentifier,
                                  @Nonnull AttributionDetails attribution,
                                  @Nonnull List<BreadcrumbEntry> breadcrumbs,
                                  @Nonnull ThrowableDetails throwable,
                                  @Nonnull RuntimeMetadata runtime) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE_CRASH = "crash";

    @Nonnull
    public static CrashReportEnvelope create(@Nonnull String projectId,
                                             @Nonnull String projectDisplayName,
                                             @Nonnull String source,
                                             @Nonnull String fingerprint,
                                             @Nonnull String pluginIdentifier,
                                             @Nonnull String pluginVersion,
                                             @Nonnull String threadName,
                                             @Nullable String worldName,
                                              @Nullable String worldRemovalReason,
                                              @Nullable String worldFailurePluginIdentifier,
                                              @Nonnull CrashAttribution.AttributionResult attributionResult,
                                              @Nonnull List<BreadcrumbEntry> breadcrumbs,
                                              @Nonnull Throwable throwable,
                                              @Nonnull RuntimeMetadata runtimeMetadata) {
        String capturedAtUtc = Instant.now().toString();
        return new CrashReportEnvelope(
                SCHEMA_VERSION,
                EVENT_TYPE_CRASH,
                UUID.randomUUID().toString(),
                normalizeNonBlank(projectId, "unknown-project"),
                normalizeNonBlank(projectDisplayName, projectId),
                normalizeNonBlank(source, "unknown"),
                normalizeNonBlank(fingerprint, "unknown"),
                capturedAtUtc,
                capturedAtUtc,
                1,
                normalizeNonBlank(pluginIdentifier, "unknown"),
                normalizeNonBlank(pluginVersion, "unknown"),
                normalizeNonBlank(threadName, Thread.currentThread().getName()),
                normalizeNullable(worldName),
                normalizeNullable(worldRemovalReason),
                normalizeNullable(worldFailurePluginIdentifier),
                new AttributionDetails(
                        attributionResult.identifiedPlugin(),
                        attributionResult.matchedPluginIdentifier(),
                        attributionResult.matchedStackPrefix()
                ),
                normalizeBreadcrumbs(breadcrumbs),
                ThrowableDetails.from(throwable),
                runtimeMetadata.normalize()
        );
    }

    @Nonnull
    public static CrashReportEnvelope fromJson(@Nonnull String json) {
        CrashReportEnvelope parsed = GSON.fromJson(json, CrashReportEnvelope.class);
        return parsed == null ? nullReport() : parsed.normalize();
    }

    @Nonnull
    public String toJson() {
        return GSON.toJson(this) + System.lineSeparator();
    }

    @Nonnull
    public CrashReportEnvelope mergeDuplicateOccurrence(@Nonnull CrashReportEnvelope incoming) {
        CrashReportEnvelope current = normalize();
        CrashReportEnvelope latest = incoming.normalize();
        return new CrashReportEnvelope(
                Math.max(current.schemaVersion(), latest.schemaVersion()),
                EVENT_TYPE_CRASH,
                current.reportId(),
                current.projectId(),
                chooseNonBlank(current.projectDisplayName(), latest.projectDisplayName()),
                chooseNonBlank(current.source(), latest.source()),
                current.fingerprint(),
                current.capturedAtUtc(),
                latest.lastCapturedAtUtc(),
                safeOccurrenceAdd(current.occurrenceCount(), latest.occurrenceCount()),
                chooseNonBlank(current.pluginIdentifier(), latest.pluginIdentifier()),
                chooseNonBlank(current.pluginVersion(), latest.pluginVersion()),
                chooseNonBlank(current.threadName(), latest.threadName()),
                chooseNullable(current.worldName(), latest.worldName()),
                chooseNullable(current.worldRemovalReason(), latest.worldRemovalReason()),
                chooseNullable(current.worldFailurePluginIdentifier(), latest.worldFailurePluginIdentifier()),
                latest.attribution(),
                latest.breadcrumbs(),
                latest.throwable(),
                latest.runtime().normalize()
        );
    }

    @Nonnull
    private CrashReportEnvelope normalize() {
        String firstCapturedAt = normalizeTimestamp(capturedAtUtc(), Instant.now().toString());
        return new CrashReportEnvelope(
                schemaVersion() <= 0 ? SCHEMA_VERSION : schemaVersion(),
                EVENT_TYPE_CRASH,
                normalizeNonBlank(reportId(), UUID.randomUUID().toString()),
                normalizeNonBlank(projectId(), "unknown-project"),
                normalizeNonBlank(projectDisplayName(), projectId()),
                normalizeNonBlank(source(), "unknown"),
                normalizeNonBlank(fingerprint(), "unknown"),
                firstCapturedAt,
                normalizeTimestamp(lastCapturedAtUtc(), firstCapturedAt),
                occurrenceCount() <= 0 ? 1 : occurrenceCount(),
                normalizeNonBlank(pluginIdentifier(), "unknown"),
                normalizeNonBlank(pluginVersion(), "unknown"),
                normalizeNonBlank(threadName(), Thread.currentThread().getName()),
                normalizeNullable(worldName()),
                normalizeNullable(worldRemovalReason()),
                normalizeNullable(worldFailurePluginIdentifier()),
                attribution() == null ? new AttributionDetails(null, false, false) : attribution(),
                normalizeBreadcrumbs(breadcrumbs()),
                throwable() == null
                        ? new ThrowableDetails("java.lang.Throwable", "<empty>", List.of(), List.of())
                        : throwable(),
                runtime() == null ? RuntimeMetadata.capture(List.of()) : runtime().normalize()
        );
    }

    @Nonnull
    private static CrashReportEnvelope nullReport() {
        String now = Instant.now().toString();
        return new CrashReportEnvelope(
                SCHEMA_VERSION,
                EVENT_TYPE_CRASH,
                UUID.randomUUID().toString(),
                "unknown-project",
                "Unknown Project",
                "unknown",
                "unknown",
                now,
                now,
                1,
                "unknown",
                "unknown",
                Thread.currentThread().getName(),
                null,
                null,
                null,
                new AttributionDetails(null, false, false),
                List.of(),
                new ThrowableDetails("java.lang.Throwable", "<empty>", List.of(), List.of()),
                RuntimeMetadata.capture(List.of())
        );
    }

    private static int safeOccurrenceAdd(int left, int right) {
        long sum = Math.max(1, left) + (long) Math.max(1, right);
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static String normalizeTimestamp(@Nullable String value, @Nonnull String fallback) {
        String normalized = normalizeNullable(value);
        return normalized == null ? fallback : normalized;
    }

    @Nonnull
    private static String normalizeNonBlank(@Nullable String value, @Nonnull String fallback) {
        String normalized = normalizeNullable(value);
        return normalized == null ? fallback : normalized;
    }

    @Nullable
    private static String chooseNullable(@Nullable String existing, @Nullable String incoming) {
        return normalizeNullable(incoming) != null ? normalizeNullable(incoming) : normalizeNullable(existing);
    }

    @Nonnull
    private static String chooseNonBlank(@Nonnull String existing, @Nonnull String incoming) {
        String normalizedIncoming = normalizeNonBlank(incoming, "");
        if (!normalizedIncoming.isBlank()) {
            return normalizedIncoming;
        }
        return normalizeNonBlank(existing, "unknown");
    }

    @Nonnull
    private static String truncate(@Nonnull String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    @Nonnull
    private static List<BreadcrumbEntry> normalizeBreadcrumbs(@Nullable List<BreadcrumbEntry> breadcrumbs) {
        if (breadcrumbs == null || breadcrumbs.isEmpty()) {
            return List.of();
        }
        ArrayList<BreadcrumbEntry> normalized = new ArrayList<>(breadcrumbs.size());
        for (BreadcrumbEntry breadcrumb : breadcrumbs) {
            if (breadcrumb == null) {
                continue;
            }
            normalized.add(breadcrumb.normalize());
        }
        return List.copyOf(normalized);
    }

    /**
     * Attribution details serialized with a crash report.
     */
    public record AttributionDetails(@Nullable String identifiedPlugin,
                                     boolean matchedPluginIdentifier,
                                     boolean matchedStackPrefix) {
    }

    /**
     * One breadcrumb recorded by a consumer mod before a crash report was captured.
     */
    public record BreadcrumbEntry(@Nonnull String atUtc,
                                  @Nonnull String category,
                                  @Nonnull String detail) {

        @Nonnull
        BreadcrumbEntry normalize() {
            return new BreadcrumbEntry(
                    normalizeTimestamp(atUtc(), Instant.now().toString()),
                    truncate(normalizeNonBlank(category(), "general"), 80),
                    truncate(normalizeNonBlank(detail(), "<empty>"), 400)
            );
        }
    }

    /**
     * Throwable details serialized with a crash report.
     */
    public record ThrowableDetails(@Nonnull String type,
                                   @Nonnull String message,
                                   @Nonnull List<String> stack,
                                   @Nonnull List<CauseDetails> causes) {

        private static final int MAX_STACK_FRAMES = 80;

        @Nonnull
        static ThrowableDetails from(@Nonnull Throwable throwable) {
            ArrayList<CauseDetails> causes = new ArrayList<>();
            Throwable cursor = throwable.getCause();
            int depth = 0;
            while (cursor != null && depth < 6) {
                causes.add(new CauseDetails(
                        cursor.getClass().getName(),
                        sanitizeMessage(cursor.getMessage()),
                        stackFrames(cursor.getStackTrace())
                ));
                cursor = cursor.getCause();
                depth++;
            }
            return new ThrowableDetails(
                    throwable.getClass().getName(),
                    sanitizeMessage(throwable.getMessage()),
                    stackFrames(throwable.getStackTrace()),
                    List.copyOf(causes)
            );
        }

        @Nonnull
        private static List<String> stackFrames(@Nonnull StackTraceElement[] stackTrace) {
            int frameCount = Math.min(stackTrace.length, MAX_STACK_FRAMES);
            ArrayList<String> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) {
                frames.add(stackTrace[i].toString());
            }
            return List.copyOf(frames);
        }

        @Nonnull
        private static String sanitizeMessage(@Nullable String message) {
            if (message == null || message.isBlank()) {
                return "<empty>";
            }
            String normalized = message.trim();
            return normalized.length() > 4000 ? normalized.substring(0, 4000) : normalized;
        }
    }

    /**
     * One causal throwable in the chain.
     */
    public record CauseDetails(@Nonnull String type,
                               @Nonnull String message,
                               @Nonnull List<String> stack) {
    }

    /**
     * Runtime details serialized with a crash report.
     */
    public record RuntimeMetadata(@Nonnull String javaVersion,
                                  @Nonnull String runtimeVersion,
                                  @Nonnull String osName,
                                  @Nonnull String osVersion,
                                  @Nonnull String osArch,
                                  @Nonnull String hytaleBuild,
                                  @Nonnull String serverVersion,
                                  @Nonnull List<LoadedModMetadata> loadedMods) {

        @Nonnull
        public static RuntimeMetadata capture(@Nonnull List<LoadedModMetadata> loadedMods) {
            return new RuntimeMetadata(
                    systemProperty("java.version"),
                    Runtime.version().toString(),
                    systemProperty("os.name"),
                    systemProperty("os.version"),
                    systemProperty("os.arch"),
                    firstNonBlank(
                            systemProperty("hytale.build"),
                            systemProperty("hytale.build.id"),
                            systemProperty("hytale.build.version"),
                            "unknown"
                    ),
                    firstNonBlank(
                            systemProperty("hytale.server.version"),
                            systemProperty("hytale.version"),
                            "unknown"
                    ),
                    normalizeLoadedMods(loadedMods)
            );
        }

        @Nonnull
        RuntimeMetadata normalize() {
            return new RuntimeMetadata(
                    normalizeNonBlank(javaVersion(), "unknown"),
                    normalizeNonBlank(runtimeVersion(), "unknown"),
                    normalizeNonBlank(osName(), "unknown"),
                    normalizeNonBlank(osVersion(), "unknown"),
                    normalizeNonBlank(osArch(), "unknown"),
                    normalizeNonBlank(hytaleBuild(), "unknown"),
                    normalizeNonBlank(serverVersion(), "unknown"),
                    normalizeLoadedMods(loadedMods())
            );
        }

        @Nonnull
        private static List<LoadedModMetadata> normalizeLoadedMods(@Nullable List<LoadedModMetadata> mods) {
            if (mods == null || mods.isEmpty()) {
                return List.of();
            }
            LinkedHashMap<String, LoadedModMetadata> deduped = new LinkedHashMap<>();
            for (LoadedModMetadata mod : mods) {
                if (mod == null) {
                    continue;
                }
                String id = truncate(normalizeNonBlank(mod.identifier(), "unknown"), 200);
                String version = truncate(normalizeNonBlank(mod.version(), "unknown"), 80);
                deduped.putIfAbsent(id.toLowerCase(), new LoadedModMetadata(id, version));
            }
            return List.copyOf(deduped.values());
        }

        @Nonnull
        private static String firstNonBlank(@Nonnull String first,
                                            @Nonnull String second,
                                            @Nonnull String fallback) {
            if (!first.isBlank()) {
                return first;
            }
            if (!second.isBlank()) {
                return second;
            }
            return fallback;
        }

        @Nonnull
        private static String firstNonBlank(@Nonnull String first,
                                            @Nonnull String second,
                                            @Nonnull String third,
                                            @Nonnull String fallback) {
            if (!first.isBlank()) {
                return first;
            }
            if (!second.isBlank()) {
                return second;
            }
            if (!third.isBlank()) {
                return third;
            }
            return fallback;
        }

        @Nonnull
        private static String systemProperty(@Nonnull String key) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) {
                return "";
            }
            return value.trim();
        }
    }

    /**
     * Summary for one loaded mod.
     */
    public record LoadedModMetadata(@Nonnull String identifier,
                                    @Nonnull String version) {
    }
}
