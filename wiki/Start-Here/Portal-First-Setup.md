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
- Permission to add files to your plugin or asset pack release package.
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
7. Enter a clear display name players will recognize, especially if you choose to display live stats.
8. Create the project.
9. Copy the new project key immediately. The key is shown so you can place it in your packaged descriptor.

Use a short lowercase project ID such as `example-mod`, `alecs-cats`, or `my-asset-pack`. The display name can be friendlier, such as `Example Mod`.

> [Screenshot Placeholder: Portal create-project form with Project ID, Display name, and the one-time project key result.]

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

Replace `replace_with_your_portal_project_key` with the key from the portal. Hosted project keys are publishable ingest keys. They are meant to ship in the descriptor.

The project key is like an API key, so avoid posting it casually. It still cannot be kept truly secret once shipped in a Hytale package, so Alec's hosted portal treats it as a scoped ingest key rather than an admin secret. You can rotate it from the portal if needed.

If another guide says "add this to your descriptor," add that block to this same file instead of creating a second descriptor.

## Add Explicit Identity When Needed

Plugins with a correct `Group`, `Name`, and `Main` can often use only the hosted key. Asset packs, descriptor-only packages, or plugins with unusual identity should add explicit identity alongside the existing `hosted` block:

```json
{
  "projectId": "example-mod",
  "displayName": "Example Mod",
  "ownerPluginIdentifiers": ["Example:Example Mod"],
  "packagePrefixes": ["com.example.examplemod"]
}
```

## Add An Optional Consent Icon

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
2. Confirm the built package contains `Server/Telemetry/project.json`.
3. If you configured an icon, confirm the built package contains the PNG under `Common/UI/Custom/...`.
4. Install the package on a local or private test server.
5. Install Alec's Telemetry too if you chose standalone dependency mode.

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

Then queue a safe test report:

```text
/telemetry test <project-id> first-setup-check
/telemetry flush <project-id>
```

## Verify In The Portal

1. Return to `https://telemetry.alecsmods.com/portal`.
2. Open the project.
3. Check that the project card or issue workspace shows recent ingest.
4. Open the issue created by the test report.
5. Confirm the project name, version, source, and details look correct.
6. Configure Discord routing if you want alerts.
7. Configure GitHub sync if you want portal issues linked to GitHub.
8. If stats are enabled, open the stats page after the first heartbeat has had time to upload.

## Next Pages

- Use [Quick Crash Setup](/mod/alecs-telemetry/quick-crash-setup) for automated crash reporting only.
- Use [Quick Stats Setup](/mod/alecs-telemetry/quick-stats-setup) for anonymous public stats only.
- Use [Project Descriptor](/mod/alecs-telemetry/project-descriptor) when you need every descriptor field.
- Use [Command Reference](/mod/alecs-telemetry/command-reference) when a server operator needs the command list.
