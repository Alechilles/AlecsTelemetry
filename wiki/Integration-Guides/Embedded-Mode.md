---
title: "Embedded Mode"
order: 4
published: true
draft: false
---

# Embedded Mode

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Embedded mode lets a modder bundle telemetry runtime bootstrap logic inside their own mod instead of requiring the standalone Alec's Telemetry dependency.

## When To Use It

Use embedded mode when a modder wants:

- one distributable mod package
- no extra CurseForge dependency for players
- direct ownership of telemetry bootstrap and lifecycle wiring

## Descriptor Requirement

Embedded mode uses the same `telemetry/project.json` descriptor as dependency mode, but it must declare:

```json
{
  "runtimeMode": "embedded"
}
```

When the standalone runtime scans installed mods, it skips descriptors that declare `runtimeMode: "embedded"`.

## Minimal Hosted Example

```json
{
  "runtimeMode": "embedded",
  "hosted": {
    "projectKey": "pub_proj_abc123"
  }
}
```

## Bootstrap Shape

The owning mod boots telemetry directly:

```java
import com.alechilles.alecstelemetry.api.TelemetryEventContext;

private EmbeddedTelemetryService telemetry;

@Override
protected void setup() {
    telemetry = EmbeddedTelemetryBootstrap.bootstrap(this);
    try {
        setupInternal();
        telemetry.recordBreadcrumb("lifecycle", "Setup completed.");
        telemetry.recordLifecycleWithContext(
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
        telemetry.recordUsageWithContext(
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
```

The `*WithContext` helpers accept `TelemetryEventContext` so embedded mods can attach standardized fields such as `subsystem`, `phase`, `featureKey`, `entryPoint`, `runtimeSide`, `commandName`, and `worldName`. Custom `detail(key, value)` entries are filtered by the descriptor allowlist before upload.

## Storage Layout

Embedded mode stores telemetry under the owning mod's data directory:

```text
<ConsumerModDataDir>/Telemetry/
```

This avoids using the standalone runtime's shared storage folder.

## Important Rule

Pick one mode per mod:

- `dependency`
- `embedded`

Do not try to make one mod use both.
