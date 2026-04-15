# Embedded Mode

Embedded mode lets a modder bundle the telemetry runtime inside their own mod instead
of requiring the standalone `Alec's Telemetry` dependency.

## When To Use It

Use embedded mode when a modder wants:

- one distributable mod package
- no extra CurseForge dependency for players
- direct ownership of telemetry bootstrap and lifecycle wiring

## Descriptor Requirement

Embedded mode uses the same `telemetry/project.json` descriptor as dependency mode,
but it must declare:

```json
{
  "runtimeMode": "embedded"
}
```

When the standalone runtime scans installed mods, it will skip descriptors that
declare `runtimeMode=embedded`.

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
private EmbeddedTelemetryService telemetry;

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
```

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
