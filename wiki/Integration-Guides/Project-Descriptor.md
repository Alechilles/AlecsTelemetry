---
title: "Project Descriptor"
order: 2
published: true
draft: false
---

# Project Descriptor

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Consumer mods opt into Alec's Telemetry by shipping a descriptor at:

```text
Server/Telemetry/project.json
```

The same descriptor is used whether telemetry is provided by the standalone runtime or by an embedded runtime packaged inside a mod. Descriptors no longer need to choose dependency versus embedded mode; runtime ownership is decided by coordinator election at startup.

## Plug-And-Play Default

If your `manifest.json` has a correct `Group`, `Name`, and `Main`, then Alec's Telemetry can infer:

- project id
- display name
- plugin identifier
- package prefix from the `Main` class package

For most hosted projects, the descriptor can stay small:

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

Hosted `projectKey` values are designed to be publishable ingest keys. Bake them into the shipped descriptor for plug-and-play telemetry, but keep destructive or admin capabilities out of ingest-key auth scope.

## Custom Endpoint Example

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

## Supported Top-Level Fields

- `schemaVersion`
- `projectId`
- `displayName`
- `runtimeMode` (legacy optional)
- `ownerPluginIdentifiers`
- `packagePrefixes`
- `capture`
- `events`
- `performance`
- `usage`
- `stats`
- `reports`
- `ui`
- `defaults`
- `hosted`
- `customEndpoint`

## Capture Fields

- `uncaughtExceptions`
- `setupFailures`
- `startFailures`
- `exceptionalWorldRemovals`

## Event Fields

- `errors.enabled`: controls explicit non-fatal error events recorded through the runtime API.
- `errors.details`: optional per-error-event allowlist for custom mod-specific Event Context fields.
- `lifecycle.enabled`: controls explicit lifecycle timing events recorded through the runtime API.
- `lifecycle.details`: optional per-lifecycle-event allowlist for custom mod-specific Event Context fields.
- `breadcrumbs.enabled`: controls breadcrumb storage and breadcrumb attachment to crashes/debug events.
- `breadcrumbs.automatic`: reserved for low-noise automatic breadcrumbs.

## Performance And Usage Fields

`performance` supports:

- `enabled`
- `sampleRate`
- `thresholdMs`
- `details`

`usage` supports:

- `enabled`
- `allowedEvents`
- `details`

`stats.enabled` controls anonymous public stats separately from feature usage telemetry. When enabled, the runtime can emit the standard `heartbeat` stats event.

Custom `events.errors.details`, `events.lifecycle.details`, `usage.details`, `stats.details`, and `performance.details` are descriptor-declared allowlists. Runtime code can send only fields listed for that event name.

Supported detail field types:

- `string` with optional `maxLength`
- `number`
- `boolean`
- `enum` with required `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set are dropped before upload.

## Manual Report Fields

`reports` lets players submit issue and suggestion reports in-game.

```json
{
  "reports": {
    "enabled": true,
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
    "suggestion": {
      "enabled": true
    },
    "attachments": {
      "currentServerLog": true,
      "previousServerLog": true,
      "maxBytes": 262144
    },
    "contact": {
      "enabled": true,
      "maxLength": 160
    },
    "resolutionUpdates": {
      "enabled": true
    }
  }
}
```

Report fields support `string`, `text`, `boolean`, `enum`, and `number`. Server-owner
`manualReports` settings can still disable reports, logs, installed mod lists,
diagnostics, contact, resolution updates, or force manual review.

## UI Fields

`ui.iconTexturePath` optionally controls the project icon shown in the first-run consent UI.

The value must be a relative custom UI texture path using forward slashes. The PNG must be packaged under the mod's `Common/UI/Custom/...` asset tree.

Example descriptor value:

```json
{
  "ui": {
    "iconTexturePath": "ExampleMod/Telemetry/ExampleModConsentIcon.png"
  }
}
```

Package that texture at:

```text
Common/UI/Custom/ExampleMod/Telemetry/ExampleModConsentIcon.png
```

Root mod icons such as `icon-256.png` are not used automatically because Hytale custom UI documents cannot reliably resolve them.

## Runtime API Event Context

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

`detail` is stored in the event `attributes` map. The other standardized fields are first-class event envelope fields. Custom `detail(key, value)` entries are uploaded only when the descriptor declares an allowlist for the matching event name.

## Runtime Mode

`runtimeMode` is a legacy optional field. New descriptors should omit it.

When present, `dependency` and `embedded` are still accepted for backwards compatibility and diagnostics, but the field no longer controls discovery, consent, queueing, upload ownership, or coordinator election. Embedded behavior is selected by packaging and calling `EmbeddedTelemetryBootstrap`, not by this descriptor field.

## Destination Fields

`defaults` supports:

- `enabled` is the mod author's default project-level consent setting and initial project toggle state in the first-run consent UI.
- `destinationMode`: `hosted` or `custom`

## Consent Defaults

Alec's Telemetry shows a first-run consent UI when an operator installs a telemetry-enabled mod that has not been reviewed before. The initial toggle state comes from the packaged descriptor:

- `defaults.enabled` controls the project-level opt-in or opt-out default.
- `capture.*` controls the default crash telemetry category.
- `events.errors.enabled` controls the default error telemetry category.
- `events.lifecycle.enabled` controls the default lifecycle telemetry category.
- `performance.enabled` controls the default performance telemetry category.
- `usage.enabled` controls the default usage telemetry category.
- `stats.enabled` controls the default anonymous public stats category.
- `events.breadcrumbs.enabled` controls breadcrumb collection and attachment.

Server owners can override each value in the consent UI or through `Settings/projects/<project-id>.json`. Descriptor defaults are used until an override exists.

`hosted` supports:

- `endpoint`
- `eventEndpoint`
- `projectKey`
- `headers`

`customEndpoint` supports:

- `url`
- `eventUrl`
- `headers`

## Recommendation

Keep the file small unless your mod needs explicit ids, extra package prefixes, custom event allowlists, or a custom destination.
