---
title: "Choose Standalone Or Embedded"
order: 4
published: true
draft: false
---

# Choose Standalone Or Embedded

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this page only to choose the packaging route. It is not the setup guide.

If you are unsure, choose [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode). It is the default route for first-time integrations, asset packs, and most plugins.

## Quick Decision

Choose standalone dependency mode when:

- your project is an asset pack or content-only package
- you want the smallest integration
- you can tell server owners to install Alec's Telemetry alongside your project
- you only need descriptor-driven crash reporting, anonymous stats, or manual reports
- your plugin can optionally call the runtime API when Alec's Telemetry is installed

Choose embedded mode only when:

- your project is a Java plugin, not a content-only asset pack
- you need one plugin package that bundles telemetry instead of requiring a separate dependency
- you want direct control over telemetry startup and shutdown in your plugin lifecycle
- you want to capture setup or start failures around your own bootstrap code
- you are willing to update and validate the bundled runtime when Alec's Telemetry changes

Custom error, lifecycle, performance, usage, breadcrumb, stats, and report API calls do not automatically require embedded mode. A standalone-dependent plugin can call the runtime API too. Embedded mode is a packaging and lifecycle ownership choice.

## What Does Not Change

Both routes still use:

- the official portal project
- the portal project key
- `Server/Telemetry/project.json`
- the same consent UI and local overrides
- the same portal ingest endpoints
- the same portal issue, event, stats, report, Discord, GitHub, ModStats, and server listing workflows

The descriptor does not choose standalone or embedded mode. New descriptors should omit `runtimeMode`. The mode is chosen by how you package and start telemetry:

- standalone mode installs Alec's Telemetry as its own runtime package
- embedded mode bundles the runtime and calls `EmbeddedTelemetryBootstrap`

An embedded runtime host must also publish the runtime's `Common/**` client UI
resources as an asset pack. Shading only the Java classes does not provide the
consent UI to clients. The Embedded Mode guide includes the packaging steps.

## Next Step

After you choose:

- Use [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode) for the full standalone setup and verification flow.
- Use [Embedded Mode](/mod/alecs-telemetry/embedded-mode) for the full embedded bootstrap and verification flow.

If you have not created the portal project yet, start with [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) before either route.
