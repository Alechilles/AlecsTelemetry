---
title: "What Is Alec's Telemetry"
order: 2
published: true
draft: false
---

# What Is Alec's Telemetry

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Alec's Telemetry is a telemetry runtime and hosted portal workflow for Hytale plugins and asset packs. It helps mod authors collect structured operational signals from installed projects without building their own ingest service, issue workspace, stats dashboard, consent UI, or upload queue.

For most modders, the destination is Alec's hosted telemetry platform. The runtime discovers packaged project descriptors, applies consent and server-owner overrides, queues telemetry locally, and uploads accepted data to the portal project identified by the hosted project key.

## What It Can Collect

Alec's Telemetry can support several different telemetry routes:

- crash reports, setup failures, start failures, and safe manual test reports
- explicit non-fatal error events recorded by plugin code
- anonymous public usage stats for server, player, version, and environment trends
- lifecycle events for setup, start, shutdown, migrations, reloads, and other plugin phases
- performance telemetry for bounded timing or numeric measurements
- feature usage events for low-volume product and support signals
- breadcrumbs that explain recent plugin activity before a failure
- manual player issue and suggestion reports with bounded project-defined fields

Not every project needs every category. A stats-only asset pack can stay descriptor-driven. A Java plugin can add richer runtime API events. A crash-only integration can keep the descriptor small and use the default crash capture behavior.

## What The Portal Adds

The hosted portal is the normal destination for Alec's Telemetry data. It gives project owners:

- project creation and hosted project-key management
- issue and occurrence views for crashes and issue-worthy events
- Explore views for raw events, grouped signals, versions, and context
- private and public stats dashboards, including ModStats-style pages
- manual player report review
- Discord alert routing
- GitHub issue sync
- team access, billing, guardrails, and project operations

## Why Modders Use It

- Find issues that only happen in real servers, real modpacks, and real player workflows.
- Prioritize fixes by impact instead of guessing from the loudest support thread.
- See which plugin versions, server versions, environments, and installed-mod combinations are tied to recurring problems.
- Catch regressions after a release and confirm whether a fix reduced new reports.
- Give players and server owners a structured way to report problems without asking them to copy logs by hand.
- Publish adoption and compatibility stats without exposing private server or player data.
- Keep operations in one place: triage, Explore, stats, reports, alerts, GitHub sync, consent, and project access.

## Why Players Benefit

- Mod authors can spot recurring failures, compatibility patterns, and version-specific problems faster.
- Support conversations can start from structured reports instead of incomplete screenshots or copied logs.
- Server communities can choose telemetry categories through consent and overrides.
- Public stats can show project adoption without exposing raw server IDs, player identifiers, chat, coordinates, secrets, or full config files.

## Integration Modes

- standalone dependency mode: the recommended default. Alec's Telemetry is installed as its own runtime package and discovers participating plugins or asset packs.
- embedded mode: an advanced plugin-only option where the owning plugin bundles the runtime and calls telemetry bootstrap code directly.

Both modes use the same portal project, hosted key, `Server/Telemetry/project.json`, consent UI, local overrides, queues, and hosted workflows.

Start with [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) for the full first-time route, then use [Quick Setup](/mod/alecs-telemetry/quick-setup) to choose crash, stats, standalone, or embedded follow-up guides.
