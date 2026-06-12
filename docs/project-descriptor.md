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
    "errors": {
      "enabled": true,
      "details": {
        "needs_seek_failed": {
          "allowedFields": {
            "reason": { "type": "string", "maxLength": 120 },
            "resource": { "type": "enum", "values": ["food", "water", "unknown"] }
          }
        }
      }
    },
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
    "eventUrl": "https://example.com/api/telemetry/event",
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
- `errors.details`
  - optional per-error-event allowlist for custom mod-specific Event Context fields
- `lifecycle.enabled`
  - controls explicit lifecycle timing events recorded through the runtime API
- `lifecycle.details`
  - optional per-lifecycle-event allowlist for custom mod-specific Event Context fields
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

Custom `events.errors.details`, `events.lifecycle.details`, `usage.details`,
and `performance.details` are intentionally descriptor-declared. Runtime code can
send only fields that are listed for that event name.

Supported field types:

- `string`
  - optional `maxLength`
- `number`
- `boolean`
- `enum`
  - requires `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set
are dropped before upload.

### Runtime API Event Context

Consumer mods can attach typed context to explicit non-crash events with:

- `recordErrorWithContext`
- `recordLifecycleWithContext`
- `recordPerformanceWithContext`
- `recordUsageWithContext`

`TelemetryEventContext` supports these standardized fields:

- `detail`
- `severity`
- `fingerprint`
- `subsystem`
- `phase`
- `operation`
- `target`
- `featureKey`
- `entryPoint`
- `runtimeSide`
- `entityType`
- `itemId`
- `blockId`
- `biomeId`
- `commandName`
- `worldName`

`detail` is stored in the event `attributes` map. The other standardized fields
are first-class event envelope fields. The context builder also accepts custom
`detail(key, value)` entries; those custom fields are uploaded only when the
descriptor declares an allowlist for the matching event name.

### Custom Event Context details

The hosted portal presents descriptor-validated `details` fields as Event Context. Scalar,
bounded, low-cardinality fields may be aggregated into Context Breakdowns on issue pages.
Mods should use stable reason codes, resource types, role IDs, entity types, and coarse
buckets. Avoid player identifiers, raw UUIDs, secrets, exact coordinates, and free-form
messages when a field is intended for breakdowns.

### `runtimeMode`

- `dependency`
  - default when omitted
  - standalone `Alec's Telemetry` runtime may discover and manage the project
- `embedded`
  - the owning mod is expected to bootstrap embedded telemetry itself
  - standalone runtime will skip the project if it sees this mode

### `defaults`

- `enabled`
  - mod author's default project-level consent setting
  - used as the initial project toggle state in the first-run consent UI
- `destinationMode`
  - `hosted`
  - `custom`

### Consent defaults

Alec's Telemetry shows a first-run consent UI when an operator installs a telemetry-enabled mod that has not been reviewed before. The initial toggle state comes from the packaged descriptor:

- `defaults.enabled` is the mod author's default project-level opt-in or opt-out setting.
- `capture.*` controls the default crash telemetry category.
- `events.errors.enabled` controls the default error telemetry category.
- `events.lifecycle.enabled` controls the default lifecycle telemetry category.
- `performance.enabled` controls the default performance telemetry category.
- `usage.enabled` controls the default usage telemetry category.
- `events.breadcrumbs.enabled` controls breadcrumb collection and attachment.

Server owners can override each value in the consent UI or through `Settings/projects/<project-id>.json`. Descriptor defaults are used until an override exists.

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
- `eventUrl`
  - optional override for the generic non-crash event ingest path
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
