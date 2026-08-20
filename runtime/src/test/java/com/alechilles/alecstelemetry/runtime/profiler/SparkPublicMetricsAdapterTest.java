package com.alechilles.alecstelemetry.runtime.profiler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import me.lucko.spark.api.AverageInfo;
import me.lucko.spark.api.MsptStatistic;
import me.lucko.spark.api.NoPollStatistic;
import me.lucko.spark.api.ObjectMsptStatistic;
import me.lucko.spark.api.ObjectPollStatistic;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkPlugin;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.TpsStatistic;
import me.lucko.spark.api.UnrelatedMeanCarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SparkPublicMetricsAdapterTest {
    private final Object plugin = new SparkPlugin();

    @BeforeEach
    void resetProvider() {
        SparkProvider.set(null);
    }

    @AfterEach
    void clearProvider() {
        SparkProvider.set(null);
    }

    // Regression: the public adapter must read both metrics through the plugin classloader.
    @Test
    void readsPublicSparkMetricsWithoutLinkingToSpark() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));

        SparkPublicMetricsAdapter adapter = new SparkPublicMetricsAdapter(() -> plugin);

        assertEquals(new SparkPublicMetrics(29.5d, 12.25d), adapter.snapshot());
    }

    // Regression: missing optional Spark state must publish unavailable values instead of throwing.
    @Test
    void nullPluginAndUnsetProviderAreUnavailable() {
        SparkPublicMetricsAdapter nullPlugin = new SparkPublicMetricsAdapter(() -> null);
        assertEquals(SparkPublicMetrics.unavailable(), nullPlugin.snapshot());

        SparkPublicMetricsAdapter unsetProvider = new SparkPublicMetricsAdapter(() -> plugin);
        assertEquals(SparkPublicMetrics.unavailable(), unsetProvider.snapshot());
    }

    // Regression: an unsupported or null statistic must not erase the other metric.
    @Test
    void unsupportedMetricDoesNotEraseOtherMetric() {
        SparkProvider.set(new Spark(null, new MsptStatistic(new AverageInfo(12.25d))));
        SparkPublicMetricsAdapter tpsUnavailable = new SparkPublicMetricsAdapter(() -> plugin);
        assertEquals(new SparkPublicMetrics(null, 12.25d), tpsUnavailable.snapshot());

        SparkProvider.set(new Spark(new TpsStatistic(29.5d), new NoPollStatistic()));
        SparkPublicMetricsAdapter msptUnavailable = new SparkPublicMetricsAdapter(() -> plugin);
        assertEquals(new SparkPublicMetrics(29.5d, null), msptUnavailable.snapshot());
    }

    // Regression: the adapter must select the bounded one-argument poll shape when extra methods exist.
    @Test
    void selectsOneArgumentPollWhenPublicShapesChange() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));

        assertEquals(
                new SparkPublicMetrics(29.5d, 12.25d),
                new SparkPublicMetricsAdapter(() -> plugin).snapshot()
        );
    }

    // Regression: a broad poll(Object) method must not be treated as the typed TPS API.
    @Test
    void rejectsBroadPollParameterWithoutErasingMspt() {
        SparkProvider.set(new Spark(
                new ObjectPollStatistic(29.5d),
                new MsptStatistic(new AverageInfo(12.25d))
        ));

        assertEquals(
                new SparkPublicMetrics(null, 12.25d),
                new SparkPublicMetricsAdapter(() -> plugin).snapshot()
        );
    }

    // Regression: an unrelated carrier mean() must not be treated as DoubleAverageInfo.
    @Test
    void rejectsUnrelatedMeanCarrierWithoutErasingTps() {
        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new ObjectMsptStatistic(new UnrelatedMeanCarrier())
        ));

        assertEquals(
                new SparkPublicMetrics(29.5d, null),
                new SparkPublicMetricsAdapter(() -> plugin).snapshot()
        );
    }

    // Regression: a throwing statistic must fail only its metric.
    @Test
    void throwingStatisticDoesNotEraseOtherMetric() {
        SparkProvider.set(new Spark(
                new TpsStatistic(null, new IllegalStateException("tps failed")),
                new MsptStatistic(new AverageInfo(12.25d))
        ));
        assertEquals(
                new SparkPublicMetrics(null, 12.25d),
                new SparkPublicMetricsAdapter(() -> plugin).snapshot()
        );

        SparkProvider.set(new Spark(
                new TpsStatistic(29.5d),
                new MsptStatistic(null, new IllegalStateException("mspt failed"))
        ));
        assertEquals(
                new SparkPublicMetrics(29.5d, null),
                new SparkPublicMetricsAdapter(() -> plugin).snapshot()
        );
    }

    // Regression: malformed, non-finite, and negative values must never enter a published context.
    @Test
    void rejectsNaNInfinityAndNegativeValuesPerMetric() {
        double[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01d};
        for (double value : invalid) {
            SparkProvider.set(new Spark(
                    new TpsStatistic(value),
                    new MsptStatistic(new AverageInfo(12.25d))
            ));
            assertEquals(new SparkPublicMetrics(null, 12.25d),
                    new SparkPublicMetricsAdapter(() -> plugin).snapshot());

            SparkProvider.set(new Spark(
                    new TpsStatistic(29.5d),
                    new MsptStatistic(new AverageInfo(value))
            ));
            assertEquals(new SparkPublicMetrics(29.5d, null),
                    new SparkPublicMetricsAdapter(() -> plugin).snapshot());
        }
    }

    // Regression: fatal failures retain the runtime's fatal-error policy through reflection wrappers.
    @Test
    void rethrowsFatalStatisticFailures() {
        OutOfMemoryError fatal = new OutOfMemoryError("test fatal");
        SparkProvider.set(new Spark(
                new TpsStatistic(null, fatal, true),
                new MsptStatistic(new AverageInfo(12.25d))
        ));

        assertThrows(OutOfMemoryError.class,
                () -> new SparkPublicMetricsAdapter(() -> plugin).snapshot());
    }

    // Regression: a public metric inherited from a non-public superclass must
    // be invoked through the public interface declaration when one exists.
    @Test
    void readsMetricThroughPublicInterfaceWhenSuperclassIsNotPublic() throws Exception {
        Path fixtureDirectory = Files.createTempDirectory("spark-public-metrics-fixture");
        try {
            Object statistic = compileExternalSparkStatistic(fixtureDirectory);
            SparkProvider.set(new Spark(
                    statistic,
                    new MsptStatistic(new AverageInfo(12.25d))
            ));

            assertEquals(
                    new SparkPublicMetrics(29.5d, 12.25d),
                    new SparkPublicMetricsAdapter(() -> plugin).snapshot()
            );
        } finally {
            deleteFixtureDirectory(fixtureDirectory);
        }
    }

    private static Object compileExternalSparkStatistic(Path outputDirectory) throws Exception {
        Path contract = outputDirectory.resolve("PublicTpsPollContract.java");
        Path hiddenBase = outputDirectory.resolve("HiddenTpsPollBase.java");
        Path statistic = outputDirectory.resolve("PublicInterfaceBackedTpsStatistic.java");
        Files.writeString(contract, """
                package me.lucko.spark.api;

                import me.lucko.spark.api.statistic.StatisticWindow;

                public interface PublicTpsPollContract {
                    Number poll(StatisticWindow.TicksPerSecond window);
                }
                """);
        Files.writeString(hiddenBase, """
                package me.lucko.spark.api;

                import me.lucko.spark.api.statistic.StatisticWindow;

                class HiddenTpsPollBase implements PublicTpsPollContract {
                    public final Number poll(StatisticWindow.TicksPerSecond window) {
                        return 29.5d;
                    }
                }
                """);
        Files.writeString(statistic, """
                package me.lucko.spark.api;

                public final class PublicInterfaceBackedTpsStatistic extends HiddenTpsPollBase {
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(
                null,
                null,
                null,
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                outputDirectory.toString(),
                contract.toString(),
                hiddenBase.toString(),
                statistic.toString()
        );
        assertEquals(0, result, "external Spark compatibility fixture must compile");

        URLClassLoader loader = new URLClassLoader(
                new URL[]{outputDirectory.toUri().toURL()},
                SparkPublicMetricsAdapterTest.class.getClassLoader()
        );
        try {
            return Class.forName(
                    "me.lucko.spark.api.PublicInterfaceBackedTpsStatistic",
                    true,
                    loader
            ).getConstructor().newInstance();
        } finally {
            loader.close();
        }
    }

    private static void deleteFixtureDirectory(Path directory) throws Exception {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
