---
title: "Performance Telemetry"
order: 9
published: true
draft: false
---

# Performance Telemetry

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Performance telemetry records timing or numeric measurements for operations your plugin controls.

## Local project profiler (Phase 2)

The optional Spark monitor provides a separate local project view. It reads
completed passive Spark `ASYNC` `EXECUTION` windows only. The global monitor,
the project, and project performance consent must all be enabled.

The view is available from `TelemetryProjectHandle.profiler()` and advertises
`profiler-api-v1`. It publishes bounded immutable in-memory summaries with class
and method names, sampled timing, selected WorldThread share, fingerprints,
rolling qualification, and bounded representative paths. Telemetry has no
dedicated profiler evidence file and no structured Phase 2 profiler evidence
queue or upload path. Ordinary monitor summaries and command output can be
retained by the server log policy and can enter a manual current or previous
log attachment under the normal report settings, review, redaction, and
clipping controls. Raw Spark profiles and frame trees are not written to those
logs or uploaded by Telemetry. Spark's own profile retention or upload settings
remain separate.

Paths use `SELF` for sampled self time in an owned method and `DOWNSTREAM` for
work below the nearest owned method, with the first external method as context.
`hot-path-v1` qualifies a path in a window at 20 ms and 2% selected WorldThread
share. `OBSERVED` is the normal early state. `PROVISIONAL` requires one 100 ms
and 20% appearance. `REPEATED` requires three qualifying appearances in the
latest five actionable windows. An actionable empty window is retained as an
empty `COMPLETE` snapshot so old candidates can age. The service keeps at most
five paths per snapshot, ten snapshots per project, 500 snapshots globally,
and 500 project-path entries globally. It accepts four listener workers per
project and 32 globally. Attribution uses an exact plugin identifier, then the
longest complete package prefix, with at most 32 normalized prefixes per
project. Each package-prefix value is limited to 512 characters before
normalization. Longer values are omitted and reported as truncated.

`TelemetryProfilerContext` can contain nullable `hytaleVersion`, nullable
aggregate `playerCount`, nullable Spark public one-minute TPS/MSPT, and non-zero
counts for declared exact-match breadcrumb categories from five one-minute
buckets. Breadcrumb counts require project enablement and breadcrumb consent.

`projectVersion` is not part of `TelemetryProfilerContext`. Separately, every
`TelemetryProfilerSnapshot` has the required logical `projectVersion` snapshot
field from the logical registration.

An older elected runtime returns an unavailable view with empty data and a
closed subscription. Disabling the project or performance consent clears the
project's local history, signals, and context. Project-filtered operator
commands are available as `/telemetry profiler top <project-id>`,
`/telemetry profiler history <project-id>`, and
`/telemetry profiler signals <project-id>`. The signals command shows at most
five active candidates with severity, qualification, hits/5, median share,
total sampled milliseconds, attribution, method tuples, and fingerprint. Their
severity describes sampled impact, not causality. Replies are copied to the
ordinary server log and may appear in a manual log attachment when the normal
report settings, review, redaction, and clipping controls allow it.

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
