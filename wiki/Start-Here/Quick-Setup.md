---
title: "Quick Setup"
order: 3
published: true
draft: false
---

# Quick Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

This is the recommended hosted setup for most mods.

## Steps

1. Install `Alec's Telemetry` alongside your mod.
2. Add a `Server/Telemetry/project.json` file to your mod project.
3. Add the hosted `projectKey` for your project.
4. Package the descriptor with your mod.

Recommended default:

```json
{
  "runtimeMode": "dependency",
  "hosted": {
    "projectKey": "replace_with_your_public_project_key"
  }
}
```

Place the file at:

```text
Server/Telemetry/project.json
```

## What Can Be Inferred

If your mod manifest already has a correct `Group`, `Name`, and `Main`, Alec's Telemetry can infer:

- `projectId`
- `displayName`
- `ownerPluginIdentifiers`
- `packagePrefixes`

That means many mods only need destination settings plus a hosted key.

## Important Hosted Key Note

Hosted `projectKey` values are publishable ingest keys. They are meant to be shipped in the descriptor, not treated like hidden operator secrets.

If you need a deeper descriptor reference, see [Project Descriptor](/mod/alecs-telemetry/project-descriptor).

## Admin Commands

The standalone runtime exposes `/telemetry status`, `/telemetry projects`, `/telemetry project <project-id>`, `/telemetry flush [project-id]`, and `/telemetry test <project-id> [detail]`.

The root command permission is `telemetry.command.telemetry`; subcommands use the same stable `telemetry.command.telemetry.*` prefix.
