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
  "usage": { "enabled": false }
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
- `usage.enabled`
- `usage.allowedEvents`
- `customEndpoint.url`
- `customEndpoint.eventUrl`
- `customEndpoint.headers`

Descriptor-declared `usage.details` and `performance.details` allowlists are not
runtime override fields. They are part of the mod author's packaged telemetry
contract so uploaded custom details stay predictable for the hosted portal.

## Merge Rules

- packaged descriptor stays the source of truth for project identity and attribution
- override files only change runtime behavior
- override values win over packaged destination values
- missing override fields fall back to packaged descriptor values
