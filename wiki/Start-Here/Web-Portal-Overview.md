---
title: "Web Portal Overview"
order: 8
published: true
draft: false
---

# Web Portal Overview

Parent: [Start Here](/mod/beacon/start-here) | [Home](/mod/beacon/home)

The Beacon web portal is the project dashboard for mod authors. It is where you create telemetry projects, manage ingest keys, review issues, explore raw signals, configure public stats and server listings, and connect Discord or GitHub workflows.

Portal URL:

```text
https://beacon.modstats.io/portal
```

The portal is not only a crash inbox. It is the operations surface for everything the runtime can upload to Beacon portal backend.

## What It Handles

- Project creation, display names, access roles, and portal project keys.
- Crash and issue triage for recurring failures and issue-worthy events.
- Explore views for raw events, grouped signals, versions, environment context, and evidence.
- Private stats dashboards for server, player, version, and environment trends.
- Public ModStats-style pages when a project owner chooses to publish stats.
- Verified server profiles, server browser listings, and reciprocal "servers running this mod" panels.
- Manual player issue and suggestion report review.
- Discord alert routing for crashes, events, and player reports.
- GitHub issue sync for linking portal issues to repository work.
- Project guardrails, billing/account ownership, audit history, and operational settings.

## How The Runtime Connects

The runtime sends crash envelopes, event envelopes, stats heartbeats, and manual reports for configured projects. The descriptor `hosted.projectKey` resolves to one portal project, and the descriptor `projectId` must match that portal project ID.

That means the web portal owns the project record, team access, display name, key rotation, stats visibility, server listing visibility, Discord routing, GitHub sync, and guardrails. The packaged descriptor tells the runtime which portal project to upload to, which telemetry categories the mod supports, and which supported categories are enabled by default.

## What To Check First

After setup, use the portal to confirm:

- recent ingest reached the expected project
- `/beacon test <project-id>` created an issue or occurrence
- stats appear after the first heartbeat when `telemetry.stats.supported` is true and stats consent is enabled
- verified server listings appear only for server owners who publish and verify a server profile
- Discord routing and GitHub sync are configured only when your team wants them
- public stats are private or public according to the project setting

## When To Use Something Else

Use a custom endpoint if you want telemetry to go somewhere other than Beacon portal backend. Use embedded mode if your plugin needs to bundle telemetry bootstrap logic directly instead of requiring the standalone dependency.
