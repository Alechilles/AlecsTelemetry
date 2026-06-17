# Usage Stats

Alec's Telemetry can publish anonymous Hytale mod usage statistics similar to
HStats and bStats, but scoped to Hytale projects and Alec's existing project
keys.

## Default Stats

The standalone runtime emits a `stats` `heartbeat` event for each dependency-mode
project every 5 minutes. Embedded telemetry runtimes emit the same heartbeat for
their owning project. The hosted stats surface derives:

- active servers
- active players
- record servers
- record players
- server/player history over 30-minute buckets
- plugin version breakdown
- Hytale build breakdown
- Hytale server version breakdown
- Java version breakdown
- Java runtime version breakdown
- operating system breakdown
- operating system version breakdown
- system architecture breakdown
- CPU core count breakdown
- server location breakdown
- server hosting mode breakdown (`local_client`, `dedicated`, or `unknown`)
- loaded mod breakdowns

`serverHostingMode` is the Hytale replacement for Minecraft-specific online mode
charts. Local client worlds are reported as `local_client` when the Hytale server
is launched with its singleplayer mode; normal external servers are reported as
`dedicated`.

The heartbeat interval is fixed at 5 minutes. It is intentionally not exposed in
runtime settings so servers cannot accidentally or deliberately report at an
unsafe cadence.

## Consent

Stats are a separate consent category from usage events. A server owner can allow
anonymous public server/player/environment stats while still disabling
feature-usage telemetry.

In `telemetry/project.json`, opt in with:

```json
{
  "stats": {
    "enabled": true
  }
}
```

## Privacy

Public stats never expose raw `serverId`, `sessionId`, IP addresses, player
names, player UUIDs, chat, coordinates, world data, secrets, or full config
files.

The hosted service may use `serverId` internally to compute active and record
server counts, but public responses should expose only aggregate counts and
breakdowns.

Stats are intentionally not customizable yet. Enabling stats reports the
standard heartbeat/environment fields only.
