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
`WorldThread`. With project performance consent, `hot-path-v1` qualifies each
project over the latest five actionable windows. A path qualifies at 20 ms and
2% selected WorldThread share. `PROVISIONAL` requires one 100 ms and 20%
appearance. `REPEATED` requires three qualifying appearances in those five
windows. Empty actionable windows are retained so old candidates can expire.
The service returns at most five active candidates.

The monitor does not queue, upload, or persist Phase 2 profiler evidence.
Ordinary log summary and command-output lines can be included if a user submits
a manual report with current or previous server-log attachments and the
manual-report settings, project descriptor, review, redaction, and clipping
controls allow them. The monitor does not write raw Spark profile data to the
log or a profile file, and does not upload that raw data. It computes the loaded
Spark JAR's SHA-256 in memory for the exact tested-artifact gate and does not
retain that hash. See [Runtime
Overrides](/mod/alecs-telemetry/integration-guides/runtime-overrides) for
settings, bounds, and fail-closed behavior.

With the monitor and project performance consent enabled, Telemetry creates
bounded local summaries of completed passive WorldThread windows. The public
local view and subscription can expose one project's immutable attributed
summary, including owned and downstream Java class/method names, sampled
timing, fingerprints, bounded representative paths, and rolling qualification.
Publication context can include nullable Hytale and project versions, aggregate
player count, nullable Spark public one-minute TPS/MSPT, and non-zero counts for
declared breadcrumb categories from five one-minute buckets.

The local API is not an authorization boundary. Server-side code with API
access can request another registered project by ID. Consumer integrations
control later handling of values they receive; this guide does not promise
that consumer code cannot log, store, or send a snapshot elsewhere.

Phase 2 does not upload or persist profiler summaries, signals, context, raw
Spark profiles, frame trees, player identifiers, or server identifiers. Spark
protobufs, reflection objects, source maps, and raw profile trees are transient.
Spark may retain or upload its own profiles under Spark's separate settings.
Disabling project performance consent makes the local project history,
qualification, signals, and context unavailable and clears them; the global
monitor can continue running. Re-enabling performance consent admits only later
completed actionable windows.

Breadcrumb consent is independent. Counting and publication require current
breadcrumb consent. Disabling breadcrumb consent clears only the five-minute
approved-category buckets. Future performance snapshots remain available with
an empty breadcrumb count map until new accepted breadcrumbs arrive.

Profiler breadcrumb context contains only exact static category names and
counts. It never contains breadcrumb detail text or structured custom fields.
The local profiler API is not an authorization boundary: server-side code with
API access can request another registered project by ID. A consumer, including
a third-party integration, controls what it later logs, stores, or sends.

The `profiler-api-v1` view keeps at most five paths per snapshot, ten snapshots
per project, 500 snapshots globally, 500 retained project-path entries globally,
four listener workers per project, and 32 globally. The profiler index accepts
at most 32 normalized package prefixes per project. Each package-prefix value
is limited to 512 characters before normalization. Longer values are omitted
and reported as truncated. Older elected providers return an unavailable view
without a mixed-version linkage error. Project command replies from `status`,
`top`, `history`, and `signals` are copied to ordinary logs, and a manual
server-log attachment can contain those lines when the existing report settings
and review controls allow it.

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
