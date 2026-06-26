---
title: "Performance Telemetry"
order: 9
published: true
draft: false
---

# Performance Telemetry

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Performance telemetry records timing or numeric measurements for operations your plugin controls.

## Descriptor Setup

```json
{
  "performance": {
    "enabled": true,
    "sampleRate": 1.0,
    "thresholdMs": 100,
    "details": {
      "reload_config_duration": {
        "allowedFields": {
          "configFileCount": { "type": "number" },
          "phase": { "type": "enum", "values": ["read", "parse", "apply"] }
        }
      }
    }
  },
  "hosted": {
    "projectKey": "paste_your_project_key_here"
  }
}
```

`thresholdMs` skips events below the configured duration. `sampleRate` lets high-volume measurements stay bounded.

## Record Performance

```java
project.recordPerformanceWithContext(
        "reload_config_duration",
        durationMs,
        null,
        TelemetryEventContext.performance()
                .subsystem("config")
                .phase("reload")
                .operation("apply")
                .detail("configFileCount", configFileCount)
                .build()
);
```

Use stable event names and low-cardinality details so the portal can group data usefully.

## Verify

1. Trigger the measured operation.
2. Run `/telemetry flush <project-id>`.
3. Check Explore in the portal.
4. Confirm events above the threshold appear.

## Troubleshooting

- Event missing: duration may be below `thresholdMs`.
- Event rate too high: lower `sampleRate`.
- Detail missing: add the field to `performance.details`.
- Measurement is too broad: split by operation or subsystem.
