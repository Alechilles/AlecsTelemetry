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
elected logical project identity and logical plugin version. Passive namespaced
descriptor projects use the same aggregate heartbeat path and are eligible when
Stats consent and destination gates pass. Invalid, protocol-ineligible, or
retired candidates are not in the active heartbeat snapshot. The hosted stats
surface derives:

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

## Descriptor-only embedded projects

An embeddable library can ship only a direct descriptor resource under:

```text
META-INF/alecs-telemetry/projects/<stable-project-id>.json
```

Presence in the final host JAR is the installation signal. The descriptor must
declare its logical `projectId`, build-stamped `projectVersion`, display name,
owner identifier, and Stats `heartbeat` allowlist. No Alec's Telemetry dependency
or Java initialization is required in the library; a standalone or embedded
Telemetry runtime must be present to discover the resource. Without one, the
descriptor is inert.

Passive discovery exposes only Stats, even if the descriptor includes richer
categories for a future active integration. A minimal passive Stats declaration
is:

```json
{
  "telemetry": {
    "stats": {
      "supported": true,
      "allowedEvents": ["heartbeat"]
    }
  }
}
```

If multiple host mods contain the
same logical project, election deduplicates them into one consent row and one
heartbeat project. It elects the highest logical semantic version, then uses
source kind and deterministic host/path/hash tie-breakers; descriptors are not
merged.

To report richer categories, the library must explicitly register
`EmbeddedTelemetryBootstrap.contribute(...)` with the same descriptor, logical
ID/version, and declared owner. A matching contribution upgrades that existing
logical project rather than adding another heartbeat project. When a persisted
supported-category snapshot exists for the previously reviewed logical project,
newly exposed categories remain disabled until an operator reviews them through
`/telemetry consent`; eligible operators are notified. Legacy reviewed records
without that snapshot remain honored and cannot be compared retrospectively.

Passive descriptors may select either the hosted destination or an author-
selected custom endpoint. A custom destination receives the same standard
Stats-only heartbeat metadata, while its endpoint operator—not Alec's hosted
platform—controls the data, security, and retention.

## Heartbeat Eligibility And Retirement

Registration alone emits no stats. For a passive or contributed project to emit a
heartbeat, its candidate must be the current per-project winner, the project and
Stats category must be enabled by consent, the descriptor must allow
`heartbeat`, and the project's destination must resolve. The coordinator checks
these gates at emission time, so changing consent or destination immediately
removes the project from later heartbeat snapshots. Multiple physical hosts
carrying the same descriptor still produce one logical heartbeat entry.

When an active contribution retires while its elected passive Stats-only base is
still present, that base resumes/continues as the logical project's consent and
heartbeat entry. Only when no passive base remains does the logical project leave
new consent and heartbeat snapshots. Retirement does not delete its local crash,
event, or report queue; the coordinator retains the project registration needed
to replay queued data, and normal retry, destination, and retention rules still
apply.

Heartbeats are grouped by their resolved event destination, so projects routed
to different endpoints are never combined into one upload. Anchored contributions
use hosted destination mode in the 1.1.0 MVP. A same-ID contribution winner is
not automatically replaced by an unrelated candidate after retirement; restart
is required before another candidate can emit.

## Consent

Stats are a separate consent category from usage events. A server owner can allow
anonymous public server/player/environment stats while still disabling
feature-usage telemetry.

In a conventional host descriptor (`Server/Telemetry/project.json`) or a
passive namespaced descriptor, opt in with:

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
