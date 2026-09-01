---
title: "Project Descriptor"
order: 13
published: true
draft: false
---

# Project Descriptor

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Consumer mods opt into Beacon by shipping:

```text
Server/Beacon/project.json
```

The same descriptor works for standalone dependency mode and embedded mode.

## Minimal Portal Descriptor

If your `manifest.json` has `Group`, `Name`, and `Main`, Beacon can infer the project id, display name, plugin identifier, and Java package prefix. Asset-pack-only projects without `Main` can still infer the project id, display name, and plugin identifier.

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
- omitted `defaultEnabled`: defaults to `true` for supported categories other
  than Diagnostics
- `defaultEnabled: false`: supported but initially off, so server owners can opt in
- legacy descriptor `enabled` is accepted as a compatibility alias for `defaultEnabled`, but new descriptors should use `defaultEnabled`
- Diagnostics is initially off unless `defaultEnabled: true` is explicit. Its
  key is `diagnostics`, and the consent UI shows it as `Diag` with the tooltip
  "Automatic diagnostic bundles with technical metadata and optional redacted
  attachments." The legacy `enabled` alias does not enable Diagnostics.

Runtime override files still use `enabled`; those files store saved consent choices.

For a previously reviewed project, newly added Diagnostics remains disabled until
an operator runs `/beacon consent` and selects Save and Close. This applies to
modern reviewed records with a supported-category snapshot and to legacy reviewed
records without one. Error and Diagnostics are independent categories.

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
      "breadcrumbs": { "supported": true, "automatic": true }
    },
    "diagnostics": {
      "supported": true,
      "defaultEnabled": false
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

## Category Fields

Crash supports `supported`, `defaultEnabled`, `uncaughtExceptions`, `setupFailures`, `startFailures`, and `exceptionalWorldRemovals`.

Events support `supported`, `defaultEnabled`, and `details` for `errors` and `lifecycle`. Breadcrumbs support `supported`, `defaultEnabled`, and `automatic`.

Diagnostics supports `supported` and `defaultEnabled`. It defaults off unless
`defaultEnabled: true` is explicit and is independent from `events.errors`.

Performance supports `supported`, `defaultEnabled`, `sampleRate`, `thresholdMs`, and `details`.

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

Top-level `capture`, `events`, `diagnostics`, `performance`, `usage`, `stats`, and `reports` are still accepted for compatibility. New descriptors should put categories under `telemetry`.

`runtimeMode` is legacy optional metadata. New descriptors should omit it.
