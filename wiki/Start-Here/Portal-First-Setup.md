---
title: "Portal First Setup"
order: 3
published: true
draft: false
---

# Portal First Setup

Parent: [Start Here](/mod/alecs-telemetry/start-here) | [Home](/mod/alecs-telemetry/home)

This is the ground-up route for a modder who has never used Alec's Telemetry before. Follow it before choosing deeper crash, stats, or runtime API guides.

## What You Need

- A Hytale plugin or asset pack that you can package and install on a test server.
- Access to `https://telemetry.alecsmods.com/portal`.
- A Discord or GitHub account for portal sign-in.
- For standalone dependency mode, the Alec's Telemetry runtime installed alongside your package.
- For embedded mode, a plugin that can call Java runtime bootstrap code.

Content-only asset packs should normally use standalone dependency mode. They can ship a telemetry descriptor and use the installed Alec's Telemetry runtime, but they cannot call custom runtime API methods unless they also include plugin code.

## Create The Portal Project

1. Open `https://telemetry.alecsmods.com/portal`.
2. Sign in with Discord or GitHub.
3. Link the other provider later from Account if you need Discord routing and GitHub issue sync.
4. Review and accept the Terms of Service when prompted.
5. On the projects page, choose `Create project`.
6. Enter a stable project ID.
7. Enter a clear display name players will recognize.
    - This will appear in the consent UI.
8. Save the project.
9. Copy the new project key immediately. The key is shown so you can place it in your packaged descriptor.

Use a short lowercase project ID such as `example-mod`, `alecs-cats`, or `my-asset-pack`. The display name can be friendlier, such as `Example Mod`.

## Choose Your Runtime Mode

Use [Standalone Dependency Mode](/mod/alecs-telemetry/standalone-dependency-mode) when:

- you want the easiest setup
- your plugin or asset pack can use Alec's Telemetry as an external dependency
- your package does not need to own telemetry startup code
- your asset pack has no Java code

Use [Embedded Mode](/mod/alecs-telemetry/embedded-mode) when:

- your plugin should ship one distributable package
- you are comfortable bundling the telemetry runtime
- you want direct lifecycle hooks for setup, start, and shutdown

If you are unsure, use standalone dependency mode first.

## Add The Descriptor

Create this file inside your plugin or asset pack:

```text
Server/Telemetry/project.json
```

Start with this descriptor:

```json
{
  "hosted": {
    "projectKey": "replace_with_your_portal_project_key"
  }
}
```

Replace `replace_with_your_portal_project_key` with the key from the portal. Project keys are publishable ingest keys. They are meant to ship in the descriptor.

The project key is like an API key, so avoid posting it casually. It still cannot be kept truly secret once shipped in a Hytale package, so the Alec's Telemetry portal has some preventative measures in the case of abuse, and you can rotate it from the portal if needed.

The portal project is the source of truth. The project key resolves to one portal project, and portal ingest rejects uploads when the descriptor's `projectId` does not match that portal project ID. If you omit `projectId`, Alec's Telemetry infers it from your manifest `Name`, so choose the portal project ID to match the inferred value or add an explicit override.

This starter descriptor only wires the project to the portal. Telemetry categories are unsupported unless another guide adds an explicit `telemetry` category block.

If another guide says "add this to your descriptor," add that block to this same file instead of creating a second descriptor.

## Add Explicit Identity If Needed

You don't need to add identity fields by default. Plugins with a correct `Group`, `Name`, and `Main` can usually use only the portal project key. Asset packs with correct `Group` and `Name` can still infer `projectId` and `displayName` from `Name`, plus `ownerPluginIdentifiers` from `Group:Name`; they only lack an inferred Java package prefix when there is no `Main`.

You can add explicit identity values when the inferred values are not enough:

- `projectId`: use this when the inferred project ID would not match the portal project ID.
- `displayName`: use this for local runtime, consent, and envelope metadata. The portal project name is managed in the portal.
- `ownerPluginIdentifiers`: use this for renamed plugins, aliases, or unusual ownership matching.
- `packagePrefixes`: use this when Java crash attribution needs packages outside the `Main` class package, or when there is no `Main` but Java stack-prefix attribution still matters.

Example override block:

```json
{
  "projectId": "example-mod",
  "displayName": "Example Mod",
  "ownerPluginIdentifiers": ["Example:Example Mod"],
  "packagePrefixes": ["com.example.examplemod"]
}
```

## Add An Optional Mod Icon For UIs

If you want your project to show a custom icon in `/telemetry consent`, add `ui.iconTexturePath` to the same descriptor:

```json
{
  "ui": {
    "iconTexturePath": "MyModAssets/MyModIcon.png"
  }
}
```

Package the PNG under:

```text
Common/UI/Custom/MyModAssets/MyModIcon.png
```

The descriptor path is relative to `Common/UI/Custom/`, so do not start it with a slash.

## Package The File

1. Build your plugin or asset pack.
2. Install the package on a local or private test server.
3. Install Alec's Telemetry too if you chose standalone dependency mode.

## Verify In Game

Run these commands on the test server:

```text
/telemetry status
/telemetry projects
/telemetry project <project-id>
```

Check that:

- the runtime is enabled
- your project appears in the project list
- the destination mode is `hosted`
- the project has the expected source path
- the project key preview is present or the endpoint is configured
- no telemetry category is supported unless you already added a category-specific block

## Verify In The Portal

1. Return to `https://telemetry.alecsmods.com/portal`.
2. Open the project.
3. Confirm the project exists and the project ID matches the descriptor's inferred or explicit `projectId`.
4. Configure Discord routing if you want alerts.
5. Configure GitHub sync if you want portal issues linked to GitHub.
6. Continue to a category guide before expecting ingest, issues, or stats to appear.
7. If stats are enabled, open the stats page after the first heartbeat has had time to upload.

## Next Pages

- Use [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup) for automated crash reporting only.
- Use [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup) for anonymous public stats only.
- Use [Project Descriptor](/mod/alecs-telemetry/project-descriptor) when you need every descriptor field.
- Use [Command Reference](/mod/alecs-telemetry/command-reference) when a server operator needs the command list.
