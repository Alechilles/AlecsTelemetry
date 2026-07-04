# Alec's Telemetry v1.0.3

## Summary
This hotfix restores `/telemetry test` as the crash-report exercise path for
standalone and embedded runtime installs.

## Changed
- Embedded runtime setup docs now reference `alecstelemetry-runtime:1.0.3`.

## Fixes
- `/telemetry test <project-id> [detail]` now queues a crash-style test report
  when crash reporting is enabled for the project.

## Compatibility
- Hytale: 0.5.x

## Files
- Alec's Telemetry v1.0.3.jar
- alecstelemetry-runtime-1.0.3.jar
