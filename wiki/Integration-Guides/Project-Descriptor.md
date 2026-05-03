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
- `lifecycle.enabled`: controls explicit lifecycle timing events recorded through the runtime API.
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

Custom `usage.details` and `performance.details` are descriptor-declared allowlists. Runtime code can send only fields listed for that event name.

Supported detail field types:

- `string` with optional `maxLength`
- `number`
- `boolean`
- `enum` with required `values`

Unknown fields, wrong types, blank strings, and enum values outside the declared set are dropped before upload.

## Runtime Mode

- `dependency`: default when omitted. The standalone Alec's Telemetry runtime may discover and manage the project.
- `embedded`: the owning mod is expected to bootstrap embedded telemetry itself. The standalone runtime skips this project if it sees this mode.

## Destination Fields

`defaults` supports:

- `enabled`
- `destinationMode`: `hosted` or `custom`

`hosted` supports:

- `endpoint`
- `eventEndpoint`
- `projectKey`
- `headers`

`customEndpoint` supports:

- `url`
- `headers`

## Recommendation

Keep the file small unless your mod needs explicit ids, extra package prefixes, custom event allowlists, or a custom destination.
