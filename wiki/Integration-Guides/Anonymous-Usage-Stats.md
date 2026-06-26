---
title: "Anonymous Usage Stats"
order: 6
published: true
draft: false
---

# Anonymous Usage Stats

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Anonymous usage stats publish aggregate server, player, version, and environment information. They are separate from feature usage events and have their own consent category.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. If this is an asset pack or descriptor-only project, make sure the inferred `projectId` matches the portal project ID. Add an explicit `projectId` only when inference would not match.

## Descriptor Block

Add this block to the shared descriptor:

```json
{
  "stats": {
    "enabled": true
  }
}
```

## Heartbeat Timing

The active runtime emits the first standard stats heartbeat 2-5 minutes after startup, then roughly every 30 minutes with stable jitter. This cadence is intentionally not exposed in runtime settings.

## Public Stats

Stats ingest and public visibility are separate:

- `stats.enabled` lets the runtime send anonymous heartbeat data.
- Portal Public Stats visibility controls whether the project is visible on public stats surfaces.

To make stats public, open project admin, set Public Stats visibility to `Public`, choose a slug, and save stats display.

## What Public Stats Exclude

Public stats do not expose raw server IDs, session IDs, IP addresses, player names, player UUIDs, chat, coordinates, world data, secrets, or full config files.

## Verify

1. Run `/telemetry project <project-id>`.
2. Wait for the first heartbeat window.
3. Open the portal Stats page.
4. If public, open the public stats page on ModStats.

## Common Problems

- Stats are not immediate; wait for the first heartbeat.
- The project is collecting stats but is not public; update portal visibility.
- Ingest is rejected with a project mismatch; make the descriptor `projectId` match the portal project ID.
- Server owner disabled stats; check `/telemetry consent` or the project override file.
