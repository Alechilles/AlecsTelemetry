---
title: "Dependency Mode Setup"
order: 18
published: true
draft: false
---

# Dependency Mode Setup

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

This page remains for older links. The current guide is [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode).

Dependency mode means Alec's Telemetry runs as a standalone external dependency and discovers participating plugins or asset packs by reading their packaged `Server/Telemetry/project.json` descriptors.

## Use This Mode When

- You want the smallest integration.
- You are comfortable requiring Alec's Telemetry as a dependency.
- Your mod can ship a descriptor file with a hosted `projectKey`.
- You do not need to own telemetry bootstrap in your own lifecycle code.

## Minimal Hosted Descriptor

Use the shared descriptor from [Portal First Setup](/mod/alecs-telemetry/portal-first-setup). This legacy page does not repeat the project-key snippet so the setup instructions stay in one place.

New descriptors do not need `runtimeMode`. Alec's Telemetry chooses the active runtime from the installed or embedded runtime candidates at startup.

## Packaging Checklist

- Include `Server/Telemetry/project.json` in your mod package.
- Keep your mod manifest `Group`, `Name`, and `Main` accurate so project metadata can be inferred.
- Add explicit `projectId` only when the inferred ID would not match the portal project ID.
- Add `displayName`, `ownerPluginIdentifiers`, or `packagePrefixes` only for local metadata or attribution overrides.
- Keep hosted setup pointed at the publishable project key from the shared descriptor.

## Advanced Alternatives

- Use [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode) for the complete step-by-step dependency-mode guide.
- Use [Embedded Mode](/mod/alecs-telemetry/embedded-mode) if your mod needs to bundle telemetry bootstrap logic directly.
- Use [Runtime Overrides](/mod/alecs-telemetry/runtime-overrides) when a server owner needs to disable telemetry or redirect reports locally.
