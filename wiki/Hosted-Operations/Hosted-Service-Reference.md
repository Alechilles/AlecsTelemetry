---
title: "Hosted Service Reference"
order: 8
published: true
draft: false
---

# Hosted Service Reference

Parent: [Hosted Operations](/mod/alecs-telemetry/hosted-operations) | [Home](/mod/alecs-telemetry/home)

The `hosted/` package in this repository is a local/dev prototype and reference implementation for Alec's Telemetry ingest behavior.

## Purpose

It accepts telemetry uploads from the runtime mod, validates them against the hosted contract, applies basic abuse protections, routes accepted crash alerts to Discord, accepts manual player reports, and exposes file-backed public stats reference routes.

## Status

Treat this package as a reference implementation, not the long-term production backend. The hosted platform owns the production portal, database-backed project model, and richer routing behavior.

## Trust Model

- `publicProjectKey` is not treated as secret.
- Key rotation is manual for the reference service.
- The backend protects itself with validation, size limits, rate limits, and alert suppression.

## Local Setup

1. Copy `.env.example` to `.env`.
2. Copy `config/projects.example.json` to `config/projects.json`.
3. Fill in the Discord bot token and project/channel settings.
4. Run `npm install`.
5. Run `npm run dev`.

## Reference Endpoints

- `GET /healthz`
- `POST /ingest/crash`
- `POST /ingest/event`
- `POST /ingest/report`
- `POST /reports/status`
- `GET /api/v1/projects`
- `GET /api/v1/projects/:projectId/summary`
- `GET /api/v1/projects/:projectId/charts/:chartId`

For the full hosted payload contract, see [Hosted Ingest Contract](/mod/alecs-telemetry/hosted-ingest-contract).
