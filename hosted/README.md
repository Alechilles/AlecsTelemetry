# Alec's Telemetry Hosted Service

This package contains the hosted ingest service for Alec's Telemetry.

## Status

This package should be treated as a local/dev prototype and reference implementation.

The intended long-term production backend is the existing VPS service in
`HytaleModWikiBot`, which already owns the running Discord bot, deployment flow,
and database-backed crash routing behavior.

## Purpose

It accepts crash telemetry and generic event uploads from the runtime mod,
validates them against the hosted contract, applies basic abuse protections, and
routes accepted crash alerts to Discord. It also includes a file-backed reference
implementation for public aggregate Hytale usage statistics.

## Trust Model

- `publicProjectKey` is not treated as secret
- key rotation is manual by editing the projects config and restarting the service
- the backend protects itself with validation, size limits, rate limits, and alert
  suppression

## Setup

1. Copy `.env.example` to `.env`
2. Copy `config/projects.example.json` to `config/projects.json`
3. Fill in your Discord bot token and project/channel settings
4. Run `npm install`
5. Run `npm run dev`

## Endpoints

- `GET /healthz`
- `POST /ingest/crash`
- `POST /ingest/event`
- `GET /api/v1/projects`
- `GET /api/v1/projects/:projectId/summary`
- `GET /api/v1/projects/:projectId/charts/:chartId`

See `../docs/hosted-ingest-contract.md` for the exact contract.
