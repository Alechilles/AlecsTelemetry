# Alec's Telemetry

Alec's Telemetry is a standalone crash telemetry runtime for Hytale mods and the
easiest way to connect a mod to Alec's hosted telemetry platform.

For modders, it turns crash reporting into a small integration task: install the
dependency, ship `telemetry/project.json`, and route real-world crashes into the
hosted portal and Discord workflows.

For players and server communities, it means faster fixes, less guesswork, and
fewer situations where someone has to manually gather logs just to explain what
broke.

[Open Telemetry Portal](https://telemetry.alecsmods.com/portal) | [Join Discord](https://discord.gg/E8n8RgTTdq) | [Runtime Source + Docs](https://github.com/Alechilles/AlecsTelemetry) 

## What Alec's Telemetry Is

- A standalone runtime mod for crash telemetry in Hytale mods.
- Designed around `dependency` mode as the default integration path.
- Also supports `embedded` mode for advanced integrations that want to bundle the
  telemetry bootstrap directly.
- Captures attributed crashes and startup/setup failures, queues reports locally,
  and flushes them to the configured destination.
- Emits anonymous Hytale usage statistics for active servers, active players,
  environment breakdowns, and descriptor-validated custom charts.
- Uses your mod metadata as fallback where possible, so many mods only need one
  small descriptor file to get started.

## Why Modders Use It

- The main integration path is simple: install Alec's Telemetry and ship
  `telemetry/project.json`.
- If your `manifest.json` already has the right `Group`, `Name`, and `Main`,
  Alec's Telemetry can infer most of the rest.
- Hosted `projectKey` values are publishable ingest keys, so normal hosted setup
  does not depend on server owners managing secrets.
- The hosted platform pairs portal-based crash triage with Discord-based access
  and routing workflows.
- Project keys, project memberships, and project management live in the hosted
  platform instead of being spread across ad-hoc scripts and manual edits.
- Most mods do not need custom Java code for the default hosted flow.

## Why Players Benefit

- Mod authors can see recurring crash issues faster instead of waiting on manual
  bug reports with incomplete logs.
- Real-world failures are easier to group and investigate, which shortens the
  path from "something broke" to "here is the actual fix."
- Server communities spend less time reproducing the same crash by hand just to
  get useful debugging context.
- Support conversations can move faster because the modder already has structured
  telemetry instead of starting from guesswork.

## What Integration Looks Like

1. Install `Alec's Telemetry` alongside your mod.
2. Add a small `telemetry/project.json` file to your mod project.
3. Put in the hosted `projectKey` for your project.
4. Package and ship your mod with that descriptor included.

Recommended default: `runtimeMode: "dependency"`.

Advanced alternatives: `embedded` mode and custom endpoints.

## Minimal Setup

If your mod manifest already has a correct `Group`, `Name`, and `Main`, Alec's
Telemetry can infer:

- `projectId`
- `displayName`
- `ownerPluginIdentifiers`
- `packagePrefixes`

That means many mods only need destination settings plus a hosted key.

Hosted `projectKey` values are publishable ingest keys. They are meant to be
shipped in the descriptor, not treated like hidden operator secrets.

```json
{
  "runtimeMode": "dependency",
  "ui": {
    "iconTexturePath": "YourMod/Telemetry/YourModConsentIcon.png"
  },
  "hosted": {
    "projectKey": "replace_with_your_public_project_key"
  }
}
```

Place the file at:

```text
telemetry/project.json
```

Then package it with your mod.

If you want your mod logo in the consent UI, package the texture under
`Common/UI/Custom/...` and set `ui.iconTexturePath` to that custom UI texture
path. Root mod icons such as `icon-256.png` are not used automatically.

## Hosted Platform Highlights

- Sign in to the portal with Discord and manage project access through
  memberships.
- Manage project keys and related project settings through the hosted platform.
- Review recurring crash issues in the portal instead of starting from raw logs.
- Use Explore workflows when you need to inspect individual telemetry
  occurrences more closely.
- Keep Discord in the loop through the hosted stack's Discord-based access and
  routing workflows.

Portal URL: `https://telemetry.alecsmods.com/portal`

## Advanced Options

Need something more custom than the default hosted dependency-mode flow?

- Use `runtimeMode: "embedded"` if you want to bundle telemetry bootstrap logic
  directly into your mod.
- Use a custom endpoint if you want reports to go somewhere other than Alec's
  hosted service.
- Server owners can override packaged destination settings at runtime through
  `Settings/projects/<project-id>.json`.
- The optional runtime API is available for richer breadcrumbs, explicit
  lifecycle forwarding, and non-crash `error`, `lifecycle`, `performance`, and
  `usage` events with typed `TelemetryEventContext` fields.
- Stats are a separate consent category from usage. Runtime heartbeats use
  `eventType: "stats"` and are controlled by the project descriptor's
  `stats.enabled` toggle.
- Admin commands are available for inspection and manual testing:
  `/telemetry status`, `/telemetry projects`, `/telemetry project <project-id>`,
  `/telemetry flush [project-id]`, `/telemetry test <project-id> [detail]`
- The root command permission is `telemetry.command.telemetry`; subcommands use
  the same stable `telemetry.command.telemetry.*` prefix.

Minimal custom-endpoint example:

```json
{
  "runtimeMode": "dependency",
  "defaults": {
    "destinationMode": "custom"
  },
  "customEndpoint": {
    "url": "https://example.com/api/telemetry/crash"
  }
}
```

## Docs and Examples

- [Project descriptor reference](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/project-descriptor.md)
- [Embedded mode guide](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/embedded-mode.md)
- [Runtime overrides](https://github.com/Alechilles/AlecsTelemetry/blob/main/docs/runtime-overrides.md)
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
