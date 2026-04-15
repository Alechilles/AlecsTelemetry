# Embedded Mode Refactor Plan

## Goal

Support two distribution modes that produce the same telemetry payloads and use the
same hosted service:

1. dependency mode
   - modders depend on the standalone `Alec's Telemetry` mod
2. embedded mode
   - modders bundle an embeddable telemetry library inside their own mod

The key product requirement is that modders can choose either path without having
to learn a different hosted platform or a different descriptor format.

## Non-Goal

Do not support "bundle the current standalone plugin jar into another mod".

That would duplicate:

- command registration
- installed-mod scanning
- standalone-owned override files
- standalone-owned storage layout
- standalone runtime lifecycle

Embedded mode should be a dedicated library/bootstrap path, not a repackaged copy
of the standalone runtime mod.

## Coexistence Rule

Preferred rule:

- if a mod declares embedded mode, the standalone runtime ignores that mod

This is the right default because it avoids duplicate reports while still allowing:

- one server to have standalone-managed mods
- another mod on the same server to manage itself through embedded mode

## Descriptor Contract Change

Keep using the same `telemetry/project.json` file for both modes.

Add one new top-level field:

```json
{
  "runtimeMode": "dependency"
}
```

Allowed values:

- `dependency`
  - default when omitted
  - means the standalone `Alec's Telemetry` mod may discover and manage this mod
- `embedded`
  - means the mod manages telemetry internally through the embedded bootstrap
  - the standalone runtime must skip this mod if it sees the descriptor

### Why this is the right shape

- same descriptor file in both modes
- one explicit coexistence flag
- backwards-compatible with existing descriptors because omitted means `dependency`

## Target Architecture

Refactor the Java side into three layers.

### 1. telemetry-core

Purpose:

- shared crash payloads
- store-first queueing
- HTTP upload client
- breadcrumb buffer
- project descriptor parsing
- hosted/custom endpoint resolution

Should contain:

- `CrashAttribution`
- `CrashReportClient`
- `HttpCrashReportClient`
- `CrashReportEnvelope`
- `CrashReportStore`
- `TelemetryBreadcrumbBuffer`
- `TelemetryProjectDescriptor`
- `TelemetryProjectRegistration`
- common config and routing models

Should not contain:

- `JavaPlugin` entrypoints
- command registration
- installed-mod scanning for every mod on disk
- standalone-specific diagnostics UX

### 2. telemetry-standalone-runtime

Purpose:

- current `Alec's Telemetry` mod behavior
- scans installed mods for descriptors
- owns `/telemetry` commands
- owns shared override files
- provides optional public runtime locator API for dependency-mode consumers

Should contain:

- `AlecsTelemetry` plugin entrypoint
- `TelemetryRuntimeService`
- `TelemetryProjectDiscovery`
- `TelemetryProjectOverrideStore`
- `TelemetryRuntimeDiagnostics`
- `/telemetry` command classes
- runtime API locator and project handles used by dependency-mode consumers

### 3. telemetry-embedded-bootstrap

Purpose:

- lightweight embeddable bootstrap for a single consumer mod
- reads that mod's own descriptor
- uses the same payload contract and hosted/custom delivery path
- does not behave like a platform runtime mod

Should contain:

- `EmbeddedTelemetryBootstrap`
- `EmbeddedTelemetryService`
- `EmbeddedTelemetryHandle`
- optional breadcrumb/lifecycle helpers for the owning mod only

Should not contain:

- installed-mod scanning across the whole server
- `/telemetry` commands
- standalone override directory support
- global project registry for unrelated mods

## Runtime Behavior By Mode

### Dependency mode

Behavior remains close to current standalone runtime:

- standalone runtime scans installed mods
- descriptor defaults to `runtimeMode=dependency`
- standalone registers the project
- optional locator API is available to the consumer mod

### Embedded mode

Behavior changes to local ownership:

- the consumer mod explicitly bootstraps telemetry for itself
- it reads only its own `telemetry/project.json`
- it uses its own plugin data directory for local queueing/settings
- it can still use the same hosted `projectKey` or a custom endpoint
- the standalone runtime ignores it if both are installed

## Coexistence Detection Rules

### Standalone scanner behavior

When the standalone runtime scans `telemetry/project.json` files:

1. if `runtimeMode` is omitted, treat it as `dependency`
2. if `runtimeMode` is `dependency`, register normally
3. if `runtimeMode` is `embedded`, skip registration and log an info-level message

Recommended log example:

```text
Skipping telemetry project cool-mod because it declares runtimeMode=embedded.
```

### Embedded bootstrap behavior

When the embedded bootstrap reads its own descriptor:

1. if `runtimeMode` is `embedded`, continue normally
2. if `runtimeMode` is omitted or `dependency`, warn and do not auto-enable

This prevents ambiguous ownership and nudges modders to declare intent explicitly.

## Storage Model

### Standalone mode

Keep the current standalone-owned structure:

```text
Alec's Telemetry data dir/
  Settings/
  Telemetry/
```

### Embedded mode

Use the consumer mod's own data directory:

```text
<ConsumerModDataDir>/
  Telemetry/
    Settings/
    crash-reports/
```

This keeps ownership intuitive and avoids a standalone runtime folder being used by
an embedded mod.

## Command Surface

### Standalone mode

Keep:

- `/telemetry status`
- `/telemetry projects`
- `/telemetry project <id>`
- `/telemetry flush [id]`
- `/telemetry test <id> [detail]`

### Embedded mode

Default: no shared commands.

Reason:

- avoids command collisions
- avoids shipping platform admin UX inside every embedded mod

If a modder wants commands, they should add their own mod-specific debug command and
call the embedded handle directly.

## Crash Capture Strategy In Embedded Mode

### Lifecycle failures

Support explicitly and make them easy:

- `captureSetupFailure(...)`
- `captureStartFailure(...)`

These are safe and deterministic.

### Breadcrumbs

Support directly.

### Uncaught exception capture

Support, but keep it scoped to the embedded project only.

Implementation expectation:

- embedded bootstrap may install a chained default uncaught exception handler
- it still classifies against only that embedded project's descriptor
- if several embedded mods install handlers, they chain and each checks attribution

This is acceptable, but it should be clearly documented as process-wide behavior.

### Exceptional world removals

Support if the owning mod explicitly wires the world-removal hook, or via an
embedded bootstrap helper if practical.

## Public APIs

### Keep for dependency mode

- `TelemetryRuntimeLocator`
- `TelemetryRuntimeApi`
- `TelemetryProjectHandle`

### Add for embedded mode

Suggested shape:

```java
EmbeddedTelemetryHandle telemetry = EmbeddedTelemetryBootstrap.bootstrap(this);
telemetry.recordBreadcrumb("bootstrap", "Config loaded.");
```

Suggested embedded API:

- `isEnabled()`
- `recordBreadcrumb(category, detail)`
- `captureSetupFailure(throwable)`
- `captureStartFailure(throwable)`
- `requestFlush()`
- `captureTestReport(detail)`

The embedded API should be local to the owning mod, not a global cross-mod locator.

## Concrete Refactor Steps

### Phase 1: descriptor and model preparation

1. Add `runtimeMode` parsing to `TelemetryProjectDescriptor`.
2. Default missing `runtimeMode` to `dependency`.
3. Add tests for `dependency` default and explicit `embedded` mode.

### Phase 2: core extraction inside the existing repo

1. Move reusable runtime-independent classes into a `core` package boundary.
2. Keep package names stable where possible to minimize churn.
3. Make `TelemetryRuntimeService` depend on core abstractions instead of owning all logic directly.

### Phase 3: standalone skip behavior

1. Update `TelemetryProjectDiscovery` / registration flow to skip `runtimeMode=embedded`.
2. Add diagnostics/logging so skipped embedded projects are visible.
3. Add tests proving standalone ignores embedded descriptors.

### Phase 4: embedded bootstrap implementation

1. Add `EmbeddedTelemetryBootstrap`.
2. Add `EmbeddedTelemetryService` backed by `telemetry-core`.
3. Load the consumer mod's own descriptor only.
4. Use the consumer mod's data directory for storage.
5. Support hosted/custom routing, breadcrumbs, lifecycle forwarding, and flush.

### Phase 5: embedded sample consumer

1. Add an `examples/EmbeddedConsumerMod/` example.
2. Show:
   - manifest dependency-free setup
   - `runtimeMode=embedded`
   - hosted `projectKey`
   - one small Java bootstrap example

### Phase 6: docs and publishing strategy

1. Update docs to explain both integration modes.
2. Add a strong warning: pick one mode, not both.
3. Publish embedded mode guidance as a separate quick-start.

## Descriptor Examples

### Dependency mode

```json
{
  "runtimeMode": "dependency",
  "hosted": {
    "projectKey": "pub_proj_abc123"
  }
}
```

### Embedded mode

```json
{
  "runtimeMode": "embedded",
  "hosted": {
    "projectKey": "pub_proj_abc123"
  }
}
```

The rest of the descriptor stays the same.

## Testing Matrix

### Unit tests

- descriptor parsing with `runtimeMode`
- standalone skip logic
- embedded bootstrap descriptor loading
- embedded local storage path selection

### Integration tests

- dependency-mode consumer still works unchanged
- embedded consumer can emit and flush a hosted test report
- standalone runtime ignores an embedded consumer descriptor
- mixed server with one dependency-mode mod and one embedded-mode mod works without duplicate alerts

### Manual validation

1. standalone-only server
2. embedded-only server
3. mixed server with both modes installed
4. hosted delivery for both modes

## Recommended Execution Order

1. add `runtimeMode`
2. add standalone skip behavior
3. extract `core` boundary
4. add embedded bootstrap
5. add embedded sample mod
6. update docs

## Recommendation

Implement embedded mode, but keep the standalone runtime as the primary default path.

That gives you:

- the easiest adoption path for modders who are fine with dependencies
- an escape hatch for modders who want a single bundled mod
- one hosted backend and one descriptor format across both modes
