# Embedded Consumer Mod

This example shows how a modder can embed telemetry bootstrap logic directly in
their own mod instead of requiring the standalone `Alec's Telemetry` dependency.

Key pieces:

- `Server/Telemetry/project.json` declares `runtimeMode: "embedded"`
- the mod boots telemetry in its own lifecycle
- embedded consumers can still declare `reports` in the descriptor and open the
  shared manual report UI through `TelemetryReportOpenRequest` when the standalone
  runtime is present
