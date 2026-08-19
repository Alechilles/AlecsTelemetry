package me.lucko.spark.common;

public final class SparkPlatform {
    private final SamplerContainer samplerContainer;

    public SparkPlatform(Object sampler) {
        this.samplerContainer = new SamplerContainer(sampler);
    }

    public SamplerContainer getSamplerContainer() {
        return samplerContainer;
    }

    public Object createClassSourceLookup() {
        return new Object();
    }

    public static final class SamplerContainer {
        private final Object activeSampler;

        private SamplerContainer(Object activeSampler) {
            this.activeSampler = activeSampler;
        }

        public Object getActiveSampler() {
            return activeSampler;
        }
    }
}
