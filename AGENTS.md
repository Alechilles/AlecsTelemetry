# AGENTS.md

This is the default operating contract for human and AI contributors working in this repo.

## Privacy Policy Sync

- The canonical privacy policy lives at `docs/privacy-policy.md`.
- Any change that affects what Alec's Telemetry collects, generates, stores locally, uploads, exposes publicly, shares with integrations, or retains must update the privacy policy in the same change.
- This includes runtime or descriptor changes for consent categories, crash/error/event payloads, stats heartbeats, manual reports, attachments, local queues, server identity, report receipts, custom endpoints, redaction, retention, player/server identifiers, public stats, or ModStats server verification.
- If a platform change in `AlecsTelemetryPlatform` affects the hosted service, portal accounts, OAuth, billing, GitHub/Discord integrations, web analytics, ingest validation, stats storage, public stats APIs, retention, or public server listings, update this repo's `docs/privacy-policy.md` as part of that work.
- If the implementation changes but the policy does not need a wording change, call that out explicitly in the final summary or PR notes.

## Documentation Expectations

- Keep player-facing privacy language concrete and current-state focused.
- Do not promise that third-party mods cannot send sensitive data unless the code enforces it.
- Separate Alec's hosted platform behavior from custom endpoint behavior.
- Preserve unrelated dirty worktree changes and stage only the requested scope.
