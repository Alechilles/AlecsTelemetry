package me.lucko.spark.api;

/** Carrier with a method that must not be accepted as Spark MSPT data. */
public final class UnrelatedMeanCarrier {
    public double mean() {
        return 12.25d;
    }
}
