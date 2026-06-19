# Embedded Mode

Embedded mode lets a modder bundle the telemetry runtime inside their own mod instead
of requiring the standalone `Alec's Telemetry` dependency.

## When To Use It

Use embedded mode when a modder wants:

- one distributable mod package
- no extra CurseForge dependency for players
- direct ownership of telemetry bootstrap and lifecycle wiring

## Descriptor Shape

Embedded mode uses the same `Server/Telemetry/project.json` descriptor as the standalone dependency flow. The descriptor does not need a runtime-mode flag; embedded behavior comes from packaging the runtime and bootstrapping it from the owning mod.

Every installed copy of Alec's Telemetry, standalone or embedded, registers as a
runtime coordinator candidate. The latest compatible runtime version wins.
Non-winning copies become passive clients and forward telemetry operations to
the active coordinator. If standalone is installed but an embedded copy is
newer, standalone can still provide commands and UI while the newer embedded
runtime owns capture, queueing, and upload.

## Runtime Precedence

Runtime ownership is selected per server process:

1. Only coordinator candidates with the current coordinator protocol are eligible.
2. The highest telemetry runtime version wins.
3. If versions match, standalone wins over embedded.
4. If versions and origin match, provider plugin identifier and source path provide a stable tie-breaker.

The active coordinator handles descriptors from all installed enabled mods,
including descriptors with or without the legacy `runtimeMode` field. Passive embedded copies
do not install their own uncaught exception handlers, stats heartbeats, queues,
or upload loops.

## Minimal Hosted Example

```json
{
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

The `*WithContext` helpers accept `TelemetryEventContext` so embedded mods can
attach standardized fields such as `subsystem`, `phase`, `featureKey`,
`entryPoint`, `runtimeSide`, `commandName`, and `worldName`. Custom
`detail(key, value)` entries are filtered by the descriptor allowlist before
upload.

## Storage Layout

An embedded owner keeps its local project override files under the owning mod's
data directory:

```text
<ConsumerModDataDir>/Telemetry/
```

The elected active coordinator stores runtime settings, queues, server identity,
and uploads under the shared telemetry coordinator root:

```text
<HytaleUserData>/Telemetry/
```

## Important Rule

Pick one packaging strategy per mod: either depend on the standalone runtime or embed and bootstrap the runtime yourself. Do not add a descriptor flag to choose the strategy; runtime election handles coexistence when multiple providers are installed.
