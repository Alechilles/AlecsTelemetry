# Changelog

## 0.2.3 - Intake Backoff Hotfix - 2026-06-21

### Fixed
- Honored hosted intake `Retry-After` and stats heartbeat interval hints so
  runtimes back off instead of repeatedly sending during platform pressure.
- Stopped the flush loop after a backoff response so queued uploads do not keep
  hammering a saturated ingest lane in the same flush pass.

## 0.2.2 - Aggregate Stats Heartbeat Hotfix - 2026-06-20

### Changed
- Reduced stats telemetry volume by reporting one aggregate heartbeat for all active projects on a runtime instead of one separate heartbeat per project.

### Fixed
- Preserved per-project stats attribution in aggregate heartbeat details so the hosted service can fan out one runtime upload into project-specific ModStats rows.

## 0.2.1 - Consent Metrics Hotfix - 2026-06-19

### Fixed
- Fixed hosted consent metrics so first-review choices and later opt-in or opt-out changes are uploaded for portal consent-rate reporting.

## 0.2.0 - Unified Runtime, Consent, Reports, and Stats - 2026-06-18

### Added
- Added a shared runtime host and coordinator election model so standalone and embedded telemetry copies can agree on one active runtime.
- Added consent UI and commands plus project-scoped consent bridge support for dependency and embedded integrations.
- Added manual player reports with attachments, receipts, review commands, and hosted ingest and storage contracts.
- Added Hytale usage stats and project-defined stats heartbeats for hosted dashboards.

### Changed
- Moved project discovery, command wiring, stats, consent, and provider handling into the shared runtime module so the standalone mod stays thin.
- Updated hosted and descriptor docs and examples for the unified runtime model.

### Fixed
- Fixed provider and coordinator parity issues around consent-only projects, passive project listings, startup discovery, and heartbeat ownership.
- Fixed host-backed embedded telemetry startup and teardown so pre-start events and replaced coordinators are preserved cleanly.
