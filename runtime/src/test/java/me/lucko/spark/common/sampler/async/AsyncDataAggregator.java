package me.lucko.spark.common.sampler.async;

import me.lucko.spark.common.sampler.aggregator.AbstractDataAggregator;
import me.lucko.spark.common.sampler.node.ThreadNode;

public final class AsyncDataAggregator extends AbstractDataAggregator {
    public AsyncDataAggregator thread(String key, ThreadNode thread) {
        threadData.put(key, thread);
        return this;
    }
}
