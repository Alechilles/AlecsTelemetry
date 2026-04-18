# Hosted Key Operations

Hosted `projectKey` values are publishable ingest keys.

They are intended to be baked into shipped mod descriptors so telemetry works out
of the box for the mod author.

## Security Model

- publishable ingest keys can only write telemetry for one project
- they cannot read project data from the portal
- they cannot manage memberships, billing, or configuration
- they are still rate-limited and project-scoped on the platform side

## Rotation Model

Dual-key rotation is supported in Alec's Telemetry Platform deployments that
include the grace-key feature.

The lightweight reference hosted backend that lives in this repository still
uses a single publishable key with manual rotation, so older mod versions stop
ingesting as soon as that backend key changes.

- one active key remains the primary key shown in the portal
- one grace key may continue to ingest temporarily after rotation
- this lets older released mod versions continue sending telemetry while a new
  build is rolling out

## Recommended Rotation Flow

1. rotate the key in the portal
2. update the shipped `telemetry/project.json` with the new `projectKey`
3. publish the updated mod build
4. wait for the grace period to expire
5. verify old versions are no longer using the grace key

## Abuse Response

If a publishable ingest key is abused:

1. tighten project policy in the portal
   - lower rate limits
   - reduce allowed event types/names
2. rotate the key
3. ship a new mod build with the new key
4. use the grace period only as long as needed

## Operator Overrides

Server owners do not need an override for normal hosted telemetry.

Overrides remain useful for:

- disabling telemetry
- redirecting telemetry to another backend
- swapping keys for local testing
- changing sampling or allowlists locally
