---
title: "Dependency Mode Setup"
order: 3
published: true
draft: false
---

# Dependency Mode Setup

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Dependency mode is the recommended integration path. Alec's Telemetry runs as a standalone dependency and discovers participating mods by reading their packaged `Server/Telemetry/project.json` descriptors.

## Use This Mode When

- You want the smallest integration.
- You are comfortable requiring Alec's Telemetry as a dependency.
- Your mod can ship a descriptor file with a hosted `projectKey`.
- You do not need to own telemetry bootstrap in your own lifecycle code.

## Minimal Hosted Descriptor

```json
{
  "hosted": {
    "projectKey": "your_public_project_key"
  }
}
```

New descriptors do not need `runtimeMode`. Alec's Telemetry chooses the active runtime from the installed or embedded runtime candidates at startup.

## Packaging Checklist

- Include `Server/Telemetry/project.json` in your mod package.
- Keep your mod manifest `Group`, `Name`, and `Main` accurate so project metadata can be inferred.
- Add explicit `projectId`, `displayName`, or `packagePrefixes` only when the inferred values are not enough.
- Keep hosted setup pointed at the publishable project key for your hosted project.

## Advanced Alternatives

- Use [Embedded Mode](/mod/alecs-telemetry/embedded-mode) if your mod needs to bundle telemetry bootstrap logic directly.
- Use [Runtime Overrides](/mod/alecs-telemetry/runtime-overrides) when a server owner needs to disable telemetry or redirect reports locally.
