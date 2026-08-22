package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A bounded, local summary of one completed Spark background-profiler window.
 */
public record SparkProfileSnapshot(@Nonnull String sparkVersion,
                                   int windowKey,
                                   int totalThreadCount,
                                   int selectedThreadCount,
                                   int totalFrameCount,
                                   int selectedFrameCount,
                                   double totalSelfMilliseconds,
                                   @Nonnull List<HotPath> hotPaths) {

    public SparkProfileSnapshot(@Nonnull String sparkVersion,
                                 int windowKey,
                                 int threadCount,
                                 int frameCount,
                                 double totalSelfMilliseconds,
                                 @Nonnull List<HotPath> hotPaths) {
        this(
                sparkVersion,
                windowKey,
                threadCount,
                threadCount,
                frameCount,
                frameCount,
                totalSelfMilliseconds,
                hotPaths
        );
    }

    public SparkProfileSnapshot {
        sparkVersion = sparkVersion == null ? "<unknown>" : sparkVersion;
        hotPaths = hotPaths == null ? List.of() : List.copyOf(hotPaths);
    }

    public int threadCount() {
        return totalThreadCount;
    }

    public int frameCount() {
        return totalFrameCount;
    }

    public record HotPath(@Nonnull String source,
                          @Nonnull String frame,
                          double selfMilliseconds,
                          double selfSharePercent) {
        public HotPath {
            frame = frame == null || frame.isBlank() ? "<unknown>" : frame.trim();
            source = normalizedSource(source, frame);
        }

        @Nonnull
        private static String normalizedSource(String source, @Nonnull String frame) {
            if (source != null
                    && !source.isBlank()
                    && !"<unknown>".equalsIgnoreCase(source)
                    && !"<none>".equalsIgnoreCase(source)) {
                return source.trim();
            }
            if (frame.startsWith("com.hypixel.hytale.") || frame.startsWith("com.hytale.")) {
                return "Hytale Server";
            }
            return "<unknown>";
        }
    }
}
