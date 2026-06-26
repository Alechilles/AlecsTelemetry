---
title: "Public Stats And ModStats"
order: 6
published: true
draft: false
---

# Public Stats And ModStats

Parent: [Hosted Operations](/mod/alecs-telemetry/hosted-operations) | [Home](/mod/alecs-telemetry/home)

Public stats turn anonymous heartbeat data into a public project page. ModStats is the public stats host for browsing public Hytale mod stats.

## Enable Runtime Stats

First, make sure the descriptor enables stats:

```json
{
  "stats": {
    "enabled": true
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

Then install the project and wait for the first heartbeat.

## Make Stats Public

1. Open the project in the portal.
2. Open project admin.
3. Find Public Stats.
4. Set Visibility to `Public`.
5. Choose a public slug.
6. Add profile URLs if available:
   - CurseForge URL
   - Modtale URL
   - Modifold URL
   - GitHub repo URL
7. Save stats display.
8. Resync mod metadata if needed.

> [Screenshot Placeholder: Public Stats project admin form]

## Public Page Data

Public stats can show:

- active servers
- active players
- record servers
- record players
- history over completed 30-minute buckets
- plugin or asset pack version breakdowns
- Hytale, Java, operating system, architecture, CPU, location, hosting mode, and loaded-mod breakdowns

Public stats should not expose raw server IDs, session IDs, IP addresses, player names, player UUIDs, chat, coordinates, world data, secrets, or full config files.

## Verify

1. Confirm the portal Stats page shows heartbeat data.
2. Confirm Public Stats visibility is `Public`.
3. Open the public stats page by slug.
4. Confirm the public page shows the expected project name and aggregate stats.

## Troubleshooting

- Public page missing: check the public visibility setting.
- Slug wrong: update the public slug in project admin.
- Dashboard empty: wait for the first heartbeat.
- Metadata old: use Resync mod metadata.
