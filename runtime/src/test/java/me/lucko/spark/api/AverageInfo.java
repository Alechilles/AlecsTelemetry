package me.lucko.spark.api;

import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;

/** MSPT mean fixture. */
public final class AverageInfo implements DoubleAverageInfo {
    private final double mean;
    private final RuntimeException failure;
    private final Error fatalFailure;

    public AverageInfo(double mean) {
        this(mean, null, null);
    }

    public AverageInfo(double mean, RuntimeException failure) {
        this(mean, failure, null);
    }

    public AverageInfo(double mean, Error fatalFailure, boolean fatal) {
        this(mean, null, fatal ? fatalFailure : null);
    }

    private AverageInfo(double mean, RuntimeException failure, Error fatalFailure) {
        this.mean = mean;
        this.failure = failure;
        this.fatalFailure = fatalFailure;
    }

    @Override
    public double mean() {
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        if (failure != null) {
            throw failure;
        }
        return mean;
    }
}
