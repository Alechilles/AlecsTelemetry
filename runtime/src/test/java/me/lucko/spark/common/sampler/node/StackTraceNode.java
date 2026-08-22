package me.lucko.spark.common.sampler.node;

public final class StackTraceNode extends AbstractNode {
    private final String className;
    private final String methodName;
    private final String methodDescription;

    public StackTraceNode(String className, String methodName, String methodDescription) {
        this.className = className;
        this.methodName = methodName;
        this.methodDescription = methodDescription;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDescription() {
        return methodDescription;
    }

    public StackTraceNode time(int window, long microseconds) {
        putTime(window, microseconds);
        return this;
    }

    public StackTraceNode child(StackTraceNode child) {
        addChildNode(child);
        return this;
    }
}
