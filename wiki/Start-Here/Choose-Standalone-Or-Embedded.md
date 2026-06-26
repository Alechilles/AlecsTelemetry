---
title: "Choose Standalone Or Embedded"
order: 4
published: true
draft: false
---

# Choose Standalone Or Embedded

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Alec's Telemetry can run as a standalone dependency or as an embedded runtime inside a plugin. Both modes use the same `Server/Telemetry/project.json` descriptor and the same official portal.

## Recommended Default

Choose standalone dependency mode unless you have a concrete reason to embed.

Standalone dependency mode is best for:

- first-time integrations
- asset packs and content-only packages
- plugins that only need crash capture and anonymous stats
- packages that can declare or document Alec's Telemetry as a dependency
- modders who want fewer lifecycle hooks to maintain

Embedded mode is best for:

- plugins that need one distributable package
- plugins that want to record setup, start, usage, lifecycle, or performance events from their own code
- teams willing to update and validate the bundled runtime when Alec's Telemetry changes
- releases where the plugin owner wants direct bootstrap ownership

## What Stays The Same

Both modes still use:

- the official portal project
- the hosted project key
- `Server/Telemetry/project.json`
- the same consent UI
- the same local override files
- the same hosted ingest endpoints
- the same portal issue, event, stats, report, Discord, and GitHub workflows

The descriptor does not choose the mode. New descriptors should omit `runtimeMode`. The mode is chosen by packaging and startup:

- standalone mode installs Alec's Telemetry as its own runtime package
- embedded mode bundles the runtime and calls `EmbeddedTelemetryBootstrap`

## Asset Pack Route

If your package is an asset pack or another content-only package, use standalone dependency mode.

1. Create the portal project.
2. Add `Server/Telemetry/project.json` to the asset pack.
3. Include the hosted project key.
4. Add explicit `projectId` only if the inferred ID would not match the portal project ID.
5. Install Alec's Telemetry alongside the asset pack on the server.
6. Use `/telemetry projects` to confirm the descriptor was discovered.

An asset pack cannot record custom Java API events by itself. It can still have a portal project, descriptor defaults, consent visibility, hosted key routing, and telemetry handled by the installed runtime when the runtime can discover the packaged descriptor.

## Plugin Route

If your package is a plugin with code, choose based on how much control you need.

Use standalone mode for a quick release:

1. Add the descriptor.
2. Depend on Alec's Telemetry.
3. Optionally call the runtime API if the dependency is present.
4. Verify with `/telemetry test <project-id>`.

Use embedded mode for direct ownership:

1. Add the descriptor.
2. Bundle the runtime.
3. Call `EmbeddedTelemetryBootstrap.bootstrap(this)` during setup.
4. Start and shut down the returned service with your plugin lifecycle.
5. Record setup/start failures around your own lifecycle code.

## Runtime Election

Only one active telemetry coordinator owns capture, queues, heartbeats, and uploads in a server process. When multiple standalone or embedded copies are present, the latest compatible runtime wins. If versions match, standalone wins over embedded.

This means multiple mods can include telemetry safely, but each mod should still pick one strategy for its own release.
