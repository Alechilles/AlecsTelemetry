# Changelog

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
