# Telemetry Coordinator Election Verification

Date: 2026-06-17

## Automated

- `.\mvnw.cmd -pl runtime "-Dtest=TelemetryCoordinatorRegistryTest,EmbeddedTelemetryServiceTest" test`: passed, 10 tests.
- `.\mvnw.cmd -pl standalone -am "-Dtest=TelemetryRuntimeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`: passed, 19 tests.
- `.\mvnw.cmd -pl standalone -am test`: passed, 68 runtime tests and 47 standalone tests.
- `.\mvnw.cmd -pl standalone -am package`: passed, including 68 runtime tests and 47 standalone tests.
- Stale docs scan for old embedded skip/local-only wording: passed.

## Package Inspection

- Standalone jar contains:
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.class`
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.class`
  - `com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class`
  - `com/alechilles/alecstelemetry/AlecsTelemetry.class`
- Installed Tamework jar from `UserData\Mods` contains:
  - `telemetry/project.json`
  - `com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class`
- Installed Tamework descriptor declares:
  - `"projectId": "alecs-tamework"`
  - `"runtimeMode": "embedded"`
- Rebuilt Tamework jar from `alecstamework\target` after installing this branch's runtime locally contains:
  - `telemetry/project.json`
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorBridge.class`
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorRegistry.class`
  - `com/alechilles/alecstelemetry/coordinator/TelemetryCoordinatorService.class`
  - `com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryBootstrap.class`

## Manual Server

Ran an isolated Hytale dedicated server with:

- Rebuilt Tamework jar from `alecstamework\target`, embedding this branch's runtime.
- Temporary standalone telemetry jar with its fallback runtime version bytecode-patched from `0.1.3` to `0.1.2`, so the embedded runtime should win deterministically.
- Working directory `artifacts\telemetry-coordinator-server-check\isolated-server-workdir-command-check-defaultmods`.
- Logs:
  - `artifacts\telemetry-coordinator-server-check\server-command-check-defaultmods.out.log`
  - `artifacts\telemetry-coordinator-server-check\server-command-check-defaultmods.err.log`

Observed runtime election:

```text
[Alec's Telemetry|P] Alec's Telemetry enabled. Active coordinator=embedded:Alechilles:Alec's Tamework!, standaloneOwnsRuntime=false, registered projects=1
```

Observed command availability from the live server console:

```text
[CommandManager] Console executed command: telemetry status
[CommandManager] Console executed command: telemetry status
[CommandManager] Console executed command: telemetry flush alecs-tamework
[CommandManager] Console executed command: telemetry flush alecs-tamework
```

The command manager logs strip the leading slash, so the two `status` lines represent `telemetry status` and `/telemetry status`, and the two `flush` lines represent `telemetry flush alecs-tamework` and `/telemetry flush alecs-tamework`. No telemetry command errors were emitted; stderr only contained the known SLF4J no-binding and native reallocation warnings.
