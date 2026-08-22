package me.lucko.spark.common.sampler.aggregator;

import me.lucko.spark.common.sampler.node.ThreadNode;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractDataAggregator {
    protected final Map<String, ThreadNode> threadData = new LinkedHashMap<>();
}
