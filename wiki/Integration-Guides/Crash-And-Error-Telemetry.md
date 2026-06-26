---
title: "Crash And Error Telemetry"
order: 5
published: true
draft: false
---

# Crash And Error Telemetry

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Crash and error telemetry is the core Alec's Telemetry workflow. It sends structured failures to the hosted portal so mod authors can group recurring issues, inspect context, and route alerts.

## What Gets Captured

- uncaught exceptions
- plugin setup failures
- plugin start failures
- exceptional world removals
- explicit non-fatal error events recorded through the runtime API
- breadcrumbs attached to crash/error reports when breadcrumbs are enabled

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. Configure [Discord Routing](/mod/alecs-telemetry/discord-routing) or [GitHub Issue Sync](/mod/alecs-telemetry/github-issue-sync) only if your project needs those hosted operations.

## Descriptor Block

The minimal shared descriptor is enough for normal crash setup. Add this block only when you want capture and related event defaults to be explicit:

```json
{
  "capture": {
    "uncaughtExceptions": true,
    "setupFailures": true,
    "startFailures": true,
    "exceptionalWorldRemovals": true
  },
  "events": {
    "errors": {
      "enabled": true
    },
    "breadcrumbs": {
      "enabled": true
    }
  }
}
```

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
  "events": {
    "errors": {
      "enabled": true,
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
```

## Verify

Run:

```text
/telemetry test <project-id> crash-guide
/telemetry flush <project-id>
```

Then open the portal project and check Issues.

## Troubleshooting

- No issue appears: check the project key and run `/telemetry project <project-id>`.
- Details are missing: add descriptor allowlists for custom fields.
- Errors are disabled: check the consent UI and project override file.
- Attribution is wrong: add `ownerPluginIdentifiers` and `packagePrefixes`.
