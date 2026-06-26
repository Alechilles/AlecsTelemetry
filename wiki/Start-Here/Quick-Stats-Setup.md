---
title: "Quick Stats Setup"
order: 7
published: true
draft: false
---

# Quick Stats Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this route when you only want anonymous public usage stats, similar in spirit to common mod stats services.

## Portal Setup

1. Open `https://telemetry.alecsmods.com/portal`.
2. Sign in with Discord or GitHub.
3. Create a project.
4. Copy the project key.
5. Open the project admin page.
6. Set public stats visibility if you want a public ModStats page.
7. Choose a public slug if the default project ID is not the slug you want.
8. Add CurseForge, Modtale, Modifold, or GitHub URLs if you want public profile metadata.

Stats ingest and public visibility are separate. A project can receive stats privately before you make a public stats page visible.

## Descriptor

Create `Server/Telemetry/project.json`:

```json
{
  "stats": {
    "enabled": true
  },
  "hosted": {
    "projectKey": "replace_with_your_portal_project_key"
  }
}
```

For stats-only consent defaults, use:

```json
{
  "defaults": {
    "enabled": true,
    "destinationMode": "hosted"
  },
  "capture": {
    "uncaughtExceptions": false,
    "setupFailures": false,
    "startFailures": false,
    "exceptionalWorldRemovals": false
  },
  "events": {
    "errors": { "enabled": false },
    "lifecycle": { "enabled": false },
    "breadcrumbs": { "enabled": false }
  },
  "performance": { "enabled": false },
  "usage": { "enabled": false },
  "stats": {
    "enabled": true
  },
  "hosted": {
    "projectKey": "replace_with_your_portal_project_key"
  }
}
```

## Install And Wait For Heartbeat

1. Package the descriptor with your plugin or asset pack.
2. Install Alec's Telemetry as a standalone dependency, unless your plugin embeds it.
3. Start the server.
4. Confirm the project appears in `/telemetry projects`.
5. Wait for the first stats heartbeat. New runtimes emit the first standard heartbeat 2-5 minutes after startup, then roughly every 30 minutes with stable jitter.
6. Use `/telemetry flush <project-id>` if you want to ask the runtime to upload pending data now.

## Verify

Run:

```text
/telemetry project <project-id>
```

Then check the portal stats page. If public stats are enabled, also check the public stats page for your slug on the public stats host.

## What Public Stats Do Not Include

Public stats are aggregate only. They should not expose raw server IDs, session IDs, IP addresses, player names, player UUIDs, chat, coordinates, world data, secrets, or full config files.

For the full stats guide, see [Anonymous Usage Stats](/mod/alecs-telemetry/anonymous-usage-stats).
