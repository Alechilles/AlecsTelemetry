---
title: "Consent And Privacy"
order: 12
published: true
draft: false
---

# Consent And Privacy

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Alec's Telemetry has project-level and category-level consent. Descriptor defaults provide the initial state; server owners can review and change the final state in game.

## Consent Categories

The consent UI can control:

- project enabled state
- crash capture
- error events
- lifecycle events
- performance events
- usage events
- anonymous public stats
- breadcrumbs

Stats are separate from usage events. A server owner can allow anonymous public stats while disabling feature usage telemetry.

## Open Consent

```text
/telemetry consent
```

The first-run notice uses the same consent runtime. A project that has not been reviewed before can appear in the first-run consent UI.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once. Consent defaults live in the same shared descriptor as the portal project key and optional icon.

## Descriptor Defaults

Telemetry opt-in/out options are automatically determined by the categories supported in the project descriptor.

Omitted telemetry categories are unsupported and hidden from consent. A supported category defaults on unless it sets `defaultEnabled: false`. Add only the categories your project actually uses:

- use [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup) for crash capture defaults
- use [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup) for stats-only defaults
- use the specific integration guide for error events, lifecycle events, performance telemetry, usage events, breadcrumbs, or manual reports

For opt-in category behavior, keep the category supported and set `defaultEnabled: false`.

## Stored Settings

Project category choices are stored under the canonical Alec's Telemetry settings root:

```text
<ServerOrSaveRoot>/mods/Alechilles_Alec's Telemetry!/Settings/projects/<project-id>.json
```

Runtime settings and consent state also live under the same canonical root.

The optional Spark hot-path monitor is a separate server-owner tool. It is off
by default. When enabled, it reads completed passive Spark `ASYNC` `EXECUTION`
windows and ranks Java self time from Hytale thread names containing
`WorldThread`. It keeps bounded summaries and history in memory and writes
bounded summary lines to the local server log.

The monitor does not directly queue or upload profiler data. Ordinary log
summary lines can be included if a user submits a manual report with current or
previous server-log attachments and the manual-report settings, project
descriptor, review, redaction, and clipping controls allow them. The monitor
does not write raw Spark profile data to the log or a profile file, and does not
upload that raw data. It computes the loaded Spark JAR's SHA-256 in memory for
the exact tested-artifact gate and does not retain that hash. See [Runtime
Overrides](/mod/alecs-telemetry/integration-guides/runtime-overrides) for
settings, bounds, and fail-closed behavior.

## Privacy Rules For Mod Authors

- Use stable reason codes instead of raw player data.
- Avoid names, UUIDs, tokens, secrets, chat, exact coordinates, and private config values.
- Declare custom detail allowlists in the descriptor.
- Keep manual report fields bounded and understandable.
- Explain your telemetry behavior in your own mod documentation.
- Link users to the canonical Privacy Policy when discussing Alec's web portal behavior.

## Portal Versus Custom Endpoints

Alec's web portal behavior is covered by the Alec's Telemetry privacy policy. If a server owner redirects telemetry to a custom endpoint, that endpoint is controlled by whoever configured it, not by Alec's portal backend.

## Verify

1. Run `/telemetry consent`.
2. Confirm your project appears with the expected defaults.
3. Toggle one category.
4. Run `/telemetry project <project-id>`.
5. Confirm the effective category state matches the consent choice.
