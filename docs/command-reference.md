# Command Reference

The root command is:

```text
/telemetry
```

The root permission is `telemetry.command.telemetry`. Runtime inspection and
operator actions are intended for `OP`, `Admin`, or `Operator` groups unless
noted otherwise. Text-based diagnostics and operator actions can be run by
players with permission or directly from the server console. UI commands still
require an in-game player sender.

## Runtime Diagnostics

```text
/telemetry status
/telemetry projects
/telemetry project <project-id>
```

- `status` shows runtime enablement, loaded-mod count, registered projects,
  pending queue count, last flush state, active coordinator, and discovered mods
  directory.
- `projects` lists each registered project, effective destination mode, pending
  count, and override state.
- `project` shows one project's endpoint, plugin identity, package prefixes,
  source path, and pending queue count.

For anchored embedded contributions, the project list and consent diagnostics
reflect the live elected catalog. A protocol-ineligible, invalid, passive, or
retired candidate is not a writable active project. Candidate election and
validation warnings are bounded local diagnostics available to integrations via
`TelemetryProjectContributionRegistry.diagnostics()`; the physical source path
shown by local diagnostics is not added to uploaded envelopes.

## Consent

```text
/telemetry consent
```

This opens the shared consent UI. It can change:

- project-level enabled state
- crash capture
- error events
- lifecycle events
- performance events
- usage events
- anonymous public stats
- breadcrumbs

The first-run notice uses the same consent runtime. Descriptor defaults provide
the initial state until a server owner reviews or changes a project.

`/telemetry consent` opens an in-game UI and must be run by a player.

Consent choices are persisted under the canonical Alec's Telemetry settings
root. Project category choices are written to
`mods/Alechilles_Alec's Telemetry!/Settings/projects/<project-id>.json`, and
reviewed/notice state is kept in the runtime consent state file.

## Flush And Test

```text
/telemetry flush
/telemetry flush <project-id>
/telemetry test <project-id> [detail]
```

- `flush` schedules uploads for all projects or one project.
- `test` queues a manual telemetry test report without crashing the server.

## Server Verification

```text
/telemetry server verify
/telemetry server verify <key>
```

`server verify <key>` saves the ModStats server claim token to the canonical
`Settings/server-identity.json`, emits a stats heartbeat containing that token,
and flushes it immediately. Running `server verify` without a key still verifies
with the already configured `serverClaimToken`.

## Manual Player Reports

Players can open the report UI with:

```text
/telemetry report
/telemetry report issue
/telemetry report suggestion
/telemetry report <project-id> [issue|suggestion]
```

`/telemetry report` is player-facing and is available to the `Player`, `OP`,
`Admin`, and `Operator` groups. It opens an in-game UI and must be run by a
player.

Operators can review locally held reports with:

```text
/telemetry reports pending
/telemetry reports approve <report-id>
/telemetry reports reject <report-id>
/telemetry reports submitted
```

These review commands matter when `manualReports.manualReviewRequired` is true
in the canonical `Settings/runtime.json`. They can be run from the server
console.
