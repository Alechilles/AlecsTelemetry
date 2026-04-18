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

## Merge Rules

- packaged descriptor stays the source of truth for project identity and attribution
- override files only change runtime behavior
- override values win over packaged destination values
- missing override fields fall back to packaged descriptor values
