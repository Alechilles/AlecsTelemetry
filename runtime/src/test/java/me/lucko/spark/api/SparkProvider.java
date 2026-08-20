package me.lucko.spark.api;

/** Minimal public Spark provider fixture for reflection-boundary tests. */
public final class SparkProvider {
    private static volatile Spark spark;

    private SparkProvider() {
    }

    public static Spark get() {
        return spark;
    }

    public static void set(Spark value) {
        spark = value;
    }
}
