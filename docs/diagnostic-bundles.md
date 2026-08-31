# Diagnostic Bundles

Diagnostic bundles let a project send bounded technical evidence with a
structured failure summary. They are for automatic or programmatic diagnostics.
They are not manual player reports.

## Contract

Call `submitDiagnosticBundle` on `TelemetryProjectHandle`,
`EmbeddedTelemetryHandle`, or `EmbeddedTelemetryService`. The runtime adds the
project and plugin identity, plugin version, session hash, and server hash.

A bundle contains:

- a project-scoped diagnostic ID and UTC capture time
- a source, diagnostic kind, title, summary, and severity
- an `informational` or `create_or_join_issue` disposition
- a stable fingerprint for `create_or_join_issue`
- bounded scalar attributes
- zero or more opaque attachments

Each attachment includes a stable attachment ID, kind, safe file name, content
type, `identity` or canonical `base64` encoding, decoded byte count, decoded
SHA-256, and content. The portal does not inspect archive members as part of the
wire contract.

## Limits

Diagnostic bundles require both project telemetry and the Error events category
to be enabled. A consent or runtime override change applies before a bundle can
enter the local queue.

Attributes are limited to 32 entries. Keys use safe identifier characters and
are at most 128 characters. Values must be booleans, finite numbers, or strings
of at most 256 characters. Nested objects, arrays, and other value types are
rejected.

The runtime accepts at most 16 attachments. Each decoded attachment must be at
most 1 MiB. The serialized diagnostic payload must be at most 2 MiB. A hosted
project can impose a smaller request limit.

Submission is best effort. A `QUEUED` result means the local event queue accepted
the envelope. It does not guarantee that the hosted service stored it. Reusing a
diagnostic ID lets the hosted service treat retries as the same diagnostic.

## Producer Responsibilities

The producing mod must:

- capture diagnostics only under its documented telemetry policy
- redact secrets and personal or save-specific data before submission
- keep titles, summaries, attributes, and fingerprints low-cardinality
- limit queues and prevent recursive reporting loops
- treat attachments as opaque evidence and choose safe file names

Do not put player names, raw UUIDs, chat, coordinates, access tokens, inventory
contents, raw databases, or unrestricted logs into a diagnostic bundle.

## Manual Reports Stay Separate

Diagnostic bundles do not support player contact, follow-up tokens, report
review, approval, or rejection. Use the manual report API and UI when a player
must write, review, or approve the submission.
