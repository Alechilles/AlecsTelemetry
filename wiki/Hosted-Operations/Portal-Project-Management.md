---
title: "Portal Project Management"
order: 2
published: true
draft: false
---

# Portal Project Management

Parent: [Hosted Operations](/mod/alecs-telemetry/hosted-operations) | [Home](/mod/alecs-telemetry/home)

The official portal is the primary setup and operations surface for hosted Alec's Telemetry projects.

## Sign In

1. Open [the portal](https://telemetry.alecsmods.com/portal).
2. Sign in with Discord or GitHub.
3. Accept the Terms of Service and acknowledge the Privacy Policy if prompted.
4. Link the other provider later from Account if your workflow needs both.

Discord is used for routing and guild lookup. GitHub is used for sign-in and GitHub issue sync.

## Create A Project

1. Open Projects.
2. Choose **Create project**.
3. Enter a stable `Project ID`.
4. Enter a user-facing `Display name`.
5. Create the project.
6. Copy the one-time project key.

The project key belongs in `Server/Telemetry/project.json`.

> [Screenshot Placeholder: Portal create project form with Project ID and Display name fields]

## After Project Creation

For a new project, complete these tasks:

1. Add the project key to your descriptor.
2. Package and install your plugin or asset pack.
3. Confirm `/telemetry project <project-id>` sees the project.
4. Send a test report or wait for a stats heartbeat.
5. Confirm the portal project card shows recent ingest.
6. Configure Discord routing if alerts should be delivered.
7. Configure public stats if the project should appear on ModStats.
8. Configure GitHub issue sync if issues should link to GitHub.

## Roles

Project owners and admins can manage project settings, keys, public stats display, Discord routing, and GitHub integration. Lower roles may be able to inspect project data without changing operations settings.

## Common Problems

- `project_id_conflict`: choose a unique project ID.
- Project key not copied: rotate the key from project admin.
- No recent ingest: verify the descriptor is packaged and the runtime is installed.
- Routing needed badge remains: configure Discord routing.
