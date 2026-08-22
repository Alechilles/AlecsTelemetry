---
title: "Command Reference"
order: 16
published: true
draft: false
---

# Command Reference

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

The root command is `/telemetry`. The root permission is `telemetry.command.telemetry`.

## Runtime Diagnostics

```text
/telemetry status
/telemetry projects
/telemetry project <project-id>
```

Use these to inspect runtime enablement, active coordinator ownership, registered projects, endpoints, pending queues, overrides, source paths, registration warnings, and the compact local Spark profiler monitor state.

## Spark Hot-Path Monitor

When the opt-in `sparkProfiler` setting is enabled, the monitor reads completed
passive Spark background windows and ranks Java self time from Hytale
`WorldThread` threads. It does not read a profile started by a user.
Project snapshots and signals require the global monitor, the project, and
project performance consent to be enabled.

```text
/telemetry profiler status
/telemetry profiler top
/telemetry profiler history
/telemetry profiler top <project-id>
/telemetry profiler history <project-id>
/telemetry profiler signals <project-id>
```

- `profiler status` shows current diagnostics, including monitor state, active
  ownership and circuit flags, Spark version, schedule, current completed
  window when available, capture timing, heap delta, capture-thread allocated
  bytes when supported, thread/frame counts, and hot-path count. It does not
  show retained history.
- `profiler top` shows up to 10 ranked hot paths from the current actionable
  diagnostics result. It can show no data while the monitor is `CAPTURING` or
  after a later non-actionable state, even when history contains an earlier
  completed window.
- `profiler history` shows the newest retained completed windows, limited by
  `maxHistorySnapshots` and the command's 10-entry output bound.

The project forms read the local `profiler-api-v1` view. `top <project-id>`
shows at most five paths from the latest project snapshot. Its header includes
analysis duration, omitted-path count, and truncated-prefix count. `history
<project-id>` shows the newest ten retained snapshots in oldest-to-newest
order. Each path uses a compact line with `SELF` or `DOWNSTREAM`, sampled
milliseconds, selected WorldThread share, qualification, and a 32-character
fingerprint. The next line contains the complete bounded owned method tuple. A
`DOWNSTREAM` path also gets a complete bounded first external method tuple.
Current paths use `OBSERVED`, `PROVISIONAL`, or `REPEATED` from `hot-path-v1`.

`signals <project-id>` evaluates the latest five actionable project snapshots
and shows at most five active candidates. A candidate can remain visible after
it is absent from the newest window until it expires. Each candidate includes
rank, `severity`, `qualification`, qualifying appearances as `hits/5`, median
selected WorldThread share as `medianShare`, total sampled time as
`totalSampledMs`, `SELF` or `DOWNSTREAM` attribution, and the stable
fingerprint. The next line contains the owned method tuple. A `DOWNSTREAM`
candidate can also include its first external method tuple.

Severity is `NOTICE` from 2% to under 5%, `MODERATE` from 5% to under 10%,
`HIGH` from 10% to under 20%, and `SEVERE` at 20% or more. Severity describes
sampled impact. It does not prove that the mod caused lag or that the server
was lagging. A project with no active candidates gets a clear local message.
An unavailable or mixed-version provider returns stable unavailable text.

Project IDs are unquoted and trimmed. One project ID is allowed. An unavailable
project does not fall back to global paths. An older elected provider returns
an unavailable project view without a linkage error.

These text commands use the root Telemetry permission and accept server-console
senders. The monitor keeps bounded summaries, rolling signals, history, and
publication context in memory. It has no dedicated profiler evidence file and
no structured Phase 2 profiler evidence queue or upload path. A user-submitted
manual report can include ordinary log summary lines through current or previous
log attachments
when the manual-report settings, project descriptor, review, redaction, and
clipping controls allow them. The server log policy can retain the summary and
command-output text. Raw Spark profile or frame-tree data is not written by the
monitor to the log or a profile file, and Telemetry does not upload that raw
data. Every line returned by `profiler status`, `profiler top`, `profiler
history`, and `profiler signals` is also written at `INFO` to the ordinary
server log with a `Spark profiler command output` prefix. Project-filtered
replies use the same log copy.

## Consent

```text
/telemetry consent
```

This opens the shared consent UI. It can change project-level enablement, crash capture, errors, lifecycle, performance, usage, anonymous public stats, and breadcrumbs.

The first-run notice uses the same consent runtime. Descriptor defaults provide the initial state until a server owner reviews or changes a project.

## Flush And Test

```text
/telemetry flush
/telemetry flush <project-id>
/telemetry test <project-id> [detail]
```

`flush` schedules uploads for all projects or one project. `test` queues a manual telemetry test report without crashing the server.

## Manual Player Reports

```text
/telemetry report
/telemetry report issue
/telemetry report suggestion
/telemetry report <project-id> [issue|suggestion]
```

`/telemetry report` is player-facing and available to `Player`, `OP`, `Admin`, and `Operator` groups.

Operator review commands:

```text
/telemetry reports pending
/telemetry reports approve <report-id>
/telemetry reports reject <report-id>
/telemetry reports submitted
```

These review commands matter when `manualReports.manualReviewRequired` is true in `Settings/runtime.json`.
