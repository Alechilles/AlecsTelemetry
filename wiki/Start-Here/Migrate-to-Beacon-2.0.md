---
title: "Migrate To Beacon 2.0"
order: 9
published: true
draft: false
---

# Migrate To Beacon 2.0

Parent: [Start Here](/mod/beacon/start-here) | [Home](/mod/beacon/home)

Beacon 2.0 replaces Alec's Telemetry 1.x. Follow the sections that apply to
your server or mod. Java integrations must use the new namespace, but existing
descriptor paths remain supported.

## Server Owners

### Commands And Permissions

Use `/beacon` instead of `/telemetry`. There is no old command alias.
Update any scripts or instructions that use the old command.

If you assigned explicit command permissions, replace the
`telemetry.command.telemetry` prefix with `beacon.command.beacon`, including
subcommand permissions. See the [Command Reference](/mod/beacon/command-reference).

### Settings And Local Data

Beacon stores runtime settings and data under:

```text
mods/Alechilles_Beacon/
```

At first startup, Beacon copies known state files from supported old data
folders when the destination file is absent. It keeps the old files. If both
copies exist, Beacon uses the file in the Beacon folder. Make future settings
changes there, not in the old folder. See
[Runtime Overrides](/mod/beacon/runtime-overrides) for details.

Beacon automatically uses the new hosted ingest URLs when it loads settings
that contain the old default URLs. You do not need to edit those defaults.
Custom endpoint URLs stay unchanged; review them only if they point to the old
host, as described under Custom Integrations below.

## Mod Authors

Keep your existing portal project and `projectId`. The Beacon rename does not
require a new project or changes to your project's consent records or override
filenames.

### Manifest Dependency

If your mod requires the standalone runtime, replace its old manifest
dependency:

```json
"Alechilles:Alec's Telemetry!": ">=0.1.0"
```

with:

```json
"Alechilles:Beacon": ">=2.0.0"
```

Update the dependency listing on your mod's download page as well. Mods that
intentionally work without telemetry do not need to add a required dependency.

### Descriptor Paths

Use the new path when you update a conventional descriptor:

```text
Server/Telemetry/project.json -> Server/Beacon/project.json
```

For passive and contribution descriptors, use:

```text
META-INF/alecs-telemetry/projects/ -> META-INF/beacon/projects/
```

Beacon 2.x still accepts `Server/Telemetry/project.json`,
`telemetry/project.json`, and `META-INF/alecs-telemetry/projects/` as deprecated
fallbacks. Existing descriptor-only integrations do not need an immediate path
change. The Beacon path takes precedence when both forms declare the same
project.

### Java And Embedded Integrations

Change Java imports from `com.alechilles.alecstelemetry...` to
`com.alechilles.beacon...` and rebuild your mod. There are no aliases for the old
Java namespace. Your own mod's main class does not change.

Replace the `com.alechilles:alecstelemetry-runtime` dependency with
`com.alechilles:beacon-runtime`.

Use the Beacon Maven repository:

```text
https://beacon.modstats.io/maven/releases
```

For Maven, add the Beacon repository and depend on the runtime artifact:

```xml
<repository>
  <id>beacon-releases</id>
  <url>https://beacon.modstats.io/maven/releases</url>
</repository>
<dependency>
  <groupId>com.alechilles</groupId>
  <artifactId>beacon-runtime</artifactId>
  <version>2.0.0</version>
</dependency>
```

For Gradle Kotlin DSL:

```kotlin
repositories {
    maven {
        url = uri("https://beacon.modstats.io/maven/releases")
    }
}

dependencies {
    implementation("com.alechilles:beacon-runtime:2.0.0")
}
```

The old Maven coordinates do not provide Beacon 2.x.

For embedded mode, change bootstrap imports to `com.alechilles.beacon.embedded`.
Continue to package the runtime `Common/**` assets, including
`Common/UI/Custom/TelemetryConsentPage.ui`. See
[Embedded Mode](/mod/beacon/embedded-mode) for packaging instructions.

## Custom Integrations

If your scripts, external clients, or custom endpoint settings explicitly use
`https://telemetry.alecsmods.com`, change that base URL to
`https://beacon.modstats.io`. Do not change URLs for a separate service that you
operate.

The rename does not change the ingest endpoint paths. See the
[Ingest Contract](/mod/beacon/ingest-contract) for paths and request formats.
