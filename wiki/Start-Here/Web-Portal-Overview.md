---
title: "Web Portal Overview"
order: 8
published: true
draft: false
---

# Web Portal Overview

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

The Alec's Telemetry web portal is the project dashboard for mod authors. It is where you create telemetry projects, manage ingest keys, review issues, explore raw signals, configure public stats, and connect Discord or GitHub workflows.

Portal URL:

```text
https://telemetry.alecsmods.com/portal
```

The portal is not only a crash inbox. It is the operations surface for everything the runtime can upload to Alec's hosted service.

## What It Handles

- Project creation, display names, access roles, and hosted project keys.
- Crash and issue triage for recurring failures and issue-worthy events.
- Explore views for raw events, grouped signals, versions, environment context, and evidence.
- Private stats dashboards for server, player, version, and environment trends.
- Public ModStats-style pages when a project owner chooses to publish stats.
- Manual player issue and suggestion report review.
- Discord alert routing for crashes, events, and player reports.
- GitHub issue sync for linking portal issues to repository work.
- Project guardrails, billing/account ownership, audit history, and operational settings.

## How The Runtime Connects

The runtime sends crash envelopes, event envelopes, stats heartbeats, and manual reports for configured projects. The hosted `projectKey` resolves to one portal project, and the descriptor `projectId` must match that portal project ID.

That means the web portal owns the project record, team access, display name, key rotation, stats visibility, Discord routing, GitHub sync, and guardrails. The packaged descriptor tells the runtime which portal project to upload to and which telemetry categories are enabled by default.

## What To Check First

After setup, use the portal to confirm:

- recent ingest reached the expected project
- `/telemetry test <project-id>` created an issue or occurrence
- stats appear after the first heartbeat when `stats.enabled` is true
- Discord routing and GitHub sync are configured only when your team wants them
- public stats are private or public according to the project setting

## When To Use Something Else

Use a custom endpoint if you want telemetry to go somewhere other than Alec's hosted service. Use embedded mode if your plugin needs to bundle telemetry bootstrap logic directly instead of requiring the standalone dependency.
