# Alec's Telemetry Privacy Policy

Effective date: July 24, 2026

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
- Consent metrics are collected separately and are limited to project/version,
  category choice, and first-review funnel events.
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
- server-owner consent choices and runtime overrides
- server identity and verification values used for ModStats server claiming
- queued telemetry waiting to upload
- manual report drafts, review queues, submitted receipts, and local follow-up
  tokens
- local runtime settings for manual reports, attachments, endpoints, and related
  controls

Local files stay on the server or local machine unless the configured telemetry
category or report workflow uploads them.

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

Crash messages and stack traces come from the running software. Mod authors should
avoid putting secrets, player names, coordinates, tokens, or other identifying
values into exception messages, breadcrumbs, or custom event context.

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

The hosted service may use raw server IDs, session IDs, loaded mod evidence, and
private heartbeat payloads internally to compute aggregates, prevent duplicates,
verify server claims, and maintain the public stats data. Public stats responses
must expose only aggregate counts and breakdowns.

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
- Crash report metadata and raw JSON, manual report metadata, and manual report
  raw JSON are retained for 730 days by default.
- Manual report attachments and log attachments are retained for 180 days by
  default.
- Consent metrics and first-review funnel events are retained for 730 days by
  default.
- Portal audit logs and security records are retained for 1095 days by default.
- Aggregate public stats headline rollups, issue workflow/activity, project
  configuration, and public server listing history are retained while the project
  or server profile exists. Per-server stats bucket snapshots are retained for a
  recent active/evidence window of 30 days by default, and per-server loaded-mod
  bucket snapshots are retained for 7 days by default; both are deleted after
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
