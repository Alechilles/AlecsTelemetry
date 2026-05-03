---
title: "What Is Alec's Telemetry"
order: 2
published: true
draft: false
---

# What Is Alec's Telemetry

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Alec's Telemetry is a standalone runtime mod for crash telemetry in Hytale mods. It captures attributed crashes and startup/setup failures, queues reports locally, and flushes them to the configured destination.

For most modders, the destination is Alec's hosted telemetry platform. That setup keeps crash triage, project access, and Discord-connected workflows in one place.

## Why Modders Use It

- The default integration is small: install Alec's Telemetry and ship `telemetry/project.json`.
- If the mod manifest has a correct `Group`, `Name`, and `Main`, Alec's Telemetry can infer most project metadata.
- Hosted `projectKey` values are publishable ingest keys, so normal hosted setup does not require server owners to manage private secrets.
- The hosted portal gives mod authors a place to review recurring crash issues instead of starting from raw logs.
- Most dependency-mode integrations do not need custom Java code.

## Why Players Benefit

- Mod authors can see recurring crash issues faster.
- Support conversations can start from structured telemetry instead of incomplete manual reports.
- Server communities spend less time reproducing the same failure just to gather context.

## Integration Modes

- `dependency`: the recommended default. Alec's Telemetry runs as a standalone dependency and discovers participating mods.
- `embedded`: an advanced option where the owning mod bundles telemetry bootstrap logic directly.

Start with [Quick Setup](/mod/alecs-telemetry/quick-setup) unless you know you need embedded mode or a custom endpoint.
