---
title: "Ingest Contract"
order: 8
published: true
draft: false
---

# Ingest Contract

Parent: [Portal And Ingest](/mod/beacon/portal-and-ingest) | [Home](/mod/beacon/home)

This page describes the telemetry ingest contract used by the standalone runtime, embedded bootstrap consumers, and Beacon web portal backend.

## Trust Model

- The client includes the portal `projectKey`.
- The key is treated as public, not secret.
- Protection comes from validation and backend limits, not from the client keeping the key hidden.
- Key rotation is handled by updating portal project config and the mod descriptor.

## Canonical Backend

The production backend is Beacon Platform. The `hosted/` package in this repo is a reference/dev implementation and should not be treated as the long-term production backend.

## Endpoints

The hosted base URL is `https://beacon.modstats.io`. The paths below are
relative to that URL and are unchanged from Alec's Telemetry 1.x.

```text
POST /ingest/crash
POST /ingest/event
POST /ingest/report
POST /reports/status
```

`/ingest/crash` remains the compatibility crash endpoint. `/ingest/event` is the canonical portal ingest endpoint for both crash envelopes and normal event envelopes.
`/ingest/report` accepts manual player issue and suggestion reports. `/reports/status`
checks a local player receipt using `reportId` plus the raw follow-up token.

## Required Headers

- `Content-Type: application/json`
- `X-Telemetry-Project-Key: <projectKey>`

## Crash Report Body

Crash reports use the crash envelope emitted by the runtime mod.

Important fields:

- `schemaVersion`
- `eventType`
- `reportId`
- `projectId`
- `projectDisplayName`
- `source`
- `fingerprint`
- `capturedAtUtc`
- `lastCapturedAtUtc`
- `occurrenceCount`
- `pluginIdentifier`
- `pluginVersion`
- `threadName`
- `attribution`
- `breadcrumbs`
- `throwable`
- `runtime`

## Normal Event Body

Normal event envelopes use:

- `schemaVersion`
- `eventType`
- `eventName`
- `eventId`
- `projectId`
- `projectDisplayName`
- `source`
- `sessionId`
- `serverId`
- `fingerprint`
- `capturedAtUtc`
- `pluginIdentifier`
- `pluginVersion`
- `worldName`
- `severity`
- `durationMs`
- `metricValue`
- `subsystem`
- `phase`
- `operation`
- `target`
- `featureKey`
- `entryPoint`
- `runtimeSide`
- `entityType`
- `itemId`
- `blockId`
- `biomeId`
- `commandName`
- `environment`
- `attributes`
- `details`
- `runtime`

Normal `eventType` values include:

- `error`
- `lifecycle`
- `performance`
- `usage`
- `stats`

`details` is reserved for descriptor-validated custom Event Context fields and bounded debug context such as breadcrumbs on error or failed lifecycle events. Standardized context fields are first-class event envelope fields; custom `detail(key, value)` entries are uploaded only after descriptor allowlist validation.

Stats events use `eventType: "stats"` and `eventName: "heartbeat"`. The public stats API exposes standard server/player history and environment breakdown charts without exposing raw `serverId`, `sessionId`, IP addresses, player identifiers, chat, coordinates, secrets, or full config files.

## Manual Report Body

Manual reports use `eventType: "manual_report"` and `reportKind: "issue"` or
`"suggestion"`. Important fields include:

- `reportId`
- `followUpTokenHash`
- `projectId`
- `projectDisplayName`
- `submittedAtUtc`
- `sessionId`
- `serverId`
- `pluginIdentifier`
- `pluginVersion`
- `title`
- `description`
- `contact`
- `formValues`
- `attachmentManifests`
- `attachments`
- `context`
- `environment`
- `runtime`

Status lookup posts:

```json
{
  "reportId": "uuid",
  "followUpToken": "raw-local-token"
}
```

The status response does not expose contact information or the full report payload.

## Validation Rules

The portal backend should reject or throttle when:

- the project key is missing
- the project key does not map to a known portal project
- the project is disabled
- `projectId` does not match the project mapped by the key
- the request body exceeds the global or per-project size limit
- the body is not valid JSON
- the body does not match either the crash envelope schema or event envelope schema
- the event type or event name is not allowed for the portal project
- the project exceeds its request-per-minute budget

## Response Codes

- `202 Accepted`: report accepted.
- `400 Bad Request`: invalid JSON or schema mismatch.
- `401 Unauthorized`: missing project key.
- `403 Forbidden`: unknown key, disabled project, or `projectId` mismatch.
- `413 Payload Too Large`: request exceeds the size limit.
- `429 Too Many Requests`: project exceeded rate limits.
- `500 Internal Server Error`: unexpected server failure.

## Discord Routing Behavior

After accepting a report, the portal backend may dispatch a Discord alert or suppress the alert if the same `projectId + fingerprint` was recently alerted.

Suppression should not reject the ingest request. It only reduces notification spam.
