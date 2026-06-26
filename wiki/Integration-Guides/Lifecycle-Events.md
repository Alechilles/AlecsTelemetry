---
title: "Lifecycle Events"
order: 8
published: true
draft: false
---

# Lifecycle Events

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Lifecycle events describe important plugin phases such as setup, start, shutdown, migrations, reloads, or data initialization.

## Descriptor Setup

```json
{
  "events": {
    "lifecycle": {
      "enabled": true,
      "details": {
        "plugin_setup": {
          "allowedFields": {
            "phase": { "type": "enum", "values": ["setup", "start", "shutdown"] }
          }
        }
      }
    }
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

## Record Lifecycle Events

```java
project.recordLifecycleWithContext(
        "plugin_setup",
        durationMs,
        true,
        TelemetryEventContext.lifecycle()
                .subsystem("plugin")
                .phase("setup")
                .runtimeSide("server")
                .build()
);
```

Use lifecycle events for coarse phases, not per-tick or high-volume work.

## Verify

1. Start the plugin locally.
2. Run `/telemetry flush <project-id>`.
3. Open Explore or the issue context in the portal.
4. Confirm lifecycle events appear with the expected event name and context.

## Troubleshooting

- Event missing: check `events.lifecycle.enabled` and consent.
- Custom detail missing: add the field to the descriptor allowlist.
- Too many events: report phase boundaries instead of repeated loop activity.
