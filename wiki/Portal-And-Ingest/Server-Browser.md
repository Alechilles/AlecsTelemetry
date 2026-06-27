---
title: "Server Browser"
order: 7
published: true
draft: false
---

# Server Browser

Parent: [Portal And Ingest](/mod/alecs-telemetry/portal-and-ingest) | [Home](/mod/alecs-telemetry/home)

The server browser lets server owners create public ModStats listings that are verified by recent Alec's Telemetry heartbeats. A verified listing can show the server name, description, logo, banner, links, join information, player counts, and public mod list depending on the server owner's privacy settings.

Public server pages are separate from mod project pages, but they connect to them. A public mod stats page can show verified servers running that mod, and a public server page can show mods detected from telemetry or added manually by the server owner.

## Before This Page

Set up at least one telemetry-enabled mod on the server first:

1. Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) for your mod or asset pack.
2. Enable stats with [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup) or [Anonymous Usage Stats](/mod/alecs-telemetry/anonymous-usage-stats).
3. Install the project on the server.
4. Start the server and wait for a stats heartbeat.

The server browser verification command is provided by the Alec's Telemetry runtime, so standalone installations must have Alec's Telemetry installed and embedded installations must bootstrap the runtime correctly.

## Create A Server Profile

1. Open [the portal](https://telemetry.alecsmods.com/portal).
2. Sign in with the account that should own the server listing.
3. Open **Servers**.
4. Choose **New profile**.
5. Enter a server name and public slug.
6. Add optional profile details:
   - short description
   - long description
   - logo URL
   - banner URL
   - website URL
   - Discord URL
   - join address
   - join instructions
7. Choose which details can be public:
   - **Public listing**
   - **Public players**
   - **Public mod list**
   - **Public join info**
8. Save the profile.

Leave **Public listing** enabled if the server should appear in the public server directory after verification.

## Verify Server Ownership

After saving the profile, the portal shows a claim token and a command like this:

```text
/telemetry server verify <claim-token>
```

Run that command on the Hytale server you want to bind to the profile. You can run it in the server console or as a server operator in chat.

When the command succeeds, Alec's Telemetry saves the claim token with the server identity and sends a verification heartbeat immediately. The portal refreshes pending claims, then marks the profile as verified once the heartbeat binds the profile to the server.

## Manage The Public Mod List

The server profile can show two kinds of mod entries:

- **Telemetry-observed mods**: mods detected from recent telemetry heartbeats.
- **Manual mods**: extra entries the server owner adds in the portal for mods that do not report through Alec's Telemetry.

Use the profile editor to hide observed mods that should not be public, or add manual mods for important non-telemetry dependencies.

## Verify Public Visibility

1. Open the server profile in the portal.
2. Confirm the profile is verified.
3. Confirm **Public listing** is enabled.
4. Open the public server directory.
5. Search for the server name or slug.
6. Open the public server page and confirm the public details match the selected privacy toggles.
7. Open a public mod stats page for a telemetry-observed mod and confirm the server appears under verified servers running that mod.

## Troubleshooting

- No claim command appears: rotate the claim token from the server profile.
- Command unknown: install Alec's Telemetry or confirm the embedded runtime is bootstrapped.
- Profile remains unverified: make sure the command ran on the intended server and wait for the verification heartbeat.
- Server not public: confirm **Public listing** is enabled and the profile is not disabled by moderation.
- Player counts hidden: enable **Public players**.
- Mod list hidden: enable **Public mod list** and confirm stats heartbeats are uploading.
- Join address hidden: enable **Public join info**.
