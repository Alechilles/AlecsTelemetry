---
title: "Command Reference"
order: 16
published: true
draft: false
---

# Command Reference

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

The root command is `/beacon`. The root permission is `beacon.command.beacon`.

## Runtime Diagnostics

```text
/beacon status
/beacon projects
/beacon project <project-id>
```

Use these to inspect runtime enablement, active coordinator ownership, registered projects, endpoints, pending queues, overrides, source paths, and registration warnings.

## Consent

```text
/beacon consent
```

This opens the shared consent UI. It can change project-level enablement, crash
capture, errors, automatic diagnostics (`Diag`), lifecycle, performance, usage,
anonymous public stats, and breadcrumbs.

The first-run notice uses the same consent runtime. Descriptor defaults provide the initial state until a server owner reviews or changes a project.

Diagnostics is off by default unless a descriptor explicitly sets
`telemetry.diagnostics.defaultEnabled` to `true`. Error events and Diagnostics
are independent. A previously reviewed project, including a legacy reviewed
record without a supported-category snapshot, keeps newly added Diagnostics
disabled until an operator runs this command and selects Save and Close.

## Flush And Test

```text
/beacon flush
/beacon flush <project-id>
/beacon test <project-id> [detail]
```

`flush` schedules uploads for all projects or one project. `test` queues a manual telemetry test report without crashing the server.

## Manual Player Reports

```text
/beacon report
/beacon report issue
/beacon report suggestion
/beacon report <project-id> [issue|suggestion]
```

`/beacon report` is player-facing and available to `Player`, `OP`, `Admin`, and `Operator` groups.

Operator review commands:

```text
/beacon reports pending
/beacon reports approve <report-id>
/beacon reports reject <report-id>
/beacon reports submitted
```

These review commands matter when `manualReports.manualReviewRequired` is true in `Settings/runtime.json`.
