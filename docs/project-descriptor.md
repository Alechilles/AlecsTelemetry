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

Hosted `projectKey` values are designed to be publishable ingest keys.

- bake them into the shipped descriptor for plug-and-play telemetry
- do not treat them as operator-managed secrets
- keep destructive/admin capabilities out of ingest-key auth scope

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
  "events": {
    "errors": { "enabled": true },
    "lifecycle": { "enabled": true },
    "breadcrumbs": { "enabled": true, "automatic": true }
  },
  "performance": {
    "enabled": true,
    "sampleRate": 1.0,
    "thresholdMs": 100,
    "details": {
      "reload_config_duration": {
        "allowedFields": {
          "configFileCount": { "type": "number" },
          "phase": { "type": "enum", "values": ["read", "parse", "apply"] }
        }
      }
    }
  },
  "usage": {
    "enabled": true,
    "allowedEvents": ["settings_opened"],
    "details": {
      "settings_opened": {
        "allowedFields": {
          "source": { "type": "enum", "values": ["command", "settings_ui"] },
          "changedSettingCount": { "type": "number" },
          "configArea": { "type": "string", "maxLength": 60 }
        }
      }
    }
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
- `events`
- `performance`
- `usage`
- `defaults`
- `hosted`
- `customEndpoint`

### `capture`

- `uncaughtExceptions`
- `setupFailures`
- `startFailures`
- `exceptionalWorldRemovals`

### `events`

- `errors.enabled`
  - controls explicit non-fatal error events recorded through the runtime API
- `lifecycle.enabled`
  - controls explicit lifecycle timing events recorded through the runtime API
- `breadcrumbs.enabled`
  - controls breadcrumb storage and breadcrumb attachment to crashes/debug events
- `breadcrumbs.automatic`
  - reserved for low-noise automatic breadcrumbs; leave enabled unless the project wants manual breadcrumbs only

### `performance`

- `enabled`
- `sampleRate`
  - `0.0` to `1.0`
- `thresholdMs`
  - events below this duration are skipped
- `details`
  - optional per-event allowlist for custom mod-specific detail fields

### `usage`

- `enabled`
- `allowedEvents`
  - usage event names that this descriptor permits
- `details`
  - optional per-event allowlist for custom mod-specific detail fields

### Detail allowlists

Custom `usage.details` and `performance.details` are intentionally descriptor-declared.
Runtime code can send only fields that are listed for that event name.

Supported field types:

- `string`
  - optional `maxLength`
- `number`
- `boolean`
- `enum`
  - requires `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set
are dropped before upload.

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
- `eventEndpoint`
  - optional override for the generic non-crash event ingest path
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
- publishable hosted `projectKey` when using Alec's hosted service
- extra package prefixes if your mod spans multiple packages
- explicit ids if you need stable naming that differs from your manifest

## Hosted Key Rotation

Hosted key rotation is manual.

To rotate a hosted `projectKey`:

1. update the hosted project registry
2. update the mod's `telemetry/project.json`
3. redeploy the mod or descriptor change
