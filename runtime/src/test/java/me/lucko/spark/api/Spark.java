package me.lucko.spark.api;

/** Minimal API object with the public metric accessors used by the adapter. */
public final class Spark {
    private final Object tps;
    private final Object mspt;

    public Spark(Object tps, Object mspt) {
        this.tps = tps;
        this.mspt = mspt;
    }

    public Object tps() {
        return tps;
    }

    public Object mspt() {
        return mspt;
    }
}
