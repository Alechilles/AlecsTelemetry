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

```text
/telemetry profiler status
/telemetry profiler top
/telemetry profiler history
```

- `profiler status` shows current diagnostics, including monitor state, active
  ownership and circuit flags, Spark version, schedule, current completed
  window when available, capture timing, heap delta, thread/frame counts, and
  hot-path count. It does not show retained history.
- `profiler top` shows up to 10 ranked hot paths from the current actionable
  diagnostics result. It can show no data while the monitor is `CAPTURING` or
  after a later non-actionable state, even when history contains an earlier
  completed window.
- `profiler history` shows the newest retained completed windows, limited by
  `maxHistorySnapshots` and the command's 10-entry output bound.

These text commands use the root Telemetry permission and accept server-console
senders. The monitor keeps bounded summaries and history in memory and writes
bounded summary lines to the ordinary server log. It does not directly queue or
upload profiler data. A user-submitted manual report can include ordinary log
summary lines through current or previous log attachments when the manual-report
settings, project descriptor, review, redaction, and clipping controls allow
them. Raw Spark profile data is not written by the monitor to the log or a
profile file and is not uploaded by it. Every line returned by `profiler
status`, `profiler top`, and `profiler history` is also written at `INFO` to the
ordinary server log with a `Spark profiler command output` prefix.

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
