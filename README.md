# Alec's Telemetry

Crash and event telemetry platform for Hytale mods with both standalone
dependency mode and embedded library mode.

## Goal

Make useful telemetry as plug-and-play as possible for mod authors.

Two integration modes are supported:

1. dependency mode
   - install `Alec's Telemetry` as a standalone mod dependency
2. embedded mode
   - bundle the telemetry bootstrap inside the owning mod

In the common dependency-mode case, a mod author should only need to:

1. Add Alec's Telemetry as a dependency.
2. Ship a small `telemetry/project.json` file.
3. Bake in either:
   - a publishable hosted `projectKey` for Alec's telemetry service, or
   - a custom endpoint URL.

The runtime will then:

- discover the mod automatically
- infer fallback values from the mod manifest when possible
- capture attributed crashes
- record opt-in error, lifecycle, performance, and usage events
- attach session, environment, breadcrumb, and typed event context
- queue reports locally
- flush them to the configured destination

## Quick Start

### Pick a runtime mode

`telemetry/project.json` supports:

- `runtimeMode: "dependency"`
- `runtimeMode: "embedded"`

If omitted, the default is `dependency`.

### Minimal hosted descriptor

If your mod manifest already has a correct `Main` class package, a minimal hosted
descriptor can be as small as:

```json
{
  "runtimeMode": "dependency",
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

### Minimal custom-endpoint descriptor

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

If you want richer breadcrumbs, lifecycle timings, usage events, or performance
samples, use the optional runtime API:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api != null) {
    TelemetryProjectHandle project = api.findProject("my-mod-id");
    if (project != null) {
        project.recordBreadcrumb("bootstrap", "Finished loading config.");
        project.recordUsageWithContext(
            "settings_opened",
            TelemetryEventContext.usage()
                .subsystem("settings")
                .featureKey("settings_page")
                .entryPoint("/my settings")
                .runtimeSide("server")
                .detail("source", "command")
                .build()
        );
    }
}
```

See:

- `docs/project-descriptor.md`
- `docs/embedded-mode.md`
- `docs/runtime-overrides.md`
- `docs/hosted-key-operations.md`
- `docs/hosted-ingest-contract.md`
- `docs/expanded-telemetry-system-plan.md`
- `docs/embedded-mode-refactor-plan.md`
- `examples/ExampleConsumerMod/`
- `examples/EmbeddedConsumerMod/`

## Admin Commands

- `/telemetry status`
- `/telemetry projects`
- `/telemetry project <project-id>`
- `/telemetry flush [project-id]`
- `/telemetry test <project-id> [detail]`

## Runtime Overrides

Server owners can override packaged destination settings without editing another
mod's files.

Overrides are optional. The intended default model is:

- mod author ships a publishable hosted ingest key in `telemetry/project.json`
- telemetry works out of the box for the mod author's project
- server owners only add overrides when they want to disable telemetry, redirect
  it, or change runtime behavior

Override files live under:

```text
Settings/projects/<project-id>.json
```

See `docs/runtime-overrides.md`.

## Hosted Service

This repo also contains a hosted-service prototype under:

```text
hosted/
```

It currently provides a local/dev reference implementation for:

- hosted crash ingest endpoint
- project-key validation
- basic rate limiting and duplicate-alert suppression
- Discord routing

Production hosting recommendation:

- use `HytaleModWikiBot` as the canonical hosted backend and Discord bot service
- treat `AlecsTelemetry/hosted` as prototype/dev reference code, not the long-term production service

See:

- `docs/hosted-ingest-contract.md`
- `hosted/README.md`

## Embedded Mode

Embedded mode is implemented for modders who want a single bundled mod instead of a
standalone dependency.

See:

- `docs/embedded-mode.md`
- `docs/embedded-mode-refactor-plan.md`

## License

This project is source-available under the Business Source License 1.1.

See:

- `LICENSE`
- `LICENSE-FAQ.md`
- `COMMERCIAL-LICENSE.md`
