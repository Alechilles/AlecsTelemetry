package com.example.embedded;

import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryBootstrap;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class EmbeddedConsumerMod extends JavaPlugin {

    private EmbeddedTelemetryService telemetry;

    public EmbeddedConsumerMod(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        telemetry = EmbeddedTelemetryBootstrap.bootstrap(this);
        try {
            setupInternal();
            telemetry.recordBreadcrumb("lifecycle", "Setup completed.");
        } catch (Throwable throwable) {
            telemetry.captureSetupFailure(throwable);
            throw throwable;
        }
    }

    @Override
    protected void start() {
        try {
            startInternal();
            telemetry.start();
            telemetry.recordBreadcrumb("lifecycle", "Start completed.");
        } catch (Throwable throwable) {
            telemetry.captureStartFailure(throwable);
            throw throwable;
        }
    }

    @Override
    protected void shutdown() {
        if (telemetry != null) {
            telemetry.shutdown();
        }
    }

    private void setupInternal() {
    }

    private void startInternal() {
    }
}
