---
title: "Example Mods"
order: 6
published: true
draft: false
---

# Example Mods

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

The repository includes example consumer mods that show both integration modes.

## Example Consumer Mod

The standard example shows the minimum files another mod would ship to integrate with Alec's Telemetry.

In many cases, the only telemetry-specific file needed is:

```text
Server/Telemetry/project.json
```

The runtime can infer the rest from `manifest.json` when the manifest has correct `Group`, `Name`, and `Main` values.

Use this example when you want dependency mode and a standalone Alec's Telemetry dependency.

## Embedded Consumer Mod

The embedded example shows how a modder can bootstrap telemetry directly in their own mod instead of requiring the standalone dependency.

Key pieces:

- `Server/Telemetry/project.json` declares `runtimeMode: "embedded"`.
- The mod boots telemetry in its own lifecycle.

Use this example only when embedded mode is an intentional packaging or lifecycle choice.
