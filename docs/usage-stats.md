# Usage Stats

Alec's Telemetry can publish anonymous Hytale mod usage statistics similar to
HStats and bStats, but scoped to Hytale projects and Alec's existing project
keys.

## Default Stats

The standalone runtime emits a `stats` `heartbeat` event for each dependency-mode
project every 30 minutes by default. The hosted stats surface derives:

- active servers
- active players
- record servers
- record players
- plugin version breakdown
- Hytale build breakdown
- Java version breakdown
- operating system breakdown
- system architecture breakdown
- loaded mod breakdowns

The heartbeat interval is controlled by `statsHeartbeatIntervalSeconds` in the
runtime settings file. The default is `1800`.

## Consent

Stats are a separate consent category from usage events. A server owner can allow
anonymous public server/player/environment stats while still disabling
feature-usage telemetry.

In `telemetry/project.json`, opt in with:

```json
{
  "stats": {
    "enabled": true,
    "allowedEvents": ["heartbeat", "chart_sample"]
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

## Custom Charts

Mods can emit custom chart values through the runtime API:

```java
TelemetryProjectHandle handle = TelemetryRuntimeLocator.get()
        .findProject("example-mod");

if (handle != null) {
    handle.recordSimpleStatChart("language", "English");
    handle.recordNumericStatChart("active_npcs", 12);
}
```

Custom chart samples are sent as:

- `eventType: "stats"`
- `eventName: "chart_sample"`
- `details.chartId`
- `details.value`

Each public custom chart must also be declared in hosted project config before
the public API exposes it.
