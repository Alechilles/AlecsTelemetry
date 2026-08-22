package me.lucko.spark.common.sampler.node;

public final class ThreadNode extends AbstractNode {
    private final String name;
    public String label;

    public ThreadNode(String name) {
        this.name = name;
    }

    public String getThreadLabel() {
        return label == null ? name : label;
    }

    public String getThreadGroup() {
        return "";
    }

    public ThreadNode time(int window, long microseconds) {
        putTime(window, microseconds);
        return this;
    }

    public ThreadNode child(StackTraceNode child) {
        addChildNode(child);
        return this;
    }
}
