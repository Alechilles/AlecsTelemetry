---
title: "Runtime API"
order: 14
published: true
draft: false
---

# Runtime API

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Consumer mods can use Alec's Telemetry as an optional runtime integration after they ship `Server/Telemetry/project.json`.

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

Embedded mods normally keep the `EmbeddedTelemetryService` returned by `EmbeddedTelemetryBootstrap.bootstrap(this)`.

## Project Operations

`TelemetryProjectHandle` exposes breadcrumbs, setup and start failure capture, error events, lifecycle events, performance timings, usage events, stats events, manual report UI opening, and project-scoped flush requests.

The runtime ignores events that are disabled by project consent, descriptor defaults, runtime overrides, sampling, or descriptor allowlists.

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

Standard context fields become first-class event envelope fields. Custom `detail(key, value)` entries are uploaded only when the descriptor declares the matching detail allowlist.

## Manual Report UI

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

`TelemetryReportOpenRequest` accepts `issue` or `suggestion`.
