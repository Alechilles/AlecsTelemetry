## 1.0.3 - Manual Test Report Hotfix - 2026-07-04

### Changed
- Updated embedded runtime setup docs to reference the `1.0.3` Maven artifact.

### Fixed
- Fixed `/telemetry test <project-id> [detail]` so crash-enabled projects accept
  the runtime's manual test source and actually queue a crash-style test report.
