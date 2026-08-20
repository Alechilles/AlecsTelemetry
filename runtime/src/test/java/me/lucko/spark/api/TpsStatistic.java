package me.lucko.spark.api;

import me.lucko.spark.api.statistic.StatisticWindow;

/** TPS statistic fixture with extra public methods to exercise shape filtering. */
public final class TpsStatistic {
    private final Number value;
    private final RuntimeException failure;
    private final Error fatalFailure;

    public TpsStatistic(Number value) {
        this(value, null, null);
    }

    public TpsStatistic(Number value, RuntimeException failure) {
        this(value, failure, null);
    }

    public TpsStatistic(Number value, Error fatalFailure, boolean fatal) {
        this(value, null, fatal ? fatalFailure : null);
    }

    private TpsStatistic(Number value, RuntimeException failure, Error fatalFailure) {
        this.value = value;
        this.failure = failure;
        this.fatalFailure = fatalFailure;
    }

    public Number poll(StatisticWindow.TicksPerSecond window) {
        failIfNeeded();
        return value;
    }

    public Number poll() {
        return value;
    }

    public Number poll(String ignored) {
        return value;
    }

    private void failIfNeeded() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (failure != null) {
            throw failure;
        }
    }
}
