## 1.0.2 - Console Commands and Upload Retry Hotfix - 2026-06-30

### Changed
- Text-based telemetry diagnostics and operator commands can now run directly
  from the server console as well as from in-game players with permission.
  Player UI commands such as `/telemetry consent` and `/telemetry report` remain
  player-only.
- Added the production Telemetry website URL to the standalone manifest.
- Updated embedded runtime setup docs to reference the `1.0.2` Maven artifact.
- Documented portal project and server-profile deletion behavior in the privacy
  policy.

### Fixed
- Added upload retry cooldowns after failed telemetry delivery attempts so
  automatic flushes do not repeatedly hammer an unavailable or throttling ingest
  endpoint. Manual, shutdown, and server-verification flushes still bypass the
  cooldown.
