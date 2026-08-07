# Embedded Contributions

Embedded mode can expose more than one logical telemetry project from one
physical Hytale mod. Use the anchored contribution API when a component library,
feature module, or other embedded package owns its own project identity. The
physical host gets one shared runtime provider, while each logical project keeps
its own descriptor, consent, destination, attribution, and queue.

This guide describes the Alec's Telemetry 1.1.0 contribution ABI (ABI 1) and
coordinator protocol 3.

## Register a logical project

Put the contribution descriptor in the contributor's own jar and anchor the
resource lookup to a class owned by that contributor:

```java
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryBootstrap;
import com.alechilles.alecstelemetry.embedded.EmbeddedTelemetryService;
import com.alechilles.alecstelemetry.embedded.TelemetryProjectContribution;

TelemetryProjectContribution contribution = TelemetryProjectContribution.builder()
        .descriptorResource(
                MyContributorTelemetry.class,
                "META-INF/alecs-telemetry/projects/my-contributor.json"
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

An established logical project winner is not hot-replaced or automatically
failed over. Unregistering the winner fences its token and leaves any already
registered fallback passive, so a second copy cannot begin writing while the
active catalog is changing. Restart the server after retirement when a
replacement is required. Re-registering that project ID in the same process
keeps it passive. This boundary
also keeps queued envelopes on the destination that created them.

Before `start()`, only setup breadcrumbs, lifecycle events, and setup-failure
captures are accepted for bounded replay. Manual reports, flushes, and other
operations return a bounded `not_started` rejection and are never drained after
startup.

The builder accepts either `.descriptorResource(anchor, path)` or separate
`.resourceAnchor(anchor)` and `.descriptorResource(path)` calls. One leading `/`
is removed from the resource path before lookup.

## Use a unique descriptor resource

The anchored API reads the descriptor from `resourceAnchor.getClassLoader()`;
it does not search the host plugin's conventional `Server/Telemetry/project.json`.
Give every logical contributor a namespaced path so two embedded jars cannot
silently resolve one another's descriptor. A recommended layout is:

```text
META-INF/alecs-telemetry/projects/<stable-contributor-name>.json
```

The descriptor must explicitly declare `projectId` and `displayName`, and its
`ownerPluginIdentifiers` must include the exact logical identifier supplied to
the builder (case-insensitive). The logical version must parse as a Hytale
semantic version. A contribution descriptor can otherwise use the normal
[Project Descriptor](project-descriptor.md) schema and telemetry categories.

Example:

```json
{
  "schemaVersion": 1,
  "projectId": "my-contributor",
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

The conventional `EmbeddedTelemetryBootstrap.bootstrap(plugin)` API remains
source and binary compatible. It still loads `Server/Telemetry/project.json`
with the legacy `telemetry/project.json` fallback. Use `contribute(...)` for
anchored logical projects; do not make a contribution depend on a host-owned
conventional resource path.

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
providers are ineligible to own generic contribution projects. The conventional
`EmbeddedTelemetryBootstrap.bootstrap(plugin)` caller remains source and binary
compatible. When a newer provider takes
over, the complete contribution snapshot is reconciled before activation. A
failed replay leaves the previous active provider and catalog in place.

Candidate validation and election warnings are bounded and available through
`TelemetryProjectContributionRegistry.diagnostics()`. Invalid ABI, semantic
version, owner, descriptor, origin, or source metadata leaves the candidate
passive. Diagnostics are local operational information; a malformed candidate
does not block the host mod's setup or start lifecycle.

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

## Heartbeats and live changes

The active coordinator emits aggregate stats heartbeats for the current live
project catalog. A contributed project is heartbeat-eligible only when its
candidate is elected, the project and stats category are enabled by consent, the
descriptor allows `heartbeat`, and a destination resolves. Passive, invalid,
retired, disabled, or destination-less candidates do not emit heartbeats.
Registration itself never emits a heartbeat. See [Usage Stats](usage-stats.md)
for cadence and public aggregation behavior.

Contribution reconciliation is live for first registration; adding a valid
candidate makes its project available to consent and runtime operations without
restarting the server. Retiring a candidate removes it from new consent and
heartbeat snapshots, but it does not delete that project's local crash, event,
or manual-report queue. The coordinator retains the project registration needed
to replay queued envelopes; queued data remains subject to the normal consent,
destination, retry, and retention rules. A same-ID replacement is intentionally
deferred until restart.

## Hosted and custom destinations

With `defaults.destinationMode` set to `hosted`, the descriptor's publishable
`hosted.projectKey` routes envelopes to Alec's hosted platform. The hosted
platform's validation, retention, portal, and public-stats rules apply there.

With `defaults.destinationMode` set to `custom`, use `customEndpoint.url` (and,
when needed, `eventUrl` and `headers`) for a conventional project. A custom
endpoint operator controls the data received at that endpoint, including any
descriptor-approved fields, attachments, or sensitive values supplied by a mod
or player. Alec's hosted platform policy and retention commitments do not cover
a custom endpoint; the mod author and server owner must document, secure, and
retain that data under their own rules. Anchored contributions using custom mode
are disabled by the 1.1.0 MVP gate above.

## Failure behavior

Missing or malformed anchored resources, missing explicit identity, owner
mismatch, invalid logical versions, linkage failures, and runtime dispatch
errors produce a disabled service or a rejected/no-op operation with a bounded
diagnostic reason. They are logged for operators but do not throw through the
embedding mod's lifecycle. A host can therefore keep running while a telemetry
contribution remains passive or unavailable.
