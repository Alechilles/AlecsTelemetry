# Alec's Telemetry & ModStats.io

[![Alec's Telemetry! ModStats](https://www.modstats.io/api/v1/stats/projects/alecs-telemetry/embed/card.svg?layout=live)](https://www.modstats.io/stats/alecs-telemetry)

Alec's Telemetry is the all-in-one telemetry solution for Hytale mods: a runtime that
runs in-game, a [hosted portal](https://telemetry-dev.alecsmods.com/portal) for mod authors, a [public mod stats](https://www.modstats.io/stats) list, and even a [server list](https://www.modstats.io/servers) filterable by mod for players to find servers verified to be running the mods they're looking for.

It started as crash reporting, but it now covers the broader support loop around
a mod: attributed crashes, structured errors, lifecycle and performance events,
anonymous usage stats, manual player issue reports, project access, ingest keys,
and portal-based triage.

[Open Telemetry Portal](https://telemetry-dev.alecsmods.com/portal) | [View Public Stats](https://www.modstats.io/stats/alecs-telemetry) | [Join Discord](https://discord.gg/E8n8RgTTdq)

## The Combined Solution

Alec's Telemetry works best as a pair:

- the runtime discovers telemetry-enabled mods, applies consent and server-owner
  overrides, queues reports locally, and uploads sanitized envelopes
- the portal owns projects, keys, memberships, issues, manual reports, stats,
  public visibility, and operator workflows

<img src="docs/images/telemetry-combined-solution.png" alt="Diagram showing a Hytale mod sending telemetry through the Alec's Telemetry runtime, consent controls, local queues, the hosted portal endpoint, the Telemetry portal, ModStats.io, and mod author triage." width="849" />

Most mods should use the hosted portal. If you need to send data to your own
backend, the same runtime can target custom endpoints instead.

## What It Handles

<img src="docs/images/telemetry-runtime-lanes.png" alt="Diagram showing Alec's Telemetry runtime lanes for crash attribution, structured events, stats heartbeats, player reports, telemetry commands, portal triage, and stats APIs." width="849" />

- Crash and setup failure capture: attributed stack traces, fingerprints,
  breadcrumbs, local queueing, and hosted issue grouping.
- Structured runtime events: explicit errors, lifecycle timings, performance
  measurements, feature usage events, and descriptor-validated Event Context.
- Public usage stats: aggregate active servers, active players, environment
  breakdowns, versions, loaded mods, and public embed cards through ModStats.io.
- Manual player reports: in-game issue and suggestion forms with local receipts,
  optional log attachments, server-owner review controls, and portal follow-up.
- Runtime coordination: standalone and embedded copies participate in version
  election so one active runtime can serve all enabled projects.
- Consent and overrides: descriptor defaults seed the first-run consent UI, while
  server owners keep final control through runtime settings and per-project
  overrides.

## Who It Is For
Alec's Telemetry is meant to benefit _everyone_ in the Hytale modding ecosystem.

For mod authors, Alec's Telemetry turns real-world support signals into a small,
repeatable integration: ship a descriptor, add a hosted project key, and let the
runtime and portal handle the plumbing.

For server owners, it keeps telemetry visible and controllable. Crash capture,
usage events, performance telemetry, stats, breadcrumbs, and reports are separate
categories with runtime-level controls.

For players and communities, it shortens the path from "something broke" to a
real fix, while public stats can show whether a mod is actively used without
exposing raw server or player data.

## Quick Setup

Most hosted integrations only need a descriptor at:

```text
Server/Telemetry/project.json
```

If your `manifest.json` already has the right `Group`, `Name`, and `Main`,
Alec's Telemetry can infer the project id, display name, plugin identifier, and
package prefix.

```json
{
  "ui": {
    "iconTexturePath": "YourMod/Telemetry/YourModConsentIcon.png"
  },
  "hosted": {
    "projectKey": "replace_with_your_public_project_key"
  },
  "stats": {
    "enabled": true
  }
}
```

Hosted `projectKey` values are publishable ingest keys. They are meant to ship
inside the descriptor; admin capabilities stay in the portal, not in the key.

If you want your mod logo in the consent UI, package the texture under
`Common/UI/Custom/...` and set `ui.iconTexturePath` to that custom UI texture
path. Root mod icons such as `icon-256.png` are not used automatically.

## Runtime Options

### Standalone Dependency

Use the standalone Alec's Telemetry mod when you want the normal dependency
model. The runtime discovers enabled project descriptors from installed mods and
coordinates uploads for those projects.

### Embedded Runtime

Use embedded mode when your mod needs to bundle the telemetry bootstrap directly.
Embedded copies still participate in coordinator election, and the latest
compatible runtime can serve all installed enabled projects.

### Custom Endpoint

Use a custom endpoint when you want the runtime but not Alec's hosted service.

```json
{
  "defaults": {
    "destinationMode": "custom"
  },
  "customEndpoint": {
    "url": "https://example.com/api/telemetry/crash",
    "eventUrl": "https://example.com/api/telemetry/event"
  }
}
```

Server owners can also override packaged destination settings at runtime under:

```text
mods/Alechilles_Alec's Telemetry!/Settings/projects/<project-id>.json
```

## Hosted Portal

The hosted portal is the canonical management surface for Alec's Telemetry
projects.

- Sign in with Discord.
- Manage project memberships and publishable project keys.
- Review crash groups, issue-producing events, and individual occurrences.
- Inspect manual player reports and link or promote them into issue workflows.
- View public and portal-only stats dashboards.
- Configure public stats visibility and ModStats.io slugs.

Portal URL:

```text
https://telemetry-dev.alecsmods.com/portal
```

Public stats:

```text
https://www.modstats.io/stats/alecs-telemetry
```

## Runtime API

Mods can stay descriptor-only, but richer integrations can call the runtime API
for explicit events and custom player-report entry points.

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
if (api == null || !api.isEnabled()) {
    return;
}

TelemetryProjectHandle project = api.findProject("example-consumer-mod");
if (project == null || !project.isEnabled()) {
    return;
}

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

The runtime ignores events that are disabled by consent, descriptor defaults,
runtime overrides, sampling, or descriptor allowlists.

## Useful Commands

```text
/telemetry status
/telemetry projects
/telemetry project <project-id>
/telemetry consent
/telemetry report [project-id] [issue|suggestion]
/telemetry reports pending
/telemetry flush [project-id]
/telemetry test <project-id> [detail]
```

The root command permission is `telemetry.command.telemetry`; subcommands use the
same stable `telemetry.command.telemetry.*` prefix.

## Privacy Model

- Server owners control telemetry categories through consent and runtime
  overrides.
- Public stats expose aggregate counts and breakdowns, not raw server ids,
  session ids, IP addresses, player names, player UUIDs, chat, coordinates,
  secrets, or full config files.
- Custom Event Context fields must be descriptor-declared and type-checked before
  upload.
- Manual reports can require local operator review before upload, and optional
  attachments are controlled by runtime settings.

## Docs and Examples

- [Project descriptor reference](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/project-descriptor.md)
- [Embedded mode guide](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/embedded-mode.md)
- [Runtime API guide](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/runtime-api.md)
- [Command reference](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/command-reference.md)
- [Runtime overrides](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/runtime-overrides.md)
- [Manual player reports](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/manual-player-reports.md)
- [Usage stats guide](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/usage-stats.md)
- [Hosted key operations](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/hosted-key-operations.md)
- [Hosted ingest contract](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/hosted-ingest-contract.md)
- [Example consumer mod](https://github.com/Alechilles/AlecsTelemetry/tree/main/examples/ExampleConsumerMod)
- [Embedded consumer mod](https://github.com/Alechilles/AlecsTelemetry/tree/main/examples/EmbeddedConsumerMod)

## License / Support

This project is source-available under the Alec's Telemetry Runtime License.

- [License](https://github.com/Alechilles/AlecsTelemetry/blob/main/LICENSE)
- [License FAQ](https://github.com/Alechilles/AlecsTelemetry/blob/main/LICENSE-FAQ.md)
- [Commercial License](https://github.com/Alechilles/AlecsTelemetry/blob/main/COMMERCIAL-LICENSE.md)
- [Discord support and issue reporting](https://discord.gg/E8n8RgTTdq)
