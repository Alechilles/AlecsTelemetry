---
title: "Performance Telemetry"
order: 9
published: true
draft: false
---

# Performance Telemetry

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Performance telemetry records timing or numeric measurements for operations your plugin controls.

## Local project profiler (Phase 1)

The optional Spark monitor provides a separate local project view. It reads
completed passive Spark `ASYNC` `EXECUTION` windows only. The global monitor,
the project, and project performance consent must all be enabled.

The view is available from `TelemetryProjectHandle.profiler()` and advertises
`profiler-api-v1`. It publishes immutable summaries with class and method
names, sampled timing, selected WorldThread share, fingerprints, and bounded
representative paths. It does not upload profiler summaries or expose raw
profiles, frame trees, player data, or server identity. Spark's own profile
retention or upload settings remain separate.

Paths use `SELF` for sampled self time in an owned method and `DOWNSTREAM` for
work below the nearest owned method, with the first external method as context.
Phase 1 emits only `OBSERVED`; `signals` belongs to Phase 2 `hot-path-v1`.
The service keeps at most five paths per snapshot and ten snapshots per
project, with 500 project-path entries globally. It accepts four listener
workers per project and 32 globally. Attribution uses an exact plugin
identifier, then the longest complete package prefix, with at most 32
normalized prefixes per project.

An older elected runtime returns an unavailable view with empty data and a
closed subscription. Disabling performance consent clears the project's local
history. Project-filtered operator commands are available as
`/telemetry profiler top <project-id>` and
`/telemetry profiler history <project-id>`; their replies are copied to the
ordinary server log and may appear in a manual log attachment when the normal
report controls allow it.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once.

## Descriptor Block

Add this block to the shared descriptor:

```json
{
  "telemetry": {
    "performance": {
      "supported": true,
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
    }
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
