# Alec's Telemetry v1.0.2

## Summary
This hotfix improves server-owner operations by allowing text-based telemetry
admin commands from the server console and makes telemetry delivery retries less
aggressive when an ingest endpoint is unavailable or throttling.

## Changed
- `/telemetry status`, `projects`, `project`, `flush`, `test`, `reports`, and
  `server verify` can now be run directly from the server console.
- `/telemetry consent` and `/telemetry report` remain player-only because they
  open in-game UI.
- The standalone manifest now points at the production Telemetry website.
- Embedded runtime setup docs now reference `alecstelemetry-runtime:1.0.2`.
- The privacy policy now documents portal project and server-profile deletion
  behavior.

## Fixes
- Automatic telemetry flushes now respect an upload retry cooldown after failed
  delivery attempts, reducing repeated requests to unavailable or throttling
  ingest endpoints.
- Manual, shutdown, and server-verification flushes still bypass retry cooldowns
  so operator actions can run immediately.

## Compatibility
- Hytale: 0.5.x

## Files
- Alec's Telemetry v1.0.2.jar
- alecstelemetry-runtime-1.0.2.jar
