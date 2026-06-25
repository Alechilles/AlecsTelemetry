# Changelog

## 0.2.5 - Embedded Consent Funnel Hotfix - 2026-06-24

### Fixed
- Fixed host-backed embedded runtimes so first-review consent completion is
  delegated to the active runtime host, allowing Tamework-embedded installs to
  upload `first_review_completed` consent funnel metrics instead of only marking
  local consent state.
- Fixed embedded runtime version reporting so shaded hosts read Alec's
  Telemetry runtime metadata from packaged Maven properties instead of falling
  back to the stale `0.1.3` value.
- Fixed embedded stats heartbeats so player interval peak and average counts are
  preserved by the active runtime coordinator instead of collapsing to only the
  current online-player count.

## 0.2.4 - Server Verification Setup Hotfix - 2026-06-22

### Changed
- Added Alec's Telemetry self reporting so standalone installs have a
  crash/error/stats-enabled project for runtime failures, public usage
  heartbeats, and server verification even when no consumer mods are configured.
- Wired the Alec's Telemetry self project to its hosted project key and added
  internal error events for runtime command and shared event registration
  failures.
- Added an explicit standalone consent UI logo asset and embedded Creditor so
  Alec's Telemetry contributes to `/credits` when installed.
- Fixed Alec's Telemetry's own consent row icon in the consent UI.
- Added `/telemetry server verify <key>` so server owners can save a ModStats server claim token from the command instead of editing the identity file by hand.
- Server verification now uses the newly saved token immediately while still reporting real heartbeat delivery state from the flush summary.
- Added one-time telemetry data migration so Windows save-folder data, lowercase
  server-root telemetry folders, and embedded-owner settings are consolidated
  under the canonical `mods/Alechilles_Alec's Telemetry!` runtime root.
- Fixed consent override saves so unsupported telemetry categories are left
  undefined instead of being pinned to `false`, preserving future descriptor
  defaults when a mod later adds support for those categories.
- Added consent funnel metrics for first-run prompt display, consent UI opens,
  and first-review completion, with per-viewer/project/version dedupe.

### Fixed
- Removed stale legacy telemetry files after successful migration when canonical
  files already exist, preventing old save and embedded-owner telemetry folders
  from lingering after the runtime has adopted the canonical root.
- Fixed embedded-owner cleanup so `Telemetry/Settings/runtime.json` and
  `Telemetry/Settings/server-id.txt` are pruned along with project override
  files after migration.

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
