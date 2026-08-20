package me.lucko.spark.api;

import me.lucko.spark.api.statistic.StatisticWindow;

/** MSPT statistic whose carrier has an unrelated mean method. */
public final class ObjectMsptStatistic {
    private final Object value;

    public ObjectMsptStatistic(Object value) {
        this.value = value;
    }

    public Object poll(StatisticWindow.MillisPerTick window) {
        return value;
    }
}
