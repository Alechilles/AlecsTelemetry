# Runtime API

Consumer mods can use Alec's Telemetry as an optional runtime integration after
they ship `Server/Telemetry/project.json`.

Use the public API when a mod wants to add breadcrumbs, explicit non-crash
events, performance timings, usage events, stats events, bounded diagnostic
bundles, or a custom entry point for manual player reports.

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

Embedded mods normally keep the `EmbeddedTelemetryService` returned by
`EmbeddedTelemetryBootstrap.bootstrap(this)` and use its project-scoped helper
methods directly.

## Project Operations

`TelemetryProjectHandle` exposes:

- `projectId()`
- `displayName()`
- `isEnabled()`
- `recordBreadcrumb(category, detail)`
- `recordBreadcrumb(TelemetryBreadcrumbContext)`
- `captureSetupFailure(throwable)`
- `captureStartFailure(throwable)`
- `recordError(...)` and `recordErrorWithContext(...)`
- `recordLifecycle(...)` and `recordLifecycleWithContext(...)`
- `recordPerformance(...)` and `recordPerformanceWithContext(...)`
- `recordUsage(...)` and `recordUsageWithContext(...)`
- `recordStats(...)` and `recordStatsWithContext(...)`
- `submitDiagnosticBundle(...)`
- `openReportPage(...)`
- `requestFlush()`

The runtime ignores events that are disabled by project consent, descriptor
defaults, runtime overrides, sampling, or descriptor allowlists.

## Diagnostic Bundles

Use `submitDiagnosticBundle` when a mod detects a technical failure and owns a
safe, bounded evidence package. Diagnostic bundles use event telemetry. They do
not open the manual report UI and do not include player contact or follow-up
fields.

```java
TelemetryDiagnosticAttachment attachment = TelemetryDiagnosticAttachment.binary(
        diagnosticId + "-database",
        "database_export",
        "database-debug.zip",
        "application/zip",
        redactedZip
);

TelemetryDiagnosticBundleResult result = project.submitDiagnosticBundle(
        new TelemetryDiagnosticBundle(
                diagnosticId,
                Instant.now().toString(),
                "automatic",
                "persistence_failure",
                "Persistence failure",
                "A redacted diagnostic export is attached.",
                "error",
                TelemetryDiagnosticDisposition.createOrJoinIssue(fingerprint),
                Map.of("operation", "checkpoint"),
                List.of(attachment)
        )
);
```

The producer owns capture policy, redaction, and attachment contents. The
runtime validates the envelope and queues it through the normal event path.
See [Diagnostic Bundles](diagnostic-bundles.md) for limits and privacy rules.

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

Standard context fields become first-class event envelope fields. Custom
`detail(key, value)` entries are uploaded only when the project descriptor
declares the matching detail allowlist for that event name.

Supported context fields are documented in `docs/project-descriptor.md`.

## Structured Breadcrumb Context

Use `TelemetryBreadcrumbContext` when a diagnostic operation needs correlation
without placing identifiers into free-form text:

```java
project.recordBreadcrumb(
        TelemetryBreadcrumbContext.builder("persistence", "incident opened")
                .correlationId(traceId)
                .incidentId(incidentId)
                .phase("CANONICAL_OUTCOME_UNKNOWN")
                .operation("coop_release")
                .scopeType("COOP_SLOT")
                .failureClass("OUTCOME_UNKNOWN")
                .disposition("SCOPED_QUARANTINE")
                .attribute("recoveryAttempt", 1)
                .build()
);
```

Structured breadcrumbs retain the legacy category/detail fields and add bounded
optional correlation, phase, operation, scope, failure, disposition, and scalar
attribute fields. Older handle implementations safely fall back to the legacy
category/detail call. Consumer mods must use randomized or hashed correlation
values and must not put player names, raw UUIDs, coordinates, tokens, chat, or
inventory contents into these fields.

## Manual Report UI

Consumer mods can open the shared report UI from their own player UI:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

`TelemetryReportOpenRequest` accepts `issue` or `suggestion`.

## Flush Requests

`TelemetryRuntimeApi.requestFlush()` asks the active runtime to flush all pending
projects. `TelemetryProjectHandle.requestFlush()` asks it to flush one project.

Both methods schedule work through the active coordinator; they are not a
guarantee that every queued item was uploaded.
