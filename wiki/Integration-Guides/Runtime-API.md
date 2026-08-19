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

It also exposes the local Phase 1 project profiler view:

```java
TelemetryProjectHandle project = api.findProject("example-mod");
TelemetryProfilerView profiler = project.profiler();
TelemetryProfilerStatus status = profiler.status();
Optional<TelemetryProfilerSnapshot> latest = profiler.latest();
List<TelemetryProfilerSnapshot> history = profiler.history();
TelemetryProfilerSubscription subscription = profiler.subscribe(snapshot ->
        snapshot.paths().forEach(path -> logger.info(
                path.attribution() + " " + path.sampledMilliseconds() + " ms "
                        + path.ownedClassName() + "#" + path.ownedMethodName())));
```

The capability is `profiler-api-v1`. A legacy handle or older elected provider
returns status `UNAVAILABLE`, empty latest/history, and a closed subscription.
This keeps mixed runtime versions safe. `SELF` identifies sampled self time in
an owned method. `DOWNSTREAM` identifies sampled work below the nearest owned
method and includes the first external method as context. Phase 1 emits only
`OBSERVED`; `signals` belongs to the later Phase 2 `hot-path-v1` work.

Publication requires the global Spark monitor and the project's performance
consent. The view exposes bounded class/method/timing/fingerprint/path data,
not raw Spark profiles or frame trees. It does not upload profiler data or
expose player data or server identity. The API is not an authorization boundary
and server-side consumer code with API access can request another registered
project by ID. Consumer code controls later handling of snapshots.

The service keeps at most five paths per snapshot, ten snapshots per project,
and 500 project-path entries globally. It accepts at most four listener workers
per project and 32 globally. Attribution matches an exact plugin identifier,
then the longest complete package prefix, with at most 32 normalized prefixes
per project. Disabling performance consent clears the project's history.

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
