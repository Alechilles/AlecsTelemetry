package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.consent.TelemetryConsentSnapshot;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts consent runtime state to the JDK-only payloads exposed by coordinator bridges.
 */
public final class TelemetryConsentBridgePayload {

    private TelemetryConsentBridgePayload() {
    }

    @Nonnull
    public static Map<String, Object> diagnosticsSummary(@Nonnull TelemetryRuntimeDiagnostics diagnostics) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", diagnostics.enabled());
        summary.put("registeredProjects", diagnostics.registeredProjects());
        summary.put("loadedMods", diagnostics.loadedMods());
        summary.put("totalPendingReports", diagnostics.totalPendingReports());
        summary.put("flushInProgress", diagnostics.flushInProgress());
        summary.put("lastFlushResult", diagnostics.lastFlushResult());
        putIfPresent(summary, "modsDirectory", diagnostics.modsDirectory());
        summary.put("registrationWarnings", diagnostics.registrationWarnings());
        summary.put("projects", diagnostics.projects().stream()
                .map(TelemetryConsentBridgePayload::projectDiagnosticsSummary)
                .toList());
        return Map.copyOf(summary);
    }

    @Nonnull
    public static Map<String, Object> projectDiagnosticsSummary(
            @Nonnull TelemetryRuntimeDiagnostics.ProjectDiagnostics project) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", project.projectId());
        summary.put("displayName", project.displayName());
        summary.put("enabled", project.enabled());
        summary.put("overridePresent", project.overridePresent());
        summary.put("destinationMode", project.destinationMode());
        putIfPresent(summary, "endpoint", project.endpoint());
        summary.put("pendingReports", project.pendingReports());
        summary.put("pluginIdentifier", project.pluginIdentifier());
        summary.put("pluginVersion", project.pluginVersion());
        putIfPresent(summary, "sourcePath", project.sourcePath());
        putIfPresent(summary, "iconTexturePath", project.iconTexturePath());
        summary.put("packagePrefixes", project.packagePrefixes());
        summary.put("runtimeMode", project.runtimeMode());
        summary.put("crashEnabled", project.crashEnabled());
        summary.put("errorEnabled", project.errorEnabled());
        summary.put("diagnosticsEnabled", project.diagnosticsEnabled());
        summary.put("lifecycleEnabled", project.lifecycleEnabled());
        summary.put("performanceEnabled", project.performanceEnabled());
        summary.put("usageEnabled", project.usageEnabled());
        summary.put("statsEnabled", project.statsEnabled());
        summary.put("breadcrumbsEnabled", project.breadcrumbsEnabled());
        summary.put("crashSupported", project.crashSupported());
        summary.put("errorSupported", project.errorSupported());
        summary.put("diagnosticsSupported", project.diagnosticsSupported());
        summary.put("lifecycleSupported", project.lifecycleSupported());
        summary.put("performanceSupported", project.performanceSupported());
        summary.put("usageSupported", project.usageSupported());
        summary.put("statsSupported", project.statsSupported());
        summary.put("breadcrumbsSupported", project.breadcrumbsSupported());
        return Map.copyOf(summary);
    }

    @Nonnull
    public static TelemetryRuntimeDiagnostics diagnosticsFromSummary(@Nonnull Map<String, Object> summary) {
        ArrayList<TelemetryRuntimeDiagnostics.ProjectDiagnostics> projects = new ArrayList<>();
        Object rawProjects = summary.get("projects");
        if (rawProjects instanceof List<?> projectList) {
            for (Object rawProject : projectList) {
                if (rawProject instanceof Map<?, ?> projectSummary) {
                    projects.add(projectDiagnosticsFromSummary(stringObjectMap(projectSummary)));
                }
            }
        }
        return new TelemetryRuntimeDiagnostics(
                booleanValue(summary, "enabled"),
                intValue(summary, "registeredProjects"),
                intValue(summary, "loadedMods"),
                intValue(summary, "totalPendingReports"),
                booleanValue(summary, "flushInProgress"),
                firstNonBlank(stringValue(summary.get("lastFlushResult")), "<unavailable>"),
                stringValue(summary.get("modsDirectory")),
                stringList(summary.get("registrationWarnings")),
                List.copyOf(projects)
        );
    }

    @Nonnull
    public static TelemetryRuntimeDiagnostics.ProjectDiagnostics projectDiagnosticsFromSummary(
            @Nonnull Map<String, Object> summary) {
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                firstNonBlank(stringValue(summary.get("projectId")), "unknown"),
                firstNonBlank(stringValue(summary.get("displayName")), "Unknown Project"),
                booleanValue(summary, "enabled"),
                booleanValue(summary, "overridePresent"),
                firstNonBlank(stringValue(summary.get("destinationMode")), "hosted"),
                stringValue(summary.get("endpoint")),
                intValue(summary, "pendingReports"),
                firstNonBlank(stringValue(summary.get("pluginIdentifier")), "unknown:unknown"),
                firstNonBlank(stringValue(summary.get("pluginVersion")), "unknown"),
                stringValue(summary.get("sourcePath")),
                stringValue(summary.get("iconTexturePath")),
                stringList(summary.get("packagePrefixes")),
                firstNonBlank(stringValue(summary.get("runtimeMode")), TelemetryProjectDescriptor.RUNTIME_MODE_DEPENDENCY),
                booleanValue(summary, "crashEnabled"),
                booleanValue(summary, "errorEnabled"),
                booleanValue(summary, "diagnosticsEnabled"),
                booleanValue(summary, "lifecycleEnabled"),
                booleanValue(summary, "performanceEnabled"),
                booleanValue(summary, "usageEnabled"),
                booleanValue(summary, "statsEnabled"),
                booleanValue(summary, "breadcrumbsEnabled"),
                booleanValue(summary, "crashSupported"),
                booleanValue(summary, "errorSupported"),
                booleanValue(summary, "diagnosticsSupported"),
                booleanValue(summary, "lifecycleSupported"),
                booleanValue(summary, "performanceSupported"),
                booleanValue(summary, "usageSupported"),
                booleanValue(summary, "statsSupported"),
                booleanValue(summary, "breadcrumbsSupported")
        );
    }

    @Nonnull
    public static Map<String, Object> snapshotSummary(@Nonnull TelemetryConsentSnapshot snapshot) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectEnabled", snapshot.projectEnabled());
        summary.put("crashEnabled", snapshot.crashEnabled());
        summary.put("errorEnabled", snapshot.errorEnabled());
        summary.put("diagnosticsEnabled", snapshot.diagnosticsEnabled());
        summary.put("lifecycleEnabled", snapshot.lifecycleEnabled());
        summary.put("performanceEnabled", snapshot.performanceEnabled());
        summary.put("usageEnabled", snapshot.usageEnabled());
        summary.put("statsEnabled", snapshot.statsEnabled());
        summary.put("breadcrumbsEnabled", snapshot.breadcrumbsEnabled());
        return Map.copyOf(summary);
    }

    @Nonnull
    public static TelemetryConsentSnapshot snapshotFromSummary(@Nonnull Map<String, Object> summary) {
        return new TelemetryConsentSnapshot(
                booleanValue(summary, "projectEnabled"),
                booleanValue(summary, "crashEnabled"),
                booleanValue(summary, "errorEnabled"),
                booleanValue(summary, "diagnosticsEnabled"),
                booleanValue(summary, "lifecycleEnabled"),
                booleanValue(summary, "performanceEnabled"),
                booleanValue(summary, "usageEnabled"),
                booleanValue(summary, "statsEnabled"),
                booleanValue(summary, "breadcrumbsEnabled")
        );
    }

    @Nonnull
    public static Map<String, Object> stringObjectMap(@Nonnull Map<?, ?> source) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            values.put(entry.getKey().toString(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isBlank() ? null : normalized;
    }

    @Nonnull
    private static String firstNonBlank(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean booleanValue(@Nonnull Map<String, Object> values, @Nonnull String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool && bool;
    }

    private static int intValue(@Nonnull Map<String, Object> values, @Nonnull String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Nonnull
    private static List<String> stringList(@Nullable Object value) {
        if (!(value instanceof List<?> rawValues) || rawValues.isEmpty()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (Object rawValue : rawValues) {
            String normalized = stringValue(rawValue);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private static void putIfPresent(@Nonnull Map<String, Object> details,
                                     @Nonnull String key,
                                     @Nullable String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value);
        }
    }
}
