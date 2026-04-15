# Alec's Telemetry

Standalone crash telemetry runtime mod for Hytale mods.

## Goal

Make crash telemetry as plug-and-play as possible for mod authors.

In the common case, a mod author should only need to:

1. Add Alec's Telemetry as a dependency.
2. Ship a small `telemetry/project.json` file.
3. Provide either:
   - a hosted `projectKey` for Alec's telemetry service, or
   - a custom endpoint URL.

The runtime will then:

- discover the mod automatically
- infer fallback values from the mod manifest when possible
- capture attributed crashes
- queue reports locally
- flush them to the configured destination

## Quick Start

### Minimal hosted descriptor

If your mod manifest already has a correct `Main` class package, a minimal hosted
descriptor can be as small as:

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

### Minimal custom-endpoint descriptor

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

Place the file at:

```text
telemetry/project.json
```

inside your mod project so it ships with the mod.

## What Gets Inferred Automatically

When fields are omitted from `telemetry/project.json`, Alec's Telemetry will try to
use the mod's `manifest.json` as fallback.

Current fallbacks:

- `projectId` from manifest `Name`
- `displayName` from manifest `Name`
- `ownerPluginIdentifiers` from manifest `Group` + `Name`
- `packagePrefixes` from the package of manifest `Main`

That means many mods only need to specify destination settings.

## Optional Runtime API

Most mods do not need Java integration.

If you want richer breadcrumbs or explicit lifecycle forwarding, use the optional
runtime API:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api != null) {
    TelemetryProjectHandle project = api.findProject("my-mod-id");
    if (project != null) {
        project.recordBreadcrumb("bootstrap", "Finished loading config.");
    }
}
```

See:

- `docs/project-descriptor.md`
- `docs/runtime-overrides.md`
- `examples/ExampleConsumerMod/`

## Admin Commands

- `/telemetry status`
- `/telemetry projects`
- `/telemetry project <project-id>`
- `/telemetry flush [project-id]`
- `/telemetry test <project-id> [detail]`

## Runtime Overrides

Server owners can override packaged destination settings without editing another
mod's files.

Override files live under:

```text
Settings/projects/<project-id>.json
```

See `docs/runtime-overrides.md`.

## License

This project is source-available under the Business Source License 1.1.

See:

- `LICENSE`
- `LICENSE-FAQ.md`
- `COMMERCIAL-LICENSE.md`
