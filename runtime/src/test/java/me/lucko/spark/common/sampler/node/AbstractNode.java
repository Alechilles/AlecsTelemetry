package me.lucko.spark.common.sampler.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public abstract class AbstractNode {
    private final Map<Object, StackTraceNode> children = new LinkedHashMap<>();
    private final Map<Integer, LongAdder> times = new LinkedHashMap<>();

    protected final void addChildNode(StackTraceNode child) {
        children.put(child, child);
    }

    protected final void putTime(int window, long value) {
        LongAdder adder = new LongAdder();
        adder.add(value);
        times.put(window, adder);
    }
}
