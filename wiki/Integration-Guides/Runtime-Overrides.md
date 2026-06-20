---
title: "Runtime Overrides"
order: 5
published: true
draft: false
---

# Runtime Overrides

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Server owners can override destination settings without editing the packaged descriptor inside another mod.

This is optional. Hosted telemetry is expected to work from the shipped descriptor alone when the mod bakes in a publishable ingest key.

Override files live under Alec's Telemetry data directory:

```text
Settings/projects/<project-id>.json
```

## Switch Hosted To A Custom Endpoint

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

## Override Hosted Key

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

Descriptor-declared `usage.details`, `stats.details`, and `performance.details` allowlists are not runtime override fields. They are part of the mod author's packaged telemetry contract so uploaded custom details stay predictable for the hosted portal.

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
  "hostedIngestEndpoint": "https://telemetry.alecsmods.com/ingest/crash",
  "hostedEventIngestEndpoint": "https://telemetry.alecsmods.com/ingest/event"
}
```

## Merge Rules

- The packaged descriptor stays the source of truth for project identity and attribution.
- Override files only change runtime behavior.
- Override values win over packaged destination values.
- Missing override fields fall back to packaged descriptor values.
