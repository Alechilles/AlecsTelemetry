---
title: "Standalone Dependency Mode"
order: 3
published: true
draft: false
---

# Standalone Dependency Mode

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Standalone dependency mode is the recommended default. Alec's Telemetry is installed as its own runtime mod, and your plugin or asset pack ships a descriptor at `Server/Telemetry/project.json`.

## Use This Mode When

- You want the smallest integration.
- You want to use Alec's Telemetry as an external dependency.
- You do not need telemetry bootstrap code.
- Your mod is an asset pack.
- Your release can list Alec's Telemetry as a dependency on the sites where players download it.
- You want crash/error telemetry, anonymous stats, or manual reports without owning the runtime lifecycle.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. That page owns the repeated setup details: portal project creation, project key, `Server/Telemetry/project.json`, optional identity overrides, optional consent icon, and packaging checks.

Then return here for the standalone-specific install and verification flow.

## Step 1: List The Dependency

Standalone mode only works when server owners install Alec's Telemetry with your project. On CurseForge, Modtale, and Modifold, list `Alec's Telemetry!` as a dependency for the release so players and server owners know to install it.

You do not need to declare Alec's Telemetry in your project's `manifest.json` for descriptor discovery or descriptor-only features such as crash/error collection, anonymous stats, and manual reports. Choose the manifest behavior that matches your mod:

- Omit Alec's Telemetry entirely from the manifest when your mod does not use the Java runtime API and should still boot and work normally without telemetry installed.
- Add Alec's Telemetry to `Dependencies` only when you intentionally want Hytale to require the runtime before loading your mod.

The optional enforcement entry looks like this:

```json
{
  "Dependencies": {
    "Alechilles:Alec's Telemetry!": ">=0.1.0"
  },
  "OptionalDependencies": {}
}
```

## Step 2: Install For Testing

1. Install Alec's Telemetry.
2. Install your plugin or asset pack.
3. Start a local server or test world.
4. Review the first-run consent UI if it appears.

## Step 3: Verify Discovery

Run:

```text
/telemetry status
/telemetry projects
/telemetry project <project-id>
```

Confirm:

- the runtime is enabled
- your project appears
- the source path points at your installed package
- the destination is hosted
- pending queue counts are reasonable

## Step 4: Verify Upload

For crash/error setup:

```text
/telemetry test <project-id> standalone-check
/telemetry flush <project-id>
```

For stats setup, wait for the first heartbeat window, then check the project Stats page in the portal.

## Plugin Notes

Plugins can add richer telemetry by using the [Runtime API](/mod/alecs-telemetry/runtime-api). Treat the runtime as optional from your plugin code:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api == null || !api.isEnabled()) {
    return;
}
```

Then resolve your project handle and record breadcrumbs, lifecycle events, performance timings, usage events, errors, stats, or manual report UI openings.

## Asset Pack Notes

Asset packs can use descriptor-driven setup for portal identity and stats-only telemetry. Runtime API events require plugin Java code, so an asset pack without code should not try to document lifecycle, performance, usage, or explicit error events as if they are automatic.

## Next Pages

- [Portal First Setup](/mod/alecs-telemetry/portal-first-setup)
- [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup)
- [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup)
- [Project Descriptor](/mod/alecs-telemetry/project-descriptor)
