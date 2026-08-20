package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads only Spark's small public metrics surface without linking the runtime
 * to Spark classes.
 */
public final class SparkPublicMetricsAdapter {
    private static final String SPARK_PROVIDER = "me.lucko.spark.api.SparkProvider";
    private static final String TICKS_WINDOW =
            "me.lucko.spark.api.statistic.StatisticWindow$TicksPerSecond";
    private static final String MSPT_WINDOW =
            "me.lucko.spark.api.statistic.StatisticWindow$MillisPerTick";
    private static final String DOUBLE_AVERAGE_INFO =
            "me.lucko.spark.api.statistic.misc.DoubleAverageInfo";

    private final Supplier<Object> sparkPlugin;

    public SparkPublicMetricsAdapter(@Nonnull Supplier<Object> sparkPlugin) {
        this.sparkPlugin = Objects.requireNonNull(sparkPlugin, "sparkPlugin");
    }

    @Nonnull
    public SparkPublicMetrics snapshot() {
        Object plugin;
        try {
            plugin = sparkPlugin.get();
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return SparkPublicMetrics.unavailable();
        }
        if (plugin == null) {
            return SparkPublicMetrics.unavailable();
        }

        ClassLoader sparkClassLoader;
        try {
            sparkClassLoader = plugin.getClass().getClassLoader();
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return SparkPublicMetrics.unavailable();
        }
        if (sparkClassLoader == null) {
            return SparkPublicMetrics.unavailable();
        }

        Object sparkApi;
        try {
            Class<?> providerClass = Class.forName(SPARK_PROVIDER, false, sparkClassLoader);
            Method get = providerClass.getMethod("get");
            if (!Modifier.isStatic(get.getModifiers()) || get.getParameterCount() != 0) {
                return SparkPublicMetrics.unavailable();
            }
            sparkApi = get.invoke(null);
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return SparkPublicMetrics.unavailable();
        }
        if (sparkApi == null) {
            return SparkPublicMetrics.unavailable();
        }

        Double tps = readTps(sparkApi, sparkClassLoader);
        Double mspt = readMspt(sparkApi, sparkClassLoader);
        return new SparkPublicMetrics(tps, mspt);
    }

    @Nullable
    private static Double readTps(@Nonnull Object sparkApi,
                                  @Nonnull ClassLoader sparkClassLoader) {
        try {
            Object statistic = invokePublicNoArgs(sparkApi, "tps");
            if (statistic == null) {
                return null;
            }
            Object window = minutesOne(sparkClassLoader, TICKS_WINDOW);
            Object raw = invokeOneArgumentPoll(statistic, window);
            if (!(raw instanceof Number number)) {
                return null;
            }
            return finiteNonNegative(number.doubleValue());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return null;
        }
    }

    @Nullable
    private static Double readMspt(@Nonnull Object sparkApi,
                                   @Nonnull ClassLoader sparkClassLoader) {
        try {
            Object statistic = invokePublicNoArgs(sparkApi, "mspt");
            if (statistic == null) {
                return null;
            }
            Object window = minutesOne(sparkClassLoader, MSPT_WINDOW);
            Object average = invokeOneArgumentPoll(statistic, window);
            if (average == null) {
                return null;
            }
            Class<?> averageInfoType = Class.forName(DOUBLE_AVERAGE_INFO, false, sparkClassLoader);
            if (!Modifier.isPublic(averageInfoType.getModifiers())
                    || !averageInfoType.isInterface()
                    || !averageInfoType.isInstance(average)) {
                return null;
            }
            Method mean = averageInfoType.getMethod("mean");
            if (!Modifier.isPublic(mean.getModifiers())
                    || !Modifier.isPublic(mean.getDeclaringClass().getModifiers())
                    || Modifier.isStatic(mean.getModifiers())
                    || mean.getParameterCount() != 0) {
                return null;
            }
            Object raw = mean.invoke(average);
            if (!(raw instanceof Number number)) {
                return null;
            }
            return finiteNonNegative(number.doubleValue());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            return null;
        }
    }

    @Nonnull
    private static Object invokePublicNoArgs(@Nonnull Object target,
                                             @Nonnull String methodName) throws Throwable {
        Method method = findPublicMethod(target.getClass(), methodName, 0, null, new HashSet<>());
        if (method == null) {
            throw new NoSuchMethodException(methodName);
        }
        return method.invoke(target);
    }

    @Nonnull
    private static Object minutesOne(@Nonnull ClassLoader sparkClassLoader,
                                     @Nonnull String enumClassName) throws Throwable {
        Class<?> windowClass = Class.forName(enumClassName, false, sparkClassLoader);
        if (!windowClass.isEnum()) {
            throw new NoSuchFieldException(enumClassName + " is not an enum");
        }
        Field minutesOne = windowClass.getField("MINUTES_1");
        if (!Modifier.isStatic(minutesOne.getModifiers())) {
            throw new NoSuchFieldException(enumClassName + ".MINUTES_1");
        }
        Object value = minutesOne.get(null);
        if (!(value instanceof Enum<?> enumValue)
                || enumValue.getDeclaringClass() != windowClass
                || !"MINUTES_1".equals(enumValue.name())) {
            throw new NoSuchFieldException(enumClassName + ".MINUTES_1 is not its enum constant");
        }
        return value;
    }

    @Nonnull
    private static Object invokeOneArgumentPoll(@Nonnull Object statistic,
                                                @Nonnull Object window) throws Throwable {
        Class<?> windowType = window.getClass();
        Method selected = findPublicMethod(
                statistic.getClass(),
                "poll",
                1,
                windowType,
                new HashSet<>()
        );
        if (selected == null) {
            throw new NoSuchMethodException("public one-argument poll");
        }
        return selected.invoke(statistic, window);
    }

    @Nullable
    private static Method findPublicMethod(@Nonnull Class<?> type,
                                           @Nonnull String methodName,
                                           int parameterCount,
                                           @Nullable Class<?> argumentType,
                                           @Nonnull Set<Class<?>> visited) {
        if (!visited.add(type)) {
            return null;
        }
        if (Modifier.isPublic(type.getModifiers())) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(methodName)
                        || method.getParameterCount() != parameterCount
                        || !Modifier.isPublic(method.getModifiers())
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (argumentType == null
                        || method.getParameterTypes()[0].equals(argumentType)) {
                    return method;
                }
            }
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            Method method = findPublicMethod(interfaceType, methodName, parameterCount, argumentType, visited);
            if (method != null) {
                return method;
            }
        }
        Class<?> superType = type.getSuperclass();
        return superType == null
                ? null
                : findPublicMethod(superType, methodName, parameterCount, argumentType, visited);
    }

    @Nullable
    private static Double finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0d ? value : null;
    }

    private static void rethrowIfFatal(@Nonnull Throwable failure) {
        Throwable unwrapped = failure;
        while (unwrapped instanceof InvocationTargetException invocation
                && invocation.getCause() != null) {
            unwrapped = invocation.getCause();
        }
        if (unwrapped instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if ("java.lang.ThreadDeath".equals(unwrapped.getClass().getName())) {
            throw (Error) unwrapped;
        }
    }
}
