# Changelog

## 1.2.3 - Runtime Version Stamp Hotfix - 2026-08-30

### Fixed
- Maven and shaded hosts now report the concrete runtime version.

## 1.2.2 - Stable 0.6 Compatibility Hotfix - 2026-08-27

### Changed
- Updated the embedded Creditor library to 1.1.1 for stable Hytale 0.6 support.

### Fixed
- Corrected the embedded-runtime packaging guide and example manifest so hosts
  publish the Telemetry `Common/**` UI assets required by `/telemetry consent`.

## 1.2.1 - Upload Scheduler Hotfix - 2026-08-14

### Changed
- Telemetry uploads now run on isolated virtual threads instead of Hytale's
  shared scheduler. Slow upload responses no longer delay unrelated scheduled
  server tasks.
- Updated the privacy policy to describe anonymous ModStats chart-view and
  refresh events.

### Fixed
- Coordinated shutdown with active uploads to prevent duplicate delivery
  attempts and lingering upload tasks during runtime handoff.
- Corrected the standalone Telemetry credit asset key so its logo resolves in
  the credits UI.

## 1.2.0 - Passive Descriptor Projects - 2026-08-12

### Added
- Embedded libraries can declare a namespaced telemetry project descriptor
  without packaging or calling the Telemetry runtime. An installed standalone
  or embedded runtime discovers the project and reports consented Stats
  heartbeats for it.
- An active contribution can upgrade the same passive project with more
  telemetry categories. New categories stay disabled until the player reviews
  them, and the player receives an in-game review notice.
- Added logical project versions, descriptor hashes, source provenance, and a
  manual host fixture for passive descriptor validation.

### Changed
- Passive descriptors expose Stats only until an active contribution supplies
  more capabilities. Duplicate copies use owner, project version, source, and
  host data to elect one logical project.
- Telemetry discovery refreshes after all server plugins load. Descriptor-only
  projects are available before a player joins.
- Hytale administrators can use the Telemetry commands without a separate
  Telemetry permission assignment.

### Fixed
- Preserved saved consent overrides when a passive project later becomes an
  active contribution.
- Rejected conflicting owner identities and invalid contribution descriptor
  hashes before project election.
- Kept Telemetry commands registered while projects refresh, and detected
  command registration failures instead of reporting a false success.
- Preserved enum detail allowlists when descriptors move through the embedded
  contribution bridge. This prevents valid contributions from blocking runtime
  activation and making all `/telemetry` commands unavailable.

## 1.1.0 - Generic Embedded Contributions - 2026-08-06

### Added
- Added the generic anchored embedded-contribution API and contributor guide in
  `docs/embedded-contributions.md`. A contribution can register a logical
  telemetry project from a descriptor resource in its own classloader without
  colliding with the host mod's conventional descriptor.
- Added contribution ABI 1 with independent project consent, destinations,
  queueing, logical project attribution, and live catalog reconciliation.

### Changed
- Advanced the coordinator bridge to protocol 3. Protocol-2 providers are
  ineligible to own generic contribution projects; existing bootstrap callers
  remain source and binary compatible.
- Existing `EmbeddedTelemetryBootstrap.bootstrap(JavaPlugin)` source and binary
  behavior remains supported. New contributors should use the anchored API and
  a namespaced descriptor resource.
- Active contribution candidates now emit stats heartbeats only when their
  logical project is elected, enabled, stats-consented, heartbeat-eligible, and
  routed to a configured destination. Retiring a contribution removes it from
  new consent and heartbeat snapshots while retaining queued envelopes for
  replay.
- 1.1.0 MVP contributions are hosted-only; conventional projects retain custom
  endpoint support. Established same-ID contribution winners are not
  automatically hot-replaced or failed over, so a server restart is required
  before that project ID can elect a replacement.
- Aggregate heartbeats are separated by resolved destination, and stopped
  contribution services reject non-setup operations instead of buffering them
  for a later start.
- Physical host identifiers, host versions, source paths, and descriptor hashes
  are used for local election diagnostics; normal envelopes attribute the
  logical project and logical plugin version. Existing loaded-mod metadata keeps
  its prior behavior.

## 1.0.5 - Server Mod Discovery Hotfix - 2026-08-05

### Fixed
- Telemetry project discovery now scans only the active server's mod directory,
  preventing globally installed client or release copies from being registered
  by a local server.

## 1.0.4 - Structured Breadcrumb Correlation - 2026-07-19

### Added
- Added bounded structured breadcrumb context for phase, operation, correlation, incident,
  failure-class, disposition, scope type, and allowlisted primitive attributes.
- Added structured breadcrumb propagation through embedded, hosted, standalone-coordinator,
  and reflective provider paths.

### Changed
- Existing category/detail breadcrumb calls remain source and binary compatible and are
  represented as legacy-equivalent structured breadcrumbs.
- Updated privacy and integration documentation for correlation identifiers and structured
  diagnostic context.

### Fixed
- Structured breadcrumbs now survive every supported runtime bridge and retain deterministic
  bounds, ordering, consent behavior, and failed-event attachment semantics.

## 1.0.3 - Manual Test Report Hotfix - 2026-07-04

### Changed
- Updated embedded runtime setup docs to reference the `1.0.3` Maven artifact.

### Fixed
- Fixed `/telemetry test <project-id> [detail]` so crash-enabled projects accept
  the runtime's manual test source and actually queue a crash-style test report.

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

## 1.0.1 - Public Documentation Refresh - 2026-06-28

### Changed
- Updated the README and embedded-mode guides with the production telemetry
  portal, runtime downloads, Maven repository, Gradle dependency, server
  browser, and server verification paths.
- Clarified that server browser listings are server-operator owned and do not
  require a mod-author project.
- Updated the privacy policy to explicitly include loaded mod names in crash,
  event, and public aggregate stats metadata.

## 1.0.0 - Public Telemetry Runtime - 2026-06-26

### Changed
- Telemetry descriptor categories now separate `supported` from
  `defaultEnabled`, allowing a mod to show a category in consent while keeping
  it off by default for opt-in setups.
- Omitted telemetry categories are now treated as unsupported and hidden from
  consent, while supported categories default on unless `defaultEnabled` is
  explicitly set to false.
- Simplified stats-only descriptor setup so a project can declare only
  `telemetry.stats.supported` without listing every unrelated telemetry category
  as false.
- Updated the public wiki setup path to keep portal project creation separate
  from descriptor category opt-in, making the quick crash and stats guides
  clearer for first-time users.
- Promoted the standalone and embeddable runtime artifacts to the first public
  `1.0.0` release line.

### Fixed
- Fixed the example consumer manifest dependency identifier so it targets
  `Alechilles:Alec's Telemetry!`.

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
