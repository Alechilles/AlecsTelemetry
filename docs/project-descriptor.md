# Project Descriptor

Consumer mods opt into Alec's Telemetry by shipping a descriptor at:

```text
telemetry/project.json
```

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
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

## Minimal Custom Endpoint Example

```json
{
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

### `defaults`

- `enabled`
- `destinationMode`
  - `hosted`
  - `custom`

### `hosted`

- `endpoint`
  - optional override; normally omitted so the runtime default hosted endpoint is used
- `projectKey`
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
