---
title: "Quick Crash Setup"
order: 6
published: true
draft: false
---

# Quick Crash Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this route when you only want hosted crash reporting. It is the quickest useful Alec's Telemetry setup for most plugins.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. That creates the portal project, project key, descriptor, optional consent icon, package checks, and first upload verification.

This page only covers the crash-specific settings.

## Crash Descriptor Block

Add this block to the shared descriptor:

```json
{
  "telemetry": {
    "crash": {
      "supported": true,
      "uncaughtExceptions": true,
      "setupFailures": true,
      "startFailures": true,
      "exceptionalWorldRemovals": true
    }
  }
}
```

Omitted telemetry categories are unsupported and hidden from consent. A supported category defaults on unless you set `defaultEnabled: false`. The minimal descriptor from [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) gives the runtime the portal project key, but it does not support crash capture by itself.

To make crash reporting available but off by default, add `"defaultEnabled": false` inside `telemetry.crash`.

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

For non-fatal error events or breadcrumbs, use [Crash And Error Telemetry](/mod/alecs-telemetry/crash-and-error-telemetry).
