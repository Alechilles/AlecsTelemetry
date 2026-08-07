package com.alechilles.alecstelemetry.coordinator;

import com.hypixel.hytale.common.semver.Semver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process-wide, classloader-safe election registry for telemetry project contributions.
 *
 * <p>The only shared state is a JDK {@link ConcurrentHashMap} held by a system property. Raw
 * bridge objects stay in that map, while every public snapshot is made up solely of defensive
 * JDK collections and scalar values. Election and token validation share the map monitor so a
 * retiring token is fenced before a replacement token can dispatch.</p>
 */
public final class TelemetryProjectContributionRegistry {

    public static final int CONTRIBUTION_ABI_VERSION = TelemetryProjectContributionCandidate.CONTRIBUTION_ABI_VERSION;
    public static final int ABI_VERSION = CONTRIBUTION_ABI_VERSION;
    public static final String STANDALONE_ORIGIN = TelemetryProjectContributionCandidate.STANDALONE_ORIGIN;
    public static final String EMBEDDED_ORIGIN = TelemetryProjectContributionCandidate.EMBEDDED_ORIGIN;
    public static final String REGISTRY_KEY = "com.alechilles.alecstelemetry.project-contribution.registry.v1";

    private static final String STATE_REVISION_KEY = "__state.revision";
    private static final String STATE_WINNERS_KEY = "__state.winners";
    private static final String STATE_LINEAGES_KEY = "__state.lineages";
    private static final String STATE_DIAGNOSTICS_KEY = "__state.diagnostics";
    private static final String STATE_FINGERPRINT_KEY = "__state.fingerprint";
    private static final int MAX_DIAGNOSTICS = 64;
    private static final Logger LOGGER = Logger.getLogger(TelemetryProjectContributionRegistry.class.getName());

    private static final List<String> CANDIDATE_KEYS = List.of(
            "abiVersion",
            "projectId",
            "logicalPluginIdentifier",
            "logicalPluginVersion",
            "origin",
            "hostPluginIdentifier",
            "hostPluginVersion",
            "sourcePath",
            "descriptorJson",
            "descriptorHash"
    );

    private TelemetryProjectContributionRegistry() {
    }

    /** Registers a bridge and returns an opaque token for its candidate. */
    public static String register(Object bridge) {
        Objects.requireNonNull(bridge, "bridge");
        String token = UUID.randomUUID().toString();
        Map<String, Object> candidate = candidateFromBridge(bridge);
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>(candidate);
        entry.put("token", token);
        entry.put("bridge", bridge);

        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            registry.put(token, Collections.unmodifiableMap(entry));
            reconcileLocked(registry);
        }
        return token;
    }

    /** Removes a token and publishes the next complete election snapshot atomically. */
    public static void unregister(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            Object removed = registry.remove(token);
            if (removed == null) {
                for (Map.Entry<String, Object> item : List.copyOf(registry.entrySet())) {
                    if (isEntry(item.getValue()) && token.equals(item.getValue() instanceof Map<?, ?> map
                            ? stringValue(map.get("token"))
                            : "")) {
                        registry.remove(item.getKey(), item.getValue());
                        break;
                    }
                }
            }
            if (removed != null || !token.isBlank()) {
                reconcileLocked(registry);
            }
        }
    }

    /** Returns whether this token is the current winner for its project. */
    public static boolean isActive(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            reconcileLocked(registry);
            Map<String, String> winners = winnersLocked(registry);
            return winners.containsValue(token);
        }
    }

    /** Returns the immutable winning contribution snapshot without raw bridge objects. */
    public static List<Map<String, Object>> activeContributions() {
        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            reconcileLocked(registry);
            ArrayList<Map<String, Object>> active = new ArrayList<>();
            for (String token : winnersLocked(registry).values()) {
                Map<String, Object> entry = entryForTokenLocked(registry, token);
                if (entry == null) {
                    continue;
                }
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                for (String key : CANDIDATE_KEYS) {
                    copy.put(key, entry.getOrDefault(key, ""));
                }
                copy.put("token", token);
                active.add(Collections.unmodifiableMap(copy));
            }
            active.sort(Comparator.comparing(
                    candidate -> stringValue(candidate.get("projectId")).toLowerCase(Locale.ROOT)
            ));
            return List.copyOf(active);
        }
    }

    /** Returns bounded registry diagnostics, including owner conflicts and descriptor drift. */
    public static List<Map<String, Object>> diagnostics() {
        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            reconcileLocked(registry);
            Object raw = registry.get(STATE_DIAGNOSTICS_KEY);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            ArrayList<Map<String, Object>> copy = new ArrayList<>();
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) {
                    copy.add(copyStringObjectMap(map));
                }
            }
            return List.copyOf(copy);
        }
    }

    /** Returns the monotonic snapshot revision used by coordinator replay. */
    public static long revision() {
        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            reconcileLocked(registry);
            Object value = registry.get(STATE_REVISION_KEY);
            return value instanceof Number number ? number.longValue() : 0L;
        }
    }

    /** Dispatches through the current winning bridge while holding the election monitor. */
    public static Map<String, Object> dispatch(String token,
                                               String operation,
                                               Map<String, Object> payload,
                                               Throwable throwable) {
        if (token == null || token.isBlank()) {
            return rejection("missing_token");
        }
        if (operation == null || operation.isBlank()) {
            return rejection("missing_operation");
        }

        ConcurrentHashMap<String, Object> registry = registry();
        synchronized (registry) {
            reconcileLocked(registry);
            Map<String, String> winners = winnersLocked(registry);
            if (!winners.containsValue(token)) {
                return rejection("inactive_token");
            }
            Map<String, Object> entry = entryForTokenLocked(registry, token);
            if (entry == null) {
                return rejection("inactive_token");
            }
            Object bridge = entry.get("bridge");
            if (bridge == null) {
                return rejection("bridge_unavailable");
            }
            try {
                Object result = invoke(
                        bridge,
                        "dispatch",
                        new Class<?>[]{String.class, String.class, Map.class, Throwable.class},
                        token,
                        operation,
                        copyJdkMap(payload),
                        throwable
                );
                return acceptedResult(result);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return rejection("bridge_dispatch_failed");
            }
        }
    }

    /** Removes only this registry's shared property and state. */
    public static void clearForTests() {
        Object existing = System.getProperties().get(REGISTRY_KEY);
        if (existing instanceof ConcurrentHashMap<?, ?> map) {
            synchronized (map) {
                map.clear();
            }
        }
        System.getProperties().remove(REGISTRY_KEY);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> registry() {
        Properties properties = System.getProperties();
        Object existing = properties.get(REGISTRY_KEY);
        if (existing instanceof ConcurrentHashMap<?, ?> map) {
            return (ConcurrentHashMap<String, Object>) map;
        }
        ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<>();
        Object raced = properties.putIfAbsent(REGISTRY_KEY, created);
        return raced instanceof ConcurrentHashMap<?, ?> map
                ? (ConcurrentHashMap<String, Object>) map
                : created;
    }

    private static void reconcileLocked(ConcurrentHashMap<String, Object> registry) {
        List<Map<String, Object>> entries = entriesLocked(registry);
        String fingerprint = fingerprint(entries);
        String previousFingerprint = stringValue(registry.get(STATE_FINGERPRINT_KEY));
        if (fingerprint.equals(previousFingerprint)
                && registry.get(STATE_WINNERS_KEY) instanceof Map<?, ?>
                && registry.get(STATE_LINEAGES_KEY) instanceof Map<?, ?>
                && registry.get(STATE_DIAGNOSTICS_KEY) instanceof List<?>) {
            return;
        }

        LinkedHashMap<String, String> lineages = new LinkedHashMap<>(stringMap(registry.get(STATE_LINEAGES_KEY)));
        LinkedHashMap<String, String> winners = new LinkedHashMap<>();
        ArrayList<Map<String, Object>> diagnostics = new ArrayList<>();
        LinkedHashMap<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> entry : entries) {
            TelemetryProjectContributionCandidate candidate = candidateFromEntry(entry);
            String projectId = candidate.normalizedProjectId();
            if (projectId.isBlank()) {
                addDiagnostic(diagnostics, diagnostic(
                        "invalid_candidate", "", stringValue(entry.get("token")), candidate.invalidReason()
                ));
                continue;
            }
            grouped.computeIfAbsent(projectId, ignored -> new ArrayList<>()).add(entry);
            if (!candidate.valid()) {
                addDiagnostic(diagnostics, diagnostic(
                        "invalid_candidate", candidate.projectId(), stringValue(entry.get("token")), candidate.invalidReason()
                ));
            }
        }

        for (Map.Entry<String, List<Map<String, Object>>> group : grouped.entrySet()) {
            String projectId = group.getKey();
            ArrayList<Map<String, Object>> valid = new ArrayList<>();
            for (Map<String, Object> entry : group.getValue()) {
                TelemetryProjectContributionCandidate candidate = candidateFromEntry(entry);
                if (candidate.valid()) {
                    valid.add(entry);
                }
            }
            if (valid.isEmpty()) {
                continue;
            }

            String lineage = lineages.get(projectId);
            if (lineage == null || lineage.isBlank()) {
                Map<String, Object> initialWinner = valid.stream().max(ELECTION_ORDER).orElse(null);
                if (initialWinner == null) {
                    continue;
                }
                lineage = candidateFromEntry(initialWinner).normalizedOwner();
                lineages.put(projectId, lineage);
            }

            String establishedLineage = lineage;
            ArrayList<Map<String, Object>> ownerCandidates = new ArrayList<>();
            for (Map<String, Object> entry : valid) {
                TelemetryProjectContributionCandidate candidate = candidateFromEntry(entry);
                if (establishedLineage.equals(candidate.normalizedOwner())) {
                    ownerCandidates.add(entry);
                } else {
                    addDiagnostic(diagnostics, diagnostic(
                            "owner_conflict",
                            candidate.projectId(),
                            stringValue(entry.get("token")),
                            "logical_owner_lineage_established"
                    ));
                }
            }
            if (ownerCandidates.isEmpty()) {
                continue;
            }

            Map<String, Object> winner = ownerCandidates.stream().max(ELECTION_ORDER).orElse(null);
            if (winner == null) {
                continue;
            }
            winners.put(projectId, stringValue(winner.get("token")));

            addDescriptorDriftDiagnostics(diagnostics, ownerCandidates, projectId);
        }

        long previousRevision = numberValue(registry.get(STATE_REVISION_KEY));
        long nextRevision = entries.isEmpty()
                && registry.get(STATE_REVISION_KEY) == null
                ? previousRevision
                : previousRevision + 1L;
        registry.put(STATE_WINNERS_KEY, immutableStringMap(winners));
        registry.put(STATE_LINEAGES_KEY, immutableStringMap(lineages));
        registry.put(STATE_DIAGNOSTICS_KEY, immutableDiagnosticList(diagnostics));
        registry.put(STATE_FINGERPRINT_KEY, fingerprint);
        registry.put(STATE_REVISION_KEY, nextRevision);
    }

    private static final Comparator<Map<String, Object>> ELECTION_ORDER = (left, right) -> {
        TelemetryProjectContributionCandidate a = candidateFromEntry(left);
        TelemetryProjectContributionCandidate b = candidateFromEntry(right);
        int comparison = compareVersions(a, b);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(originRank(a.origin()), originRank(b.origin()));
        if (comparison != 0) {
            return comparison;
        }
        comparison = lexicalAscendingWinner(a.normalizedHostPluginIdentifier(), b.normalizedHostPluginIdentifier());
        if (comparison != 0) {
            return comparison;
        }
        comparison = lexicalAscendingWinner(a.normalizedSourcePath(), b.normalizedSourcePath());
        if (comparison != 0) {
            return comparison;
        }
        comparison = lexicalAscendingWinner(a.normalizedDescriptorHash(), b.normalizedDescriptorHash());
        if (comparison != 0) {
            return comparison;
        }
        return lexicalAscendingWinner(stringValue(left.get("token")), stringValue(right.get("token")));
    };

    private static int compareVersions(TelemetryProjectContributionCandidate left,
                                       TelemetryProjectContributionCandidate right) {
        try {
            return left.semanticVersion().compareTo(right.semanticVersion());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int originRank(String origin) {
        return TelemetryProjectContributionCandidate.STANDALONE_ORIGIN.equals(origin) ? 1 : 0;
    }

    /** A positive result means the lexically smaller value wins in max(). */
    private static int lexicalAscendingWinner(String left, String right) {
        return right.compareToIgnoreCase(left);
    }

    private static void addDescriptorDriftDiagnostics(ArrayList<Map<String, Object>> diagnostics,
                                                       List<Map<String, Object>> candidates,
                                                       String projectId) {
        LinkedHashSet<String> hashesByVersion = new LinkedHashSet<>();
        for (Map<String, Object> entry : candidates) {
            TelemetryProjectContributionCandidate candidate = candidateFromEntry(entry);
            for (Map<String, Object> otherEntry : candidates) {
                TelemetryProjectContributionCandidate other = candidateFromEntry(otherEntry);
                if (compareVersions(candidate, other) == 0
                        && !candidate.normalizedDescriptorHash().equals(other.normalizedDescriptorHash())) {
                    hashesByVersion.add(candidate.normalizedDescriptorHash());
                    hashesByVersion.add(other.normalizedDescriptorHash());
                }
            }
        }
        if (!hashesByVersion.isEmpty()) {
            if (diagnostics.size() < MAX_DIAGNOSTICS) {
                addDiagnostic(diagnostics, diagnostic(
                        "descriptor_drift", projectId, "", "same_version_descriptor_snapshot_drift"
                ));
                LOGGER.log(Level.WARNING, "Telemetry contribution descriptor drift for project " + bounded(projectId));
            }
        }
    }

    private static List<Map<String, Object>> entriesLocked(ConcurrentHashMap<String, Object> registry) {
        ArrayList<Map<String, Object>> entries = new ArrayList<>();
        for (Object value : registry.values()) {
            if (isEntry(value)) {
                entries.add(copyEntry((Map<?, ?>) value));
            }
        }
        entries.sort(Comparator.comparing(entry -> stringValue(entry.get("token"))));
        return List.copyOf(entries);
    }

    private static boolean isEntry(Object value) {
        return value instanceof Map<?, ?> map
                && map.get("token") != null
                && map.get("bridge") != null;
    }

    private static Map<String, Object> entryForTokenLocked(ConcurrentHashMap<String, Object> registry,
                                                            String token) {
        Object direct = registry.get(token);
        if (isEntry(direct) && token.equals(stringValue(((Map<?, ?>) direct).get("token")))) {
            return copyEntry((Map<?, ?>) direct);
        }
        for (Object value : registry.values()) {
            if (isEntry(value) && token.equals(stringValue(((Map<?, ?>) value).get("token")))) {
                return copyEntry((Map<?, ?>) value);
            }
        }
        return null;
    }

    private static Map<String, String> winnersLocked(ConcurrentHashMap<String, Object> registry) {
        return stringMap(registry.get(STATE_WINNERS_KEY));
    }

    private static Map<String, Object> candidateFromBridge(Object bridge) {
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        int bridgeAbi = 0;
        try {
            Object rawAbi = invoke(bridge, "contributionAbiVersion", new Class<?>[0]);
            bridgeAbi = rawAbi instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(rawAbi));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the ABI incompatible, but still return a token that can be fenced normally.
        }
        try {
            Object raw = invoke(bridge, "candidate", new Class<?>[0]);
            if (raw instanceof Map<?, ?> values) {
                for (String key : CANDIDATE_KEYS) {
                    if (values.containsKey(key)) {
                        candidate.put(key, copyJdkValue(values.get(key)));
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Missing bridge methods are represented by invalid metadata below.
        }
        candidate.putIfAbsent("abiVersion", bridgeAbi);
        for (String key : CANDIDATE_KEYS) {
            candidate.putIfAbsent(key, key.equals("abiVersion") ? 0 : "");
        }
        return TelemetryProjectContributionCandidate.fromMap(candidate).toMap();
    }

    private static TelemetryProjectContributionCandidate candidateFromEntry(Map<String, Object> entry) {
        return TelemetryProjectContributionCandidate.fromMap(entry);
    }

    private static String fingerprint(List<Map<String, Object>> entries) {
        StringBuilder value = new StringBuilder();
        for (Map<String, Object> entry : entries) {
            value.append(stringValue(entry.get("token"))).append('|');
            for (String key : CANDIDATE_KEYS) {
                value.append(key).append('=').append(stringValue(entry.get(key))).append('|');
            }
            value.append('\n');
        }
        return value.toString();
    }

    private static Map<String, Object> diagnostic(String kind,
                                                  String projectId,
                                                  String token,
                                                  String reason) {
        LinkedHashMap<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("kind", bounded(kind));
        diagnostic.put("projectId", bounded(projectId));
        if (token != null && !token.isBlank()) {
            diagnostic.put("token", bounded(token));
        }
        diagnostic.put("reason", bounded(reason));
        return Collections.unmodifiableMap(diagnostic);
    }

    private static void addDiagnostic(ArrayList<Map<String, Object>> diagnostics,
                                      Map<String, Object> diagnostic) {
        if (diagnostics.size() < MAX_DIAGNOSTICS) {
            diagnostics.add(diagnostic);
        }
    }

    private static Map<String, Object> rejection(String reason) {
        return Map.of("accepted", false, "reason", bounded(reason));
    }

    private static Map<String, Object> acceptedResult(Object rawResult) {
        if (!(rawResult instanceof Map<?, ?> map)) {
            return Map.of("accepted", true);
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), copyJdkValue(entry.getValue()));
            }
        }
        Object accepted = copy.get("accepted");
        if (!(accepted instanceof Boolean)) {
            copy.put("accepted", true);
        } else if (Boolean.FALSE.equals(accepted) && !copy.containsKey("reason")) {
            copy.put("reason", "bridge_rejected");
        }
        if (copy.containsKey("reason")) {
            copy.put("reason", bounded(stringValue(copy.get("reason"))));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> copyEntry(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (String key : CANDIDATE_KEYS) {
            copy.put(key, copyJdkValue(source.get(key)));
        }
        copy.put("token", stringValue(source.get("token")));
        copy.put("bridge", source.get("bridge"));
        return copy;
    }

    private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), copyJdkValue(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> copyJdkMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), copyJdkValue(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyJdkValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return copyJdkMap(map);
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyJdkValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> copy = new ArrayList<>();
            for (Object item : iterable) {
                copy.add(copyJdkValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value.toString();
    }

    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return copy;
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static List<Map<String, Object>> immutableDiagnosticList(List<Map<String, Object>> values) {
        ArrayList<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> value : values) {
            copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(value)));
        }
        return List.copyOf(copy);
    }

    private static long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static Object invoke(Object target,
                                 String methodName,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        if (!method.trySetAccessible()) {
            method.setAccessible(true);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static Method findMethod(Class<?> type,
                                     String methodName,
                                     Class<?>[] parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return type.getMethod(methodName, parameterTypes);
    }
}
