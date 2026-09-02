---
title: "Server Browser"
order: 7
published: true
draft: false
---

# Server Browser

Parent: [Portal And Ingest](/mod/beacon/portal-and-ingest) | [Home](/mod/beacon/home)

The server browser lets server owners create public ModStats listings that are verified by recent Beacon heartbeats. A verified listing can show the server name, description, logo, banner, links, join information, player counts, and public mod list depending on the server owner's privacy settings.

Public server pages are separate from mod project pages, but they connect to them. A public mod stats page can show verified servers running that mod, and a public server page can show mods detected from telemetry or added manually by the server owner.

## Before This Page

You do not need to own a mod project or complete [Portal First Setup](/mod/beacon/portal-first-setup) to list a server. Server listings are owned by server operators, not by mod authors.

Before creating the listing:

1. Install Beacon on the Hytale server.
2. Start the server at least once.
3. Make sure you can run server console commands or operator chat commands.

The server browser verification command is provided by the Beacon runtime. It can verify the server profile even if no installed mod has created its own portal project.

## Create A Server Profile

1. Open [the portal](https://beacon.modstats.io/portal).
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
/beacon server verify <claim-token>
```

Run that command on the Hytale server you want to bind to the profile. You can run it in the server console or as a server operator in chat.

When the command succeeds, Beacon saves the claim token with the server identity and sends a verification heartbeat immediately. The portal refreshes pending claims, then marks the profile as verified once the heartbeat binds the profile to the server.

## Manage The Public Mod List

The server profile can show two kinds of mod entries:

- **Telemetry-observed mods**: mods detected from installed projects that report public stats through Beacon.
- **Manual mods**: extra entries the server owner adds in the portal for mods that do not report through Beacon.

Use the profile editor to hide observed mods that should not be public, or add manual mods for important mods that do not report through Beacon.

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
- Command unknown: install Beacon on the server and restart it.
- Profile remains unverified: make sure the command ran on the intended server and wait for the verification heartbeat.
- Server not public: confirm **Public listing** is enabled and the profile is not disabled by moderation.
- Player counts hidden: enable **Public players**.
- Mod list missing entries: add manual mods for projects that do not report public stats through Beacon.
- Join address hidden: enable **Public join info**.
