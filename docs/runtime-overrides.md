# Runtime Overrides

Server owners can override destination settings without editing the packaged
descriptor inside another mod.

This is optional. Hosted telemetry is expected to work from the shipped
descriptor alone when the mod bakes in a publishable ingest key.

Override files live under Alec's Telemetry data directory:

```text
Settings/projects/<project-id>.json
```

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

Global runtime settings live in `Settings/runtime.json`, not per-project override
files. The runtime creates this file with defaults the first time it starts.

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
  "hostedIngestEndpoint": "https://telemetry.alecsmods.com/ingest/crash",
  "hostedEventIngestEndpoint": "https://telemetry.alecsmods.com/ingest/event"
}
```

Manual report controls also live in `Settings/runtime.json`:

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
