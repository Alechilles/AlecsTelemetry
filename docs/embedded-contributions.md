# Embedded Contributions

Embedded mode can expose more than one logical telemetry project from one
physical Hytale mod. Use the anchored contribution API when a component library,
feature module, or other embedded package owns its own project identity. The
physical host gets one shared runtime provider, while each logical project keeps
its own descriptor, consent, destination, attribution, and queue.

This guide describes the Beacon contribution ABI (ABI 1) and
coordinator protocol 3.

## Descriptor-only first, active integration when needed

An embeddable library does not need to bundle or initialize Beacon to
identify its project. Ship a direct descriptor resource in the final host JAR
or host mod folder:

```text
META-INF/beacon/projects/<stable-project-id>.json
```

Presence of a valid resource is the accepted installation signal. Any
standalone or embedded Beacon runtime can discover it; if no runtime
is installed, the resource is inert and the host remains operational. Passive
discovery provides one independently consented aggregate Stats `heartbeat`
project and never executes contributor code. It masks every richer category,
even if the descriptor declares those categories for a later active integration.

Use the passive descriptor shape below. `projectVersion` is the logical
library version and should be build-stamped; the physical host's manifest
version is used only as local provenance. This example uses the Beacon hosted
destination and its publishable project key. Passive descriptors may instead
select `defaults.destinationMode: "custom"` with `customEndpoint.url`; in that
case the standard Stats-only heartbeat goes to the author-selected endpoint,
whose operator controls the received data, security, and retention.

```json
{
  "schemaVersion": 1,
  "projectId": "my-contributor",
  "projectVersion": "1.4.0",
  "displayName": "My Contributor",
  "ownerPluginIdentifiers": ["Example:My Contributor"],
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "stats": {
      "supported": true,
      "allowedEvents": ["heartbeat"]
    }
  }
}
```

When the same descriptor is shaded into multiple host mods, logical project
election keeps one complete winner by logical semantic version, source kind, and
deterministic host/path/hash tie-breakers. Consent and the heartbeat catalog
therefore contain one project, not one row per host. Invalid descriptors are
skipped with bounded diagnostics and do not block the host.

If richer telemetry is valuable, add the explicit `contribute(...)` registration
below. It must use the same descriptor resource, `projectId`, logical version,
and a declared owner so it can upgrade the passive project. A matching active
registration reuses the existing consent row and destination. When a persisted
supported-category snapshot exists for the previously reviewed logical project,
newly exposed categories stay disabled until an eligible operator reviews them
through `/beacon consent`; eligible operators are notified. Legacy reviewed
records without that snapshot remain honored for older categories and cannot be
compared retrospectively. If Diagnostics is currently supported, it is treated
as newly supported, remains disabled, and requires Save and Close.

## Register a logical project

Put the contribution descriptor in the contributor's own jar and anchor the
resource lookup to a class owned by that contributor:

```java
import com.alechilles.beacon.embedded.EmbeddedTelemetryBootstrap;
import com.alechilles.beacon.embedded.EmbeddedTelemetryService;
import com.alechilles.beacon.embedded.TelemetryProjectContribution;

TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
        .descriptorResource(
                MyContributorTelemetry.class,
                "META-INF/beacon/projects/my-contributor.json"
        )
        .logicalPluginIdentifier("Example:My Contributor")
        .logicalPluginVersion(MyContributorVersion.current())
        .build();

EmbeddedTelemetryService telemetry =
        EmbeddedTelemetryBootstrap.contribute(hostPlugin, contribution);
```

Call `telemetry.start()` after the host plugin has completed its normal setup,
and call `telemetry.shutdown()` from the host shutdown path. The service exposes
the same recording, crash-capture, manual-report, consent, diagnostics, and
flush operations as the conventional embedded service. Calls made before
`start()` are bounded and replayed only by the elected candidate; passive or
retired candidates do not drain those calls.

`TelemetryProjectContribution` is immutable. Its required values are:

- `resourceAnchor`: a contributor class whose classloader can read the descriptor
- `descriptorResource`: a nonblank classloader resource path
- `logicalPluginIdentifier`: the stable owner identifier used by the descriptor
- `logicalPluginVersion`: a valid Hytale semantic version used for election and
  envelope attribution

### 1.1.0 MVP boundaries

Anchored contributions are hosted-only in the 1.1.0 MVP. Set
`defaults.destinationMode` to `hosted` and provide the descriptor's hosted
endpoint/project key. The conventional `bootstrap(plugin)` path continues to
support custom destinations; a custom destination in an anchored contribution
is rejected as a disabled contribution before it can register.

An established active contribution is not automatically failed over to an
unrelated candidate. Unregistering the winner fences its token and leaves any
already registered fallback passive, so a second copy cannot begin writing
while the active catalog is changing. Restart the server after retirement when
an unrelated replacement is required. A matching explicit contribution may
still upgrade its elected passive base as described below. This boundary also
keeps queued envelopes on the destination that created them.

Before `start()`, setup breadcrumbs, lifecycle events, setup-failure captures,
and diagnostic bundles are accepted for bounded replay. Manual reports, flushes, and other
operations return a bounded `not_started` rejection and are never drained after
startup.

The builder accepts either `.descriptorResource(anchor, path)` or separate
`.resourceAnchor(anchor)` and `.descriptorResource(path)` calls. One leading `/`
is removed from the resource path before lookup.

## Use a unique descriptor resource

The anchored API reads the descriptor from `resourceAnchor.getClassLoader()`;
it does not search the host plugin's conventional `Server/Beacon/project.json`.
That conventional path belongs to the physical host mod. The coordinator also
scans direct JSON entries under the namespaced directory for passive
descriptor-only projects, so give every logical contributor a stable unique path
to prevent unrelated resources from colliding. A recommended layout is:

```text
META-INF/beacon/projects/<stable-contributor-name>.json
```

The descriptor must explicitly declare `projectId` and `displayName`, and its
`ownerPluginIdentifiers` must include the exact logical identifier supplied to
the builder (case-insensitive). The logical version supplied to the builder
must parse as a Hytale semantic version. For a passive upgrade, the descriptor
must also declare the same `projectVersion` and it must exactly match the
builder's logical version. A contribution descriptor can otherwise use the normal
[Project Descriptor](project-descriptor.md) schema and telemetry categories.

Example:

```json
{
  "schemaVersion": 1,
  "projectId": "my-contributor",
  "projectVersion": "1.4.0",
  "displayName": "My Contributor",
  "ownerPluginIdentifiers": ["Example:My Contributor"],
  "packagePrefixes": ["com.example.contributor"],
  "defaults": {
    "enabled": true,
    "destinationMode": "hosted"
  },
  "hosted": {
    "projectKey": "your_public_project_key"
  },
  "telemetry": {
    "events": {
      "breadcrumbs": { "supported": true },
      "errors": { "supported": true }
    },
    "stats": { "supported": true }
  }
}
```

Beacon 2.x is not source or binary compatible with the 1.x Alec's Telemetry
Java namespace. The conventional `EmbeddedTelemetryBootstrap.bootstrap(plugin)`
API prefers `Server/Beacon/project.json`, then accepts
`Server/Telemetry/project.json` or `telemetry/project.json` as deprecated
fallbacks. The coordinator also accepts
`META-INF/alecs-telemetry/projects/` for passive discovery when the canonical
directory contains no direct JSON descriptors. The anchored `contribute(...)`
API reads the exact resource path supplied by the caller and does not rewrite
it.

## Election and protocol compatibility

The process-wide contribution registry uses only JDK values across classloader
boundaries. Each candidate has contribution ABI 1 metadata and an opaque write
token. Candidates for the same project ID are compared case-insensitively:

1. the highest valid logical semantic version wins;
2. an equal-version standalone origin wins over an embedded origin;
3. equal origin/version candidates use deterministic host identifier, source
   path, and descriptor-hash tie-breakers.

Only the elected bridge can write. A same-owner passive copy may submit an
operation through that elected bridge, which lets an elected host component use
the stable project even when a different physical copy registered it first.
Different-owner, invalid, fenced, and retired candidates cannot submit. A
retiring winner is fenced before it can write again, and registered fallbacks
remain passive until restart. Registration alone emits nothing; every operation
still passes the elected descriptor's support, project/category consent, detail
allowlist, sampling, and destination gates.

The active runtime coordinator must implement coordinator protocol 3, including
contribution snapshot reconciliation and token-bound dispatch. Protocol-2
providers are ineligible to own generic contribution projects. Within the Beacon
namespace, the conventional `EmbeddedTelemetryBootstrap.bootstrap(plugin)`
caller remains source and binary compatible across eligible Beacon providers.
When a newer provider takes
over, the complete contribution snapshot is reconciled before activation. A
failed replay leaves the previous active provider and catalog in place.

Candidate validation and election warnings are bounded and available through
`TelemetryProjectContributionRegistry.diagnostics()`. Invalid ABI, semantic
version, owner, descriptor, origin, or source metadata leaves the candidate
passive. Diagnostics are local operational information; a malformed candidate
does not block the host mod's setup or start lifecycle.

For a descriptor discovered passively, an explicit contribution replaces the
Stats-only registration only when the logical project ID, logical version,
declared owner, and canonical descriptor hash all match the elected passive
snapshot. Other collisions remain passive. The replacement preserves one
project row, one consent state, and one heartbeat project. If the contribution
retires, the passive base can be restored by reconciliation.

## Independent project behavior

One host-local provider may serve a conventional host project and several
contributed projects, but project boundaries remain independent:

- each logical project has its own consent and category overrides;
- each project resolves its own hosted or custom destination and headers (with
  anchored contributions limited to hosted mode in this MVP);
- each project is attributed with its own `projectId`, logical plugin identifier,
  and logical plugin version;
- queues, manual-report eligibility, allowlists, and stats settings are scoped
  to the logical project.

The physical host plugin identifier/version, origin, normalized source path, and
descriptor hash are used for local provider pooling, election, collision
diagnostics, and operator inspection. They are not added as fields to crash,
event, stats, or manual-report envelopes. Existing runtime metadata such as the
loaded-mod list retains its prior behavior and may still be included where the
normal telemetry policy allows it.

Passive descriptor discovery uses the same physical-host values only to elect a
single candidate. It never proves that the library code executed and never
grants a richer write capability merely because its resource is present.

## Heartbeats and live changes

The active coordinator emits aggregate stats heartbeats for the current live
project catalog. A contributed project is heartbeat-eligible only when its
candidate is elected, the project and stats category are enabled by consent, the
descriptor allows `heartbeat`, and a destination resolves. Passive projects use
the same gates and can emit one aggregate heartbeat when Stats consent is
enabled. Multiple physical hosts carrying the same project still produce one
logical heartbeat entry. Invalid, retired, disabled, or destination-less
candidates do not emit heartbeats. Registration itself never emits a heartbeat.
See [Usage Stats](usage-stats.md) for cadence and public aggregation behavior.

Contribution reconciliation is live for first registration; adding a valid
candidate makes its project available to consent and runtime operations without
restarting the server. If an active contribution retires while its elected
passive Stats-only base remains present, that base resumes/continues as the
logical project's consent and heartbeat entry. Only when no passive base remains
does the logical project leave new consent and heartbeat snapshots. Retirement
does not delete that project's local crash, event, or manual-report queue; the
coordinator retains the project registration needed to replay queued envelopes,
which remain subject to the normal consent, destination, retry, and retention
rules. An unrelated same-ID replacement is intentionally deferred until
restart.

## Hosted and custom destinations

With `defaults.destinationMode` set to `hosted`, the descriptor's publishable
`hosted.projectKey` routes envelopes to the Beacon hosted platform. The hosted
platform's validation, retention, portal, and public-stats rules apply there.

With `defaults.destinationMode` set to `custom`, use `customEndpoint.url` (and,
when needed, `eventUrl` and `headers`) for a conventional project or a passive
descriptor-only Stats project. A custom endpoint operator controls the data
received at that endpoint, including any descriptor-approved fields,
attachments, or sensitive values supplied by a mod or player. The Beacon hosted
platform policy and retention commitments do not cover a custom endpoint; the
mod author and server owner must document, secure, and retain that data under
their own rules. Executable anchored contributions using custom mode are
disabled by the 1.1.0 MVP gate above; that gate does not disable passive
descriptor Stats delivery to an author-selected custom endpoint.

## Failure behavior

Missing or malformed anchored resources, missing explicit identity, owner
mismatch, invalid logical versions, linkage failures, and runtime dispatch
errors produce a disabled service or a rejected/no-op operation with a bounded
diagnostic reason. They are logged for operators but do not throw through the
embedding mod's lifecycle. A host can therefore keep running while a telemetry
contribution remains passive or unavailable.
