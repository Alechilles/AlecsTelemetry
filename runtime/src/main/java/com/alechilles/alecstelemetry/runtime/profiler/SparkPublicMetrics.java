package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nullable;

/** Bounded values read from Spark's optional public statistics API. */
record SparkPublicMetrics(@Nullable Double tps, @Nullable Double mspt) {
    static SparkPublicMetrics unavailable() {
        return new SparkPublicMetrics(null, null);
    }
}
