# Telemetry Coordinator Election Verification

Date: 2026-06-17

## Automated

- `.\mvnw.cmd -pl runtime test`: passed, 68 tests.
- `.\mvnw.cmd -pl standalone -am "-Dtest=TelemetryRuntimeServiceTest,TelemetryCommandRootTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`: passed, 20 tests.
- `.\mvnw.cmd -pl standalone -am test`: failed only on the two pre-existing standalone UI asset tests:
  - `TelemetryConsentAssetPackTest.consentPageBindsHeaderToggleButtonsToDeterministicServerActions`
  - `TelemetryReportAssetPackTest.reportPageBuffersTextInputChangesAndSanitizesSubmitFallbackSelectors`
- `.\mvnw.cmd -pl standalone -am -DskipTests package`: passed.
- Stale docs scan for old embedded skip/local-only wording: passed.

## Package Inspection

- Standalone jar contains:
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.class`
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.class`
  - `com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class`
  - `com/alechilles/alecstelemetry/AlecsTelemetry.class`
- Installed Tamework jar contains:
  - `telemetry/project.json`
  - `com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class`
- Installed Tamework descriptor declares:
  - `"projectId": "alecs-tamework"`
  - `"runtimeMode": "embedded"`

## Manual Server

Not run in this pass. Expected manual signal after installing a standalone build with a lower runtime version than an embedded runtime candidate:

```text
Alec's Telemetry enabled. Active coordinator=embedded:<provider>, standaloneOwnsRuntime=false
```

Expected `/telemetry status` behavior:

- Command remains available if standalone is installed.
- Status names the active coordinator provider.
- Embedded projects appear in registered telemetry projects.
- Flush requests route to the active coordinator.
