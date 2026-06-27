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
