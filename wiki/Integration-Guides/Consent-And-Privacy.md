---
title: "Consent And Privacy"
order: 12
published: true
draft: false
---

# Consent And Privacy

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Beacon has project-level and category-level consent. Descriptor defaults provide the initial state; server owners can review and change the final state in game.

## Consent Categories

The consent UI can control:

- project enabled state
- crash capture
- error events
- automatic diagnostics (`Diag`)
- lifecycle events
- performance events
- usage events
- anonymous public stats
- breadcrumbs

Stats are separate from usage events. A server owner can allow anonymous public stats while disabling feature usage telemetry.

## Open Consent

```text
/beacon consent
```

The first-run notice uses the same consent runtime. A project that has not been reviewed before can appear in the first-run consent UI.

## Before This Page

Complete [Portal First Setup](/mod/beacon/portal-first-setup) once. Consent defaults live in the same shared descriptor as the portal project key and optional icon.

## Descriptor Defaults

Telemetry opt-in/out options are automatically determined by the categories supported in the project descriptor.

Omitted telemetry categories are unsupported and hidden from consent. A
supported category defaults on unless it sets `defaultEnabled: false`, except
Diagnostics, which is off unless `defaultEnabled: true` is explicit. Add only
the categories your project actually uses:

- use [Quick Crash Setup](/mod/beacon/quick-crash-setup) for crash capture defaults
- use [Quick Stats Setup](/mod/beacon/quick-stats-setup) for stats-only defaults
- use the specific integration guide for error events, lifecycle events, performance telemetry, usage events, breadcrumbs, manual reports, or diagnostic bundles

For opt-in category behavior, keep the category supported and set `defaultEnabled: false`.

Diagnostics uses the descriptor key `diagnostics` and appears as `Diag` in the
consent UI. Its tooltip is "Automatic diagnostic bundles with technical metadata
and optional redacted attachments." Diagnostics is independent from Error
events. A fresh or unreviewed project follows the descriptor default. A project
that already completed a consent review keeps newly added Diagnostics disabled
until an operator runs `/beacon consent` and selects Save and Close. This also
applies to legacy reviewed records without a supported-category snapshot.

## Stored Settings

Project category choices are stored under the canonical Beacon settings root:

```text
<ServerOrSaveRoot>/mods/Alechilles_Beacon/Settings/projects/<project-id>.json
```

Runtime settings and consent state also live under the same canonical root.

## Privacy Rules For Mod Authors

- Use stable reason codes instead of raw player data.
- Avoid names, UUIDs, tokens, secrets, chat, exact coordinates, and private config values.
- Declare custom detail allowlists in the descriptor.
- Keep manual report fields bounded and understandable.
- Explain your telemetry behavior in your own mod documentation.
- Link users to the canonical Privacy Policy when discussing Beacon web portal behavior.

## Portal Versus Custom Endpoints

Beacon web portal behavior is covered by the Beacon privacy policy. If a server owner redirects telemetry to a custom endpoint, that endpoint is controlled by whoever configured it, not by Beacon.

## Verify

1. Run `/beacon consent`.
2. Confirm your project appears with the expected defaults.
3. Toggle one category.
4. Run `/beacon project <project-id>`.
5. Confirm the effective category state matches the consent choice.
