# Command Reference

The root command is:

```text
/telemetry
```

The root permission is `telemetry.command.telemetry`. Runtime inspection and
operator actions are intended for `OP`, `Admin`, or `Operator` groups unless
noted otherwise.

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

Consent choices are persisted under the runtime settings root. Project category
choices are written to `Settings/projects/<project-id>.json`, and reviewed/notice
state is kept in the runtime consent state file.

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

`server verify <key>` saves the ModStats server claim token to
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
`Admin`, and `Operator` groups.

Operators can review locally held reports with:

```text
/telemetry reports pending
/telemetry reports approve <report-id>
/telemetry reports reject <report-id>
/telemetry reports submitted
```

These review commands matter when `manualReports.manualReviewRequired` is true
in `Settings/runtime.json`.
