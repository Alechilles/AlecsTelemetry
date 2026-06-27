# Usage Stats

Alec's Telemetry can publish anonymous Hytale mod usage statistics similar to
HStats and bStats.

## Default Stats

The active telemetry runtime emits the first standard `stats` `heartbeat` event
2-5 minutes after startup, then emits roughly every 30 minutes with stable
jitter so servers do not all report at the same instant. Heartbeats are emitted
for each installed, enabled project that has opted into stats, regardless of
whether the winning runtime came from the standalone jar or an embedded copy. The
hosted stats surface derives:

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

The heartbeat interval is intentionally not exposed in runtime settings so
servers cannot accidentally or deliberately report at an unsafe cadence. Hosted
interval hints may temporarily keep older 5-minute clients working during
rollout, but new runtime clients target the 30-minute cadence.

## Consent

Stats are a separate consent category from usage events. A server owner can allow
anonymous public server/player/environment stats while still disabling
feature-usage telemetry.

In `Server/Telemetry/project.json`, opt in with:

```json
{
  "telemetry": {
    "stats": {
      "supported": true
    }
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

Stats are intentionally not customizable. Enabling stats reports the standard
heartbeat/environment fields only. Heartbeats include the current player count
plus optional interval peak and average player counts so lower cadence reporting
does not lose short-lived player peaks.
