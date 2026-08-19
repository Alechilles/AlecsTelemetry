package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A bounded, local summary of one completed Spark background-profiler window.
 */
public record SparkProfileSnapshot(@Nonnull String sparkVersion,
                                   int windowKey,
                                   int threadCount,
                                   int frameCount,
                                   double totalSelfMilliseconds,
                                   @Nonnull List<HotPath> hotPaths) {

    public SparkProfileSnapshot {
        sparkVersion = sparkVersion == null ? "<unknown>" : sparkVersion;
        hotPaths = hotPaths == null ? List.of() : List.copyOf(hotPaths);
    }

    public record HotPath(@Nonnull String source,
                          @Nonnull String frame,
                          double selfMilliseconds,
                          double selfSharePercent) {
    }
}
