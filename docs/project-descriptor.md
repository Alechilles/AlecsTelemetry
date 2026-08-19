# Project Descriptor

Consumer mods opt into Alec's Telemetry by shipping:

```text
Server/Telemetry/project.json
```

The same descriptor works for standalone dependency mode and embedded mode.
Runtime ownership is decided by coordinator election at startup.

## Passive descriptor-only libraries

An embeddable library can report one aggregate Stats project without depending
on Alec's Telemetry, loading a Telemetry class, or running Java initialization.
Put a direct JSON resource in the final host JAR (or host mod folder) at:

```text
META-INF/alecs-telemetry/projects/<stable-project-id>.json
```

Presence of a valid resource is the installation signal. A standalone or
embedded Alec's Telemetry runtime must still be present to discover and process
it; without a runtime the resource is inert and the host continues normally.
Passive discovery only exposes the standard Stats `heartbeat` capability. It
does not execute contributor code or activate crash, error, lifecycle,
performance, usage, breadcrumbs, or manual-report categories declared for a
future active integration.

The passive form requires an explicit logical identity, build-stamped logical
version, display name, owner identifier, and Stats heartbeat allowlist:

```json
{
  "schemaVersion": 1,
  "projectId": "creditor",
  "projectVersion": "1.4.0",
  "displayName": "Creditor",
  "ownerPluginIdentifiers": ["Author:Creditor"],
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "stats": {
      "supported": true,
      "allowedEvents": ["heartbeat"]
    }
  }
}
```

`projectVersion` is the library's logical semantic version; the containing
host's manifest version is retained only as physical-host provenance. If the
same library descriptor appears in multiple hosts, election chooses one whole
logical project (highest logical version, then source kind and deterministic
host/path/hash tie-breakers). It never merges descriptor fields, so consent and
Stats contain one row and one heartbeat project.

The descriptor may include richer categories for an active implementation, but
passive registration masks them. To expose those categories, the library must
explicitly call `EmbeddedTelemetryBootstrap.contribute(...)` using the same
descriptor resource, project ID, logical version, and an owner declared in the
descriptor. A matching contribution upgrades the existing passive row rather
than creating a duplicate. When a persisted supported-category snapshot exists
for the previously reviewed logical project, newly exposed categories remain
disabled until an operator reviews them with `/telemetry consent`; eligible
operators are notified. Legacy reviewed records without that snapshot remain
honored and cannot be compared retrospectively.

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

The namespaced directory is intentionally discoverable for passive
descriptor-only projects. `Server/Telemetry/project.json` remains the
conventional descriptor owned by the physical host mod; it is not a substitute
for the library's namespaced resource.

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

For a passive namespaced descriptor, only Stats with the `heartbeat` event is
available. Other declared categories are retained as metadata for a later
explicit `contribute(...)` call and are masked from the passive project. When a
matching active contribution exposes new categories, and a persisted
supported-category snapshot exists for the previously reviewed logical project,
those additions remain disabled until an operator saves reviewed choices.
Eligible operators receive a permission-scoped chat reminder to run
`/telemetry consent`. Legacy reviewed records without a snapshot remain honored
and cannot be compared retrospectively.

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
- `projectVersion`: the logical project release used for passive installation,
  election, attribution, and active upgrade matching. Build-stamp this for an
  embedded library; do not use the physical host manifest version.
- `displayName`: local consent/runtime text and envelope metadata; it does not rename the portal project
- `ownerPluginIdentifiers`: aliases, renamed plugins, or unusual ownership matching
- `packagePrefixes`: crash attribution when Java code lives outside the package inferred from `Main`, or when no `Main` exists

### Profiler attribution

The optional local Spark profiler uses the same project identity metadata. It
matches an exact normalized plugin identifier first. If no exact source label is
available, it matches the longest complete normalized package prefix. A
partial package string is not a match, and equal-best matches remain
unattributed.

The profiler accepts at most 32 normalized package prefixes per project. It
trims values, replaces `/` with `.`, removes trailing dots, and removes
duplicates in descriptor order. Extra prefixes remain valid for other
Telemetry behavior but do not enter the profiler index; the local status
reports the truncation. A profiler summary is local and bounded. It does not
make the descriptor a profiler upload contract.

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

The local project profiler uses the project's performance consent gate. It
requires `performance.supported: true` and current performance consent, plus
the server owner's global Spark monitor setting. It does not turn performance
consent into profiler upload permission.

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

Passive descriptor-only projects may use either the hosted or custom destination
declared in the descriptor. Hosted delivery requires a publishable project key
plus normal destination validation. With `defaults.destinationMode` set to
`custom`, the standard Stats-only heartbeat is delivered to the author-selected
`customEndpoint.url` (and its configured headers/event endpoint); that endpoint
operator controls the received data, security, and retention. Passive discovery
does not change the endpoint's ownership or activate richer categories.

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
running. A valid contribution that exactly matches an elected passive project's
logical ID, version, owner, and descriptor hash upgrades that existing project
row and exposes its additional supported categories. It does not create a
second project or heartbeat. When a persisted supported-category snapshot
exists for the previously reviewed logical project, newly exposed categories
remain disabled until an operator reviews them through `/telemetry consent`.
Legacy reviewed records without that snapshot remain honored and cannot be
compared retrospectively.

A valid elected contribution appears as an independent project in consent and
command diagnostics. An invalid or protocol-ineligible candidate stays passive
and contributes no telemetry. A valid same-owner passive copy can submit
operations only through the established winner's descriptor and destination; it
does not become a second writer. If an active contribution retires while its
elected passive Stats-only base is still present, that base resumes/continues as
the logical project's heartbeat and consent entry. Only when no passive base
remains does the logical project leave new consent and heartbeat snapshots.
Existing local queues remain available for replay under the normal project
retention and destination rules. Only first registration is live in the MVP; an
established winner remains the stable writable candidate until restart.
