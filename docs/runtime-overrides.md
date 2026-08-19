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
  "sparkProfilerCanary": {
    "enabled": false,
    "initialDelaySeconds": 90,
    "timeoutSeconds": 10,
    "maxSummaryEntries": 5
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

## Optional Spark profiler canary

The `sparkProfilerCanary` block is an experimental server-owner setting. It is
off by default. To use it, enable Spark's background profiler, set `enabled` to
`true` in the canonical `Settings/runtime.json`, and restart the server.

The active Telemetry coordinator schedules one read after the startup delay.
The canary reads only Spark's latest completed `ASYNC` `EXECUTION` background
window. It does not read a profile that a user started. A completed Spark window
can be up to 60 seconds behind the current server activity.

The result is local only. Telemetry keeps a bounded in-memory summary and writes
up to `maxSummaryEntries` source/frame hot paths to the server log. It does not
retain the raw Spark profile, create a profile file, or send the profile or
summary to Alec's hosted platform or a custom telemetry endpoint. Use
`/telemetry status` to inspect the canary state.

Safety limits are fixed or bounded:

- `initialDelaySeconds`: 60 to 3600; default 90
- `timeoutSeconds`: 1 to 60; default 10
- `maxSummaryEntries`: 1 to 10; default 5
- at least 256 MiB of JVM heap headroom is required
- a window with more than 25,000 distinct frames is discarded as `TOO_COMPLEX`
  instead of producing a partial ranking
- only one capture attempt runs per server process
- a timeout or compatibility failure opens the canary circuit; there is no retry
- capture work uses dedicated daemon threads and does not run on Hytale's shared
  scheduler

A Java thread cannot forcibly stop a Spark export that ignores interruption.
After a timeout, Telemetry discards any late result and does not retry. The
dedicated worker is a daemon thread, so it cannot keep the server process alive.

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
runtime member also makes the canary fail closed. Normal Telemetry and Spark
behavior continue when the canary is disabled, absent, unsupported, timed out,
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
