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
telemetry/project.json
```

The descriptor supports both integration modes:

- `dependency`
- `embedded`

## Plug-And-Play Default

If your `manifest.json` has a correct `Group`, `Name`, and `Main`, then Alec's Telemetry can infer:

- project id
- display name
- plugin identifier
- package prefix from the `Main` class package

For most hosted projects, the descriptor can stay small:

```json
{
  "runtimeMode": "dependency",
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

Hosted `projectKey` values are designed to be publishable ingest keys. Bake them into the shipped descriptor for plug-and-play telemetry, but keep destructive or admin capabilities out of ingest-key auth scope.

## Custom Endpoint Example

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

## Supported Top-Level Fields

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

Custom `events.errors.details`, `events.lifecycle.details`, `usage.details`, and `performance.details` are descriptor-declared allowlists. Runtime code can send only fields listed for that event name.

Supported detail field types:

- `string` with optional `maxLength`
- `number`
- `boolean`
- `enum` with required `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set are dropped before upload.

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

- `dependency`: default when omitted. The standalone Alec's Telemetry runtime may discover and manage the project.
- `embedded`: the owning mod is expected to bootstrap embedded telemetry itself. The standalone runtime skips this project if it sees this mode.

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
