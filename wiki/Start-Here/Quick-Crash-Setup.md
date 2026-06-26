---
title: "Quick Crash Setup"
order: 6
published: true
draft: false
---

# Quick Crash Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

Use this route when you only want hosted crash and error reporting. It is the quickest useful Alec's Telemetry setup for most plugins.

## Portal Setup

1. Open `https://telemetry.alecsmods.com/portal`.
2. Sign in with Discord or GitHub.
3. Create a project.
4. Copy the project key.
5. Optional: configure Discord routing so new issues notify your support server.

## Descriptor

Create `Server/Telemetry/project.json` in your plugin or asset pack:

```json
{
  "hosted": {
    "projectKey": "replace_with_your_portal_project_key"
  }
}
```

For an explicit crash-only default, use:

```json
{
  "defaults": {
    "enabled": true,
    "destinationMode": "hosted"
  },
  "capture": {
    "uncaughtExceptions": true,
    "setupFailures": true,
    "startFailures": true,
    "exceptionalWorldRemovals": true
  },
  "events": {
    "errors": { "enabled": true },
    "breadcrumbs": { "enabled": true, "automatic": true },
    "lifecycle": { "enabled": false }
  },
  "performance": { "enabled": false },
  "usage": { "enabled": false },
  "stats": { "enabled": false },
  "hosted": {
    "projectKey": "replace_with_your_portal_project_key"
  }
}
```

The minimal descriptor is enough when defaults are acceptable. Use the explicit version when you want the consent screen to show non-crash categories disabled by default.

## Install And Package

1. Package `Server/Telemetry/project.json` into your release file.
2. Install Alec's Telemetry as a standalone dependency, unless your plugin embeds it.
3. Install your plugin or asset pack.
4. Start the server.
5. Open the consent UI if prompted and confirm crash reporting is enabled.

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
