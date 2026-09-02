---
title: "What Is Beacon"
order: 2
published: true
draft: false
---

# What Is Beacon

Parent: [Start Here](/mod/beacon/start-here) | [Home](/mod/beacon/home)

Beacon is a telemetry runtime and web portal workflow for Hytale plugins and asset packs. It helps mod authors collect structured operational signals from installed projects without building their own ingest service, issue workspace, stats dashboard, consent UI, or upload queue.

For most modders, the destination is the Beacon web portal. The runtime discovers packaged project descriptors, applies consent and server-owner overrides, queues telemetry locally, and uploads accepted data to the portal project identified by the portal project key.

## What It Can Collect

Beacon can support several different telemetry routes:

- crash reports, setup failures, start failures, and manual test reports
- explicit non-fatal error events recorded by plugin code
- anonymous public usage stats for server, player, version, and environment trends ([ModStats.io](https://modstats.io))
- verified public server listings for the ModStats server browser when server owners opt in
- lifecycle events for setup, start, shutdown, migrations, reloads, and other plugin phases
- performance telemetry for bounded timing or numeric measurements
- feature usage events for low-volume product and support signals
- breadcrumbs that explain recent plugin activity before a failure
- manual player issue and suggestion reports with JSON-defined fields

Not every project needs every category. A stats-only asset pack can stay descriptor-driven. A Java plugin can add richer runtime API events. A crash-only integration can keep the descriptor small by enabling only crash capture and any related error or breadcrumb options it wants.

## What The Portal Adds

The web portal is the normal destination for Beacon data. It gives project owners:

- project creation and portal project-key management
- issue and occurrence views for crashes and issue-worthy events
- Explore views for raw events, grouped signals, versions, and context
- private and public stats dashboards, including ModStats-style pages
- verified server profiles, server browser listings, and "servers running this mod" evidence
- manual player report review
- Discord alert routing
- GitHub issue sync
- team access, billing, guardrails, and project operations

## Why Modders Use It

- Find issues that only happen in real servers, real modpacks, and real player scenarios.
- Prioritize fixes by impact instead of guessing from the loudest support thread.
- See which plugin versions, server versions, environments, and installed-mod combinations are tied to recurring problems.
- Catch regressions after a release and confirm whether a fix reduced new reports.
- Give players and server owners a structured way to report problems without asking them to copy logs by hand.
- Publish adoption and compatibility stats without exposing private server or player data.
- Show real public servers using the project when server owners choose to publish verified listings.
- Keep operations in one place: issue triage, raw event exploration, stats, reports, alerts, GitHub sync, consent, and project access.

## Why Players Benefit

- Mod authors can spot recurring failures, compatibility patterns, and version-specific problems faster.
- Support conversations can start from structured reports instead of incomplete screenshots or copied logs.
- Server communities can choose telemetry categories through consent and overrides.
- Players can discover verified public servers through ModStats listings when server owners opt in.
- Public stats can show project adoption without exposing raw server IDs, player identifiers, chat, coordinates, secrets, or full config files.

## Integration Modes

- standalone dependency mode: the recommended default. Beacon is installed as its own runtime package and discovers participating plugins or asset packs.
- embedded mode: an advanced plugin-only option where the owning plugin bundles the runtime and calls telemetry bootstrap code directly.

Both modes use the same portal project, project key, `Server/Beacon/project.json`, consent UI, local overrides, queues, and portal workflows.

Start with [Portal First Setup](/mod/beacon/portal-first-setup) for the full first-time route, then use [Quick Setup](/mod/beacon/quick-setup) to choose crash, stats, standalone, or embedded follow-up guides.
