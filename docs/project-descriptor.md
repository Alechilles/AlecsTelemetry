# Project Descriptor

Consumer mods opt into Alec's Telemetry by shipping:

```text
Server/Telemetry/project.json
```

The same descriptor works for standalone dependency mode and embedded mode.
Runtime ownership is decided by coordinator election at startup.

An embedded component that owns a separate logical project can use
`EmbeddedTelemetryBootstrap.contribute(...)` instead of the conventional
descriptor lookup. That API reads a namespaced descriptor resource from the
contributor's anchored classloader (for example,
`META-INF/alecs-telemetry/projects/component.json`), so it does not collide with
the host mod's `Server/Telemetry/project.json`. The contribution descriptor must
declare an explicit `projectId` and `displayName`, and its
`ownerPluginIdentifiers` must include the contribution's logical plugin
identifier. See [Embedded Contributions](embedded-contributions.md) for the
builder and election contract.

## Minimal Hosted Example

If your `manifest.json` has `Group`, `Name`, and `Main`, Alec's Telemetry can infer the project id, display name, plugin identifier, and Java package prefix. Asset-pack-only projects without `Main` can still infer the project id, display name, and plugin identifier.

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

This only connects the packaged descriptor to the portal project. It does not make any telemetry category available.

## Telemetry Categories

Telemetry category descriptors separate capability from initial consent:

- omitted category: unsupported and hidden from consent
- `"supported": true`: the category is available and shown in consent
- omitted `defaultEnabled`: defaults to `true` for supported categories
- `"defaultEnabled": false`: supported but initially off, so server owners can opt in
- legacy descriptor `"enabled"` is still accepted as a compatibility alias for `defaultEnabled`, but new descriptors should use `defaultEnabled`

Runtime override files still use `enabled`; that is a saved consent state, not the packaged descriptor schema.

## Stats-Only Example

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "stats": {
      "supported": true
    }
  }
}
```

## Crash Available But Opt-In

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "crash": {
      "supported": true,
      "defaultEnabled": false,
      "uncaughtExceptions": true,
      "setupFailures": true,
      "startFailures": true,
      "exceptionalWorldRemovals": true
    }
  }
}
```

## Full Example

```json
{
  "schemaVersion": 1,
  "projectId": "example-mod",
  "displayName": "Example Mod",
  "ownerPluginIdentifiers": ["Example:Example Mod"],
  "packagePrefixes": ["com.example.telemetry", "com.example.shared"],
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "crash": {
      "supported": true,
      "uncaughtExceptions": true,
      "setupFailures": true,
      "startFailures": true,
      "exceptionalWorldRemovals": true
    },
    "events": {
      "errors": {
        "supported": true,
        "details": {
          "needs_seek_failed": {
            "allowedFields": {
              "reason": { "type": "string", "maxLength": 120 },
              "resource": { "type": "enum", "values": ["food", "water", "unknown"] }
            }
          }
        }
      },
      "lifecycle": { "supported": true },
      "breadcrumbs": { "supported": true, "automatic": true }
    },
    "performance": {
      "supported": true,
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
      "supported": true,
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
    "stats": {
      "supported": true
    },
    "reports": {
      "supported": true,
      "issue": {
        "enabled": true,
        "fields": {
          "severity": {
            "type": "enum",
            "label": "Severity",
            "required": true,
            "values": ["minor", "moderate", "major", "blocking"]
          }
        }
      },
      "suggestion": { "enabled": true }
    }
  },
  "ui": {
    "iconTexturePath": "ExampleMod/Telemetry/ExampleModConsentIcon.png"
  }
}
```

## Identity Overrides

Identity fields are optional overrides.

- `projectId`: override only when the inferred ID would not match the portal project ID or a custom endpoint expects a different stable ID
- `displayName`: local consent/runtime text and envelope metadata; it does not rename the portal project
- `ownerPluginIdentifiers`: aliases, renamed plugins, or unusual ownership matching
- `packagePrefixes`: crash attribution when Java code lives outside the package inferred from `Main`, or when no `Main` exists

For an anchored contribution, the logical plugin identifier and logical semantic
version come from the contribution builder. They are the project attribution in
telemetry envelopes; the physical host plugin remains local provider/election
metadata. The descriptor's `projectId`, display name, and owner list still have
to validate against that logical identity before the contribution can become
active.

## Category Fields

Crash:

- `supported`
- `defaultEnabled`
- `uncaughtExceptions`
- `setupFailures`
- `startFailures`
- `exceptionalWorldRemovals`

Events:

- `events.errors.supported`
- `events.errors.defaultEnabled`
- `events.errors.details`
- `events.lifecycle.supported`
- `events.lifecycle.defaultEnabled`
- `events.lifecycle.details`
- `events.breadcrumbs.supported`
- `events.breadcrumbs.defaultEnabled`
- `events.breadcrumbs.automatic`

Performance:

- `supported`
- `defaultEnabled`
- `sampleRate`
- `thresholdMs`
- `details`

Usage:

- `supported`
- `defaultEnabled`
- `allowedEvents`
- `details`

Stats:

- `supported`
- `defaultEnabled`
- `details`

Manual reports:

- `supported`
- `defaultEnabled`
- `issue`
- `suggestion`
- `attachments`
- `contact`
- `resolutionUpdates`

## Details Allowlists

Custom `events.errors.details`, `events.lifecycle.details`, `usage.details`, `stats.details`, and `performance.details` are descriptor-declared allowlists. Runtime code can send only fields listed for that event name.

Supported detail field types:

- `string` with optional `maxLength`
- `number`
- `boolean`
- `enum` with required `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set are dropped before upload.

## Destination Fields

`defaults.enabled` controls the initial project-level consent toggle. `defaults.destinationMode` can be `hosted` or `custom`.

Destination and consent are project-scoped. A conventional host project and any
anchored contributions can share one runtime provider without sharing category
choices, endpoint configuration, queues, or project keys.

Anchored contributions are hosted-only in the 1.1.0 MVP. A descriptor that
selects `custom` is valid for a conventional project but is rejected when loaded
through the anchored contribution API. Same-ID contribution winners are not
hot-replaced or automatically failed over; a server restart is required for a
replacement.

`hosted.projectKey` is a publishable ingest key. Bake it into the shipped descriptor for plug-and-play telemetry, but keep destructive or admin capabilities out of ingest-key auth scope.

`customEndpoint` supports:

- `url`
- `eventUrl`
- `headers`

When `destinationMode` is `custom`, the configured endpoint operator—not Alec's
hosted platform—controls the received data, security, retention, and response
handling. The mod author and server owner are responsible for documenting that
endpoint and its data practices.

## UI Icon

`ui.iconTexturePath` optionally controls the project icon shown in the first-run consent UI. The PNG must be packaged under the mod's `Common/UI/Custom/...` asset tree.

```json
{
  "ui": {
    "iconTexturePath": "ExampleMod/Telemetry/ExampleModConsentIcon.png"
  }
}
```

## Legacy Fields

Top-level `capture`, `events`, `performance`, `usage`, `stats`, and `reports` are still accepted for compatibility. New descriptors should put categories under `telemetry`.

`runtimeMode` is legacy optional metadata. New descriptors should omit it.

## Live Contribution Catalog

The active coordinator reconciles anchored contributions while the server is
running. A valid elected contribution appears as an independent project in
consent and command diagnostics. An invalid or protocol-ineligible candidate
stays passive and contributes no telemetry. A valid same-owner passive copy can
submit operations only through the established winner's descriptor and
destination; it does not become a second writer. Retiring a contribution removes it
from new consent and heartbeat snapshots but keeps its existing local queue
available for replay under the normal project retention and destination rules.
Only first registration is live in the MVP; an established winner remains the
stable writable candidate until restart.
