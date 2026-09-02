---
title: "Reference Ingest Service"
order: 9
published: true
draft: false
---

# Reference Ingest Service

Parent: [Portal And Ingest](/mod/beacon/portal-and-ingest) | [Home](/mod/beacon/home)

The `hosted/` package in this repository is a local/dev prototype and reference implementation for Beacon ingest behavior.

## Purpose

It accepts telemetry uploads from the runtime mod, validates them against the ingest contract, applies basic abuse protections, routes accepted crash alerts to Discord, accepts manual player reports, and exposes file-backed public stats reference routes.

## Status

Treat this package as a reference implementation, not the long-term production backend. The web portal and production backend own the database-backed project model and richer routing behavior.

## Trust Model

- portal `projectKey` values are not treated as secrets
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

For the full payload contract, see [Ingest Contract](/mod/beacon/ingest-contract).
