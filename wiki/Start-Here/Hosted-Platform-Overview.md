---
title: "Hosted Platform Overview"
order: 8
published: true
draft: false
---

# Hosted Platform Overview

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Alec's hosted telemetry platform is the easiest destination for Alec's Telemetry reports. It gives mod authors a portal for project management and crash review without requiring each mod to run its own backend.

## What It Handles

- Discord sign-in and project memberships.
- Project key management.
- Recurring crash issue review.
- Explore workflows for inspecting individual telemetry occurrences.
- Discord-connected access and routing workflows.

Portal URL:

```text
https://telemetry.alecsmods.com/portal
```

## What The Runtime Sends

The runtime sends crash envelopes and event envelopes for configured projects. The hosted `projectKey` resolves to the portal project, and the descriptor `projectId` must match that portal project ID.

## When To Use Something Else

Use a custom endpoint if you want reports to go somewhere other than Alec's hosted service. Use embedded mode if your mod needs to bundle telemetry bootstrap logic directly instead of requiring the standalone dependency.
