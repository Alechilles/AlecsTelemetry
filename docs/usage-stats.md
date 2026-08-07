# Usage Stats

Alec's Telemetry can publish anonymous Hytale mod usage statistics similar to
HStats and bStats.

## Default Stats

The active telemetry runtime emits the first standard `stats` `heartbeat` event
2-5 minutes after startup, then emits roughly every 30 minutes with stable
jitter so servers do not all report at the same instant. Heartbeats are emitted
for each project in the active coordinator's live catalog that is enabled and
has opted into stats, regardless of whether the winning runtime came from the
standalone jar or an embedded copy. Anchored embedded contributions use their
elected logical project identity and logical plugin version. A passive, invalid,
protocol-ineligible, or retired contribution is not in the active heartbeat
snapshot. The hosted stats surface derives:

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

## Heartbeat Eligibility And Retirement

Registration alone emits no stats. For a contributed project to emit a heartbeat,
its candidate must be the current per-project winner, the project and stats
category must be enabled by consent, the descriptor must allow `heartbeat`, and
the project's hosted or custom destination must resolve. The coordinator checks
these gates at emission time, so changing consent or destination immediately
removes the project from later heartbeat snapshots.

When a contribution retires, the project disappears from new consent and
heartbeat snapshots without deleting its local crash, event, or report queue.
The coordinator retains the project registration needed to replay queued data;
normal retry, destination, and retention rules still apply.

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

The existing runtime metadata contract still applies to contributed projects:
loaded mod identifiers and versions may be included in heartbeat/environment
payloads. Physical host identifiers, source paths, and descriptor hashes used for
local contribution election are not added as new public stats fields.
