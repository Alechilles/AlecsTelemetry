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

## Portal Setup

1. Create a project in [the portal](https://telemetry.alecsmods.com/portal).
2. Copy the project key.
3. Add the key to `Server/Telemetry/project.json`.
4. Configure Discord routing if crash alerts should post to a channel.
5. Configure GitHub issue sync if portal issues should link to GitHub.

## Descriptor Setup

Minimal hosted crash setup:

```json
{
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

Explicit capture settings:

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
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
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
