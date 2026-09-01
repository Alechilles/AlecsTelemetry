---
title: "Crash And Error Telemetry"
order: 5
published: true
draft: false
---

# Crash And Error Telemetry

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Crash and error telemetry is the core Beacon workflow. It sends structured failures to the web portal so mod authors can group recurring issues, inspect context, and route alerts.

## What Gets Captured

- uncaught exceptions
- plugin setup failures
- plugin start failures
- exceptional world removals
- explicit non-fatal error events recorded through the runtime API
- breadcrumbs attached to crash/error reports when breadcrumbs are enabled

## Before This Page

Complete [Portal First Setup](/mod/beacon/portal-first-setup) once. Configure [Discord Routing](/mod/beacon/discord-routing) or [GitHub Issue Sync](/mod/beacon/github-issue-sync) only if your project needs those portal integrations.

## Descriptor Block

Add this block for hosted crash capture, non-fatal error events, and breadcrumbs:

```json
{
  "telemetry": {
    "crash": {
      "supported": true,
      "uncaughtExceptions": true,
      "setupFailures": true,
      "startFailures": true,
      "exceptionalWorldRemovals": true
    },
    "events": {
      "errors": {
        "supported": true
      },
      "breadcrumbs": {
        "supported": true
      }
    }
  }
}
```

Crash, error, and breadcrumb categories default on. Add `defaultEnabled: false`
to any of those supported categories that should be opt-in. Automatic diagnostic
bundles use the separate `diagnostics` category, shown as `Diag` in consent, and
are off by default unless `defaultEnabled: true` is explicit. Error and
Diagnostics consent choices are independent.

## Explicit Error Events

Plugins can report non-fatal errors without throwing:

```java
project.recordErrorWithContext(
        "needs_seek_failed",
        throwable,
        TelemetryEventContext.error()
                .severity("warning")
                .subsystem("needs")
                .detail("reason", "missing_resource")
                .build()
);
```

Declare custom detail fields in the descriptor before expecting them to upload:

```json
{
  "telemetry": {
    "events": {
      "errors": {
        "supported": true,
        "details": {
          "needs_seek_failed": {
            "allowedFields": {
              "reason": { "type": "enum", "values": ["missing_resource", "path_blocked"] }
            }
          }
        }
      }
    }
  }
}
```

## Verify

Run:

```text
/beacon test <project-id> crash-guide
/beacon flush <project-id>
```

Then open the portal project and check Issues.

## Troubleshooting

- No issue appears: check the project key and run `/beacon project <project-id>`.
- Details are missing: add descriptor allowlists for custom fields.
- Errors are disabled: check the consent UI and project override file.
- Attribution is wrong: add `ownerPluginIdentifiers` and `packagePrefixes`.
