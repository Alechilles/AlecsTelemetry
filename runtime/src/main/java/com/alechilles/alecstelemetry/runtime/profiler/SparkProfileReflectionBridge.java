package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads a bounded summary from tested Spark Hytale builds without linking to Spark.
 */
final class SparkProfileReflectionBridge implements SparkProfileReader {
    private static final String HYTALE_PLUGIN_CLASS = "me.lucko.spark.hytale.HytaleSparkPlugin";
    private static final String SPARK_PLATFORM_CLASS = "me.lucko.spark.common.SparkPlatform";
    private static final String EXPORT_PROPS_CLASS = "me.lucko.spark.common.sampler.Sampler$ExportProps";
    private static final String CLASS_SOURCE_LOOKUP_CLASS =
            "me.lucko.spark.common.sampler.source.ClassSourceLookup";
    private static final Set<String> TESTED_VERSIONS = Set.of(
            "1.10.158",
            "1.10.162",
            "1.10.164",
            "1.10.165",
            "1.10.172"
    );
    private static final Map<String, String> TESTED_ARTIFACTS = Map.ofEntries(
            Map.entry("f57c7a93f77657318340f214fbc37bc244900bc49bd55612f002e85696cc0662", "1.10.158-r1"),
            Map.entry("0d5889ca426a5919bb56a1b36177fbeb86ab143428fec7b236103f4fec874342", "1.10.162-r1"),
            Map.entry("5bd826a5da5a8d9c0b540f2a84de4538de51c3ab199019f7379fdea30ecc5756", "1.10.164-r1"),
            Map.entry("13654be2a1f00047e3fadf69335afdbb45332bd4eb0d222c7754c4685f6684f2", "1.10.165-beta2"),
            Map.entry("4178cccab6afff2d3c2f0a68fb200d39811838fad1a21e595deb4c917eadfc69", "1.10.165-beta3"),
            Map.entry("41a5bbd8ea681525df80d7b08839b85824cfa9e2ecb8ea1837e26809c441603f", "1.10.165-beta4"),
            Map.entry("9c3c762619893fc44289d712e18c57ba3c0675b6b2e1967a36273c75705ebecb", "1.10.165-r1"),
            Map.entry("ed2b28921d1ddd606d612ec602472d79ae606656ec840fe9f8ba593d39f3a159", "1.10.172-beta5"),
            Map.entry("5e536469e7f4511e9ba279114ddf1b634938161bb896e4098a5a4af28966d19f", "1.10.172-beta6"),
            Map.entry("1c5806cdc276af608051b8fc6c3aee05ab184da28e81735ba9682e5950c5617a", "1.10.172-beta7")
    );
    private static final int MAX_DISTINCT_FRAMES = 25_000;
    private static final int MAX_TEXT_LENGTH = 240;

    private final SparkArtifactVerifier artifactVerifier;

    SparkProfileReflectionBridge() {
        this(SparkProfileReflectionBridge::testedArtifactRelease);
    }

    SparkProfileReflectionBridge(@Nonnull SparkArtifactVerifier artifactVerifier) {
        this.artifactVerifier = artifactVerifier;
    }

    @Nonnull
    @Override
    public SparkProfileReadResult read(@Nullable Object sparkPlugin, int maxEntries) {
        if (sparkPlugin == null) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.ABSENT,
                    null,
                    "Spark is not loaded."
            );
        }
        if (!HYTALE_PLUGIN_CLASS.equals(sparkPlugin.getClass().getName())) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.UNSUPPORTED_PLUGIN,
                    null,
                    "The loaded Spark plugin class is not the tested Hytale adapter."
            );
        }

        String version = null;
        try {
            version = safeText(String.valueOf(invokeNoArgs(sparkPlugin, "getVersion")));
            if (!TESTED_VERSIONS.contains(baseVersion(version))) {
                return SparkProfileReadResult.of(
                        SparkProfileReadResult.Status.UNSUPPORTED_VERSION,
                        version,
                        "Spark " + version + " is outside the tested compatibility set."
                );
            }
            String testedRelease = artifactVerifier.testedRelease(sparkPlugin.getClass(), version);
            if (testedRelease == null) {
                return SparkProfileReadResult.of(
                        SparkProfileReadResult.Status.UNSUPPORTED_ARTIFACT,
                        version,
                        "Spark's artifact fingerprint is outside the tested compatibility set."
                );
            }
            return readTestedVersion(sparkPlugin, testedRelease, Math.max(1, Math.min(10, maxEntries)));
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.INCOMPATIBLE,
                    version,
                    "Spark compatibility check failed: " + failureSummary(failure)
            );
        }
    }

    @Nonnull
    private SparkProfileReadResult readTestedVersion(@Nonnull Object sparkPlugin,
                                                     @Nonnull String version,
                                                     int maxEntries) throws Throwable {
        Class<?> pluginClass = sparkPlugin.getClass();
        ClassLoader sparkClassLoader = pluginClass.getClassLoader();
        Class<?> platformClass = Class.forName(SPARK_PLATFORM_CLASS, false, sparkClassLoader);

        Field platformField = pluginClass.getDeclaredField("platform");
        if (!platformField.trySetAccessible()) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.INCOMPATIBLE,
                    version,
                    "Spark denied read-only access to its platform field."
            );
        }
        Object platform = platformField.get(sparkPlugin);
        if (platform == null) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NOT_RUNNING,
                    version,
                    "Spark has no active platform."
            );
        }
        if (!platformClass.isInstance(platform)) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.INCOMPATIBLE,
                    version,
                    "Spark's platform type did not match the tested contract."
            );
        }

        Object samplerContainer = invokeNoArgs(platform, "getSamplerContainer");
        Object sampler = invokeNoArgs(samplerContainer, "getActiveSampler");
        if (sampler == null) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NOT_RUNNING,
                    version,
                    "Spark has no active profiler."
            );
        }
        if (!Boolean.TRUE.equals(invokeNoArgs(sampler, "isRunningInBackground"))) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NOT_BACKGROUND,
                    version,
                    "The active Spark profile was started by a user and was not read."
            );
        }
        String samplerType = String.valueOf(invokeNoArgs(sampler, "getType"));
        String samplerMode = String.valueOf(invokeNoArgs(sampler, "getMode"));
        if (!"ASYNC".equals(samplerType) || !"EXECUTION".equals(samplerMode)) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.UNSUPPORTED_SAMPLER,
                    version,
                    "Only Spark's ASYNC EXECUTION background sampler is supported."
            );
        }

        Object profileData = exportProfileData(sparkClassLoader, platformClass, platform, sampler);
        return summarize(version, profileData, maxEntries);
    }

    @Nonnull
    private static Object exportProfileData(@Nonnull ClassLoader sparkClassLoader,
                                            @Nonnull Class<?> platformClass,
                                            @Nonnull Object platform,
                                            @Nonnull Object sampler) throws Throwable {
        Class<?> exportPropsClass = Class.forName(EXPORT_PROPS_CLASS, true, sparkClassLoader);
        Object exportProps = exportPropsClass.getConstructor().newInstance();

        Class<?> classSourceLookupClass = Class.forName(CLASS_SOURCE_LOOKUP_CLASS, true, sparkClassLoader);
        Method createClassSourceLookup = classSourceLookupClass.getMethod("create", platformClass);
        Supplier<Object> lookupSupplier = () -> invokeSupplier(createClassSourceLookup, platform);
        invoke(
                exportPropsClass.getMethod("classSourceLookup", Supplier.class),
                exportProps,
                lookupSupplier
        );

        Method toProto = sampler.getClass().getMethod("toProto", platformClass, exportPropsClass);
        return invoke(toProto, sampler, platform, exportProps);
    }

    @Nonnull
    private static Object invokeSupplier(@Nonnull Method method, @Nonnull Object platform) {
        try {
            return invoke(method, null, platform);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            throw new IllegalStateException("Spark class-source lookup failed.", failure);
        }
    }

    @Nonnull
    private static SparkProfileReadResult summarize(@Nonnull String version,
                                                    @Nonnull Object profileData,
                                                    int maxEntries) throws Throwable {
        List<?> windows = listValue(invokeNoArgs(profileData, "getTimeWindowsList"));
        if (windows.isEmpty()) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NO_DATA,
                    version,
                    "Spark has not completed a background profile window yet."
            );
        }

        int latestIndex = latestWindowIndex(windows);
        int windowKey = numberValue(windows.get(latestIndex)).intValue();
        List<?> threads = listValue(invokeNoArgs(profileData, "getThreadsList"));
        Map<?, ?> classSources = mapValue(invokeNoArgs(profileData, "getClassSourcesMap"));
        Map<?, ?> methodSources = mapValue(invokeNoArgs(profileData, "getMethodSourcesMap"));

        Map<FrameKey, MutableHotPath> aggregates = new HashMap<>();
        Set<FrameKey> distinctFrames = new HashSet<>();
        int totalFrameCount = 0;
        int selectedThreadCount = 0;
        int selectedFrameCount = 0;
        double totalSelfMilliseconds = 0.0d;
        for (Object thread : threads) {
            List<?> nodes = listValue(invokeNoArgs(thread, "getChildrenList"));
            totalFrameCount += nodes.size();
            String threadName = safeText(String.valueOf(invokeNoArgs(thread, "getName")));
            boolean selectedThread = isWorldThread(threadName);
            if (selectedThread) {
                selectedThreadCount++;
                selectedFrameCount += nodes.size();
            }
            for (Object node : nodes) {
                double inclusive = timeAt(node, latestIndex);
                if (!Double.isFinite(inclusive) || inclusive <= 0.0d) {
                    continue;
                }

                double directChildren = 0.0d;
                for (Object childRefValue : listValue(invokeNoArgs(node, "getChildrenRefsList"))) {
                    int childRef = numberValue(childRefValue).intValue();
                    if (childRef >= 0 && childRef < nodes.size()) {
                        double childTime = timeAt(nodes.get(childRef), latestIndex);
                        if (Double.isFinite(childTime) && childTime > 0.0d) {
                            directChildren += childTime;
                        }
                    }
                }
                double self = inclusive - directChildren;
                double tolerance = Math.max(0.001d, inclusive * 0.000001d);
                if (self < -tolerance) {
                    self = 0.0d;
                } else {
                    self = Math.max(0.0d, self);
                }
                if (self == 0.0d) {
                    continue;
                }

                String className = safeText(String.valueOf(invokeNoArgs(node, "getClassName")));
                String methodName = safeText(String.valueOf(invokeNoArgs(node, "getMethodName")));
                String methodDesc = safeText(String.valueOf(invokeNoArgs(node, "getMethodDesc")));
                String source = sourceFor(classSources, methodSources, className, methodName, methodDesc);
                FrameKey key = new FrameKey(
                        source,
                        className,
                        methodName,
                        methodDesc,
                        selectedThread ? "WORLD_THREAD" : "OTHER_THREAD"
                );
                if (!distinctFrames.add(key)) {
                    if (!selectedThread) {
                        continue;
                    }
                } else if (distinctFrames.size() > MAX_DISTINCT_FRAMES) {
                    return SparkProfileReadResult.of(
                            SparkProfileReadResult.Status.TOO_COMPLEX,
                            version,
                            "Spark's latest completed window exceeded 25,000 distinct frames. No partial summary was kept."
                    );
                }
                if (!selectedThread || !isJavaFrame(className)) {
                    continue;
                }
                totalSelfMilliseconds += self;
                MutableHotPath aggregate = aggregates.get(key);
                if (aggregate == null) {
                    aggregate = new MutableHotPath(key);
                    aggregates.put(key, aggregate);
                }
                aggregate.selfMilliseconds += self;
            }
        }

        if (totalFrameCount == 0) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NO_DATA,
                    version,
                    "Spark's latest completed background window had no usable execution samples."
            );
        }
        if (selectedThreadCount == 0
                || selectedFrameCount == 0
                || totalSelfMilliseconds <= 0.0d
                || aggregates.isEmpty()) {
            return SparkProfileReadResult.noActionableData(
                    version,
                    "Spark's latest completed background window had no usable WorldThread execution samples."
            );
        }

        double total = totalSelfMilliseconds;
        List<SparkProfileSnapshot.HotPath> hotPaths = aggregates.values().stream()
                .sorted(Comparator
                        .comparingDouble((MutableHotPath value) -> value.selfMilliseconds)
                        .reversed()
                        .thenComparing(value -> value.key.frame()))
                .limit(maxEntries)
                .map(value -> value.toHotPath(total))
                .toList();
        return SparkProfileReadResult.complete(new SparkProfileSnapshot(
                version,
                windowKey,
                threads.size(),
                selectedThreadCount,
                totalFrameCount,
                selectedFrameCount,
                totalSelfMilliseconds,
                hotPaths
        ));
    }

    private static double timeAt(@Nonnull Object node, int index) throws Throwable {
        List<?> times = listValue(invokeNoArgs(node, "getTimesList"));
        if (index < 0 || index >= times.size()) {
            return 0.0d;
        }
        return numberValue(times.get(index)).doubleValue();
    }

    @Nonnull
    private static String sourceFor(@Nonnull Map<?, ?> classSources,
                                    @Nonnull Map<?, ?> methodSources,
                                    @Nonnull String className,
                                    @Nonnull String methodName,
                                    @Nonnull String methodDesc) {
        Object source = methodDesc.isBlank()
                ? null
                : methodSources.get(className + ";" + methodName + ";" + methodDesc);
        if (source == null) {
            source = classSources.get(className);
        }
        return source == null ? "<unknown>" : safeText(String.valueOf(source));
    }

    private static boolean isWorldThread(@Nonnull String threadName) {
        return threadName.contains("WorldThread");
    }

    private static boolean isJavaFrame(@Nonnull String className) {
        return !className.isBlank() && !"native".equals(className);
    }

    private static int latestWindowIndex(@Nonnull List<?> windows) {
        int latestIndex = 0;
        int latestWindow = numberValue(windows.getFirst()).intValue();
        for (int index = 1; index < windows.size(); index++) {
            int candidate = numberValue(windows.get(index)).intValue();
            if (candidate > latestWindow) {
                latestWindow = candidate;
                latestIndex = index;
            }
        }
        return latestIndex;
    }

    @Nonnull
    private static Number numberValue(@Nullable Object value) {
        return value instanceof Number number ? number : 0;
    }

    @Nonnull
    private static List<?> listValue(@Nullable Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    @Nonnull
    private static Map<?, ?> mapValue(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    @Nullable
    private static Object invokeNoArgs(@Nonnull Object target, @Nonnull String methodName) throws Throwable {
        return invoke(target.getClass().getMethod(methodName), target);
    }

    @Nullable
    private static Object invoke(@Nonnull Method method,
                                 @Nullable Object target,
                                 @Nullable Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    @Nonnull
    private static String baseVersion(@Nonnull String version) {
        int suffix = version.indexOf('-');
        return suffix < 0 ? version : version.substring(0, suffix);
    }

    @Nullable
    private static String testedArtifactRelease(@Nonnull Class<?> pluginClass,
                                                @Nonnull String reportedVersion) throws Exception {
        CodeSource codeSource = pluginClass.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        URL location = codeSource.getLocation();
        if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
            return null;
        }
        URI artifactUri = location.toURI();
        Path artifactPath = Path.of(artifactUri);
        if (!Files.isRegularFile(artifactPath)) {
            return null;
        }

        String release = TESTED_ARTIFACTS.get(sha256(artifactPath));
        if (release == null || !baseVersion(release).equals(baseVersion(reportedVersion))) {
            return null;
        }
        return release;
    }

    @Nonnull
    private static String sha256(@Nonnull Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Nonnull
    private static String safeText(@Nullable String text) {
        if (text == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(text.length(), MAX_TEXT_LENGTH));
        for (int index = 0; index < text.length() && safe.length() < MAX_TEXT_LENGTH; index++) {
            char character = text.charAt(index);
            safe.append(Character.isISOControl(character) ? ' ' : character);
        }
        return safe.toString().trim();
    }

    @Nonnull
    private static String failureSummary(@Nonnull Throwable failure) {
        String message = safeText(failure.getMessage());
        return failure.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }

    private static void rethrowIfFatal(@Nonnull Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(failure.getClass().getName())) {
            throw (Error) failure;
        }
    }

    private record FrameKey(@Nonnull String source,
                            @Nonnull String className,
                            @Nonnull String methodName,
                            @Nonnull String methodDesc,
                            @Nonnull String threadScope) {
        @Nonnull
        private String frame() {
            return safeText(className + "#" + methodName + methodDesc);
        }
    }

    private static final class MutableHotPath {
        private final FrameKey key;
        private double selfMilliseconds;

        private MutableHotPath(@Nonnull FrameKey key) {
            this.key = key;
        }

        @Nonnull
        private SparkProfileSnapshot.HotPath toHotPath(double totalSelfMilliseconds) {
            return new SparkProfileSnapshot.HotPath(
                    key.source(),
                    key.frame(),
                    selfMilliseconds,
                    selfMilliseconds * 100.0d / totalSelfMilliseconds
            );
        }
    }
}

@FunctionalInterface
interface SparkArtifactVerifier {
    @Nullable
    String testedRelease(@Nonnull Class<?> pluginClass, @Nonnull String reportedVersion) throws Throwable;
}
