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

It also exposes the local Phase 2 project profiler view:

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
method and includes the first external method as context. Current paths use
`OBSERVED`, `PROVISIONAL`, or `REPEATED` from the `hot-path-v1` rolling rules.

The low-cost direct capture path does not run Spark class-source lookup.
Current captures therefore use `<unknown>` source labels. Project descriptors
must provide accurate `packagePrefixes` for reliable attribution.

An actionable window is a successful bounded Spark capture with usable selected
WorldThread samples. Every eligible project advances for that window, even
when it has no attributed path. The result is an empty `COMPLETE` snapshot, so
old candidates can expire. A failed, incompatible, too-complex, timed-out, or
sample-free window does not advance history. A path qualifies at 20 ms and 2%
share. `PROVISIONAL` requires one 100 ms and 20% appearance. `REPEATED` requires
three qualifying appearances in the latest five actionable windows.

Listeners are offered each published actionable snapshot, including an empty
`COMPLETE` marker. Delivery runs outside Spark capture and service locks. Each
listener has one pending value, so a slow listener can receive the newest value
after intermediate callbacks are coalesced; use `history()` for complete
retention.

Publication requires the global Spark monitor, the project, and the project's
performance consent to be enabled. The view exposes bounded
class/method/timing/fingerprint/path data, not raw Spark profiles or frame
trees. Telemetry has no dedicated profiler evidence file and no structured
Phase 2 profiler evidence queue or upload path. Monitor summaries and command
output can still be retained by ordinary server logging and can enter a manual
current or previous log attachment under the normal report settings, review,
redaction, and clipping controls. The bounded snapshot, signal, and context
values remain in memory. Raw Spark profile and frame-tree data are not written
to ordinary logs or uploaded by Telemetry. The API is not an authorization
boundary. Server-side consumer code with API access can request another
registered project by ID. Consumer code, including a third-party consumer,
controls later handling of snapshots.

`TelemetryProfilerContext` captures publication-time `observedAtMillis`,
`hytaleVersion` (nullable), aggregate `playerCount` (nullable), Spark public
one-minute `tps` and `mspt` (nullable), and non-zero counts for approved
breadcrumb categories from five one-minute buckets. The snapshot separately
contains the required logical `projectVersion` field. Missing context sources
remain unknown. Breadcrumb counts require project enablement and breadcrumb
consent. Breadcrumb detail text and custom fields do not enter context. The
profiler worker does not query Hytale World or ECS state.

The service keeps at most five paths per snapshot, ten snapshots per project,
500 snapshots globally, and 500 project-path entries globally. It accepts at
most four listener workers per project and 32 globally. A slow listener can
receive the newest pending snapshot, so history remains the source of truth.
Attribution matches an exact plugin identifier, then the longest complete
package prefix, with at most 32 normalized prefixes per project. Each
package-prefix value is limited to 512 characters before normalization. Longer
values are omitted and reported as truncated. Disabling the project or
performance consent clears the project's history, signals, and context.

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
