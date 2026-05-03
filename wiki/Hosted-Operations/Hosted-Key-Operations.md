---
title: "Hosted Key Operations"
order: 2
published: true
draft: false
---

# Hosted Key Operations

Parent: [Hosted Operations](/mod/alecs-telemetry/hosted-operations) | [Home](/mod/alecs-telemetry/home)

Hosted `projectKey` values are publishable ingest keys. They are intended to be baked into shipped mod descriptors so telemetry works out of the box for the mod author.

## Security Model

- Publishable ingest keys can only write telemetry for one project.
- They cannot read project data from the portal.
- They cannot manage memberships, billing, or configuration.
- They are still rate-limited and project-scoped on the platform side.

## Rotation Model

Dual-key rotation is supported in Alec's Telemetry Platform deployments that include the grace-key feature.

The lightweight reference hosted backend in this repository still uses a single publishable key with manual rotation, so older mod versions stop ingesting as soon as that backend key changes.

- One active key remains the primary key shown in the portal.
- One grace key may continue to ingest temporarily after rotation.
- This lets older released mod versions continue sending telemetry while a new build is rolling out.

## Recommended Rotation Flow

1. Rotate the key in the portal.
2. Update the shipped `telemetry/project.json` with the new `projectKey`.
3. Publish the updated mod build.
4. Wait for the grace period to expire.
5. Verify old versions are no longer using the grace key.

## Abuse Response

If a publishable ingest key is abused:

1. Tighten project policy in the portal.
2. Lower rate limits or reduce allowed event types/names where needed.
3. Rotate the key.
4. Ship a new mod build with the new key.
5. Use the grace period only as long as needed.

## Operator Overrides

Server owners do not need an override for normal hosted telemetry.

Overrides remain useful for:

- disabling telemetry
- redirecting telemetry to another backend
- swapping keys for local testing
- changing sampling or allowlists locally
