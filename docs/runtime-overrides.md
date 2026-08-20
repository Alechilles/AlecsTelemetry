# Runtime Overrides

Server owners can override destination settings without editing the packaged
descriptor inside another mod.

This is optional. Hosted telemetry is expected to work from the shipped
descriptor alone when the mod bakes in a publishable ingest key.

Override files live under the canonical Alec's Telemetry data root:

```text
<ServerOrSaveRoot>/mods/Alechilles_Alec's Telemetry!/Settings/projects/<project-id>.json
```

Runtime settings, server identity, queues, and project overrides share this same
root. Older files under `<ServerOrSaveRoot>/telemetry`,
`<ServerOrSaveRoot>/Telemetry`, or embedded owner
`<ConsumerModDataDir>/Telemetry/Settings/projects` folders are migrated into the
canonical root on startup when the canonical destination is missing.

## Example: switch hosted to custom endpoint

```json
{
  "destinationMode": "custom",
  "customEndpoint": {
    "url": "https://example.com/api/telemetry/crash",
    "headers": {
      "Authorization": "Bearer your-token"
    }
  }
}
```

## Example: disable one project entirely

```json
{
  "enabled": false
}
```

## Example: disable breadcrumbs only

```json
{
  "events": {
    "breadcrumbs": {
      "enabled": false
    }
  }
}
```

## Example: disable telemetry categories

These fields are also written by the first-run consent UI and by `/telemetry consent`.

```json
{
  "enabled": true,
  "capture": {
    "uncaughtExceptions": false,
    "setupFailures": false,
    "startFailures": false,
    "exceptionalWorldRemovals": false
  },
  "events": {
    "errors": { "enabled": false },
    "lifecycle": { "enabled": false },
    "breadcrumbs": { "enabled": false }
  },
  "performance": { "enabled": false },
  "usage": { "enabled": false },
  "stats": { "enabled": false }
}
```

## Example: override hosted key

```json
{
  "destinationMode": "hosted",
  "hosted": {
    "projectKey": "new_public_project_key"
  }
}
```

## Example: server-owner manual report controls

Global runtime settings live in
`<ServerOrSaveRoot>/mods/Alechilles_Alec's Telemetry!/Settings/runtime.json`, not
per-project override files. The runtime creates this file with defaults the first
time it starts.

```json
{
  "enabled": true,
  "flushIntervalSeconds": 180,
  "connectTimeoutMs": 2000,
  "readTimeoutMs": 3000,
  "maxPendingReportsPerProject": 200,
  "maxPendingEventsPerProject": 500,
  "maxUploadsPerFlush": 10,
  "maxBreadcrumbsPerProject": 30,
  "sparkProfiler": {
    "enabled": false,
    "initialDelaySeconds": 90,
    "intervalSeconds": 60,
    "timeoutSeconds": 10,
    "maxSummaryEntries": 5,
    "maxHistorySnapshots": 10
  },
  "hostedIngestEndpoint": "https://telemetry.alecsmods.com/ingest/crash",
  "hostedEventIngestEndpoint": "https://telemetry.alecsmods.com/ingest/event"
}
```

Manual report controls also live in the canonical `Settings/runtime.json`:

```json
{
  "manualReports": {
    "enabled": true,
    "manualReviewRequired": true,
    "allowContact": true,
    "allowResolutionUpdates": true,
    "allowCurrentServerLog": false,
    "allowPreviousServerLog": false,
    "allowLoadedModList": true,
    "allowDiagnostics": true,
    "maxLogAttachmentBytes": 262144,
    "maxPendingManualReportsPerProject": 200,
    "hostedReportIngestEndpoint": "https://telemetry.alecsmods.com/ingest/report"
  }
}
```

Manual report settings can disable reports entirely, require local review before
upload, disable logs, disable installed-mod lists, disable diagnostics, disable
contact fields, and disable resolution updates. Log attachments are clipped to
`maxLogAttachmentBytes` and redacted before local storage or upload.

## Optional Spark hot-path monitor

The canonical `sparkProfiler` block is an opt-in server-owner setting. It is off
by default. To use it, enable Spark's passive background profiler, set
`enabled` to `true` in the canonical `Settings/runtime.json`, and restart the
server.

The active Telemetry coordinator starts the monitor after its service starts.
The monitor reads only completed Spark `ASYNC` `EXECUTION` background windows; it
does not read a profile started by a user. It checks the latest completed window
after `initialDelaySeconds`. After a capture attempt resolves without opening
the circuit, the next poll is scheduled `intervalSeconds` later. A poll can
read and summarize the same latest completed window again. The monitor retains
and logs a completed actionable window key once, but `NO_ACTIONABLE_DATA` can be
processed again on later polls. A completed window can be behind current server
activity by the Spark background-profiler interval.

The ranking uses Java self time from Hytale thread names containing
`WorldThread`. Native frames and Java work from other thread names do not enter
the ranking. Telemetry keeps only bounded immutable summaries and history in
memory, and writes up to `maxSummaryEntries` hot paths when it first retains and
logs an actionable window key in the ordinary server log. The monitor does not
directly queue or upload profiler data. Its ordinary log summary lines can be
included if a user submits
a manual report with current or previous server-log attachments and the
manual-report settings, project descriptor, review, redaction, and clipping
controls allow them. The monitor does not write raw Spark profile data to the
log or a profile file, and does not upload that raw data. Spark may retain its
own profiler data under its own settings.

Every line returned by `/telemetry profiler status`, `/telemetry profiler top`,
and `/telemetry profiler history` is also written at `INFO` to the ordinary
server log with a `Spark profiler command output` prefix. This includes current
status values, ranked hot paths, and retained-window summary lines shown by the
commands. Command logging failure does not prevent the reply from reaching the
operator.

### Project profiler views

Phase 1 also publishes a local project view after a completed passive window.
Publication requires all three gates:

- the global `sparkProfiler.enabled` setting is `true`;
- the project is enabled; and
- the project's performance consent is enabled.

The view contains immutable summaries, not raw Spark data. It can expose owned
Java class and method names, downstream Java method names, sampled timing,
WorldThread share, fingerprints, and bounded representative paths. It never
exposes Spark protobufs, reflection objects, raw frame trees, player data, or
server identity. Spark can retain or upload its own profiles under Spark's
separate settings.

Consumer code reads the view with `TelemetryProjectHandle.profiler()` and can
subscribe to later snapshots. The capability is `profiler-api-v1`. A legacy
handle or older elected provider returns an unavailable view with empty data
and a closed subscription instead of throwing a linkage error. The API is not
an authorization boundary: server-side code with API access can request another
registered project by ID, and consumer integrations control later handling of a
snapshot.

The local service retains at most five paths per snapshot, ten snapshots per
project, and 500 retained project-path entries globally. It accepts at most
four listener workers per project and 32 globally. A listener has one pending
snapshot and receives the newest pending value when it is slow. A project view
is cleared when performance consent is disabled; re-enabling consent does not
restore old summaries.

Project commands are optional filters on the existing commands:

```text
/telemetry profiler top <project-id>
/telemetry profiler history <project-id>
```

`top` shows at most five paths from the latest project snapshot. `history`
shows the newest ten snapshots. Path lines include `SELF` or `DOWNSTREAM`,
sampled milliseconds, selected WorldThread share, the owned method, an optional
first external method, and a 32-character fingerprint. Phase 1 emits only
`OBSERVED`; `signals` belongs to the later Phase 2 `hot-path-v1` work. Every
project-filtered reply also copies to the ordinary server log. A manually
attached server log can contain those lines when the normal manual-report
settings and review controls allow the attachment.

Profiler attribution matches exact plugin identifiers first, then the longest
complete package prefix. The profiler index accepts at most 32 normalized
package prefixes per project and 512 characters per prefix. It reports omitted
prefixes in diagnostics.

### Settings

The canonical `sparkProfiler` fields are:

| Field | Default | Accepted range |
| --- | ---: | ---: |
| `enabled` | `false` | `true` or `false` |
| `initialDelaySeconds` | `90` | 60 to 3600 |
| `intervalSeconds` | `60` | 30 to 3600 |
| `timeoutSeconds` | `10` | 1 to 60 |
| `maxSummaryEntries` | `5` | 1 to 10 |
| `maxHistorySnapshots` | `10` | 1 to 10 |

Values outside a numeric range are clamped. New settings templates write only
the canonical `sparkProfiler` block. The legacy `sparkProfilerCanary` JSON block
is still accepted for compatibility. It supports `enabled`,
`initialDelaySeconds`, `timeoutSeconds`, and `maxSummaryEntries`; its interval
is 60 seconds and its history limit is 10. If both JSON blocks are present, the
canonical `sparkProfiler` block wins. This includes an explicit `null` canonical
value, which uses safe canonical defaults instead of the legacy block.

### Safety and compatibility

- At least 256 MiB of JVM heap headroom is required before a Spark read. Low
  headroom skips that read and tries again at the next interval.
- A window with more than 25,000 distinct frames is discarded as `TOO_COMPLEX`;
  Telemetry does not keep a partial ranking.
- A JVM-wide capture gate allows only one Telemetry Spark read at a time, even
  when standalone and embedded runtimes are present. Capture work uses daemon
  threads and does not run on Hytale's shared scheduler.
- The reflection bridge accepts only the exact tested Spark artifacts in the
  table below. It checks both the reported base version and the SHA-256 of the
  complete loaded Spark JAR before reading private Spark state. A repackaged,
  later, or changed artifact fails closed. A missing or changed private member
  also fails closed.
- Unsupported Spark, incompatible Spark, excessive profile complexity, a
  failed read, or a timeout opens the monitor circuit for the process. The
  monitor does not retry after the circuit opens. A late export result is
  discarded if Spark ignores interruption. Spark absence, no active background
  sampler, no completed window, no actionable WorldThread data, and low heap do
  not open the circuit; the monitor can poll again.
- A new read starts only while its provider owns the active Telemetry
  coordinator. Ownership loss requests cancellation, stops future polls, and
  fences late results. A Spark export that ignores interruption may continue in
  its daemon worker until it returns; the late result is not published.
  Coordinator shutdown suspends the monitor before service shutdown, and the
  provider handle closes it on permanent shutdown.

The status output uses states such as `READY`, `WAITING`, `CAPTURING`,
`COMPLETE`, `ABSENT`, `NOT_RUNNING`, `NO_DATA`, `NO_ACTIONABLE_DATA`,
`LOW_HEAP`, `UNSUPPORTED`, `TOO_COMPLEX`, `INCOMPATIBLE`, `TIMED_OUT`,
`FAILED`, `NOT_OWNER`, and `SHUTDOWN`. `DISABLED` means the setting is off.
The `circuitOpen` flag identifies a permanent session stop after a compatibility,
complexity, failure, or timeout condition.

Inspect local state with `/telemetry status`,
`/telemetry profiler status`, `/telemetry profiler top`, and
`/telemetry profiler history`.

### Tested Spark Hytale builds

| Spark Hytale release | Accepted artifact SHA-256 |
| --- | --- |
| 1.10.158-r1 | `f57c7a93f77657318340f214fbc37bc244900bc49bd55612f002e85696cc0662` |
| 1.10.162-r1 | `0d5889ca426a5919bb56a1b36177fbeb86ab143428fec7b236103f4fec874342` |
| 1.10.164-r1 | `5bd826a5da5a8d9c0b540f2a84de4538de51c3ab199019f7379fdea30ecc5756` |
| 1.10.165-beta2 | `13654be2a1f00047e3fadf69335afdbb45332bd4eb0d222c7754c4685f6684f2` |
| 1.10.165-beta3 | `4178cccab6afff2d3c2f0a68fb200d39811838fad1a21e595deb4c917eadfc69` |
| 1.10.165-beta4 | `41a5bbd8ea681525df80d7b08839b85824cfa9e2ecb8ea1837e26809c441603f` |
| 1.10.165-r1 | `9c3c762619893fc44289d712e18c57ba3c0675b6b2e1967a36273c75705ebecb` |
| 1.10.172-beta5 | `ed2b28921d1ddd606d612ec602472d79ae606656ec840fe9f8ba593d39f3a159` |
| 1.10.172-beta6 | `5e536469e7f4511e9ba279114ddf1b634938161bb896e4098a5a4af28966d19f` |
| 1.10.172-beta7 | `1c5806cdc276af608051b8fc6c3aee05ab184da28e81735ba9682e5950c5617a` |

The compatibility gate checks Spark's manifest base version and the SHA-256 of
the complete loaded Spark JAR before it reads private Spark state. Only the
exact official artifacts in this table are accepted. A repackaged or later
build is skipped even if it reuses a listed version. Any missing or changed
runtime member also makes the monitor fail closed. Normal Telemetry and Spark
behavior continue when the monitor is disabled, absent, unsupported, timed out,
or incompatible.

## Supported Override Fields

- `enabled`
- `destinationMode`
- `capture.uncaughtExceptions`
- `capture.setupFailures`
- `capture.startFailures`
- `capture.exceptionalWorldRemovals`
- `events.breadcrumbs.enabled`
- `events.errors.enabled`
- `events.lifecycle.enabled`
- `hosted.endpoint`
- `hosted.eventEndpoint`
- `hosted.projectKey`
- `hosted.headers`
- `performance.enabled`
- `performance.sampleRate`
- `performance.thresholdMs`
- `stats.enabled`
- `stats.allowedEvents`
- `usage.enabled`
- `usage.allowedEvents`
- `customEndpoint.url`
- `customEndpoint.eventUrl`
- `customEndpoint.headers`

Descriptor-declared `usage.details`, `stats.details`, and `performance.details`
allowlists are not runtime override fields. They are part of the mod author's
packaged telemetry contract so uploaded custom details stay predictable for the
hosted portal.

## Merge Rules

- packaged descriptor stays the source of truth for project identity and attribution
- override files only change runtime behavior
- override values win over packaged destination values
- missing override fields fall back to packaged descriptor values
