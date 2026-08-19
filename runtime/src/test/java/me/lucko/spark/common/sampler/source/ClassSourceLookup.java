package me.lucko.spark.common.sampler.source;

import me.lucko.spark.common.SparkPlatform;

public final class ClassSourceLookup {
    private ClassSourceLookup() {
    }

    public static Object create(SparkPlatform platform) {
        return platform.createClassSourceLookup();
    }
}
