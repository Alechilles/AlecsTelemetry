---
title: "Usage Events"
order: 10
published: true
draft: false
---

# Usage Events

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Usage events track how players or server operators use specific plugin features. They are not the same as anonymous public stats.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once.

## Descriptor Block

Add this block to the shared descriptor:

```json
{
  "telemetry": {
    "usage": {
      "supported": true,
      "allowedEvents": ["settings_opened"],
      "details": {
        "settings_opened": {
          "allowedFields": {
            "source": { "type": "enum", "values": ["command", "settings_ui"] },
            "configArea": { "type": "string", "maxLength": 60 }
          }
        }
      }
    }
  }
}
```

Only record usage events that are useful for product decisions or support. Do not log raw chat, names, UUIDs, or exact coordinates.

## Record Usage

```java
project.recordUsageWithContext(
        "settings_opened",
        TelemetryEventContext.usage()
                .subsystem("settings")
                .featureKey("settings_page")
                .entryPoint("/example settings")
                .runtimeSide("server")
                .detail("source", "command")
                .build()
);
```

## Verify

1. Trigger the feature locally.
2. Run `/telemetry flush <project-id>`.
3. Open Explore in the portal.
4. Filter for the usage event name.

## Troubleshooting

- Event missing: add it to `usage.allowedEvents`.
- Detail missing: add it to `usage.details`.
- Server owner opted out: check `/telemetry consent`.
- Event names are hard to analyze: rename future events to stable action names.
