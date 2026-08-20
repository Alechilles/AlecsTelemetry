---
title: "Project Descriptor"
order: 13
published: true
draft: false
---

# Project Descriptor

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Consumer mods opt into Alec's Telemetry by shipping:

```text
Server/Telemetry/project.json
```

The same descriptor works for standalone dependency mode and embedded mode.

## Minimal Portal Descriptor

If your `manifest.json` has `Group`, `Name`, and `Main`, Alec's Telemetry can infer the project id, display name, plugin identifier, and Java package prefix. Asset-pack-only projects without `Main` can still infer the project id, display name, and plugin identifier.

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

This only connects the packaged descriptor to the portal project. It does not make any telemetry category available.

## Category Defaults

Telemetry category descriptors separate capability from initial consent:

- omitted category: unsupported and hidden from consent
- `supported: true`: available and shown in consent
- omitted `defaultEnabled`: defaults to `true` for supported categories
- `defaultEnabled: false`: supported but initially off, so server owners can opt in
- legacy descriptor `enabled` is accepted as a compatibility alias for `defaultEnabled`, but new descriptors should use `defaultEnabled`

Runtime override files still use `enabled`; those files store saved consent choices.

## Stats Only

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

## Full Descriptor Shape

```json
{
  "schemaVersion": 1,
  "projectId": "example-mod",
  "displayName": "Example Mod",
  "ownerPluginIdentifiers": ["Example:Example Mod"],
  "packagePrefixes": ["com.example.telemetry"],
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
      "errors": { "supported": true },
      "lifecycle": { "supported": true },
      "breadcrumbs": {
        "supported": true,
        "automatic": true,
        "profilerCorrelationCategories": ["companion.ai.tick"]
      }
    },
    "performance": {
      "supported": true,
      "sampleRate": 1.0,
      "thresholdMs": 100
    },
    "usage": {
      "supported": true,
      "allowedEvents": ["settings_opened"]
    },
    "stats": {
      "supported": true
    },
    "reports": {
      "supported": true,
      "issue": { "enabled": true },
      "suggestion": { "enabled": true }
    }
  },
  "ui": {
    "iconTexturePath": "ExampleMod/Telemetry/ExampleModConsentIcon.png"
  }
}
```

## Identity Overrides

Identity fields are optional overrides:

- `projectId`: use only when the inferred ID would not match the portal project ID or a custom endpoint expects a different stable ID
- `displayName`: local consent/runtime text and envelope metadata; it does not rename the portal project
- `ownerPluginIdentifiers`: aliases, renamed plugins, or unusual ownership matching
- `packagePrefixes`: crash attribution when Java code lives outside the package inferred from `Main`, or when no `Main` exists

### Profiler attribution

The local Spark profiler matches an exact normalized plugin identifier first.
When that is not available, it uses the longest complete normalized package
prefix. Partial strings are not matches, and equal-best project matches remain
unattributed.

The profiler accepts at most 32 normalized package prefixes per project. It
trims values, replaces `/` with `.`, removes trailing dots, and removes
duplicates in descriptor order. Each package-prefix value is limited to 512
characters before normalization. Extra or longer values remain valid for other
Telemetry behavior but do not enter the profiler index; local diagnostics
report the truncation.

### Profiler breadcrumb correlation

The optional `events.breadcrumbs.profilerCorrelationCategories` field allows
small local context counts in profiler snapshots:

```json
{
  "telemetry": {
    "events": {
      "breadcrumbs": {
        "supported": true,
        "profilerCorrelationCategories": [
          "companion.ai.tick",
          "companion.lease.scan"
        ]
      }
    }
  }
}
```

The runtime accepts at most 16 categories. Each category must be no longer than
40 characters and must match the exact lowercase pattern `[a-z0-9_.-]+`.
Duplicates are removed in first-seen order. Invalid entries and entries after
the first 16 are excluded and reported by a bounded local diagnostic. The
project remains valid when entries are excluded.

At publication, the profiler keeps only non-zero counts from five one-minute
buckets. A breadcrumb is counted only when its trimmed static category exactly
matches an accepted descriptor category. The detail text, structured custom
fields, and undeclared or dynamically generated categories never enter profiler
context. Project enablement and breadcrumb consent are required for counting and
publication; clearing breadcrumb consent clears these counts without clearing
performance snapshots.

## Category Fields

Crash supports `supported`, `defaultEnabled`, `uncaughtExceptions`, `setupFailures`, `startFailures`, and `exceptionalWorldRemovals`.

Events support `supported`, `defaultEnabled`, and `details` for `errors` and `lifecycle`. Breadcrumbs support `supported`, `defaultEnabled`, `automatic`, and `profilerCorrelationCategories`.

Performance supports `supported`, `defaultEnabled`, `sampleRate`, `thresholdMs`, and `details`.

The local project profiler requires `performance.supported: true`, an enabled
project, current performance consent, and the server owner's global Spark
monitor setting. These gates enable bounded in-memory summaries only. They do
not create a dedicated profiler evidence file or structured profiler upload
path; ordinary monitor and command-output text follows the server log policy
and can enter manual current or previous log attachments under the normal
report settings, review, redaction, and clipping controls.

Usage supports `supported`, `defaultEnabled`, `allowedEvents`, and `details`.

Stats supports `supported`, `defaultEnabled`, and `details`.

Manual reports support `supported`, `defaultEnabled`, `issue`, `suggestion`, `attachments`, `contact`, and `resolutionUpdates`.

## Details Allowlists

Custom `events.errors.details`, `events.lifecycle.details`, `usage.details`, `stats.details`, and `performance.details` are descriptor-declared allowlists. Runtime code can send only fields listed for that event name.

Supported field types are `string`, `number`, `boolean`, and `enum`. Unknown fields, wrong types, blank strings, and enum values outside the declared set are dropped before upload.

## Destination Fields

`defaults.enabled` controls the initial project-level consent toggle. `defaults.destinationMode` can be `hosted` or `custom`.

`hosted.projectKey` is a publishable ingest key. Bake it into the shipped descriptor for plug-and-play telemetry, but keep destructive or admin capabilities out of ingest-key auth scope.

`customEndpoint` supports `url`, `eventUrl`, and `headers`.

## UI Icon

`ui.iconTexturePath` optionally controls the project icon shown in the first-run consent UI. The PNG must be packaged under the mod's `Common/UI/Custom/...` asset tree.

## Legacy Fields

Top-level `capture`, `events`, `performance`, `usage`, `stats`, and `reports` are still accepted for compatibility. New descriptors should put categories under `telemetry`.

`runtimeMode` is legacy optional metadata. New descriptors should omit it.
