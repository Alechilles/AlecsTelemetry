# Expanded Telemetry System Plan

This document plans the runtime-side expansion from crash-only telemetry into a
broader event system covering six additional telemetry categories:

- non-fatal error events
- lifecycle timing events
- config snapshot hashes (deferred after the initial event/context pass)
- environment snapshots with per-mod indexing support
- performance sample events
- feature usage events

The backend and portal companion plan lives in:

- `AlecsTelemetryPlatform/docs/expanded-telemetry-system-plan.md`

## Goals

- keep existing crash capture and ingest working during the rollout
- add a shared event model instead of six unrelated point solutions
- keep event emission cheap enough for production server use
- preserve high diagnostic value without turning telemetry into noisy spam

## Event Model

Expand from a crash-only envelope into a shared event envelope with event-specific
details.

Suggested common fields:

- `schemaVersion`
- `eventType`
- `eventName`
- `eventId`
- `projectId`
- `projectDisplayName`
- `pluginIdentifier`
- `pluginVersion`
- `capturedAtUtc`
- `source`
- `sessionId`
- `environmentSnapshotKey`
- `worldName`
- `severity`
- `attributes`
- `details`
- `runtime`

Suggested `eventType` values:

- `crash`
- `error`
- `lifecycle`
- `performance`
- `usage`

Recommendation:

- keep `CrashReportEnvelope` for backward compatibility during rollout
- add a new shared `TelemetryEventEnvelope`
- move shared serialization helpers out of crash-specific code where useful
- introduce a generic `/ingest/event` flow now while keeping `/ingest/crash` working
  for crash compatibility during rollout

## Shared Runtime Foundations

### Session Identity

Generate a `sessionId` once per runtime start.

Use it to:

- correlate lifecycle, usage, performance, error, and crash events
- associate events with one environment snapshot
- support future portal timelines

### Environment Snapshot

Capture one normalized environment snapshot at runtime start, or when the detected
environment changes.

Environment snapshot contents should include:

- `projectId`
- `pluginIdentifier`
- `pluginVersion`
- `runtimeMode`
- `hytaleBuild`
- `serverVersion`
- normalized loaded mods list: `[{ id, version }]`
- derived `modSetHash`

Rules:

- lowercase and trim mod ids
- normalize missing mod versions to `unknown`
- dedupe by mod id
- sort by mod id before hashing
- do not include timestamps inside the snapshot hash input

Runtime behavior:

- build and attach the snapshot lazily on the first outbound crash or event
- attach the full snapshot or a stable `environmentSnapshotKey` to outbound events
- prefer reusing one snapshot per session instead of copying the mod list into every
  event payload

### Config Projection

Status: deferred. The first implementation pass intentionally leaves config
snapshot paths, config hashes, and config schema versions for a later design pass.

Do not upload full config files by default.

Instead, define a curated config projection per telemetry-enabled project that
contains:

- `configProfileHash`
- `configSchemaVersion`
- selected diagnostic toggles or preset names

Rules:

- only include fields with real debugging value
- avoid secrets, freeform text, paths, or identifiers that could become sensitive
- keep the projection stable so the hash is meaningful across releases

### Rate Limiting and Sampling

New event classes need stricter controls than crashes.

Recommended runtime controls:

- per-event fingerprint cooldowns for `error` events
- threshold-triggered emission for `performance` events
- coarse aggregation or periodic summaries for high-frequency metrics
- per-project allowlists for `usage` event instrumentation

Default enablement:

- emit `error` and `lifecycle` events by default for integrated projects
- keep `performance` events opt-in or sampled
- keep `usage` events behind per-project allowlists

Performance enablement model:

- packaged descriptor config defines the default performance telemetry behavior
- runtime settings overrides can leave default, force on, force off, or adjust
  thresholds and sampling
- precedence order is runtime override, then descriptor config, then system default

## The Six Telemetry Categories

### 1. Non-Fatal Error Events

Purpose:

- capture handled exceptions and degraded behavior that never reaches uncaught crash
  telemetry

Suggested event names:

- `handled_exception`
- `retry_exhausted`
- `fallback_activated`
- `integration_failure`
- `validation_failure`

Suggested details:

- throwable type, message, and stack
- subsystem or operation name
- retry count
- degraded mode flag
- related asset, command, interaction, or config ids where applicable

Runtime API additions:

- `captureError(projectId, source, throwable, attributes)`
- `captureWarning(projectId, eventName, attributes)` when there is no throwable

Implementation notes:

- reuse throwable serialization from crash telemetry
- add lightweight fingerprinting for dedupe
- prefer explicit call sites over broad automatic catch-all wrappers

### 2. Lifecycle Timing Events

Purpose:

- track slow or failed initialization and major runtime transitions

Suggested event names:

- `plugin_init`
- `config_load`
- `registry_load`
- `world_join`
- `first_tick_ready`
- `telemetry_flush`

Suggested details:

- `durationMs`
- `success`
- `phase`
- optional failure reason or throwable summary

Runtime API additions:

- `startSpan(projectId, eventName)`
- `finishSpan(span, success, attributes)`

Implementation notes:

- emit one completed event per phase, not raw start and end messages
- keep the list of timed lifecycle phases curated and small

### 3. Config Snapshot Hashes

Purpose:

- correlate incidents with config state without uploading full configs

Suggested fields attached to events:

- `configProfileHash`
- `configSchemaVersion`
- `configDimensions`

Suggested dimensions:

- important feature enable/disable toggles
- selected presets
- compatibility mode values
- debug mode flags when they materially affect behavior

Implementation notes:

- define a project-local config projection helper instead of ad hoc field selection
- include the hash on crashes, errors, lifecycle events, and performance events

### 4. Environment Snapshots with Per-Mod Indexing Support

Purpose:

- identify whether specific loaded mods or versions frequently co-occur with issues

Recommended shape:

- one environment snapshot per session or environment change
- stable `environmentSnapshotKey` attached to later events
- loaded mods represented as normalized `id + version` pairs

Implementation notes:

- individual loaded mods are the primary analytical signal
- `modSetHash` is a secondary grouping key, not the main compatibility signal
- keep raw snapshot JSON available for forward compatibility

### 5. Performance Sample Events

Purpose:

- detect laggy or degraded behavior before it becomes a crash

Suggested event names:

- `slow_task`
- `queue_backlog`
- `tick_over_budget`
- `flush_latency`
- `io_latency`

Suggested details:

- `durationMs` or `metricValue`
- threshold crossed
- subsystem
- operation name
- sample window or count

Implementation notes:

- start with thresholded events, not continuous detailed sampling
- prefer periodic summaries for noisy metrics
- do not emit one event per tick
- performance telemetry enablement uses descriptor defaults with runtime overrides
  for server-owner control

### 6. Feature Usage Events

Purpose:

- measure which features are actually used and whether release work changes user
  behavior

Suggested event names:

- `command_used`
- `ui_opened`
- `interaction_started`
- `interaction_completed`
- `setting_changed`

Suggested details:

- feature name
- action result
- optional target ids
- optional latency for completion flows

Implementation notes:

- track high-level intent, not every click or repaint
- default to no player identifiers
- add instrumentation deliberately per feature instead of bulk auto-instrumentation

## Runtime Rollout Plan

### Phase 1

- generate `sessionId`
- capture normalized environment snapshots
- add config projection and `configProfileHash`
- attach environment/config context to crash telemetry first

### Phase 2

- add `TelemetryEventEnvelope`
- add runtime APIs for `error` and `lifecycle` events
- keep crash submission on the existing endpoint during transition while the new
  generic event ingest path ships in parallel

### Phase 3

- add thresholded `performance` events
- add first-pass `usage` event instrumentation for major features only

### Phase 4

- refine dedupe, cooldown, and sampling behavior from real production traffic
- migrate more call sites from crash-only helpers to shared event helpers

## Code Areas Likely To Change

- `src/main/java/com/alechilles/alecstelemetry/crash/CrashReportEnvelope.java`
- `src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- `src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java`
- `src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
- `src/main/java/com/alechilles/alecstelemetry/runtime/`

Likely additions:

- `TelemetryEventEnvelope`
- `TelemetryEnvironmentSnapshot`
- `TelemetrySessionContext`
- `TelemetrySpan` or equivalent lightweight timing helper
- config projection helpers

## Success Criteria

- crash telemetry still works unchanged for existing consumers
- new event classes can be added without schema churn in every layer
- a session can be correlated across crashes, warnings, slow phases, and usage
- compatibility analysis can identify individual loaded mods and versions as common
  factors
- config state can be correlated without shipping raw configs
