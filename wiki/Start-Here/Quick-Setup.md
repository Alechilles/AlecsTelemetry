---
title: "Quick Setup"
order: 5
published: true
draft: false
---

# Quick Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this page as a route map. If you are setting up Alec's Telemetry for the first time, start with [Portal First Setup](/mod/alecs-telemetry/portal-first-setup), then pick the quick guide that matches what you want to collect first.

## Fastest Routes

- [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup): collect crash reports, setup failures, start failures, and explicit error events.
- [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup): publish anonymous public server/player/environment stats.
- [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode): require Alec's Telemetry as an external dependency and ship only a descriptor with your plugin or asset pack.
- [Embedded Mode](/mod/alecs-telemetry/embedded-mode): bundle and bootstrap telemetry from your own plugin when you want one distributable package.

## Minimal Hosted Descriptor

Most hosted projects start with a `Server/Telemetry/project.json` file.

For crash/error telemetry:

```json
{
  "hosted": {
    "projectKey": "replace_with_your_public_project_key"
  }
}
```

For stats-only telemetry:

```json
{
  "stats": {
    "enabled": true
  },
  "hosted": {
    "projectKey": "replace_with_your_public_project_key"
  }
}
```

Place the descriptor at:

```text
Server/Telemetry/project.json
```

That is enough for the normal hosted setup when your plugin manifest has a correct `Group`, `Name`, and `Main`. Asset packs or descriptor-only projects should set explicit `projectId` and `displayName` so the portal has stable names.

## Full First-Time Flow

1. Open the [official portal](https://telemetry.alecsmods.com/portal).
2. Sign in with Discord or GitHub.
3. Create a telemetry project.
4. Copy the new project key while it is visible.
5. Choose [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode) or [Embedded Mode](/mod/alecs-telemetry/embedded-mode).
6. Add `Server/Telemetry/project.json` to your plugin or asset pack.
7. Package and install your project.
8. Start a local server or test world.
9. Use `/telemetry status`, `/telemetry projects`, and `/telemetry project <project-id>` to confirm discovery.
10. Confirm data appears in the portal.

## What Can Be Inferred

If your plugin manifest already has a correct `Group`, `Name`, and `Main`, Alec's Telemetry can infer `projectId`, `displayName`, `ownerPluginIdentifiers`, and `packagePrefixes`. That means many plugins only need destination settings plus a hosted key. Asset packs without plugin code should use explicit identity fields.

## Important Hosted Key Note

Hosted `projectKey` values are publishable ingest keys. They are meant to be shipped in the descriptor, not treated like hidden operator secrets.

If you need a deeper descriptor reference, see [Project Descriptor](/mod/alecs-telemetry/project-descriptor).

## Verification Commands

The standalone runtime exposes `/telemetry status`, `/telemetry projects`, `/telemetry project <project-id>`, `/telemetry flush [project-id]`, and `/telemetry test <project-id> [detail]`.

The root command permission is `telemetry.command.telemetry`; subcommands use the same stable `telemetry.command.telemetry.*` prefix.
