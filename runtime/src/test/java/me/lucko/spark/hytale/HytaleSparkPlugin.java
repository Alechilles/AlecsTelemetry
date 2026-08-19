package me.lucko.spark.hytale;

import me.lucko.spark.common.SparkPlatform;

public final class HytaleSparkPlugin {
    private final String version;
    private final SparkPlatform platform;

    public HytaleSparkPlugin(String version, SparkPlatform platform) {
        this.version = version;
        this.platform = platform;
    }

    public String getVersion() {
        return version;
    }
}
