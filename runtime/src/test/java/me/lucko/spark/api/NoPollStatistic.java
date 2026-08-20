package me.lucko.spark.api;

/** Statistic shape with no usable one-argument public poll method. */
public final class NoPollStatistic {
    public Object poll() {
        return null;
    }
}
