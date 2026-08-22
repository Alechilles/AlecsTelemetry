package me.lucko.spark.common.sampler.async;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.sampler.Sampler;

public final class AsyncSampler {
    private final AsyncDataAggregator dataAggregator;
    private final Object[] currentJobMutex = new Object[0];
    private final boolean background;
    private final Sampler.SamplerType type;
    private final Sampler.SamplerMode mode;
    private int toProtoCalls;

    public AsyncSampler(boolean background,
                        Sampler.SamplerType type,
                        Sampler.SamplerMode mode,
                        AsyncDataAggregator dataAggregator) {
        this.background = background;
        this.type = type;
        this.mode = mode;
        this.dataAggregator = dataAggregator;
    }

    public boolean isRunningInBackground() {
        return background;
    }

    public Sampler.SamplerType getType() {
        return type;
    }

    public Sampler.SamplerMode getMode() {
        return mode;
    }

    public Object toProto(SparkPlatform platform, Sampler.ExportProps props) {
        toProtoCalls++;
        throw new IllegalStateException("toProto must not be called");
    }

    public int toProtoCalls() {
        return toProtoCalls;
    }
}
