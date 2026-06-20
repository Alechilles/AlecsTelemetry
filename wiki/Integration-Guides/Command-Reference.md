---
title: "Command Reference"
order: 7
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

Use these to inspect runtime enablement, active coordinator ownership, registered projects, endpoints, pending queues, overrides, source paths, and registration warnings.

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
