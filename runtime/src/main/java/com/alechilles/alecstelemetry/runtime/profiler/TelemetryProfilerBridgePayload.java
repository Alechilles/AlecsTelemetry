package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerContext;
import com.alechilles.alecstelemetry.api.TelemetryProfilerPathEvidence;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;
import com.alechilles.alecstelemetry.api.TelemetryProfilerSnapshot;
import com.alechilles.alecstelemetry.api.TelemetryProfilerStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Converts profiler values to bounded, immutable, JDK-only bridge payloads. */
public final class TelemetryProfilerBridgePayload {
    public static final int MAX_HISTORY = 10;
    public static final int MAX_PATHS = 5;
    public static final int MAX_FRAMES = 8;
    public static final int MAX_PROJECT_OR_VERSION_LENGTH = 120;
    public static final int MAX_DETAIL_LENGTH = 512;
    public static final int MAX_METHOD_LENGTH = 512;
    public static final int MAX_FINGERPRINT_LENGTH = 32;

    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{32}");
    private static final int MAX_TREE_DEPTH = 12;
    private static final int MAX_TREE_ENTRIES = 128;
    private static final int MAX_CONTEXT_ENTRIES = 32;

    private TelemetryProfilerBridgePayload() {
    }

    @Nonnull
    public static Map<String, Object> statusSummary(@Nullable TelemetryProfilerStatus status) {
        TelemetryProfilerStatus safe = status == null ? TelemetryProfilerStatus.unavailable() : status;
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("state", safe.state().name());
        summary.put("detail", bounded(safe.detail(), MAX_DETAIL_LENGTH));
        summary.put("active", safe.active());
        summary.put("circuitOpen", safe.circuitOpen());
        putText(summary, "sparkVersion", safe.sparkVersion(), MAX_PROJECT_OR_VERSION_LENGTH);
        putText(summary, "runtimeVersion", safe.runtimeVersion(), MAX_PROJECT_OR_VERSION_LENGTH);
        summary.put("nextCaptureAtMillis", safe.nextCaptureAtMillis());
        summary.put("analysisDurationMillis", safe.analysisDurationMillis());
        summary.put("omittedPathCount", safe.omittedPathCount());
        summary.put("truncatedPrefixCount", safe.truncatedPrefixCount());
        return immutableStringMap(summary);
    }

    @Nonnull
    public static TelemetryProfilerStatus statusFromSummary(@Nullable Object raw) {
        try {
            if (!(raw instanceof Map<?, ?> map)) {
                return TelemetryProfilerStatus.unavailable();
            }
            String stateName = requiredString(map, "state", MAX_DETAIL_LENGTH);
            TelemetryProfilerStatus.State state = TelemetryProfilerStatus.State.valueOf(stateName);
            String detail = requiredString(map, "detail", MAX_DETAIL_LENGTH);
            boolean active = requiredBoolean(map, "active");
            boolean circuitOpen = requiredBoolean(map, "circuitOpen");
            String sparkVersion = optionalString(map, "sparkVersion", MAX_PROJECT_OR_VERSION_LENGTH);
            String runtimeVersion = optionalString(map, "runtimeVersion", MAX_PROJECT_OR_VERSION_LENGTH);
            long nextCaptureAtMillis = nonNegativeLong(map, "nextCaptureAtMillis");
            long analysisDurationMillis = nonNegativeLong(map, "analysisDurationMillis");
            int omittedPathCount = nonNegativeInt(map, "omittedPathCount");
            int truncatedPrefixCount = nonNegativeInt(map, "truncatedPrefixCount");
            return new TelemetryProfilerStatus(
                    state,
                    detail,
                    active,
                    circuitOpen,
                    sparkVersion,
                    runtimeVersion,
                    nextCaptureAtMillis,
                    analysisDurationMillis,
                    omittedPathCount,
                    truncatedPrefixCount
            );
        } catch (RuntimeException | LinkageError ignored) {
            return TelemetryProfilerStatus.unavailable();
        }
    }

    @Nonnull
    public static Map<String, Object> snapshotSummary(@Nonnull TelemetryProfilerSnapshot snapshot) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", bounded(snapshot.projectId(), MAX_PROJECT_OR_VERSION_LENGTH));
        summary.put("projectVersion", bounded(snapshot.projectVersion(), MAX_PROJECT_OR_VERSION_LENGTH));
        summary.put("sparkVersion", bounded(snapshot.sparkVersion(), MAX_PROJECT_OR_VERSION_LENGTH));
        summary.put("windowKey", snapshot.windowKey());
        summary.put("observedAtMillis", snapshot.observedAtMillis());
        summary.put("selectedWorldThreadSelfMilliseconds", snapshot.selectedWorldThreadSelfMilliseconds());
        summary.put("qualificationRuleSet", bounded(snapshot.qualificationRuleSet(), MAX_PROJECT_OR_VERSION_LENGTH));
        ArrayList<Map<String, Object>> paths = new ArrayList<>(MAX_PATHS);
        List<TelemetryProfilerPathEvidence> sourcePaths = snapshot.paths();
        final int pathCount;
        try {
            pathCount = Math.min(sourcePaths.size(), MAX_PATHS);
        } catch (RuntimeException | LinkageError ignored) {
            summary.put("paths", List.of());
            summary.put("context", contextSummary(snapshot.context()));
            return immutableStringMap(summary);
        }
        for (int index = 0; index < pathCount; index++) {
            final TelemetryProfilerPathEvidence path;
            try {
                path = sourcePaths.get(index);
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
            if (path == null) {
                continue;
            }
            Map<String, Object> pathSummary = pathSummary(path);
            if (!pathSummary.isEmpty()) {
                paths.add(pathSummary);
            }
        }
        summary.put("paths", List.copyOf(paths));
        summary.put("context", contextSummary(snapshot.context()));
        return immutableStringMap(summary);
    }

    @Nullable
    public static TelemetryProfilerSnapshot snapshotFromSummary(@Nullable Object raw) {
        try {
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            String projectId = requiredString(map, "projectId", MAX_PROJECT_OR_VERSION_LENGTH);
            String projectVersion = requiredString(map, "projectVersion", MAX_PROJECT_OR_VERSION_LENGTH);
            String sparkVersion = requiredString(map, "sparkVersion", MAX_PROJECT_OR_VERSION_LENGTH);
            int windowKey = nonNegativeInt(map, "windowKey");
            long observedAtMillis = nonNegativeLong(map, "observedAtMillis");
            double selectedWorldThreadSelfMilliseconds = nonNegativeDouble(
                    map,
                    "selectedWorldThreadSelfMilliseconds"
            );
            String qualificationRuleSet = requiredString(map, "qualificationRuleSet", MAX_PROJECT_OR_VERSION_LENGTH);
            Object rawPaths = valueOf(map, "paths");
            if (!(rawPaths instanceof List<?>)) {
                throw new IllegalArgumentException("Invalid profiler paths");
            }
            Object rawContext = valueOf(map, "context");
            if (!(rawContext instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Invalid profiler context");
            }
            List<TelemetryProfilerPathEvidence> paths = pathsFromSummary(rawPaths);
            if (paths == null) {
                throw new IllegalArgumentException("Malformed profiler path");
            }
            TelemetryProfilerContext context = contextFromSummary(rawContext);
            return new TelemetryProfilerSnapshot(
                    projectId,
                    projectVersion,
                    sparkVersion,
                    windowKey,
                    observedAtMillis,
                    selectedWorldThreadSelfMilliseconds,
                    qualificationRuleSet,
                    paths,
                    context
            );
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nonnull
    public static List<Map<String, Object>> historySummaries(@Nullable List<TelemetryProfilerSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        ArrayList<Map<String, Object>> summaries = new ArrayList<>(MAX_HISTORY);
        int size = snapshots.size();
        int first = Math.max(0, size - MAX_HISTORY);
        int end = Math.min(size, first + MAX_HISTORY);
        for (int index = first; index < end; index++) {
            try {
                TelemetryProfilerSnapshot snapshot = snapshots.get(index);
                if (snapshot != null) {
                    summaries.add(snapshotSummary(snapshot));
                }
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
        }
        return List.copyOf(summaries);
    }

    @Nonnull
    public static List<TelemetryProfilerSnapshot> snapshotsFromHistory(@Nullable Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        final int size;
        try {
            size = list.size();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        int first = Math.max(0, size - MAX_HISTORY);
        ArrayList<TelemetryProfilerSnapshot> snapshots = new ArrayList<>(Math.min(size, MAX_HISTORY));
        int end = Math.min(size, first + MAX_HISTORY);
        for (int index = first; index < end; index++) {
            try {
                TelemetryProfilerSnapshot snapshot = snapshotFromSummary(list.get(index));
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
        }
        return List.copyOf(snapshots);
    }

    /**
     * Copies a foreign profiler status map while reading only known JDK-compatible values.
     * Oversized or malformed values remain visible to the strict decoder, which then returns
     * the unavailable status instead of throwing into the consumer.
     */
    @Nonnull
    public static Map<String, Object> foreignStatusMap(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        copyKnownScalar(map, copy, "state", MAX_DETAIL_LENGTH);
        copyKnownScalar(map, copy, "detail", MAX_DETAIL_LENGTH);
        copyKnownScalar(map, copy, "active");
        copyKnownScalar(map, copy, "circuitOpen");
        copyKnownScalar(map, copy, "sparkVersion", MAX_PROJECT_OR_VERSION_LENGTH);
        copyKnownScalar(map, copy, "runtimeVersion", MAX_PROJECT_OR_VERSION_LENGTH);
        copyKnownScalar(map, copy, "nextCaptureAtMillis");
        copyKnownScalar(map, copy, "analysisDurationMillis");
        copyKnownScalar(map, copy, "omittedPathCount");
        copyKnownScalar(map, copy, "truncatedPrefixCount");
        return immutableStringMap(copy);
    }

    /** Copies only bounded snapshot branches from a foreign latest payload. */
    @Nonnull
    public static Map<String, Object> foreignSnapshotMap(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return foreignSnapshotMap(map);
    }

    /** Copies at most the newest ten foreign history entries by indexed access. */
    @Nonnull
    public static List<Map<String, Object>> foreignHistoryMaps(@Nullable Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        final int size;
        try {
            size = list.size();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        int first = Math.max(0, size - MAX_HISTORY);
        ArrayList<Map<String, Object>> snapshots = new ArrayList<>(Math.min(size, MAX_HISTORY));
        int end = Math.min(size, first + MAX_HISTORY);
        for (int index = first; index < end; index++) {
            try {
                Map<String, Object> snapshot = foreignSnapshotMap(list.get(index));
                if (!snapshot.isEmpty()) {
                    snapshots.add(snapshot);
                }
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
        }
        return List.copyOf(snapshots);
    }

    @Nonnull
    public static Map<String, Object> immutableTreeMap(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Object copied = copyTree(map, 0, new IdentityHashMap<>());
        if (!(copied instanceof Map<?, ?> copiedMap)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : copiedMap.entrySet()) {
            if (entry != null && entry.getKey() instanceof String key && entry.getValue() != null) {
                typed.put(key, entry.getValue());
            }
        }
        return immutableStringMap(typed);
    }

    @Nonnull
    private static Map<String, Object> pathSummary(@Nonnull TelemetryProfilerPathEvidence path) {
        String fingerprint = validFingerprint(path.fingerprint());
        if (fingerprint == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("fingerprint", fingerprint);
        summary.put("attribution", path.attribution().name());
        summary.put("ownedClassName", bounded(path.ownedClassName(), MAX_METHOD_LENGTH));
        summary.put("ownedMethodName", bounded(path.ownedMethodName(), MAX_METHOD_LENGTH));
        summary.put("ownedMethodDescriptor", bounded(path.ownedMethodDescriptor(), MAX_METHOD_LENGTH));
        putText(summary, "firstExternalClassName", path.firstExternalClassName(), MAX_METHOD_LENGTH);
        putText(summary, "firstExternalMethodName", path.firstExternalMethodName(), MAX_METHOD_LENGTH);
        putText(summary, "firstExternalMethodDescriptor", path.firstExternalMethodDescriptor(), MAX_METHOD_LENGTH);
        summary.put("sampledMilliseconds", path.sampledMilliseconds());
        summary.put("selectedWorldThreadSharePercent", path.selectedWorldThreadSharePercent());
        summary.put("qualification", path.qualification().name());
        ArrayList<String> frames = new ArrayList<>(MAX_FRAMES);
        List<String> sourceFrames = path.representativePath();
        final int frameCount;
        try {
            frameCount = Math.min(sourceFrames.size(), MAX_FRAMES);
        } catch (RuntimeException | LinkageError ignored) {
            summary.put("representativePath", List.of());
            return immutableStringMap(summary);
        }
        for (int index = 0; index < frameCount; index++) {
            final String frame;
            try {
                frame = sourceFrames.get(index);
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
            if (frame != null) {
                frames.add(bounded(frame, MAX_METHOD_LENGTH));
            }
        }
        summary.put("representativePath", List.copyOf(frames));
        return immutableStringMap(summary);
    }

    @Nonnull
    private static Map<String, Object> contextSummary(@Nullable TelemetryProfilerContext context) {
        TelemetryProfilerContext safe = context == null ? TelemetryProfilerContext.unavailable() : context;
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("observedAtMillis", safe.observedAtMillis());
        if (safe.playerCount() != null && safe.playerCount() >= 0) {
            summary.put("playerCount", safe.playerCount());
        }
        putFiniteNonNegative(summary, "tps", safe.tps());
        putFiniteNonNegative(summary, "mspt", safe.mspt());
        LinkedHashMap<String, Object> counts = new LinkedHashMap<>();
        int inspected = 0;
        try {
            Iterator<Map.Entry<String, Integer>> entries = safe.breadcrumbCategoryCounts().entrySet().iterator();
            while (inspected < MAX_CONTEXT_ENTRIES && entries.hasNext()) {
                Map.Entry<String, Integer> entry = entries.next();
                inspected++;
                if (entry == null || entry.getKey() == null) {
                    continue;
                }
                Integer count = entry.getValue();
                if (count != null && count >= 0) {
                    counts.put(bounded(entry.getKey(), MAX_DETAIL_LENGTH), count);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep the valid prefix when a foreign map fails during iteration.
        }
        summary.put("breadcrumbCategoryCounts", immutableStringMap(counts));
        return immutableStringMap(summary);
    }

    @Nullable
    private static List<TelemetryProfilerPathEvidence> pathsFromSummary(@Nullable Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        final int size;
        try {
            size = list.size();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        ArrayList<TelemetryProfilerPathEvidence> paths = new ArrayList<>(Math.min(size, MAX_PATHS));
        int end = Math.min(size, MAX_PATHS);
        for (int index = 0; index < end; index++) {
            try {
                TelemetryProfilerPathEvidence path = pathFromSummary(list.get(index));
                if (path == null) {
                    return null;
                }
                paths.add(path);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return List.copyOf(paths);
    }

    @Nullable
    private static TelemetryProfilerPathEvidence pathFromSummary(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        try {
            String fingerprint = requiredFingerprint(map, "fingerprint");
            TelemetryProfilerAttribution attribution = TelemetryProfilerAttribution.valueOf(
                    requiredString(map, "attribution", MAX_DETAIL_LENGTH)
            );
            String ownedClassName = requiredString(map, "ownedClassName", MAX_METHOD_LENGTH);
            String ownedMethodName = requiredString(map, "ownedMethodName", MAX_METHOD_LENGTH);
            String ownedMethodDescriptor = requiredString(map, "ownedMethodDescriptor", MAX_METHOD_LENGTH);
            String firstExternalClassName = optionalString(map, "firstExternalClassName", MAX_METHOD_LENGTH);
            String firstExternalMethodName = optionalString(map, "firstExternalMethodName", MAX_METHOD_LENGTH);
            String firstExternalMethodDescriptor = optionalString(
                    map,
                    "firstExternalMethodDescriptor",
                    MAX_METHOD_LENGTH
            );
            double sampledMilliseconds = nonNegativeDouble(map, "sampledMilliseconds");
            double selectedWorldThreadSharePercent = nonNegativeDouble(
                    map,
                    "selectedWorldThreadSharePercent"
            );
            TelemetryProfilerQualification qualification = TelemetryProfilerQualification.valueOf(
                    requiredString(map, "qualification", MAX_DETAIL_LENGTH)
            );
            Object rawFrames = valueOf(map, "representativePath");
            if (!(rawFrames instanceof List<?>)) {
                throw new IllegalArgumentException("Invalid profiler representative path");
            }
            List<String> representativePath = framesFromSummary(rawFrames);
            if (representativePath == null) {
                throw new IllegalArgumentException("Malformed profiler representative path");
            }
            return new TelemetryProfilerPathEvidence(
                    fingerprint,
                    attribution,
                    ownedClassName,
                    ownedMethodName,
                    ownedMethodDescriptor,
                    firstExternalClassName,
                    firstExternalMethodName,
                    firstExternalMethodDescriptor,
                    sampledMilliseconds,
                    selectedWorldThreadSharePercent,
                    qualification,
                    representativePath
            );
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static List<String> framesFromSummary(@Nullable Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        final int size;
        try {
            size = list.size();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        ArrayList<String> frames = new ArrayList<>(Math.min(size, MAX_FRAMES));
        int end = Math.min(size, MAX_FRAMES);
        for (int index = 0; index < end; index++) {
            Object value;
            try {
                value = list.get(index);
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
            if (!(value instanceof String frame) || frame.isBlank() || frame.length() > MAX_METHOD_LENGTH) {
                return null;
            }
            frames.add(frame);
        }
        return List.copyOf(frames);
    }

    @Nonnull
    private static TelemetryProfilerContext contextFromSummary(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return TelemetryProfilerContext.unavailable();
        }
        long observedAtMillis = nonNegativeLong(map, "observedAtMillis");
        Integer playerCount = optionalNonNegativeInt(map, "playerCount");
        Double tps = optionalNonNegativeDouble(map, "tps");
        Double mspt = optionalNonNegativeDouble(map, "mspt");
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Object rawCounts = valueOf(map, "breadcrumbCategoryCounts");
        if (rawCounts != null && !(rawCounts instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Invalid profiler breadcrumb counts");
        }
        if (rawCounts instanceof Map<?, ?> countMap) {
            int inspected = 0;
            try {
                Iterator<? extends Map.Entry<?, ?>> entries = countMap.entrySet().iterator();
                while (inspected < MAX_CONTEXT_ENTRIES && entries.hasNext()) {
                    Map.Entry<?, ?> entry = entries.next();
                    inspected++;
                    if (entry == null || !(entry.getKey() instanceof String key)) {
                        continue;
                    }
                    if (key.isBlank() || key.length() > MAX_DETAIL_LENGTH || !(entry.getValue() instanceof Number)) {
                        continue;
                    }
                    int count = numberAsInt(entry.getValue());
                    if (count >= 0) {
                        counts.put(key, count);
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Keep the valid prefix when a foreign map fails during iteration.
            }
        }
        return new TelemetryProfilerContext(observedAtMillis, playerCount, tps, mspt, counts);
    }

    @Nonnull
    private static Map<String, Object> foreignSnapshotMap(@Nonnull Map<?, ?> map) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        copyKnownScalar(map, copy, "projectId", MAX_PROJECT_OR_VERSION_LENGTH);
        copyKnownScalar(map, copy, "projectVersion", MAX_PROJECT_OR_VERSION_LENGTH);
        copyKnownScalar(map, copy, "sparkVersion", MAX_PROJECT_OR_VERSION_LENGTH);
        copyKnownScalar(map, copy, "windowKey");
        copyKnownScalar(map, copy, "observedAtMillis");
        copyKnownScalar(map, copy, "selectedWorldThreadSelfMilliseconds");
        copyKnownScalar(map, copy, "qualificationRuleSet", MAX_DETAIL_LENGTH);
        Object rawPaths = valueOf(map, "paths");
        if (rawPaths instanceof List<?> paths) {
            copy.put("paths", foreignPathList(paths));
        }
        Object rawContext = valueOf(map, "context");
        if (rawContext instanceof Map<?, ?> context) {
            copy.put("context", foreignContextMap(context));
        }
        return immutableStringMap(copy);
    }

    @Nonnull
    private static List<Map<String, Object>> foreignPathList(@Nonnull List<?> paths) {
        final int size;
        try {
            size = paths.size();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        ArrayList<Map<String, Object>> copy = new ArrayList<>(Math.min(size, MAX_PATHS));
        int end = Math.min(size, MAX_PATHS);
        for (int index = 0; index < end; index++) {
            try {
                Object rawPath = paths.get(index);
                if (rawPath instanceof Map<?, ?> path) {
                    copy.add(foreignPathMap(path));
                }
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
        }
        return List.copyOf(copy);
    }

    @Nonnull
    private static Map<String, Object> foreignPathMap(@Nonnull Map<?, ?> path) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        copyKnownScalar(path, copy, "fingerprint", MAX_FINGERPRINT_LENGTH);
        copyKnownScalar(path, copy, "attribution", MAX_DETAIL_LENGTH);
        copyKnownScalar(path, copy, "ownedClassName", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "ownedMethodName", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "ownedMethodDescriptor", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "firstExternalClassName", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "firstExternalMethodName", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "firstExternalMethodDescriptor", MAX_METHOD_LENGTH);
        copyKnownScalar(path, copy, "sampledMilliseconds");
        copyKnownScalar(path, copy, "selectedWorldThreadSharePercent");
        copyKnownScalar(path, copy, "qualification", MAX_DETAIL_LENGTH);
        Object rawFrames = valueOf(path, "representativePath");
        if (rawFrames instanceof List<?> frames) {
            copy.put("representativePath", foreignFrameList(frames));
        }
        return immutableStringMap(copy);
    }

    @Nonnull
    private static List<String> foreignFrameList(@Nonnull List<?> frames) {
        final int size;
        try {
            size = frames.size();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        ArrayList<String> copy = new ArrayList<>(Math.min(size, MAX_FRAMES));
        int end = Math.min(size, MAX_FRAMES);
        for (int index = 0; index < end; index++) {
            try {
                Object frame = frames.get(index);
                if (frame instanceof String value) {
                    copy.add(value);
                }
            } catch (RuntimeException | LinkageError ignored) {
                break;
            }
        }
        return List.copyOf(copy);
    }

    @Nonnull
    private static Map<String, Object> foreignContextMap(@Nonnull Map<?, ?> context) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        copyKnownScalar(context, copy, "observedAtMillis");
        copyKnownScalar(context, copy, "playerCount");
        copyKnownScalar(context, copy, "tps");
        copyKnownScalar(context, copy, "mspt");
        Object rawCounts = valueOf(context, "breadcrumbCategoryCounts");
        if (rawCounts instanceof Map<?, ?> counts) {
            copy.put("breadcrumbCategoryCounts", immutableTreeMap(counts));
        }
        return immutableStringMap(copy);
    }

    private static void copyKnownScalar(@Nonnull Map<?, ?> source,
                                        @Nonnull Map<String, Object> target,
                                        @Nonnull String key) {
        copyKnownScalar(source, target, key, MAX_DETAIL_LENGTH);
    }

    private static void copyKnownScalar(@Nonnull Map<?, ?> source,
                                        @Nonnull Map<String, Object> target,
                                        @Nonnull String key,
                                        int maximumStringLength) {
        Object value = valueOf(source, key);
        Object copied = copyTree(value, 0, new IdentityHashMap<>());
        if (copied instanceof String text && text.length() > maximumStringLength) {
            return;
        }
        if (copied != null && (copied instanceof String
                || copied instanceof Boolean
                || copied instanceof Number)) {
            target.put(key, copied);
        }
    }

    @Nullable
    private static Object valueOf(@Nonnull Map<?, ?> source, @Nonnull String key) {
        try {
            return source.get(key);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Object copyTree(@Nullable Object value,
                                   int depth,
                                   @Nonnull IdentityHashMap<Object, Boolean> active) {
        if (value == null || depth > MAX_TREE_DEPTH) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean) {
            if (value instanceof String text && text.length() > MAX_DETAIL_LENGTH) {
                return null;
            }
            return value;
        }
        if (value instanceof Number number) {
            return finiteNumber(number);
        }
        if (active.put(value, Boolean.TRUE) != null) {
            return null;
        }
        try {
            if (value instanceof List<?> list) {
                final int size;
                try {
                    size = list.size();
                } catch (RuntimeException | LinkageError ignored) {
                    return null;
                }
                int end = Math.min(size, MAX_TREE_ENTRIES);
                ArrayList<Object> copy = new ArrayList<>(end);
                for (int index = 0; index < end; index++) {
                    Object child;
                    try {
                        child = list.get(index);
                    } catch (RuntimeException | LinkageError ignored) {
                        break;
                    }
                    Object copied = copyTree(child, depth + 1, active);
                    if (copied != null) {
                        copy.add(copied);
                    }
                }
                return List.copyOf(copy);
            }
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                int inspected = 0;
                try {
                    Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
                    while (inspected < MAX_TREE_ENTRIES && entries.hasNext()) {
                        Map.Entry<?, ?> entry = entries.next();
                        inspected++;
                        if (entry == null || !(entry.getKey() instanceof String key)
                                || key.length() > MAX_DETAIL_LENGTH) {
                            continue;
                        }
                        Object copied = copyTree(entry.getValue(), depth + 1, active);
                        if (copied != null) {
                            copy.put(key, copied);
                        }
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // Keep the valid prefix when a foreign map fails during iteration.
                }
                return immutableStringMap(copy);
            }
            return null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            active.remove(value);
        }
    }

    @Nullable
    private static Number finiteNumber(@Nonnull Number value) {
        double number;
        try {
            number = value.doubleValue();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
        if (!Double.isFinite(number)) {
            return null;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return value.intValue();
        }
        if (value instanceof Long) {
            return value.longValue();
        }
        return number;
    }

    @Nonnull
    private static Map<String, Object> immutableStringMap(@Nonnull Map<String, Object> source) {
        if (source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Nonnull
    private static String requiredString(@Nonnull Map<?, ?> map,
                                         @Nonnull String key,
                                         int maximumLength) {
        Object value = valueOf(map, key);
        if (!(value instanceof String text)
                || text.isBlank()
                || text.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid profiler text: " + key);
        }
        return text;
    }

    @Nullable
    private static String optionalString(@Nonnull Map<?, ?> map,
                                         @Nonnull String key,
                                         int maximumLength) {
        Object value = valueOf(map, key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Invalid profiler text: " + key);
        }
        if (text.isBlank()) {
            return null;
        }
        if (text.length() > maximumLength) {
            throw new IllegalArgumentException("Oversized profiler text: " + key);
        }
        return text;
    }

    private static boolean requiredBoolean(@Nonnull Map<?, ?> map, @Nonnull String key) {
        Object value = valueOf(map, key);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("Invalid profiler boolean: " + key);
        }
        return bool;
    }

    private static long nonNegativeLong(@Nonnull Map<?, ?> map, @Nonnull String key) {
        Object value = valueOf(map, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid profiler number: " + key);
        }
        double asDouble = number.doubleValue();
        if (!Double.isFinite(asDouble) || asDouble < 0.0d || asDouble != Math.rint(asDouble)
                || asDouble > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid profiler number: " + key);
        }
        return number.longValue();
    }

    private static int nonNegativeInt(@Nonnull Map<?, ?> map, @Nonnull String key) {
        long value = nonNegativeLong(map, key);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid profiler integer: " + key);
        }
        return (int) value;
    }

    @Nullable
    private static Integer optionalNonNegativeInt(@Nonnull Map<?, ?> map, @Nonnull String key) {
        if (valueOf(map, key) == null) {
            return null;
        }
        return nonNegativeInt(map, key);
    }

    private static double nonNegativeDouble(@Nonnull Map<?, ?> map, @Nonnull String key) {
        Object value = valueOf(map, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Invalid profiler number: " + key);
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < 0.0d) {
            throw new IllegalArgumentException("Invalid profiler number: " + key);
        }
        return result;
    }

    @Nullable
    private static Double optionalNonNegativeDouble(@Nonnull Map<?, ?> map, @Nonnull String key) {
        if (valueOf(map, key) == null) {
            return null;
        }
        return nonNegativeDouble(map, key);
    }

    private static int numberAsInt(@Nonnull Object raw) {
        if (!(raw instanceof Number number)) {
            return -1;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0d || value != Math.rint(value)
                || value > Integer.MAX_VALUE) {
            return -1;
        }
        return number.intValue();
    }

    @Nullable
    private static String requiredFingerprint(@Nonnull Map<?, ?> map, @Nonnull String key) {
        Object value = valueOf(map, key);
        if (!(value instanceof String fingerprint) || !FINGERPRINT.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("Invalid profiler fingerprint");
        }
        return fingerprint;
    }

    @Nullable
    private static String validFingerprint(@Nullable String value) {
        return value != null && FINGERPRINT.matcher(value).matches() ? value : null;
    }

    private static void putText(@Nonnull Map<String, Object> target,
                                @Nonnull String key,
                                @Nullable String value,
                                int maximumLength) {
        if (value != null && !value.isBlank()) {
            target.put(key, bounded(value, maximumLength));
        }
    }

    private static void putFiniteNonNegative(@Nonnull Map<String, Object> target,
                                             @Nonnull String key,
                                             @Nullable Double value) {
        if (value != null && Double.isFinite(value) && value >= 0.0d) {
            target.put(key, value);
        }
    }

    @Nonnull
    private static String bounded(@Nonnull String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
