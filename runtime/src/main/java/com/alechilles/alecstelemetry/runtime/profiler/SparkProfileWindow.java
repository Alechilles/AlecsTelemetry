package com.alechilles.alecstelemetry.runtime.profiler;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Bounded parent-linked Java frames from one completed Spark window.
 *
 * <p>The raw Spark protobuf is deliberately not part of this value. The
 * reflection bridge creates this short-lived model and the project analyzer
 * consumes it before it can escape the capture operation.</p>
 */
record SparkProfileWindow(@Nonnull String sparkVersion,
                          int windowKey,
                          int totalThreadCount,
                          int selectedThreadCount,
                          int totalFrameCount,
                          int selectedFrameCount,
                          double totalSelfMilliseconds,
                          @Nonnull List<ThreadFrames> threads) {

    SparkProfileWindow {
        sparkVersion = requiredText(sparkVersion);
        if (totalThreadCount < 0 || selectedThreadCount < 0
                || totalFrameCount < 0 || selectedFrameCount < 0) {
            throw new IllegalArgumentException("Spark window counts must be non-negative");
        }
        if (selectedThreadCount > totalThreadCount || selectedFrameCount > totalFrameCount) {
            throw new IllegalArgumentException("Selected Spark window counts exceed totals");
        }
        if (!Double.isFinite(totalSelfMilliseconds) || totalSelfMilliseconds < 0.0d) {
            throw new IllegalArgumentException("totalSelfMilliseconds must be finite and non-negative");
        }
        threads = threads == null ? List.of() : List.copyOf(threads);
    }

    @Nonnull
    private static String requiredText(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value.trim();
    }

    record ThreadFrames(@Nonnull String threadName,
                         @Nonnull List<Frame> frames) {
        ThreadFrames {
            threadName = requiredText(threadName);
            frames = frames == null ? List.of() : List.copyOf(frames);
        }
    }

    record Frame(int parentIndex,
                 @Nonnull String source,
                 @Nonnull String className,
                 @Nonnull String methodName,
                 @Nonnull String methodDescriptor,
                 double selfMilliseconds) {
        Frame {
            if (parentIndex < -1) {
                throw new IllegalArgumentException("parentIndex must be -1 or non-negative");
            }
            source = requiredText(source);
            className = requiredText(className);
            methodName = requiredText(methodName);
            methodDescriptor = requiredText(methodDescriptor);
            if (!Double.isFinite(selfMilliseconds) || selfMilliseconds < 0.0d) {
                throw new IllegalArgumentException("selfMilliseconds must be finite and non-negative");
            }
        }
    }
}
