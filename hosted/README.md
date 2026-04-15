# Alec's Telemetry Hosted Service

This package contains the hosted ingest service for Alec's Telemetry.

## Purpose

It accepts crash telemetry uploads from the runtime mod, validates them against the
hosted contract, applies basic abuse protections, and routes accepted crash alerts
to Discord.

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
- `POST /api/v1/ingest/crash`

See `../docs/hosted-ingest-contract.md` for the exact contract.
