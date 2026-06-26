---
title: "Quick Crash Setup"
order: 6
published: true
draft: false
---

# Quick Crash Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this route when you only want hosted crash and error reporting. It is the quickest useful Alec's Telemetry setup for most plugins.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. That creates the portal project, project key, descriptor, optional consent icon, package checks, and first upload verification.

This page only covers the crash-specific settings.

## Crash Descriptor Block

For an explicit crash-only default, add this block to the shared descriptor:

```json
{
  "capture": {
    "uncaughtExceptions": true,
    "setupFailures": true,
    "startFailures": true,
    "exceptionalWorldRemovals": true
  }
}
```

The minimal descriptor from [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) is enough when defaults are acceptable. Add the block above only when you want the crash category to be explicit.

## Install And Verify Locally

1. Install Alec's Telemetry as a standalone dependency, unless your plugin embeds it.
2. Install your plugin or asset pack.
3. Start the server.
4. Open the consent UI if prompted and confirm crash reporting is enabled.

## Verify

Run:

```text
/telemetry status
/telemetry project <project-id>
/telemetry test <project-id> crash-setup
/telemetry flush <project-id>
```

Then open the portal project and confirm a test issue appears. A successful setup proves the project key, descriptor packaging, runtime discovery, queueing, upload, and portal ingest path all work.

## What It Captures

Crash setup can include:

- uncaught exceptions
- setup failures
- start failures
- exceptional world removals
- explicit non-fatal errors recorded through the runtime API
- breadcrumbs attached to reports

For custom non-fatal errors, use [Crash And Error Telemetry](/mod/alecs-telemetry/crash-and-error-telemetry).
