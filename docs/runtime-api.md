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
- `profiler()` for the local Phase 2 project profiler view

The runtime ignores events that are disabled by project consent, descriptor
defaults, runtime overrides, sampling, or descriptor allowlists.

## Local project profiler API (Phase 2)

The profiler API reads completed passive Spark `ASYNC` `EXECUTION` windows. It
does not read a profile started by a user. Publication requires the global Spark
monitor, the project, and the project performance consent to be enabled. The
result is a bounded in-memory summary, not a raw profile. Telemetry has no
dedicated profiler evidence file and no structured Phase 2 profiler evidence
queue or upload path. Monitor summaries and command-output text can still be
retained by ordinary server logging and can enter a manually shared current or
previous log attachment under the normal report settings, review, redaction,
and clipping controls. The bounded snapshot, signal, and context values remain
in memory.

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

Each snapshot is for one project. It contains the project ID and required
logical project version, Spark window key, publication time, selected
WorldThread self-time, qualification rule set, immutable publication context, and at most
five path entries. A path contains an attribution, owned
class/method/descriptor, optional first external class/method/descriptor,
sampled milliseconds, selected WorldThread share, a qualification, a
32-character fingerprint, and a representative path of at most eight frames.
The rule set is `hot-path-v1`.

The low-cost capture path does not run Spark class-source lookup. Current
direct captures therefore use `<unknown>` source labels. Project descriptors
must provide accurate `packagePrefixes` for reliable attribution.

An actionable window is a successful bounded Spark capture with usable selected
WorldThread samples. The service advances every performance-eligible project
for that window, including a project with no attributed path. Such a project
gets an empty `COMPLETE` snapshot. A failed, incompatible, too-complex,
timed-out, or sample-free window does not advance history. `history()` retains
at most ten snapshots per project, subject to the global limits.

Listeners are offered each published actionable snapshot, including an empty
`COMPLETE` marker. Delivery runs outside Spark capture and service locks. Each
listener has one pending value, so a slow listener can receive the newest value
after intermediate callbacks are coalesced; use `history()` for complete
retention.

`hot-path-v1` qualifies one path in one window when sampled self time is at
least 20 ms and at least 2% of selected WorldThread self-time. A path is
`OBSERVED` until it becomes a stronger candidate. It is `PROVISIONAL` when at
least one appearance reaches 100 ms and 20% share but fewer than three latest
qualifying windows. It is `REPEATED` after qualifying in at least three of the
latest five actionable windows. Qualification uses the stable path fingerprint;
an empty actionable window ages old candidates. The `signals` command can show
an unexpired candidate that is absent from the newest window, while a snapshot
contains only paths sampled in its current window.

The signal engine ranks active candidates by median selected WorldThread share,
then total sampled milliseconds, then fingerprint. It returns at most five
candidates. Severity is a command-side descriptive band: `NOTICE` is 2% to
under 5%, `MODERATE` is 5% to under 10%, `HIGH` is 10% to under 20%, and
`SEVERE` is 20% or more. Severity describes sampled impact. It is not a
causality decision and does not prove that the project caused server lag.

`TelemetryProfilerContext` is immutable and captures publication-time context:

- `observedAtMillis`: the publication timestamp, separate from the Spark window
  key;
- `hytaleVersion`: nullable cached server manifest version;
- `playerCount`: nullable aggregate current player count;
- `tps`: nullable finite Spark public one-minute TPS value;
- `mspt`: nullable finite Spark public one-minute MSPT mean; and
- `breadcrumbCategoryCounts`: immutable non-zero counts for accepted declared
  categories from five one-minute buckets.

The five-argument `TelemetryProfilerContext` constructor remains available for
binary compatibility and sets `hytaleVersion` to unknown. A missing source is
unknown. The profiler worker does not query Hytale World, Universe, Store,
PlayerRef, components, or ECS state.

Breadcrumb counts are populated only while the project is enabled and
breadcrumb consent is enabled. Clearing breadcrumb consent clears only those
five-minute counts; it does not clear performance snapshots or signals.

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
status reports the truncation. Each package-prefix value is limited to 512
characters before normalization. Longer values are also omitted and reported
as truncated.

The local service retains at most ten snapshots per project, 500 snapshots
globally, and 500 retained project-path entries globally. It accepts at most
four listener workers per project and 32 across the runtime. Each listener has
one pending snapshot; a slow listener can receive the newest pending value, so
listeners must not be used as the history store. Close a subscription when it
is no longer needed. A project view is cleared when the project is disabled or
performance consent is disabled, and only later completed actionable windows
with all gates enabled can repopulate it.

The view never exposes Spark protobufs, reflection objects, source maps, raw
frame trees, player identifiers, or server identifiers. Breadcrumb context
contains only approved static category names and five-minute counts. It does
not contain breadcrumb detail text or custom fields. Raw Spark profile and
frame-tree data are not written to ordinary logs or uploaded by Telemetry.
Spark's own retention or upload settings remain separate. A consumer
integration controls how it handles a snapshot after it receives it.

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
