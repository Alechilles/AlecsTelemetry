---
title: "Runtime API"
order: 14
published: true
draft: false
---

# Runtime API

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Consumer mods can use Beacon as an optional runtime integration after they ship `Server/Beacon/project.json`.

## Locate The Runtime

Dependency-mode mods should treat the runtime as optional:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api == null || !api.isEnabled()) {
    return;
}

TelemetryProjectHandle project = api.findProject("example-consumer-mod");
if (project == null || !project.isEnabled()) {
    return;
}
```

Embedded mods normally keep the `EmbeddedTelemetryService` returned by `EmbeddedTelemetryBootstrap.bootstrap(this)`.

## Project Operations

`TelemetryProjectHandle` exposes breadcrumbs, setup and start failure capture,
error events, diagnostic bundles, lifecycle events, performance timings, usage
events, stats events, manual report UI opening, and project-scoped flush requests.

The runtime ignores events that are disabled by project consent, descriptor defaults, runtime overrides, sampling, or descriptor allowlists.

## Diagnostic Bundles

Use `submitDiagnosticBundle(...)` for safe, bounded technical evidence. Project
telemetry and the independent `diagnostics` category must be enabled. Error
events consent does not enable or disable diagnostic bundles. The consent UI
shows Diagnostics as `Diag` with the tooltip "Automatic diagnostic bundles with
technical metadata and optional redacted attachments." The hosted payload,
redaction, queue, size, retention, portal, and MCP rules are documented in the
[canonical Diagnostic Bundles guide](https://github.com/Alechilles/Beacon/blob/main/docs/diagnostic-bundles.md).

Embedded runtime bridges fail closed for this category: an older bridge or a
bridge payload with a missing `diagnosticsEnabled` field maps Diagnostics to
`false`.

## Event Context

Prefer `TelemetryEventContext` for explicit events:

```java
project.recordPerformanceWithContext(
        "reload_config_duration",
        durationMs,
        null,
        TelemetryEventContext.performance()
                .subsystem("config")
                .phase("reload")
                .operation("apply")
                .runtimeSide("server")
                .detail("configFileCount", configFileCount)
                .build()
);
```

Standard context fields become first-class event envelope fields. Custom `detail(key, value)` entries are uploaded only when the descriptor declares the matching detail allowlist.

## Manual Report UI

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

`TelemetryReportOpenRequest` accepts `issue` or `suggestion`.
