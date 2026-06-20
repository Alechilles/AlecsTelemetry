# Runtime API

Consumer mods can use Alec's Telemetry as an optional runtime integration after
they ship `Server/Telemetry/project.json`.

Use the public API when a mod wants to add breadcrumbs, explicit non-crash
events, performance timings, usage events, stats events, or a custom entry point
for manual player reports.

## Locate The Runtime

Dependency-mode mods should treat the runtime as optional:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api == null || !api.isEnabled()) {
    return;
}

TelemetryProjectHandle project = api.findProject("example-consumer-mod");
if (project == null || !project.isEnabled()) {
    return;
}
```

Embedded mods normally keep the `EmbeddedTelemetryService` returned by
`EmbeddedTelemetryBootstrap.bootstrap(this)` and use its project-scoped helper
methods directly.

## Project Operations

`TelemetryProjectHandle` exposes:

- `projectId()`
- `displayName()`
- `isEnabled()`
- `recordBreadcrumb(category, detail)`
- `captureSetupFailure(throwable)`
- `captureStartFailure(throwable)`
- `recordError(...)` and `recordErrorWithContext(...)`
- `recordLifecycle(...)` and `recordLifecycleWithContext(...)`
- `recordPerformance(...)` and `recordPerformanceWithContext(...)`
- `recordUsage(...)` and `recordUsageWithContext(...)`
- `recordStats(...)` and `recordStatsWithContext(...)`
- `openReportPage(...)`
- `requestFlush()`

The runtime ignores events that are disabled by project consent, descriptor
defaults, runtime overrides, sampling, or descriptor allowlists.

## Event Context

Prefer `TelemetryEventContext` for explicit events:

```java
project.recordPerformanceWithContext(
        "reload_config_duration",
        durationMs,
        null,
        TelemetryEventContext.performance()
                .subsystem("config")
                .phase("reload")
                .operation("apply")
                .runtimeSide("server")
                .detail("configFileCount", configFileCount)
                .build()
);
```

Standard context fields become first-class event envelope fields. Custom
`detail(key, value)` entries are uploaded only when the project descriptor
declares the matching detail allowlist for that event name.

Supported context fields are documented in `docs/project-descriptor.md`.

## Manual Report UI

Consumer mods can open the shared report UI from their own player UI:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

`TelemetryReportOpenRequest` accepts `issue` or `suggestion`.

## Flush Requests

`TelemetryRuntimeApi.requestFlush()` asks the active runtime to flush all pending
projects. `TelemetryProjectHandle.requestFlush()` asks it to flush one project.

Both methods schedule work through the active coordinator; they are not a
guarantee that every queued item was uploaded.
