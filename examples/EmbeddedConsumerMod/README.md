# Embedded Consumer Mod

This example shows how a modder can embed telemetry bootstrap logic directly in
their own mod instead of requiring the standalone `Beacon` dependency.

Key pieces:

- `Server/Beacon/project.json` declares the hosted project settings
- the mod boots telemetry in its own lifecycle
- `manifest.json` sets `IncludesAssetPack` to `true`
- the build must merge every `Common/**` resource from
  `beacon-runtime` into the host asset-pack source

Shading the runtime classes is not sufficient. Confirm that the final mod
contains `Common/UI/Custom/TelemetryConsentPage.ui` before release. The full
Gradle merge example is in [`docs/embedded-mode.md`](../../docs/embedded-mode.md).

Embedded consumers can declare `reports` in the descriptor and open the shared
manual report UI through `TelemetryReportOpenRequest`. The active runtime host
supplies the shared UI assets.
