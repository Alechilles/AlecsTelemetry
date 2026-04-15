# Embedded Consumer Mod

This example shows how a modder can embed telemetry bootstrap logic directly in
their own mod instead of requiring the standalone `Alec's Telemetry` dependency.

Key pieces:

- `telemetry/project.json` declares `runtimeMode: "embedded"`
- the mod boots telemetry in its own lifecycle
