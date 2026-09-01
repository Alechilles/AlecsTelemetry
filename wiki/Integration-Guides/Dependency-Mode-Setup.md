---
title: "Dependency Mode Setup"
order: 18
published: true
draft: false
---

# Dependency Mode Setup

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

This page remains for older links. The current guide is [Standalone Dependency Mode](/mod/beacon/standalone-dependency-mode).

Dependency mode means Beacon runs as a standalone external dependency and discovers participating plugins or asset packs by reading their packaged `Server/Beacon/project.json` descriptors.

## Use This Mode When

- You want the smallest integration.
- You are comfortable requiring Beacon as a dependency.
- Your mod can ship a descriptor file with a portal `projectKey`.
- You do not need to own telemetry bootstrap in your own lifecycle code.

## Minimal Portal Descriptor

Use the shared descriptor from [Portal First Setup](/mod/beacon/portal-first-setup). This legacy page does not repeat the project-key snippet so the setup instructions stay in one place.

New descriptors do not need `runtimeMode`. Beacon chooses the active runtime from the installed or embedded runtime candidates at startup.

## Packaging Checklist

- Include `Server/Beacon/project.json` in your mod package.
- Keep your mod manifest `Group`, `Name`, and `Main` accurate so project metadata can be inferred.
- Add explicit `projectId` only when the inferred ID would not match the portal project ID.
- Add `displayName`, `ownerPluginIdentifiers`, or `packagePrefixes` only for local metadata or attribution overrides.
- Keep portal setup pointed at the publishable project key from the shared descriptor.

## Advanced Alternatives

- Use [Standalone Dependency Mode](/mod/beacon/standalone-dependency-mode) for the complete step-by-step dependency-mode guide.
- Use [Embedded Mode](/mod/beacon/embedded-mode) if your mod needs to bundle telemetry bootstrap logic directly.
- Use [Runtime Overrides](/mod/beacon/runtime-overrides) when a server owner needs to disable telemetry or redirect reports locally.
