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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Reads one bounded window from a tested Spark Hytale build without linking to Spark.
 */
final class SparkProfileReflectionBridge implements SparkProfileReader {
    private static final String HYTALE_PLUGIN_CLASS = "me.lucko.spark.hytale.HytaleSparkPlugin";
    private static final String SPARK_PLATFORM_CLASS = "me.lucko.spark.common.SparkPlatform";
    private static final String ASYNC_SAMPLER_CLASS =
            "me.lucko.spark.common.sampler.async.AsyncSampler";
    private static final String ABSTRACT_AGGREGATOR_CLASS =
            "me.lucko.spark.common.sampler.aggregator.AbstractDataAggregator";
    private static final String ABSTRACT_NODE_CLASS =
            "me.lucko.spark.common.sampler.node.AbstractNode";
    private static final String THREAD_NODE_CLASS =
            "me.lucko.spark.common.sampler.node.ThreadNode";
    private static final String STACK_NODE_CLASS =
            "me.lucko.spark.common.sampler.node.StackTraceNode";
    private static final Set<String> TESTED_VERSIONS = Set.of("1.10.172");
    private static final Map<String, String> TESTED_ARTIFACTS = Map.of(
            "1c5806cdc276af608051b8fc6c3aee05ab184da28e81735ba9682e5950c5617a",
            "1.10.172-beta7"
    );
    private static final int MAX_ACTIVE_NODES = 25_000;
    private static final long MAX_READ_NANOS = 20_000_000L;
    private static final int DEADLINE_CHECK_MASK = 63;
    private static final int MAX_TEXT_LENGTH = 240;
    private static final String UNKNOWN_SOURCE = "<unknown>";

    private final SparkArtifactVerifier artifactVerifier;
    private final LongSupplier nanoTime;
    private final long maxReadNanos;
    private volatile AccessPlan accessPlan;

    SparkProfileReflectionBridge() {
        this(SparkProfileReflectionBridge::testedArtifactRelease);
    }

    SparkProfileReflectionBridge(@Nonnull SparkArtifactVerifier artifactVerifier) {
        this(artifactVerifier, System::nanoTime, MAX_READ_NANOS);
    }

    SparkProfileReflectionBridge(@Nonnull SparkArtifactVerifier artifactVerifier,
                                 @Nonnull LongSupplier nanoTime,
                                 long maxReadNanos) {
        this.artifactVerifier = artifactVerifier;
        this.nanoTime = nanoTime;
        this.maxReadNanos = Math.max(0L, maxReadNanos);
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
        String testedRelease = null;
        try {
            version = safeText(String.valueOf(invokeNoArgs(sparkPlugin, "getVersion")));
            if (!TESTED_VERSIONS.contains(baseVersion(version))) {
                return SparkProfileReadResult.of(
                        SparkProfileReadResult.Status.UNSUPPORTED_VERSION,
                        version,
                        "Spark " + version + " is outside the tested compatibility set."
                );
            }
            testedRelease = artifactVerifier.testedRelease(sparkPlugin.getClass(), version);
            if (testedRelease == null) {
                return SparkProfileReadResult.of(
                        SparkProfileReadResult.Status.UNSUPPORTED_ARTIFACT,
                        version,
                        "Spark's artifact fingerprint is outside the tested compatibility set."
                );
            }
            return readTestedVersion(
                    sparkPlugin,
                    testedRelease,
                    Math.max(1, Math.min(10, maxEntries))
            );
        } catch (ActiveNodeLimitExceeded ignored) {
            return tooComplexNodes(testedRelease == null ? version : testedRelease);
        } catch (ReadBudgetExceeded ignored) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.TOO_COMPLEX,
                    testedRelease == null ? version : testedRelease,
                    "Spark's latest window exceeded the 20 ms direct-read budget. No partial summary was kept."
            );
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
        requireAccessible(platformField, "Spark denied read-only access to its platform field.");
        Object platform = platformField.get(sparkPlugin);
        if (platform == null) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NOT_RUNNING,
                    version,
                    "Spark has no active platform."
            );
        }
        if (!platformClass.isInstance(platform)) {
            throw new IllegalStateException("Spark's platform type did not match the tested contract.");
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
        if (!ASYNC_SAMPLER_CLASS.equals(sampler.getClass().getName())) {
            throw new IllegalStateException("Spark's sampler class did not match the tested contract.");
        }

        AccessPlan plan = accessPlan(sampler.getClass(), sparkClassLoader);
        Object mutex = plan.currentJobMutex().get(sampler);
        Object aggregator = plan.dataAggregator().get(sampler);
        if (mutex == null || !plan.aggregatorClass().isInstance(aggregator)) {
            throw new IllegalStateException("Spark's sampler state did not match the tested contract.");
        }

        long startedAt = nanoTime.getAsLong();
        synchronized (mutex) {
            ReadBudget budget = new ReadBudget(nanoTime, startedAt, maxReadNanos);
            budget.checkNow();
            return summarizeDirect(version, aggregator, plan, maxEntries, budget);
        }
    }

    @Nonnull
    private AccessPlan accessPlan(@Nonnull Class<?> samplerClass,
                                  @Nonnull ClassLoader classLoader) throws Exception {
        AccessPlan cached = accessPlan;
        if (cached != null && cached.samplerClass() == samplerClass) {
            return cached;
        }

        Class<?> aggregatorClass = Class.forName(ABSTRACT_AGGREGATOR_CLASS, false, classLoader);
        Class<?> abstractNodeClass = Class.forName(ABSTRACT_NODE_CLASS, false, classLoader);
        Class<?> threadNodeClass = Class.forName(THREAD_NODE_CLASS, false, classLoader);
        Class<?> stackNodeClass = Class.forName(STACK_NODE_CLASS, false, classLoader);
        Field dataAggregator = samplerClass.getDeclaredField("dataAggregator");
        Field currentJobMutex = samplerClass.getDeclaredField("currentJobMutex");
        Field threadData = aggregatorClass.getDeclaredField("threadData");
        Field children = abstractNodeClass.getDeclaredField("children");
        Field times = abstractNodeClass.getDeclaredField("times");
        requireAccessible(dataAggregator, "Spark denied access to its data aggregator.");
        requireAccessible(currentJobMutex, "Spark denied access to its rotation mutex.");
        requireAccessible(threadData, "Spark denied access to its retained thread map.");
        requireAccessible(children, "Spark denied access to its retained child map.");
        requireAccessible(times, "Spark denied access to its retained time map.");

        AccessPlan created = new AccessPlan(
                samplerClass,
                aggregatorClass,
                threadNodeClass,
                stackNodeClass,
                dataAggregator,
                currentJobMutex,
                threadData,
                children,
                times,
                threadNodeClass.getMethod("getThreadLabel"),
                stackNodeClass.getMethod("getClassName"),
                stackNodeClass.getMethod("getMethodName"),
                stackNodeClass.getMethod("getMethodDescription")
        );
        accessPlan = created;
        return created;
    }

    @Nonnull
    private static SparkProfileReadResult summarizeDirect(@Nonnull String version,
                                                          @Nonnull Object aggregator,
                                                          @Nonnull AccessPlan plan,
                                                          int maxEntries,
                                                          @Nonnull ReadBudget budget) throws Throwable {
        Map<?, ?> threadData = mapValue(
                plan.threadData().get(aggregator),
                "Spark's retained thread data was not a map."
        );
        if (threadData.isEmpty()) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NO_DATA,
                    version,
                    "Spark has not completed a background profile window yet."
            );
        }

        int windowKey = latestWindowKey(threadData.values(), plan, budget);
        if (windowKey == Integer.MIN_VALUE) {
            return SparkProfileReadResult.of(
                    SparkProfileReadResult.Status.NO_DATA,
                    version,
                    "Spark has not completed a background profile window yet."
            );
        }

        Map<FrameKey, MutableHotPath> aggregates = new HashMap<>();
        List<SparkProfileWindow.ThreadFrames> selectedThreads = new ArrayList<>();
        NodeVisitStack stack = new NodeVisitStack();
        Integer boxedWindowKey = windowKey;
        int totalFrameCount = 0;
        int selectedThreadCount = 0;
        int selectedFrameCount = 0;
        double totalSelfMilliseconds = 0.0d;

        for (Object threadNode : threadData.values()) {
            budget.check();
            if (!plan.threadNodeClass().isInstance(threadNode)) {
                throw new IllegalStateException("Spark retained an unexpected thread node type.");
            }
            String threadName = safeText(String.valueOf(invoke(plan.threadLabel(), threadNode)));
            boolean selectedThread = isWorldThread(threadName);
            if (selectedThread) {
                selectedThreadCount++;
            }

            ArrayList<SparkProfileWindow.Frame> frames = selectedThread
                    ? new ArrayList<>()
                    : null;
            stack.clear();
            pushActiveChildren(stack, threadNode, -1, boxedWindowKey, plan, budget);
            while (!stack.isEmpty()) {
                Object node = stack.popNode();
                int parentIndex = stack.poppedParentIndex();
                long inclusiveMicros = stack.poppedInclusiveMicros();
                totalFrameCount++;
                if (totalFrameCount > MAX_ACTIVE_NODES) {
                    return tooComplexNodes(version);
                }
                if (!selectedThread) {
                    pushActiveChildren(stack, node, -1, boxedWindowKey, plan, budget);
                    continue;
                }

                selectedFrameCount++;
                String className = safeText(String.valueOf(invoke(plan.className(), node)));
                String methodName = safeText(String.valueOf(invoke(plan.methodName(), node)));
                String methodDesc = safeText(String.valueOf(invoke(plan.methodDescription(), node)));
                boolean javaFrame = isJavaFrame(className, methodDesc);
                int retainedIndex = javaFrame ? frames.size() : parentIndex;
                long directChildrenMicros = pushActiveChildren(
                        stack,
                        node,
                        retainedIndex,
                        boxedWindowKey,
                        plan,
                        budget
                );
                if (!javaFrame) {
                    continue;
                }

                double selfMilliseconds = Math.max(0L, inclusiveMicros - directChildrenMicros) / 1000.0d;
                frames.add(new SparkProfileWindow.Frame(
                        parentIndex,
                        UNKNOWN_SOURCE,
                        className,
                        methodName,
                        methodDesc,
                        selfMilliseconds
                ));
                if (selfMilliseconds <= 0.0d) {
                    continue;
                }
                totalSelfMilliseconds += selfMilliseconds;
                FrameKey key = new FrameKey(
                        UNKNOWN_SOURCE,
                        className,
                        methodName,
                        methodDesc,
                        "WORLD_THREAD"
                );
                MutableHotPath aggregate = aggregates.computeIfAbsent(key, MutableHotPath::new);
                aggregate.selfMilliseconds += selfMilliseconds;
            }
            if (selectedThread) {
                selectedThreads.add(new SparkProfileWindow.ThreadFrames(threadName, frames));
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
                        .thenComparing(value -> value.frame))
                .limit(maxEntries)
                .map(value -> value.toHotPath(total))
                .toList();
        return SparkProfileReadResult.complete(new SparkProfileSnapshot(
                version,
                windowKey,
                threadData.size(),
                selectedThreadCount,
                totalFrameCount,
                selectedFrameCount,
                totalSelfMilliseconds,
                hotPaths
        ), new SparkProfileWindow(
                version,
                windowKey,
                threadData.size(),
                selectedThreadCount,
                totalFrameCount,
                selectedFrameCount,
                totalSelfMilliseconds,
                selectedThreads
        ));
    }

    private static int latestWindowKey(@Nonnull Collection<?> threadNodes,
                                       @Nonnull AccessPlan plan,
                                       @Nonnull ReadBudget budget) throws IllegalAccessException {
        int latest = Integer.MIN_VALUE;
        for (Object threadNode : threadNodes) {
            Map<?, ?> times = mapValue(
                    plan.times().get(threadNode),
                    "Spark retained node times were not a map."
            );
            for (Object key : times.keySet()) {
                budget.check();
                if (key instanceof Integer candidate && candidate > latest) {
                    latest = candidate;
                }
            }
        }
        return latest;
    }

    private static long pushActiveChildren(@Nonnull NodeVisitStack stack,
                                           @Nonnull Object parent,
                                           int parentIndex,
                                           @Nonnull Integer windowKey,
                                           @Nonnull AccessPlan plan,
                                           @Nonnull ReadBudget budget) throws IllegalAccessException {
        Map<?, ?> children = mapValue(
                plan.children().get(parent),
                "Spark retained node children were not a map."
        );
        long directChildrenMicros = 0L;
        for (Object child : children.values()) {
            budget.check();
            if (!plan.stackNodeClass().isInstance(child)) {
                throw new IllegalStateException("Spark retained an unexpected stack node type.");
            }
            long childMicros = timeValue(child, windowKey, plan);
            if (childMicros <= 0L) {
                continue;
            }
            directChildrenMicros = saturatingAdd(directChildrenMicros, childMicros);
            stack.push(child, parentIndex, childMicros);
        }
        return directChildrenMicros;
    }

    private static long timeValue(@Nonnull Object node,
                                  @Nonnull Integer windowKey,
                                  @Nonnull AccessPlan plan) throws IllegalAccessException {
        Map<?, ?> times = mapValue(
                plan.times().get(node),
                "Spark retained node times were not a map."
        );
        Object value = times.get(windowKey);
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    @Nonnull
    private static SparkProfileReadResult tooComplexNodes(@Nullable String version) {
        return SparkProfileReadResult.of(
                SparkProfileReadResult.Status.TOO_COMPLEX,
                version,
                "Spark's latest completed window exceeded 25,000 active nodes. No partial summary was kept."
        );
    }

    private static boolean isWorldThread(@Nonnull String threadName) {
        return threadName.contains("WorldThread");
    }

    private static boolean isJavaFrame(@Nonnull String className,
                                       @Nonnull String methodDesc) {
        return !className.isBlank()
                && !"native".equals(className)
                && isJvmMethodDescriptor(methodDesc);
    }

    private static boolean isJvmMethodDescriptor(@Nonnull String descriptor) {
        if (descriptor.length() < 3 || descriptor.charAt(0) != '(') {
            return false;
        }
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            index = nextJvmTypeDescriptor(descriptor, index, false);
            if (index < 0) {
                return false;
            }
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            return false;
        }
        return nextJvmTypeDescriptor(descriptor, index + 1, true) == descriptor.length();
    }

    private static int nextJvmTypeDescriptor(@Nonnull String descriptor,
                                             int start,
                                             boolean allowVoid) {
        if (start < 0 || start >= descriptor.length()) {
            return -1;
        }
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            return -1;
        }
        return switch (descriptor.charAt(index)) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> index + 1;
            case 'V' -> allowVoid && index == start ? index + 1 : -1;
            case 'L' -> {
                int end = descriptor.indexOf(';', index + 1);
                yield end > index + 1 ? end + 1 : -1;
            }
            default -> -1;
        };
    }

    @Nonnull
    private static Map<?, ?> mapValue(@Nullable Object value, @Nonnull String failureMessage) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException(failureMessage);
    }

    private static void requireAccessible(@Nonnull Field field,
                                          @Nonnull String failureMessage) {
        if (!field.trySetAccessible()) {
            throw new IllegalStateException(failureMessage);
        }
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
        if (text.length() <= MAX_TEXT_LENGTH) {
            boolean safe = true;
            for (int index = 0; index < text.length(); index++) {
                if (Character.isISOControl(text.charAt(index))) {
                    safe = false;
                    break;
                }
            }
            if (safe) {
                return text.trim();
            }
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

    private record AccessPlan(@Nonnull Class<?> samplerClass,
                              @Nonnull Class<?> aggregatorClass,
                              @Nonnull Class<?> threadNodeClass,
                              @Nonnull Class<?> stackNodeClass,
                              @Nonnull Field dataAggregator,
                              @Nonnull Field currentJobMutex,
                              @Nonnull Field threadData,
                              @Nonnull Field children,
                              @Nonnull Field times,
                              @Nonnull Method threadLabel,
                              @Nonnull Method className,
                              @Nonnull Method methodName,
                              @Nonnull Method methodDescription) {
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
        private final String frame;
        private double selfMilliseconds;

        private MutableHotPath(@Nonnull FrameKey key) {
            this.key = key;
            this.frame = key.frame();
        }

        @Nonnull
        private SparkProfileSnapshot.HotPath toHotPath(double totalSelfMilliseconds) {
            return new SparkProfileSnapshot.HotPath(
                    key.source(),
                    frame,
                    selfMilliseconds,
                    selfMilliseconds * 100.0d / totalSelfMilliseconds
            );
        }
    }

    private static final class NodeVisitStack {
        private Object[] nodes = new Object[256];
        private int[] parentIndexes = new int[256];
        private long[] inclusiveMicros = new long[256];
        private int size;
        private int poppedParentIndex;
        private long poppedInclusiveMicros;

        private void clear() {
            size = 0;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void push(@Nonnull Object node, int parentIndex, long inclusive) {
            if (size >= MAX_ACTIVE_NODES) {
                throw ActiveNodeLimitExceeded.INSTANCE;
            }
            if (size == nodes.length) {
                int newLength = Math.min(MAX_ACTIVE_NODES, nodes.length * 2);
                Object[] grownNodes = new Object[newLength];
                int[] grownParents = new int[newLength];
                long[] grownInclusive = new long[newLength];
                System.arraycopy(nodes, 0, grownNodes, 0, size);
                System.arraycopy(parentIndexes, 0, grownParents, 0, size);
                System.arraycopy(inclusiveMicros, 0, grownInclusive, 0, size);
                nodes = grownNodes;
                parentIndexes = grownParents;
                inclusiveMicros = grownInclusive;
            }
            nodes[size] = node;
            parentIndexes[size] = parentIndex;
            inclusiveMicros[size] = inclusive;
            size++;
        }

        @Nonnull
        private Object popNode() {
            int index = --size;
            Object node = nodes[index];
            nodes[index] = null;
            poppedParentIndex = parentIndexes[index];
            poppedInclusiveMicros = inclusiveMicros[index];
            return node;
        }

        private int poppedParentIndex() {
            return poppedParentIndex;
        }

        private long poppedInclusiveMicros() {
            return poppedInclusiveMicros;
        }
    }

    private static final class ReadBudget {
        private final LongSupplier nanoTime;
        private final long startedAt;
        private final long maxNanos;
        private int operations;

        private ReadBudget(@Nonnull LongSupplier nanoTime, long startedAt, long maxNanos) {
            this.nanoTime = nanoTime;
            this.startedAt = startedAt;
            this.maxNanos = maxNanos;
        }

        private void check() {
            if ((operations++ & DEADLINE_CHECK_MASK) == 0) {
                checkNow();
            }
        }

        private void checkNow() {
            if (nanoTime.getAsLong() - startedAt >= maxNanos) {
                throw ReadBudgetExceeded.INSTANCE;
            }
        }
    }

    private static final class ReadBudgetExceeded extends RuntimeException {
        private static final ReadBudgetExceeded INSTANCE = new ReadBudgetExceeded();

        private ReadBudgetExceeded() {
            super(null, null, false, false);
        }
    }

    private static final class ActiveNodeLimitExceeded extends RuntimeException {
        private static final ActiveNodeLimitExceeded INSTANCE = new ActiveNodeLimitExceeded();

        private ActiveNodeLimitExceeded() {
            super(null, null, false, false);
        }
    }
}

@FunctionalInterface
interface SparkArtifactVerifier {
    @Nullable
    String testedRelease(@Nonnull Class<?> pluginClass, @Nonnull String reportedVersion) throws Throwable;
}
