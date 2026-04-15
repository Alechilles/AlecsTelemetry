# Project Descriptor

Consumer mods opt into Alec's Telemetry by shipping a descriptor at:

```text
telemetry/project.json
```

This descriptor now supports both integration modes:

- `dependency`
- `embedded`

Embedded-mode bootstrap details are documented separately in:

- `embedded-mode.md`
- `embedded-mode-refactor-plan.md`

## Plug-And-Play Default

If your `manifest.json` has a correct:

- `Group`
- `Name`
- `Main`

then you can omit a lot of telemetry fields because Alec's Telemetry will infer:

- project id
- display name
- plugin identifier
- package prefix from the `Main` class package

## Minimal Hosted Example

```json
{
  "runtimeMode": "dependency",
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

## Minimal Custom Endpoint Example

```json
{
  "runtimeMode": "dependency",
  "defaults": {
    "destinationMode": "custom"
  },
  "customEndpoint": {
    "url": "https://example.com/api/telemetry/crash"
  }
}
```

## Full Example

```json
{
  "schemaVersion": 1,
  "projectId": "example-mod",
  "displayName": "Example Mod",
  "runtimeMode": "dependency",
  "ownerPluginIdentifiers": [
    "Example:Example Mod"
  ],
  "packagePrefixes": [
    "com.example.telemetry",
    "com.example.shared"
  ],
  "capture": {
    "uncaughtExceptions": true,
    "setupFailures": true,
    "startFailures": true,
    "exceptionalWorldRemovals": true
  },
  "defaults": {
    "enabled": true,
    "destinationMode": "hosted"
  },
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "customEndpoint": {
    "url": "https://example.com/api/telemetry/crash",
    "headers": {
      "Authorization": "Bearer your-token"
    }
  }
}
```

## Supported Fields

### Top level

- `schemaVersion`
- `projectId`
- `displayName`
- `runtimeMode`
- `ownerPluginIdentifiers`
- `packagePrefixes`
- `capture`
- `defaults`
- `hosted`
- `customEndpoint`

### `capture`

- `uncaughtExceptions`
- `setupFailures`
- `startFailures`
- `exceptionalWorldRemovals`

### `runtimeMode`

- `dependency`
  - default when omitted
  - standalone `Alec's Telemetry` runtime may discover and manage the project
- `embedded`
  - the owning mod is expected to bootstrap embedded telemetry itself
  - standalone runtime will skip the project if it sees this mode

### `defaults`

- `enabled`
- `destinationMode`
  - `hosted`
  - `custom`

### `hosted`

- `endpoint`
  - optional override; normally omitted so the runtime default hosted endpoint is used
- `projectKey`
- `projectKey` is a public ingest key, not a hidden secret
- `headers`

### `customEndpoint`

- `url`
- `headers`

## Recommendation

For most modders, keep this file small.

Only set:

- destination config
- extra package prefixes if your mod spans multiple packages
- explicit ids if you need stable naming that differs from your manifest

## Hosted Key Rotation

Hosted key rotation is manual.

To rotate a hosted `projectKey`:

1. update the hosted project registry
2. update the mod's `telemetry/project.json`
3. redeploy the mod or descriptor change
