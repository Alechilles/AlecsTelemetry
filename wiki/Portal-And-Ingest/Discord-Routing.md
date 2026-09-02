---
title: "Discord Routing"
order: 4
published: true
draft: false
---

# Discord Routing

Parent: [Portal And Ingest](/mod/beacon/portal-and-ingest) | [Home](/mod/beacon/home)

Discord routing sends portal alerts to configured Discord channels. Use it for crash, event, and manual-report notifications when a project team wants alerts outside the portal.

## Setup

1. Sign in to the portal with Discord.
2. Open the project.
3. Open project admin.
4. Find Discord routing.
5. Choose the guild and channel you manage.
6. Follow any verification steps shown by the portal.
7. Save routing.

## What Routing Uses

Discord routing can use:

- Discord account identity for sign-in and access checks
- guild lookup for servers you can manage
- selected channel and role configuration
- telemetry alert summaries sent to the selected channel

## Verify

1. Send a test telemetry report:

```text
/beacon test <project-id> discord-routing
/beacon flush <project-id>
```

2. Confirm the portal receives the issue.
3. Confirm the Discord channel receives the alert if routing is enabled.

## Troubleshooting

- Guild missing: sign in with the Discord account that can manage it.
- Channel missing: check bot access and permissions.
- Portal receives data but Discord does not: inspect project routing status in admin.
- Alert is too noisy: adjust project routing policy or disable routing until the issue volume is under control.
