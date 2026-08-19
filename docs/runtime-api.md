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
- `recordBreadcrumb(TelemetryBreadcrumbContext)`
- `captureSetupFailure(throwable)`
- `captureStartFailure(throwable)`
- `recordError(...)` and `recordErrorWithContext(...)`
- `recordLifecycle(...)` and `recordLifecycleWithContext(...)`
- `recordPerformance(...)` and `recordPerformanceWithContext(...)`
- `recordUsage(...)` and `recordUsageWithContext(...)`
- `recordStats(...)` and `recordStatsWithContext(...)`
- `openReportPage(...)`
- `requestFlush()`
- `profiler()` for the local Phase 1 project profiler view

The runtime ignores events that are disabled by project consent, descriptor
defaults, runtime overrides, sampling, or descriptor allowlists.

## Local project profiler API (Phase 1)

The profiler API reads completed passive Spark `ASYNC` `EXECUTION` windows. It
does not read a profile started by a user. The global Spark monitor and the
project performance consent must both be enabled. The result is a local,
bounded summary. It is not a raw profile and it is not uploaded by Telemetry.

Use the view from a project handle:

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

`TelemetryProfilerView` advertises capability `profiler-api-v1`. Its public
values are immutable defensive copies. `TelemetryProjectHandle.profiler()` is
a binary-compatible default method. On a legacy handle or an older elected
provider, it returns an unavailable view: status `UNAVAILABLE`, empty
`latest()` and `history()`, and a closed subscription. The consumer can keep
running without a linkage error.

Each snapshot is for one project. It can contain the project ID and logical
version, Spark window key, observation time, selected WorldThread self-time,
qualification rule set, and at most five path entries. A path contains an
attribution, owned class/method/descriptor, optional first external
class/method/descriptor, sampled milliseconds, selected WorldThread share, a
32-character fingerprint, and a representative path of at most eight frames.
Phase 1 uses qualification `OBSERVED` and rule set `unqualified-v1`.

The project scope is an attribution scope, not an authorization boundary. The
local runtime API is not an authorization boundary: server-side code with API
access can request another registered project by ID. Consumer integrations
control later handling of snapshots they receive.

Attribution values have different meanings:

- `SELF`: the sampled self time belongs to the project's owned method.
- `DOWNSTREAM`: the sampled self time is below the nearest owned method. The
  first external method is shown as context. This is evidence of downstream
  work, not an exact blame decision.

The ownership index matches an exact normalized plugin identifier first. If
that is not available, it uses the longest complete normalized package prefix.
It never uses a partial string match. The profiler index accepts at most 32
normalized package prefixes per project. Extra prefixes are omitted and the
status reports the truncation.

The local service retains at most ten snapshots per project and 500 retained
project-path entries globally. It accepts at most four listener workers per
project and 32 across the runtime. Each listener has one pending snapshot; a
slow listener receives the newest pending value. Close a subscription when it
is no longer needed. A project view is cleared when performance consent is
disabled, and only later completed windows can repopulate it.

The view never exposes Spark protobufs, reflection objects, source maps, raw
frame trees, player data, or server identity. Spark's own retention or upload
settings remain separate. A consumer integration controls how it handles a
snapshot after it receives it.

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

## Structured Breadcrumb Context

Use `TelemetryBreadcrumbContext` when a diagnostic operation needs correlation
without placing identifiers into free-form text:

```java
project.recordBreadcrumb(
        TelemetryBreadcrumbContext.builder("persistence", "incident opened")
                .correlationId(traceId)
                .incidentId(incidentId)
                .phase("CANONICAL_OUTCOME_UNKNOWN")
                .operation("coop_release")
                .scopeType("COOP_SLOT")
                .failureClass("OUTCOME_UNKNOWN")
                .disposition("SCOPED_QUARANTINE")
                .attribute("recoveryAttempt", 1)
                .build()
);
```

Structured breadcrumbs retain the legacy category/detail fields and add bounded
optional correlation, phase, operation, scope, failure, disposition, and scalar
attribute fields. Older handle implementations safely fall back to the legacy
category/detail call. Consumer mods must use randomized or hashed correlation
values and must not put player names, raw UUIDs, coordinates, tokens, chat, or
inventory contents into these fields.

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
