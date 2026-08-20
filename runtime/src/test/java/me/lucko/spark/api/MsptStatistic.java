package me.lucko.spark.api;

import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;

/** MSPT statistic fixture with extra public methods to exercise shape filtering. */
public final class MsptStatistic {
    private final DoubleAverageInfo value;
    private final RuntimeException failure;
    private final Error fatalFailure;

    public MsptStatistic(DoubleAverageInfo value) {
        this(value, null, null);
    }

    public MsptStatistic(DoubleAverageInfo value, RuntimeException failure) {
        this(value, failure, null);
    }

    public MsptStatistic(DoubleAverageInfo value, Error fatalFailure, boolean fatal) {
        this(value, null, fatal ? fatalFailure : null);
    }

    private MsptStatistic(DoubleAverageInfo value, RuntimeException failure, Error fatalFailure) {
        this.value = value;
        this.failure = failure;
        this.fatalFailure = fatalFailure;
    }

    public DoubleAverageInfo poll(StatisticWindow.MillisPerTick window) {
        failIfNeeded();
        return value;
    }

    public DoubleAverageInfo poll() {
        return value;
    }

    public DoubleAverageInfo poll(String ignored) {
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
