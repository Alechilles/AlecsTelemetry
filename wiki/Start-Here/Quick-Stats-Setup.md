---
title: "Quick Stats Setup"
order: 7
published: true
draft: false
---

# Quick Stats Setup

Parent: [Start Here](/mod/beacon/start-here) | [Home](/mod/beacon/home)

Use this route when you only want anonymous public usage stats, similar in spirit to common mod stats services.

## Before This Page

Complete [Portal First Setup](/mod/beacon/portal-first-setup) once. That page owns project creation, key setup, descriptor placement, optional icon setup, and package checks.

This page only covers the stats-specific descriptor block and public stats portal settings.

## Stats Descriptor Block

Add this to the shared descriptor:

```json
{
  "telemetry": {
    "stats": {
      "supported": true
    }
  }
}
```

Omitted telemetry categories are unsupported and hidden from consent. A supported category defaults on unless you set `defaultEnabled: false`, so this is enough for a stats-only default.

## Public Portal Settings

Stats ingest and public visibility are separate. A project can receive stats privately before you make a public stats page visible.

1. Open the project admin page.
2. Set public stats visibility if you want a public ModStats page.
3. Choose a public slug if the default project ID is not the slug you want.
4. Add CurseForge, Modtale, Modifold, or GitHub URLs if you want public profile metadata.
   - The portal will automatically grab the mod icon, summary, and download counts from each.

## Install And Wait For Heartbeat

1. Install Beacon as a standalone dependency, unless your plugin embeds it.
2. Start the server.
3. Confirm the project appears in `/beacon projects`.
4. Wait for the first stats heartbeat. New runtimes emit the first standard heartbeat 2-5 minutes after startup, then roughly every 30 minutes with some randomization afterwards.
5. Use `/beacon flush <project-id>` if you want to ask the runtime to upload pending data now.

## Verify

Run:

```text
/beacon project <project-id>
```

Then check the portal stats page. If public stats are enabled, also check the public stats page for your slug on the public stats host.

## What Public Stats Do Not Include

Public stats are aggregate only. They should not expose raw server IDs, session IDs, IP addresses, player names, player UUIDs, chat, coordinates, world data, secrets, or full config files.

For the full stats guide, see [Anonymous Usage Stats](/mod/beacon/anonymous-usage-stats).
