---
title: "Example Mods"
order: 17
published: true
draft: false
---

# Example Mods

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

The repository includes example consumer mods that show both integration modes.

## Example Consumer Mod

The standard example shows the minimum files another mod would ship to integrate with Beacon.

In many cases, the only telemetry-specific file needed is:

```text
Server/Beacon/project.json
```

The runtime can infer the rest from `manifest.json` when the manifest has correct `Group`, `Name`, and `Main` values.

Use this example when you want dependency mode and a standalone Beacon dependency.

## Embedded Consumer Mod

The embedded example shows how a modder can bootstrap telemetry directly in their own mod instead of requiring the standalone dependency.

Key pieces:

- `Server/Beacon/project.json` declares the same hosted project settings as dependency mode.
- The mod boots telemetry in its own lifecycle.
- `manifest.json` publishes an asset pack, and the build merges the runtime's
  `Common/**` UI resources into that pack.

Use this example only when embedded mode is an intentional packaging or lifecycle choice.
