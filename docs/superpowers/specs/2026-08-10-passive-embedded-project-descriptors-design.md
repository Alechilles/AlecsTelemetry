# Passive Embedded Telemetry Project Descriptors

**Status:** Approved design

**Date:** 2026-08-10

**Scope:** Alec's Telemetry runtime and standalone distribution

## Summary

Alec's Telemetry will discover logical telemetry projects carried as data-only
resources inside installed mod archives. This lets an embeddable library such as
Creditor participate in anonymous project stats without compiling against,
shading, or bootstrapping Alec's Telemetry itself.

Presence of a valid descriptor at the namespaced resource path is sufficient to
consider that logical project installed. Whichever standalone or embedded
Telemetry runtime wins process-wide coordination discovers the descriptor,
shows one independently consented project row, and emits the standard aggregate
stats heartbeat when consent and destination gates permit it.

Libraries that want richer telemetry continue to use explicit
`EmbeddedTelemetryBootstrap.contribute(...)` registration. Passive discovery
never loads executable provider code and exposes only stats. Explicit
registration for the same logical project upgrades the live entry with the
additional descriptor-supported capabilities.

## Relationship to the Existing Embedded-Contribution Design

This design extends
`2026-08-06-generic-embedded-telemetry-contributions-design.md` and supersedes
only its decision not to enumerate resources under
`META-INF/alecs-telemetry/projects/`.

The earlier restriction prevented accidental activation of dormant transitive
libraries. That tradeoff is intentionally changed here: presence in a final
installed mod archive is now the installation signal. The containing mod author
does not need to add code or a separate index. Existing rules for independent
project identity, consent, destination ownership, deterministic election,
descriptor validation, event filtering, and process-wide runtime coordination
remain in force.

## Goals

- Let an embeddable library opt into standard project stats by shipping only a
  descriptor.
- Work when the library is shaded into another mod and has no independently
  loaded Hytale plugin identity.
- Let any active standalone or embedded Telemetry runtime service the project.
- Deduplicate the same library descriptor found in multiple host mods.
- Preserve independent consent and portal attribution for the logical library
  project.
- Permit richer telemetry through later, explicit executable registration.
- Notify operators without silently broadening previously reviewed consent.

## Non-goals

- Proving that a shaded library's code path was executed. Presence is the
  accepted installation signal.
- Loading or instantiating a provider class named by a descriptor.
- Reporting non-stats categories from a passive descriptor.
- Making Telemetry mandatory for the library or host mod.
- Recursively searching arbitrary archive paths for telemetry metadata.
- Merging conflicting descriptor documents field by field.

## Resource and Descriptor Contract

Data-only projects ship one uniquely named resource:

```text
META-INF/alecs-telemetry/projects/<stable-project-id>.json
```

Telemetry enumerates direct `.json` entries beneath that exact directory in
each installed top-level mod JAR or folder. Nested directories and conventional
paths belonging to the physical host are not part of passive discovery.

The descriptor uses the existing project schema with one new top-level field,
`projectVersion`:

```json
{
  "schemaVersion": 1,
  "projectId": "creditor",
  "projectVersion": "1.4.0",
  "displayName": "Creditor",
  "ownerPluginIdentifiers": ["Author:Creditor"],
  "hosted": {
    "projectKey": "public_project_key"
  },
  "telemetry": {
    "stats": {
      "supported": true,
      "allowedEvents": ["heartbeat"]
    }
  }
}
```

For passive discovery, the following values are required:

- `projectId`;
- `projectVersion`, parseable as a Hytale semantic version;
- `displayName`;
- at least one nonblank `ownerPluginIdentifiers` entry; and
- a stats capability that permits the standard `heartbeat` event.

The first owner identifier is the canonical logical identity. Later entries are
aliases. A library build should stamp `projectVersion` into its descriptor so
the physical host's unrelated manifest version is never used as the library
version.

Existing namespaced descriptors without `projectVersion` remain valid for the
explicit contribution API, where registration already supplies logical identity
and version. They do not activate passively.

The descriptor may declare other telemetry categories for reuse by an active
integration, but passive discovery masks every category except stats. A hosted
project still needs its publishable project key and normal destination
validation before a heartbeat can be delivered.

## Discovery and Catalog Flow

The elected Telemetry coordinator performs passive discovery alongside existing
conventional installed-mod discovery:

1. Enumerate each installed top-level mod archive or directory from the normal
   mod discovery roots.
2. Enumerate direct descriptor files under the namespaced project directory.
3. Parse and validate every candidate independently.
4. Record the containing mod identifier, version, and source path only as
   physical-host provenance.
5. Add valid passive candidates to the live project catalog and project
   election.
6. Skip invalid candidates with bounded diagnostics; never fail the host mod or
   the coordinator because one descriptor is malformed.

Every provider capable of becoming coordinator can reconstruct this catalog.
Provider handoff therefore does not depend on the passive library running code.

## Identity, Election, and Deduplication

Logical projects are keyed by normalized `projectId`. Multiple hosts containing
the same library create candidates for one project, not additional consent rows
or heartbeat counts.

Election follows the existing embedded-contribution ordering:

1. highest valid logical semantic version;
2. existing origin and host tie-breakers;
3. source path; and
4. canonical descriptor hash.

An exact same-project, same-version, same-owner, same-hash copy is deduplicated.
Same-version descriptor drift elects one complete descriptor deterministically
and emits a warning; descriptors are never field-merged. Existing owner-conflict
and destination-safety rules continue to apply.

The winning passive candidate contributes one project to consent and one
eligible entry to aggregate stats. It does not receive a write token for richer
events.

## Optional Active Integration

A library may later add executable telemetry integration by using the existing
`EmbeddedTelemetryBootstrap.contribute(...)` lifecycle:

- registration occurs explicitly when the library functionality initializes;
- it uses the same descriptor resource, project ID, logical version, and an
  owner identifier declared by the descriptor;
- it obtains a project-scoped handle;
- all writes remain subject to active-token, descriptor, consent, allowlist,
  sampling, redaction, and destination checks; and
- it closes the contribution with the library or host lifecycle.

When explicit registration matches the elected passive project snapshot, the
live project entry becomes active and exposes its additional supported
categories. There remains one project row, one consent state, one destination,
and one elected logical version.

The descriptor never names a class for Telemetry to instantiate. This prevents
data-only discovery from causing class loading or turning resource presence into
executable behavior.

## Consent and Capability Expansion

A newly discovered passive project appears in the scrollable consent list with
only project enablement and Stats available. It is never omitted because of a
display cap.

Consent state records the supported category set last reviewed for each logical
project. When active registration or a new descriptor adds categories:

1. compare the current supported set with the last reviewed set;
2. leave every newly added category disabled;
3. mark the project as needing capability review;
4. notify eligible operators; and
5. record the new reviewed set only when an operator saves the consent page.

Removed categories are disabled automatically and do not require a notification.
A version change without a category expansion does not trigger this specific
expansion alert; existing version-review behavior remains unchanged.

The chat notice names the project and newly available categories, for example:

> Creditor added telemetry options: Errors and Usage. These remain disabled
> until reviewed. Run `/telemetry consent`.

The notice is sent only to players with `telemetry.command.telemetry` or the
local singleplayer owner. Each eligible operator sees it at most once per
project capability fingerprint. Notifying each operator, rather than consuming
one server-wide notice, prevents an unavailable administrator from hiding the
review request from other administrators.

## Stats Behavior

Passive projects use only the existing aggregate `heartbeat` stats event. They
do not gain arbitrary stats event production merely by declaring more names in
the descriptor.

A heartbeat is eligible only when:

- the candidate is the elected project winner;
- the project and Stats category are enabled by consent;
- the descriptor supports Stats and allows `heartbeat`; and
- a valid destination resolves.

The coordinator emits at most one logical project entry per heartbeat interval,
regardless of how many physical host mods carry the descriptor. If no Telemetry
runtime is present, the descriptor has no effect and the library continues
normally.

## Failure and Compatibility Behavior

| Condition | Result |
|---|---|
| No standalone or embedded Telemetry runtime | Descriptor is inert; host and library continue. |
| Missing or invalid `projectVersion` | Candidate is not passively activated; explicit legacy registration remains possible. |
| Malformed descriptor | Skip it, emit bounded diagnostics, and continue discovery. |
| Duplicate identical copies | Deduplicate to one logical project. |
| Different versions in different hosts | Highest semantic version wins. |
| Same-version descriptor drift | Elect one whole snapshot deterministically and warn. |
| Passive descriptor declares richer categories | Mask them until matching explicit registration occurs. |
| New active categories appear after review | Keep additions off and notify eligible operators. |
| Provider handoff | Replacement coordinator rescans and reconstructs passive projects. |

Conventional `Server/Telemetry/project.json` discovery and existing explicit
contribution APIs remain source- and behavior-compatible.

## Implementation Surface

Implementation should remain focused on these areas:

- descriptor parsing and the optional `projectVersion` field;
- namespaced descriptor enumeration for installed JARs and directories;
- passive candidate representation and integration with existing election;
- passive Stats-only capability masking;
- matching passive and active project registration;
- persisted reviewed-capability fingerprints and expansion detection;
- operator notification text and notice deduplication; and
- documentation for descriptor-only and active author flows.

The canonical privacy policy must be updated in the implementation change to
describe automatic discovery of shaded descriptors, physical-host provenance,
passive stats heartbeats, and capability-expansion consent behavior. This design
document alone does not change runtime collection behavior, so no policy wording
changes are made in this specification commit.

## Validation Scenarios

Focused production-behavior tests should cover these regressions:

1. A host archive containing a valid Creditor-style descriptor produces one
   Stats-only project registration without executing contributor code.
2. Two host archives containing the same descriptor produce one logical project
   and one heartbeat project entry.
3. Different embedded versions elect the highest semantic version.
4. A matching explicit contribution upgrades the same row rather than creating
   a duplicate and permits descriptor-approved active events.
5. An active capability expansion leaves new categories disabled and produces a
   one-time operator review notice.
6. Provider handoff reconstructs passive projects and preserves consent.
7. Malformed or incomplete passive descriptors do not prevent valid projects or
   host mods from operating.

Tests must invoke the discovery, catalog, consent, or heartbeat behavior and
assert observable results. Do not add source-shape, resource-presence, or exact
archive-inventory tests.
