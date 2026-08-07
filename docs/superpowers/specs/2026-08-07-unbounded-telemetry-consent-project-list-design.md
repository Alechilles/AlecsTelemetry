# Unbounded Telemetry Consent Project List

## Goal

Show every registered telemetry project in the consent page's existing scrollable list. No project may become inaccessible because of a fixed row limit.

## Design

`TelemetryConsentPage.ui` will retain the page shell, grid header, and scroll container, but it will no longer declare eight project rows. A new `TelemetryConsentProjectRow.ui` document will contain one reusable project row.

When the page renders, `TelemetryConsentPage` will clear the project-row container and append one row document for every project in the consent view model. It will address appended children by list index, populate their project metadata and category controls, and bind events using the corresponding project ID. The existing project sort order, global toggles, category support rules, save behavior, and scrolling behavior remain unchanged.

The fixed `MAX_PROJECT_ROWS` constant and the `Showing X of Y telemetry projects` overflow message will be removed. Empty-state behavior remains unchanged.

## Refresh Behavior

UI refreshes will rebuild the row container from the current diagnostics snapshot before rebinding events. This prevents stale rows when projects are added or removed while keeping selectors deterministic.

## Verification

Add one focused behavior test that renders a diagnostics snapshot with more than eight projects and verifies that every project receives a row and an event binding. Run the runtime test suite, the standalone build, Hytale UI validation for both UI documents, and package inspection to confirm the row document is included.

## Patchwork Stats Finding

No stats-runtime code change is required. Production evidence shows Patchwork's heartbeat was accepted, expanded to the `patchwork` project, and persisted into the stats rollups. The apparent absence occurred before the delayed first heartbeat and portal refresh. Raw heartbeat retention does not remove the aggregate rollup used by the portal.

## Privacy

This change affects only consent-page rendering. It does not change collected fields, consent defaults, upload behavior, retention, or public exposure, so `docs/privacy-policy.md` requires no wording change.
