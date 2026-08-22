# Alec's Telemetry Privacy Policy

Effective date: August 21, 2026

This policy explains how Alec's Telemetry, Alec's hosted telemetry platform, and
ModStats.io collect and use information. It covers:

- the Alec's Telemetry runtime that can run on Hytale servers or local worlds
- telemetry sent to Alec's hosted platform at `telemetry.alecsmods.com` or
  related development hosts
- the hosted portal used by mod authors and project members, including its
  optional Model Context Protocol (MCP) agent access feature
- public ModStats.io stats and server listing pages

This policy is written for players, server owners, and mod authors. It is not a
substitute for legal advice. If you operate a public server, ship a mod that uses
Alec's Telemetry, or configure a custom telemetry endpoint, you are responsible
for making sure your own setup follows the laws and platform rules that apply to
you.

## Who Controls The Data

For telemetry sent to Alec's hosted platform, Apex Web Solutions LLC operates the
service and controls the hosted data. Mod authors and server owners control what
their projects and servers send by choosing telemetry descriptors, consent
defaults, runtime settings, and manual report settings.

If a mod or server points Alec's Telemetry at a custom endpoint instead of Alec's
hosted platform, that endpoint operator controls the data sent there. This policy
does not cover custom endpoints.

## The Short Version

Alec's Telemetry is designed to avoid player identity data by default.

- Server owners can review and change telemetry categories with
  `/telemetry consent`.
- The runtime automatically enumerates valid direct JSON descriptors under
  `META-INF/alecs-telemetry/projects/` in installed mod archives and folders.
  Resource presence is treated as the library's installation signal; it does
  not prove that the library code executed.
- Public stats show aggregate counts and breakdowns, not raw server IDs, session
  IDs, IP addresses, player names, player UUIDs, chat, coordinates, world data,
  secrets, or full config files.
- Manual player reports are optional. They can include player-written text,
  optional contact information, and optional attachments only when the server
  settings and project descriptor allow them.
- Crash and event telemetry may include technical diagnostics such as stack
  traces, error messages, plugin versions, Hytale versions, Java/runtime details,
  operating system details, loaded mod IDs, server/session identifiers, and
  descriptor-approved event context.
- Embedded contributors can register independent logical projects through an
  anchored descriptor. Their envelopes use the logical project/plugin identity
  and logical plugin version. Physical host identifiers, host versions, source
  paths, origins, and descriptor hashes used for local election and diagnostics
  are not added to uploaded telemetry fields. Existing loaded-mod metadata keeps
  its prior behavior and may still be included where the normal runtime payload
  includes it.
- Passive descriptor projects expose only the aggregate Stats heartbeat under
  independent project and Stats consent. Passive discovery loads no executable
  class from the descriptor and does not initialize library code; richer
  categories require an explicit active contribution. When a prior persisted
  supported-category snapshot exists, newly added categories stay disabled until
  operator review. Legacy reviewed records without a snapshot retain their
  prior approvals and are not compared retrospectively. A passive descriptor
  may route that standard Stats-only heartbeat to Alec's hosted platform or to
  an author-selected custom endpoint; the custom endpoint operator controls
  data, security, and retention.
- When an active contribution adds categories to a previously reviewed logical
  project and a persisted supported-category snapshot exists, those categories
  remain disabled until an operator saves new consent choices. A review reminder
  is sent only to players with `telemetry.command.telemetry` or the local
  singleplayer owner. Legacy reviewed records without a supported-category
  snapshot remain honored and cannot be compared retrospectively.
- Anchored contributions in the 1.1.0 MVP can upload only to Alec's hosted
  destination. Conventional projects may still opt into a custom endpoint; that
  endpoint operator, not Alec, controls its data and retention.
- A same-ID contribution winner is not automatically hot-replaced or failed
  over. Retirement fences the old token and leaves already registered fallbacks
  passive until server restart. Re-registering the same project ID in the same
  process does not make it writable, preserving the destination context for
  queued envelopes.
- A valid same-owner passive copy can submit an operation through the established
  winner. The winner's descriptor, consent, allowlist, sampling, and hosted
  destination remain authoritative; different-owner, invalid, fenced, and
  retired candidates are rejected.
- Before an anchored service starts, only setup breadcrumbs, lifecycle events,
  and setup-failure captures are buffered. Manual reports and other operations
  are rejected while stopped and are not replayed after startup.
- Consent metrics are collected separately and are limited to project/version,
  category choice, and first-review funnel events.
- The optional Spark hot-path monitor is off by default. When a server owner
  enables it, the runtime reads the newest completed passive Spark background
  window no more often than once every five minutes,
  computes the loaded Spark JAR's SHA-256 in memory, and writes bounded
  WorldThread hot-path summaries to the local server log. When the global
  monitor is enabled, the project is enabled, and performance consent is
  enabled, it qualifies each project over the latest five actionable windows
  using the local `hot-path-v1` rules. A publication can also contain the
  aggregate current player count, nullable Spark public one-minute TPS and
  MSPT values, Hytale and project versions, and counts for declared breadcrumb
  categories from the latest five minutes. Phase 2 has no dedicated profiler
  evidence file and no structured profiler evidence queue or upload path in
  Telemetry. Bounded profiler summaries, signals, and context remain in memory.
  Replies from the local `profiler status`, `profiler top`, `profiler history`,
  and `profiler signals` commands are also copied to the ordinary server log.
  The server logging policy can retain those summary and command-output lines.
  A manual report with current or previous server-log attachments can upload
  that text when the normal report settings, project descriptor, review,
  redaction, and clipping controls allow it. The monitor does not write raw
  Spark profile or frame-tree data to the log or a profile file, and Telemetry
  does not upload that raw data. The current direct reader does not build Spark
  protobuf data or request Spark class-source maps.
- The hosted platform may use IP addresses and request metadata for rate
  limiting, abuse prevention, server-country derivation, security logging, and
  normal hosting operations.
- Alec's Telemetry policy does not allow mod authors to collect non-optional
  personally identifiable information through telemetry payloads. Third-party
  mods are configurable, so report any suspected abuse through the support link
  below.

## What The Runtime Stores Locally

The runtime may store local files under the server or save's Alec's Telemetry data
directory. These files can include:

- project descriptors discovered from installed mods
- passive descriptor capability snapshots and consent-review state
- server-owner consent choices and runtime overrides
- server identity and verification values used for ModStats server claiming
- queued telemetry waiting to upload
- local project-election state and bounded candidate diagnostics, including the
  logical project ID/version, physical host identifier/version, normalized source
  path, registration source, and descriptor hash used to choose one provider
- manual report drafts, review queues, submitted receipts, and local follow-up
  tokens
- local runtime settings for manual reports, attachments, endpoints, and related
  controls
- when the optional Spark hot-path monitor is enabled, bounded hot-path
  summary and command-output lines in the ordinary server log. Phase 2 has no
  dedicated profiler evidence file; bounded profiler summaries, signals, and
  context remain in memory, while log text follows the server's log policy.

Local files stay on the server or local machine unless the configured telemetry
category or report workflow uploads them.

The Spark hot-path monitor reads Spark thread labels, Java class and method
names, method descriptors, tree links, and one completed window's timing
counters only while it creates a summary. It does not request Spark source or
mod-label maps. It selects Java self time from Hytale `WorldThread` threads.
Native frames and Java work on other thread names do not enter the ranking. The
monitor also computes the loaded Spark JAR's SHA-256 in memory to enforce the
tested-artifact gate. The runtime does not retain that hash or raw profile data.
It keeps only bounded summaries and history in memory.
It writes bounded automatic summary lines and operator-requested profiler
command replies to ordinary server log output. It does not create a raw Spark
profile file. The server owner and the server's logging setup control retention
of that local log. Spark can keep its own profiler data under Spark's settings
and behavior; that retention is separate from Alec's Telemetry.
Local summaries can contain Java class and method names, timing values, and
bounded thread/frame counts. The direct reader uses `<unknown>` for Spark source
labels because it skips Spark's source lookup. Completed status text can also
contain capture-thread allocated bytes when the JVM supports that counter and a
separate whole-JVM used-heap delta.

The Spark hot-path monitor has no dedicated profiler evidence file and no
structured Phase 2 profiler evidence queue or upload path. It keeps bounded
summaries, signals, and context in memory. It writes summary and profiler
command-output text to ordinary server logs, so the server logging policy can
retain that text. A manual report can upload current or previous log text when
the normal runtime settings, project descriptor, review, redaction, and byte
clipping controls allow it. Raw Spark profile and frame-tree data are not
written to the server log or a profile file, and Telemetry does not upload that
raw data. A supported Spark window with no usable WorldThread Java samples
produces no actionable ranking.

### Local profiler summaries and Phase 2 signals

With the global Spark monitor enabled, the project enabled, and the project's
performance consent enabled, Telemetry creates bounded summaries in memory of
completed passive WorldThread windows. The
latest five actionable windows are the complete rolling qualification input for
one project. An actionable window is a successful bounded Spark capture with
usable selected WorldThread samples. A project with no attributed path still
gets an empty `COMPLETE` window marker, so an old candidate can age and expire.
Failed, incompatible, too-complex, timed-out, or sample-free windows do not
advance qualification.

For one stable path fingerprint, a window qualifies when sampled self time is at
least 20 ms and at least 2% of selected WorldThread self time. `OBSERVED` is the
normal early state. `PROVISIONAL` means one appearance reaches at least 100 ms
and 20% share but the path is not yet present in three qualifying windows.
`REPEATED` means the path qualifies in at least three of the latest five
actionable windows. Signals expire when no qualifying appearance remains in
those five windows. The local `signals` command ranks at most five active
candidates.

A consumer mod can read one project's immutable view through
`TelemetryProjectHandle.profiler()` or receive later snapshots through its
subscription. A snapshot can identify the project's owned Java methods and
downstream Java methods called through them. It can contain class names, method
names and descriptors, sampled milliseconds, selected WorldThread share,
qualification, fingerprints, a bounded representative path, and publication
context. `TelemetryProfilerContext` can contain:

- `hytaleVersion`, when the server manifest provides it (the context field is
  nullable);
- nullable aggregate `playerCount`;
- nullable `tps` and `mspt`, from Spark's supported public one-minute metrics;
- non-zero counts for only the project's declared, exact-match breadcrumb
  categories, summed over five one-minute buckets.

`projectVersion` is not part of `TelemetryProfilerContext`. Separately, every
`TelemetryProfilerSnapshot` has the required logical `projectVersion` snapshot
field from the logical registration.

Breadcrumb context stores category names and counts only. It never stores
breadcrumb detail text, structured custom fields, raw Spark profiles, Spark
frame trees, player identifiers, or server identifiers. The Hytale version,
player count, TPS, MSPT, and breadcrumb values are nullable or empty when their
source is unavailable or not authorized.

Each view contains only one project's attributed immutable summary. The local
runtime API is not an authorization boundary. Server-side code that can access
the API can request another registered project by ID. Do not promise that
consumer integrations cannot log a snapshot, store it, or send it somewhere
else. The consumer integration controls its later handling of any value it
receives, including any later handling by a third-party consumer.

Phase 2 has no dedicated profiler evidence file and no structured profiler
evidence queue or upload path in Telemetry. It retains only bounded immutable
summaries, signals, and context in memory. It does not retain raw Spark
profiles, frame trees, player identifiers, or server identifiers. Spark protobufs,
reflection objects, source maps, and raw profile trees are transient and are not
retained after analysis. Spark can retain or upload its own profiles under
Spark's separate settings; that is separate behavior from Alec's Telemetry.

Profiler command replies are copied to ordinary server logs, including every
line from `status`, `top`, `history`, and `signals`. A manually attached current
or previous server log can contain those command lines when the existing
manual-report settings, project descriptor, review, redaction, and clipping
controls allow the attachment. This ordinary log path is separate from the
profiler evidence store; the monitor does not write raw profile data to that
log.

Disabling a project's performance consent makes its local profiler history
unavailable and clears the retained summaries, signals, and context for that
project. This does not require the global Spark monitor to stop. Re-enabling
performance consent allows only later completed windows to appear; cleared
summaries are not restored. Breadcrumb consent is independent: disabling it
clears only the five-minute approved-category buckets and leaves performance
summaries and signals available. Counting and publication of breadcrumb
correlation also require the project to remain enabled. Future snapshots then
contain an empty breadcrumb count map until breadcrumb consent is restored and
new accepted categories are recorded.

## Telemetry Sent To The Hosted Platform

The exact payload depends on the telemetry category enabled for a project.

### Crash, Error, Lifecycle, Performance, And Usage Events

When enabled, these events can include:

- project ID, project display name, plugin ID, plugin version, and telemetry
  source
- event type, event name, event ID, timestamp, severity, duration, metric value,
  and fingerprint
- internal server ID and session ID used for grouping and deduplication
- descriptor-approved event context fields such as subsystem, operation, feature,
  runtime side, entity type, item ID, block ID, biome ID, or command name
- environment and runtime metadata such as Java version, runtime version,
  operating system name/version/architecture, CPU core count, Hytale build,
  server version, hosting mode, and loaded mod IDs/names/versions
- for crashes, exception type, exception message, stack trace frames, causal
  chain, attribution information, thread name, breadcrumbs, and limited world
  failure metadata when available
- breadcrumbs may include bounded structured diagnostic fields supplied by the
  reporting mod, including randomized or hashed correlation/incident IDs,
  operation phase, operation type, scope category, failure classification,
  disposition, and a small set of scalar attributes

Crash messages and stack traces come from the running software. Mod authors should
avoid putting secrets, player names, coordinates, tokens, or other identifying
values into exception messages, breadcrumbs, or custom event context.
Structured breadcrumb fields are not intended for raw player, NPC, world, save,
or account identifiers; integrations should send randomized correlation values
or one-way hashes when cross-event correlation is necessary.
The hosted platform may extract randomized incident, trace, and operation IDs
from accepted diagnostic occurrences into project-scoped indexed fields. These
indexes support exact correlation lookup by authorized project members without
searching unrelated raw payloads. Correlation IDs and scope hashes are not
included in public telemetry or public stats views.

For an anchored embedded contribution, the elected logical project controls the
envelope's `projectId`, display name, plugin identifier, and plugin version. The
runtime may keep the physical host plugin identifier/version, normalized source
path, contribution origin, and descriptor hash in local election and diagnostics
state so isolated providers converge on one candidate. Those physical-host
fields are not new crash, event, stats, or manual-report payload fields. A
contribution still uses the existing runtime metadata contract, including loaded
mod identifiers and versions where that metadata is enabled.

For passive descriptor-only projects, the same logical identity/version and
physical-host provenance are used locally for election and diagnostics. The
runtime treats presence of a valid direct namespaced resource as installation;
it does not load or instantiate an executable class named by that resource. The
passive registration is Stats-only and can deliver one aggregate heartbeat per
logical project when project and Stats consent, heartbeat allowlisting, and the
configured destination all permit it. Declared crash, error, lifecycle,
performance, usage, breadcrumbs, and manual-report categories remain masked
until matching executable integration registers them.

Contribution ABI 1 and coordinator protocol 3 make this routing explicit. A
protocol-2 provider cannot own a generic contribution. A valid same-owner
passive copy can submit only through the elected bridge; invalid,
different-owner, fenced, and retired candidates cannot write. Registration alone emits nothing;
every operation remains subject to the project's descriptor, independent
consent, allowlist, sampling, and destination gates.

### Public Usage Stats

Stats heartbeats are separate from feature-usage telemetry. Current runtimes send
the first stats heartbeat a few minutes after startup, then normally report about
every 30 minutes with stable jitter. Existing five-minute clients remain
accepted during rollout, and runtimes may send a best-effort final heartbeat on
clean shutdown. The hosted service uses these heartbeats to compute public
aggregate stats such as:

- active servers and active players
- record servers and record players
- server/player history in activity buckets
- plugin, Hytale, server, Java, runtime, operating system, architecture, CPU
  core count, hosting mode, server-country, and loaded-mod name/version
  breakdowns

Public ModStats usage and download charts can also show timeline markers. Site
administrators can enter manual global chart annotations with a title and time.
The service can also show provider-backed stable release metadata from linked
CurseForge, Modtale, and Modifold projects. These release markers can include the
provider, version, and source link. Provider timestamps come from the provider's
publication, file, or version record. They may not match the exact time that a
release became public on ModStats. The service does not infer release times from
telemetry first-seen data.

An embeddable library can contribute to this flow with only a direct
`META-INF/alecs-telemetry/projects/<stable-project-id>.json` descriptor in the
final host artifact. Presence is the installation signal, not evidence that the
library executed. If several physical hosts carry the same logical
`projectId`/`projectVersion`, local election deduplicates them into one logical
Stats heartbeat project. The containing host identifier/version and source path
are local election provenance, not new uploaded stats fields. The descriptor may
route this standard Stats-only heartbeat to Alec's hosted platform or to an
author-selected custom endpoint; custom endpoint operators, rather than Alec,
control the received data, security, and retention.

The hosted service may use raw server IDs, session IDs, loaded mod evidence, and
private heartbeat payloads internally to compute aggregates, prevent duplicates,
verify server claims, and maintain the public stats data. Public stats responses
must expose only aggregate counts and breakdowns.

For passive projects and embedded contributions, a heartbeat is eligible only
for the current per-project winner after project/Stats consent, descriptor
`heartbeat` allowlisting, and destination resolution. Invalid,
protocol-ineligible, or retired candidates are omitted from new heartbeat
snapshots. Retirement does not delete queued crash, event, or report envelopes;
the local coordinator retains the project registration needed to replay them
under the normal retry and retention rules. Heartbeat payloads are grouped by
resolved destination so projects routed to different endpoints are not combined
in one upload.

### Manual Player Reports

Manual reports are submitted through player-facing issue or suggestion forms.
They can include:

- report title and description written by the player
- project-specific form fields
- optional contact information if both the server settings and project descriptor
  allow it
- optional diagnostics, loaded mod lists, current or previous server logs, and
  other attachments if both the server settings and project descriptor allow them
- a hashed follow-up token so the player can check report status without exposing
  the raw token to the hosted database

Server owners can require local review before manual reports upload. Rejected
reports remain local and are not uploaded. Diagnostic attachments are redacted on
a best-effort basis for common secrets, Discord tokens, email addresses, IPv4
addresses, and Windows user paths, but players and server owners should still
review report text and attachments before sending sensitive information.
The hosted platform stores attachment byte counts and may use them to enforce
per-file, per-report, and billing-account attachment storage quotas.

### Consent Metrics

The hosted platform may receive privacy-preserving consent metrics so project
owners can understand whether telemetry defaults are acceptable. These metrics can
include:

- runtime version
- project ID, project display name, and project version
- telemetry category
- whether the category was supported, enabled, previously enabled, and changed
  during first review or from the consent UI
- first-run funnel events such as prompt shown, consent UI opened, and first
  review completed

Consent metrics do not include project keys, raw server IDs, session IDs, player
names, player UUIDs, chat, coordinates, or manual report contents.

## Portal Accounts, Billing, And Integrations

The hosted portal lets project members manage projects, keys, memberships, issue
triage, manual reports, stats, Discord routing, GitHub issue sync, and billing.

When you sign in or are invited to a project, the portal may store:

- portal user ID
- Discord, GitHub, or Hytale provider ID, username/login, display name,
  avatar URL, and linked-provider status
- project memberships, roles, audit log entries, route verification records,
  project membership invitations, portal notifications, saved views,
  assignees, and workflow activity
- Discord guild/channel/role routing configuration selected by authorized project
  members
- GitHub repository integration settings and GitHub issue-link metadata
- linked CurseForge, Modtale, and Modifold project URLs and the public metadata
  retrieved for those projects, such as provider project ID, summary, icon URL,
  download total, and last-refresh time. For provider-backed stable release
  metadata used on public ModStats charts, the service may also store the
  provider release ID, version, published timestamp, timestamp provenance, source
  URL, active status, and first-sync and last-sync times, as applicable. This
  cached metadata is refreshed periodically while its project URL remains linked.
  Provider timestamps describe the provider's publication, file, or version
  record. They may not equal the exact public launch time on ModStats, and release
  times are not inferred from telemetry first-seen data.
- account author profile settings, such as public slug, display name, avatar
  URL, bio, website URL, Discord URL, GitHub URL, CurseForge profile URL,
  Modtale profile URL, Modifold profile URL, featured link, theme tokens, and
  public visibility settings
- public project author/contributor links and display titles selected by project
  owners
- billing account, account-project links, Stripe Checkout, Billing Portal,
  webhook, customer, subscription, plan, entitlement, billing email, downgrade
  grace, public plan recognition opt-out setting, and quota/guardrail metadata
  when billing features are used
- optional MCP access settings, selected projects, scopes, credential labels,
  expiration/revocation and last-used information, OAuth client metadata,
  authorization/approval records, and security/audit events when a project
  enables agent access

MCP agent access is optional and is controlled by authorized project members.
An agent receives only the project and scope access approved for its credential.
Telemetry, report text, and diagnostic fields returned to an agent are treated
as untrusted evidence; the service applies bounds and redaction controls and
does not return attachment bytes through MCP. MCP credentials and telemetry
ingest keys are separate. We display newly-created credential values once and
store only protected verification values thereafter. Project policies can require
portal approval before an agent changes an issue, creates a linked GitHub issue,
or provisions an ingest credential.

For Hytale sign-in, we store the stable per-application Hytale subject
identifier returned to Alec's Telemetry and, when you grant the profile scope,
the selected Hytale profile username and UUID. Hytale does not provide us your
email address through this sign-in flow.

Discord, GitHub, Hytale, and Stripe process information under their own privacy
policies when you use those integrations.

## Website Analytics

The public website may load Google Tag Manager on ModStats.io and Alec's
Telemetry landing hosts. This is separate from in-game telemetry. Browser
analytics can include normal web analytics information such as page views,
referrers, device/browser details, approximate location, and cookie or similar
identifiers depending on the active tag configuration and browser settings.

For visitors identified by trusted hosting headers as being in the EU, EEA, UK,
or an unknown region, Google Tag Manager should not load until the visitor
accepts optional website analytics. The choice is stored in the browser as
accepted or declined and is versioned by this policy date. Likely-US visitors may
continue to receive the current immediate website analytics behavior.

## Legal Bases And Reasons For Processing

Where GDPR-style legal bases are relevant, the hosted platform operated by Apex
Web Solutions LLC generally uses:

- requested service or contract-like necessity to deliver telemetry ingestion,
  dashboards, public stats, manual reports, account access, configured
  integrations, and billing features
- legitimate interests to secure the platform, prevent abuse, debug service
  issues, preserve project history, and maintain public aggregate stats
- consent or user choice for website analytics in opt-in regions, optional manual
  reports, optional report contact fields, optional attachments, and runtime
  telemetry categories controlled by server owners
- legal obligation where billing, tax, accounting, dispute, security, or valid
  legal process requires retention or disclosure

## How Data Is Used

Data is used to:

- deliver crash, error, performance, lifecycle, usage, stats, and manual-report
  workflows requested by server owners and mod authors
- show project dashboards and issue triage views to authorized project members
- compute public aggregate mod stats and server listing data
- route notifications to configured Discord channels
- create or update GitHub issues when enabled by project members
- operate billing and project guardrails
- rate-limit requests, prevent abuse, investigate service issues, and protect
  the platform
- improve the reliability and privacy posture of Alec's Telemetry

Telemetry payloads are not sold. Player report contents and raw telemetry
payloads are not used for targeted advertising.

## Who Can See Data

Access depends on the surface:

- Public ModStats.io pages show aggregate stats, public project/server profile
  information, optional public author profile information, sanitized public
  profile theme fields, project author/contributor links and display titles,
  and optional Supporter, Pro, or Partner plan badges when public badges are
  enabled. Public author profiles can include a display name, slug, avatar URL,
  bio, website URL, Discord URL, GitHub URL, CurseForge profile URL, Modtale
  profile URL, Modifold profile URL, cover image URL, featured link URL/label,
  and bounded theme tokens. Public pages do not show Stripe customer IDs,
  subscription IDs, billing email, invoices, payment methods, or private billing
  account details.
  Paid recognition does not change public ranking, active counts, or verified
  telemetry evidence.
- Project owners, admins, maintainers, and viewers can see the portal data their
  role allows for that project.
- Configured Discord channels may receive crash, event, or manual-report alert
  summaries.
- Configured GitHub repositories may receive issue content when project members
  create or sync GitHub issues.
- Apex Web Solutions LLC and trusted service operators may access data to
  operate, secure, debug, and support the platform.
- Site administrators may access and update portal account records,
  billing/account status, project ownership and guardrails, server profile
  moderation state, and related audit records when needed to provide support,
  enforce service limits, prevent abuse, investigate reliability issues, or
  protect the hosted service. Administrative changes are logged with the acting
  portal account where available.
- Hosting, analytics, OAuth, payment, and infrastructure providers may process
  data as needed to provide their services.
- Data may be disclosed if required to comply with law, protect rights, prevent
  abuse, or respond to valid legal process.

Known third-party processors or subprocessors include Google / Google Tag
Manager, Discord, GitHub, Stripe, and hosting/database/infrastructure providers.
Any hosting or database provider not discoverable from code or deployment
configuration should be confirmed before production launch.

## Retention

Retention depends on the data type and operational need.

- Retiring an embedded contribution while its elected passive Stats-only base
  remains present resumes/continues that base as the logical project's consent
  and heartbeat entry. Only when no passive base remains does the logical
  project leave new consent and heartbeat snapshots. Retirement does not by
  itself delete queued crash, event, or manual-report envelopes. The active
  coordinator may replay those envelopes using the retained project
  registration until normal upload, retry, queue-limit, or deletion rules
  apply.
- Successful stats raw private payloads and retained heartbeat rows are deleted
  after compact rollups or snapshots exist. Loaded-mod heartbeat evidence is
  deleted after compact loaded-mod snapshots exist. Operators may temporarily
  keep successful stats raw rows longer during rollout or incident debugging.
- Compact stats heartbeat receipts are retained for 24 hours by default after
  the normal retry and idempotency window, unless a retained heartbeat still
  references the receipt.
- Completed ingest jobs are retained for 2 days by default. Dead-letter ingest
  jobs, which are failed queue records the worker has stopped retrying, are
  retained for 30 days by default.
- Routine lifecycle/performance raw diagnostics are retained for 7 days on Free
  by default after daily rollups exist. Routine usage raw diagnostics are
  retained for 14 days on Free by default after daily rollups exist.
  Error/warning diagnostics keep the longer investigation window, starting at 90
  days on Free. Aggregate daily event and issue-context rollups are retained
  while the project exists. Repeated diagnostics may be summarized after a
  raw-sample cap while aggregate counts remain available.
- Indexed incident, trace, and operation IDs follow the retention of the raw
  diagnostic occurrence that supplied them and are deleted with that occurrence.
- Crash report metadata and raw JSON, manual report metadata, and manual report
  raw JSON are retained for 730 days by default.
- Manual report attachments and log attachments are retained for 180 days by
  default.
- Consent metrics and first-review funnel events are retained for 730 days by
  default.
- Portal audit logs and security records are retained for 1095 days by default.
- Aggregate public stats headline rollups, one project-level server/player
  record high-water row, issue workflow/activity, project configuration, and
  public server listing history are retained while the project or server profile
  exists. Per-server stats bucket snapshots are retained for a
  recent active/evidence window of 30 days by default, and per-server loaded-mod
  bucket snapshots are retained for 24 hours by default; both are deleted after
  matching aggregate rollups exist.
  Loaded-mod breakdowns retain 30-minute detail for 7 days by default, daily
  aggregates for 90 days by default, and weekly aggregates for 730 days by
  default. Weekly loaded-mod aggregates keep the 100 most common loaded mods
  for a project/week plus a combined "Other loaded mods" total rather than an
  unbounded long-term list of individual mods.
- Billing customer and subscription metadata may be retained for 2555 days or as
  otherwise needed for tax, accounting, dispute, chargeback, legal, or security
  reasons.
- Account and provider identity records are retained while the account exists,
  then deleted or anonymized on verified deletion request unless legal, billing,
  or security reasons require retention.

Project owners may delete a telemetry project or server profile from the portal.
This removes the project or server profile from normal portal and public
surfaces, disables the relevant hosted ingest or public listing behavior, and
hides related public stats/profile exposure. This portal action is a
soft-delete/archive for operational recovery; underlying telemetry, billing,
audit, security, backup, or legal records may remain under the retention rules
above unless a separate verified privacy deletion request is processed.

Backups and logs may retain copies for a limited period after deletion from the
primary database.

## Choices And Controls

Server owners can:

- use `/telemetry consent` to review and change project/category telemetry
  choices
- review every passively discovered logical project in the scrollable consent
  list; passive projects expose Stats only until an active integration is
  explicitly registered
- review newly added categories after a permission-scoped reminder when a
  persisted supported-category snapshot exists; those additions remain disabled
  unless the operator saves them as enabled. Legacy reviewed records without a
  snapshot remain honored and cannot be compared retrospectively
- review each embedded logical contribution as its own consent project, even
  when several contributions share one physical runtime provider
- use runtime override files to disable telemetry categories or change endpoints
- disable manual reports or require local review before upload
- disable optional report contact fields, resolution updates, log attachments,
  loaded mod lists, diagnostics, and other report extras
- use a custom endpoint instead of Alec's hosted platform
- remove Alec's Telemetry or remove telemetry-enabled project descriptors

Players can:

- choose whether to submit manual reports
- leave optional contact fields blank
- ask a server operator what telemetry settings are enabled on that server
- use report receipts and follow-up tokens when status lookup is enabled

Portal users can:

- sign out of the portal
- unlink a provider when another provider remains available for the account
- edit account author profile fields and choose whether public plan recognition
  is shown where supported
- use Stripe Checkout or the Stripe Billing Portal for self-service billing when
  available for the selected plan
- ask for account deletion or correction through the support contact below

Mod authors can:

- keep descriptors small and avoid high-risk custom Event Context fields
- enable only the telemetry categories their project actually needs
- avoid collecting player names, UUIDs, chat, exact coordinates, secrets, tokens,
  or other personally identifying values
- document any project-specific fields they add through Alec's Telemetry

## Children

Alec's Telemetry is not intended to collect personal information from children.
Telemetry categories are unavailable unless a project descriptor explicitly
supports them, and supported categories can be made opt-in with
`defaultEnabled: false` or disabled by a server-owner setting. Enabled telemetry
is designed around technical diagnostics and aggregate server stats, not player
identity. Manual report text and optional contact fields are player-controlled,
so players and server owners should not submit children's personal information.
If you believe a child has provided personal information through the hosted
platform, contact Apex Web Solutions LLC through the support contact below so it
can be reviewed and deleted where appropriate.

## Security

The hosted platform uses validation, payload size limits, rate limits, project
keys, role-based portal access, CSRF protections for authenticated actions, and
segmented public/private stats storage. No service can guarantee perfect
security, and telemetry payloads can still contain sensitive data if a mod author
or player puts that data into an allowed text field, exception, breadcrumb, log,
or attachment.

## Third-Party Mods And Custom Fields

Alec's Telemetry is configurable. Third-party mod authors can define descriptors,
choose defaults, add report fields, and send descriptor-approved Event Context.
Alec's Telemetry policy forbids non-optional collection of personally
identifiable information through telemetry payloads, but Alec cannot guarantee
that every third-party mod author follows the intended privacy standards.

If you believe a mod is using Alec's Telemetry to collect identifiable user
information without a clear optional choice, report it through the support
contact below.

## Your Rights And Requests

Depending on where you live, you may have rights to access, correct, delete,
export, or object to certain processing of personal information. Apex Web
Solutions LLC will review reasonable privacy requests for hosted platform data.
Some data may be difficult to identify without project IDs, report IDs, portal
account IDs, server IDs, or other details that connect the request to the
relevant records.

To make a request, include enough information to locate the data without sending
extra sensitive information.

Privacy requests are handled through the support contact below. There is no
public self-service request portal in v1. Internal tooling supports dry-run
exports and apply-only deletion or anonymization using portal user IDs,
Discord/GitHub identities, Hytale subject or profile identifiers, report IDs,
follow-up tokens, project IDs, server profile IDs, server IDs, and session IDs.
Some records may be retained for legal, billing, tax, accounting, dispute,
abuse-prevention, or security reasons.

## Incidents

If Apex Web Solutions LLC becomes aware of a privacy or security incident
affecting hosted platform data, the incident response process includes evidence
preservation, containment, affected data review, third-party processor
notification where relevant, and notification decision records. Apex Web
Solutions LLC assesses applicable notice obligations, deadlines, affected-user
notice, processor notice, and elevated sensitivity risks based on the facts of
the incident.

## Changes

This policy may be updated as Alec's Telemetry and the hosted platform change.
The effective date at the top shows when this version was published.

## Contact

Use the Alec's Mods Discord support link for privacy questions, deletion
requests, or suspected telemetry abuse directed to Apex Web Solutions LLC:

https://discord.gg/E8n8RgTTdq
