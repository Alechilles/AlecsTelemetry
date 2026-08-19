---
title: "Runtime Overrides"
order: 15
published: true
draft: false
---

# Runtime Overrides

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Server owners can override destination settings without editing the packaged descriptor inside another mod.

This is optional. Portal uploads are expected to work from the shipped descriptor alone when the mod bakes in a publishable ingest key.

Override files live under Alec's Telemetry data directory:

```text
Settings/projects/<project-id>.json
```

## Switch Portal Uploads To A Custom Endpoint

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

## Disable One Project

```json
{
  "enabled": false
}
```

## Disable Breadcrumbs Only

```json
{
  "events": {
    "breadcrumbs": {
      "enabled": false
    }
  }
}
```

## Disable Telemetry Categories

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

## Override Project Key

```json
{
  "destinationMode": "hosted",
  "hosted": {
    "projectKey": "new_public_project_key"
  }
}
```

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

Descriptor-declared `usage.details`, `stats.details`, and `performance.details` allowlists are not runtime override fields. They are part of the mod author's packaged telemetry contract so uploaded custom details stay predictable for the web portal.

## Global Runtime Settings

Global runtime settings live in `Settings/runtime.json`, not per-project override files. The runtime creates this file with defaults the first time it starts.

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

## Optional Spark Hot-Path Monitor

The canonical `sparkProfiler` setting is off by default. When a server owner
enables it, the active Telemetry coordinator reads completed `ASYNC` `EXECUTION`
windows from Spark's passive background profiler after startup. It does not read
a profile started by a user. After `initialDelaySeconds`, the monitor polls
`intervalSeconds` after each capture attempt resolves without opening the
circuit. It can re-read the same latest completed window. A completed actionable
window key is retained and logged once; `NO_ACTIONABLE_DATA` can be processed
again on later polls.

The ranking uses Java self time from Hytale thread names containing
`WorldThread`. Native frames and Java work on other thread names do not enter
the ranking. Telemetry keeps bounded summaries and history in memory and logs up
to `maxSummaryEntries` hot paths when it first retains and logs an actionable
window key.

The monitor does not directly queue or upload profiler data. Ordinary log
summary lines can be included if a user submits a manual report with current or
previous server-log attachments and the manual-report settings, project
descriptor, review, redaction, and clipping controls allow them. The monitor
does not write raw Spark profile data to the log or a profile file, and does not
upload that raw data. Spark may retain its own profiler data under its settings.

Every line returned by `/telemetry profiler status`, `/telemetry profiler top`,
and `/telemetry profiler history` is also written at `INFO` to the ordinary
server log with a `Spark profiler command output` prefix. This includes status,
ranked hot paths, and retained-window summaries shown by the commands. A logging
failure does not prevent the operator reply.

### Spark monitor settings

| Field | Default | Accepted range |
| --- | ---: | ---: |
| `enabled` | `false` | `true` or `false` |
| `initialDelaySeconds` | `90` | 60 to 3600 |
| `intervalSeconds` | `60` | 30 to 3600 |
| `timeoutSeconds` | `10` | 1 to 60 |
| `maxSummaryEntries` | `5` | 1 to 10 |
| `maxHistorySnapshots` | `10` | 1 to 10 |

Values outside a numeric range are clamped. The legacy `sparkProfilerCanary`
JSON block remains accepted with `enabled`, `initialDelaySeconds`,
`timeoutSeconds`, and `maxSummaryEntries`; its interval is 60 seconds and its
history limit is 10. If both blocks are present, canonical `sparkProfiler`
wins, including an explicit `null` canonical value, which uses safe canonical
defaults.

The monitor checks Spark's reported base version and the SHA-256 of the complete
loaded Spark JAR before reading private Spark state. Only the exact artifacts in
the tested release matrix are accepted. A changed, repackaged, later, or
otherwise incompatible artifact fails closed. At least 256 MiB of JVM heap
headroom is required. A window with more than 25,000 distinct frames is
discarded without a partial ranking. Unsupported or incompatible Spark,
excessive complexity, a failed read, or a timeout opens the session circuit;
the monitor does not retry after that. Spark absence, no active background
sampler, no completed window, no actionable WorldThread data, and low heap do
not open the circuit. Ownership loss requests cancellation, stops future polls,
and fences late results, but an export that ignores interruption can continue
until Spark returns. See the repository's [runtime override reference](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/runtime-overrides.md)
for the complete tested Spark version and JAR hash matrix.

## Merge Rules

- The packaged descriptor stays the source of truth for project identity and attribution.
- Override files only change runtime behavior.
- Override values win over packaged destination values.
- Missing override fields fall back to packaged descriptor values.
