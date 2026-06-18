package com.alechilles.alecstelemetry.runtime.host;

import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryRuntimeCommandRegistrar {
    private final AtomicBoolean registered = new AtomicBoolean(false);

    void register() {
        registered.set(true);
    }

    void unregister() {
        registered.set(false);
    }

    boolean registered() {
        return registered.get();
    }
}
