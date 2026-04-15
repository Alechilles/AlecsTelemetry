# Hosted Ingest Contract

This document defines the Alec-hosted crash telemetry ingest contract used by the
standalone runtime, embedded bootstrap consumers, and the hosted service.

## Trust Model

- the client includes a `publicProjectKey`
- the key is treated as public, not secret
- protection comes from validation and backend limits, not from the client keeping
  the key hidden
- key rotation is manual by updating hosted project config and the mod descriptor

## Canonical Backend

The payload contract in this document is intended to be implemented by the canonical
hosted backend running with `HytaleModWikiBot` on the VPS.

The `hosted/` package in this repo is a reference/dev implementation and should not
be treated as the long-term production backend.

## Endpoint

```text
POST /api/v1/ingest/crash
```

## Required Headers

- `Content-Type: application/json`
- `X-Telemetry-Project-Key: <publicProjectKey>`

## Request Body

The body is the crash envelope emitted by the runtime mod.

Important fields:

- `schemaVersion`
- `eventType`
  - currently must be `crash`
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

## Validation Rules

The hosted service should reject or throttle when:

- the project key is missing
- the project key does not map to a known hosted project
- the project is disabled
- `projectId` does not match the project mapped by the key
- the request body exceeds the global or per-project size limit
- the body is not valid JSON
- the body does not match the crash envelope schema
- the project exceeds its request-per-minute budget

## Response Codes

### `202 Accepted`

The report was accepted.

Example response:

```json
{
  "accepted": true,
  "projectId": "example-consumer-mod",
  "fingerprint": "abc123",
  "alertDispatched": true,
  "alertSuppressed": false
}
```

### `400 Bad Request`

Invalid JSON or schema mismatch.

### `401 Unauthorized`

Missing project key.

### `403 Forbidden`

Unknown key, disabled project, or `projectId` mismatch.

### `413 Payload Too Large`

The request exceeds the global or per-project size limit.

### `429 Too Many Requests`

The project exceeded rate limits.

### `500 Internal Server Error`

Unexpected server failure.

## Discord Routing Behavior

After accepting a report, the hosted service may either:

- dispatch a Discord alert, or
- suppress the alert if the same `projectId + fingerprint` was recently alerted

Suppression should not reject the ingest request. It only reduces notification spam.

## Manual Key Rotation

Key rotation is intentionally manual.

To rotate:

1. update `publicProjectKey` in the hosted project registry
2. update the mod's `telemetry/project.json`
3. redeploy the mod or descriptor update
4. restart the hosted service if required by the deployment setup
