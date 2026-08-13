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

## Build and Local Runtime

- Build from the shared workspace with `bash ../gradlew :alecstelemetry:standalone:build`; the runtime module remains an embeddable library and the standalone module produces the shaded plugin JAR.
- Do not place the standalone Telemetry plugin in the default `runAllMods` workspace alongside Tamework, which already embeds the runtime. Use `C:\Users\22ale\AppData\Roaming\Hytale\Modding\run\mods` for local workspace staging and treat `UserData\Mods` as legacy comparison-only state.

## Release Distribution

- Each Alec's Telemetry release has two deliverables from the same committed
  source: the standalone plugin JAR and the embeddable runtime Maven artifact.
- Release prep must record the SHA-256 for both JARs. It must also stage the
  runtime POM and parent POM for the exact release version.
- Release prep must not change the production downloads site. The live publish
  step must upload the runtime JAR, runtime POM, parent POM, checksum sidecars,
  and Maven metadata to the production downloads repository.
- The verified production root is
  `/srv/apps/alecs-telemetry-downloads/maven/releases` on the current Telemetry
  VPS. Use the `alecs-telemetry-vps` skill before you change this path.
- Do not mark a release as published until all these checks pass:
  - `/api/v1/downloads` lists the new version as `latestRuntime`.
  - `/downloads/runtime/latest` redirects to the new runtime JAR.
  - The versioned runtime JAR and POM return HTTP 200 from `/maven/releases`.
  - The downloaded runtime JAR has the same SHA-256 as the staged artifact.
