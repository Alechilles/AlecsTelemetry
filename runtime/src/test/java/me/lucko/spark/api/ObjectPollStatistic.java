package me.lucko.spark.api;

/** Unsupported broad poll signature. */
public final class ObjectPollStatistic {
    private final Number value;

    public ObjectPollStatistic(Number value) {
        this.value = value;
    }

    public Number poll(Object ignored) {
        return value;
    }
}
