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
- Your project can require Alec's Telemetry as an external dependency.
- You do not need telemetry bootstrap code.
- You are making an asset pack or descriptor-only package.
- You want crash/error telemetry, anonymous stats, or manual reports without owning the runtime lifecycle.

## Step 1: Create The Portal Project

1. Open [the official portal](https://telemetry.alecsmods.com/portal).
2. Sign in with Discord or GitHub.
3. Create a project.
4. Copy the one-time project key.

The project key is a publishable ingest key. It belongs in the descriptor that ships with your project.

## Step 2: Add The Descriptor

Create:

```text
Server/Telemetry/project.json
```

Minimal hosted plugin descriptor:

```json
{
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

Minimal stats-only asset pack descriptor:

```json
{
  "projectId": "example-asset-pack",
  "displayName": "Example Asset Pack",
  "stats": {
    "enabled": true
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

If your plugin manifest has a correct `Group`, `Name`, and `Main`, Alec's Telemetry can infer most identity fields. If not, set `projectId` and `displayName` explicitly.

## Step 3: Package Your Project

Make sure the final distributable includes:

```text
Server/Telemetry/project.json
```

If you configured `ui.iconTexturePath`, also include the icon under the matching `Common/UI/Custom/...` path.

## Step 4: Install For Testing

1. Install Alec's Telemetry.
2. Install your plugin or asset pack.
3. Start a local server or test world.
4. Review the first-run consent UI if it appears.

## Step 5: Verify Discovery

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

## Step 6: Verify Upload

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

Asset packs can use descriptor-driven setup for hosted identity and stats-only telemetry. Runtime API events require plugin Java code, so an asset pack without code should not try to document lifecycle, performance, usage, or explicit error events as if they are automatic.

## Next Pages

- [Portal First Setup](/mod/alecs-telemetry/portal-first-setup)
- [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup)
- [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup)
- [Project Descriptor](/mod/alecs-telemetry/project-descriptor)
