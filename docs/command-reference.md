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
  pending queue count, last flush state, active coordinator, discovered mods
  directory, and a compact local Spark profiler monitor state.
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

## Spark Hot-Path Monitor

When enabled in `Settings/runtime.json`, the local Spark monitor reads completed
passive Spark background windows and ranks Java self time from Hytale
`WorldThread` threads. It does not read a profile started by a user. The output
is bounded. Project snapshots and signals require the global monitor, the
project, and project performance consent to be enabled. See [Runtime
Overrides](runtime-overrides.md) for the settings, compatibility gate, and
safety limits.

```text
/telemetry profiler status
/telemetry profiler top
/telemetry profiler history
/telemetry profiler top <project-id>
/telemetry profiler history <project-id>
/telemetry profiler signals <project-id>
```

- `profiler status` shows the monitor state, active-owner and circuit flags,
  Spark version, next capture time, current completed window when available,
  capture timing, heap delta, thread/frame counts, and hot-path count from
  current diagnostics. It does not show the retained history view.
- `profiler top` shows up to 10 ranked hot paths from the current actionable
  diagnostics result. It can report no data while the monitor is `CAPTURING` or
  after a later non-actionable state, even when history contains an earlier
  completed window.
- `profiler history` shows the newest retained completed windows, limited by
  `maxHistorySnapshots` and by the command's 10-entry output bound.

The project forms read the local project profiler view. `top <project-id>`
shows the latest snapshot and at most five paths. Its header includes analysis
duration, omitted-path count, and truncated-prefix count. `history
<project-id>` shows the newest ten retained snapshots in oldest-to-newest
order. Each project path line includes `SELF` or `DOWNSTREAM`, sampled
milliseconds, selected WorldThread share, the owned method, an optional first
external method, qualification, and the 32-character fingerprint. Current
snapshot paths use the rolling `hot-path-v1` qualification: `OBSERVED`,
`PROVISIONAL`, or `REPEATED`.

`signals <project-id>` evaluates the latest five actionable project snapshots
and shows at most five active candidates. A candidate can remain visible after
it is absent from the newest window until it expires. Each candidate includes:

- rank;
- `severity`: `NOTICE`, `MODERATE`, `HIGH`, or `SEVERE`;
- `qualification`: `OBSERVED`, `PROVISIONAL`, or `REPEATED`;
- qualifying appearances as `hits/5`;
- median selected WorldThread share as `medianShare`;
- total sampled time as `totalSampledMs`;
- `SELF` or `DOWNSTREAM` attribution; and
- the stable fingerprint.

The next line contains the owned method tuple (`class#methoddescriptor`). A
`DOWNSTREAM` candidate can also include its first external method tuple. The
severity bands use median share: `NOTICE` is 2% to under 5%, `MODERATE` is 5%
to under 10%, `HIGH` is 10% to under 20%, and `SEVERE` is 20% or more. Severity
describes sampled impact. It does not prove that the mod caused lag or that
the server was lagging. A project with no active candidates gets a clear local
message. An unavailable or mixed-version provider returns stable unavailable
text.

Project IDs are unquoted and trimmed. One project ID is allowed. An extra
argument returns one usage line. If the project is unavailable, the command
does not fall back to global paths. A project filter is also unavailable when
the elected runtime does not advertise `profiler-api-v1`.

The monitor keeps bounded summaries, rolling signals, history, and publication
context in memory. It has no dedicated profiler evidence file and no structured
Phase 2 profiler evidence queue or upload path. Ordinary summary and
command-output text is
written to the server log, which can retain it under the server log policy. A
manual report can upload current or previous log text when the manual-report
settings, project descriptor, review, redaction, and clipping controls allow
it. The monitor does not write raw Spark profile or frame-tree data to the log
or a profile file, and Telemetry does not upload that raw data. These text
commands use the root Telemetry permission and accept server console senders.
Every line returned by `profiler status`, `profiler top`, `profiler history`,
and `profiler signals` is also written at `INFO` to the ordinary server log with
a `Spark profiler command output` prefix.
The same log copy applies to project-filtered replies.

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
