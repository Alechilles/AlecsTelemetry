package com.example.embedded;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
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
            telemetry.recordLifecycle(
                    "plugin_setup",
                    0,
                    true,
                    TelemetryEventContext.lifecycle()
                            .subsystem("plugin")
                            .phase("setup")
                            .runtimeSide("server")
                            .build()
            );
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
            telemetry.recordUsage(
                    "settings_opened",
                    TelemetryEventContext.usage()
                            .subsystem("settings")
                            .featureKey("settings_page")
                            .entryPoint("/example settings")
                            .runtimeSide("server")
                            .detail("source", "command")
                            .build()
            );
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
