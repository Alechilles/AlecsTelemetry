---
title: "Migrate To Beacon 2.0"
order: 9
published: true
draft: false
---

# Migrate To Beacon 2.0

Parent: [Start Here](/mod/beacon/start-here) | [Home](/mod/beacon/home)

Beacon 2.0 is a breaking rename from Alec's Telemetry 1.x. There is no
namespace compatibility layer. Update each integration surface before you
build or publish the new version.

## Build Coordinates

Use the Beacon Maven repository:

```text
https://beacon.modstats.io/maven/releases
```

Replace the old coordinates as follows:

| Alec's Telemetry 1.x | Beacon 2.x |
| --- | --- |
| `com.alechilles:alecstelemetry-parent` | `com.alechilles:beacon-parent` |
| `com.alechilles:alecstelemetry-runtime` | `com.alechilles:beacon-runtime` |
| `com.alechilles:alecstelemetry` | `com.alechilles:beacon` |

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

For Gradle, use for example:

```kotlin
implementation("com.alechilles:beacon-runtime:2.0.0")
```

Old Maven coordinates remain read-only historical artifacts. They do not
provide Beacon 2.x.

## Manifest And Java Names

Replace the manifest dependency:

```json
"Alechilles:Alec's Telemetry!": ">=0.1.0"
```

with:

```json
"Alechilles:Beacon": ">=2.0.0"
```

Change Java imports from `com.alechilles.alecstelemetry...` to
`com.alechilles.beacon...`. Change an explicitly configured standalone main
class from `com.alechilles.alecstelemetry.AlecsTelemetry` to
`com.alechilles.beacon.Beacon`.

## Descriptors And Embedded Mode

Move the conventional descriptor:

```text
Server/Telemetry/project.json -> Server/Beacon/project.json
```

Move passive and contribution resources:

```text
META-INF/alecs-telemetry/projects/ -> META-INF/beacon/projects/
```

Change embedded bootstrap imports to `com.alechilles.beacon.embedded` and use
`beacon-runtime`. Continue to publish the runtime `Common/**` asset pack,
including `Common/UI/Custom/TelemetryConsentPage.ui`. Beacon 2.x does not
search old descriptor or resource paths.

## Command And Origin

Replace `/telemetry` with `/beacon`. Replace the permission prefix
`telemetry.command.telemetry` with `beacon.command.beacon`. There is no old
command alias.

Replace the hosted origin:

```text
https://telemetry.alecsmods.com -> https://beacon.modstats.io
```

Keep the route names `/ingest/crash`, `/ingest/event`, and `/ingest/report`.

## Local Data

Beacon uses this canonical root:

```text
mods/Alechilles_Beacon/
```

At the first Beacon startup, the runtime makes one copy-only migration from
known old roots, including `mods/Alechilles_Alec's Telemetry!/`,
`mods/Alechilles_Alec's Telemetry/`, save-level `Telemetry/` or `telemetry/`
roots, and embedded owner `Telemetry/Settings/projects/` roots. It copies each
known state file only when the Beacon destination is absent. It keeps every old
source. If both files exist, Beacon uses the canonical file and keeps the old
file. It does not read legacy owner override paths after the migration attempt.

## Checklist

- update Maven or Gradle coordinates and the repository URL
- update a required `Alechilles:Beacon` manifest dependency
- update Java imports and an explicit main class
- move conventional and namespaced descriptor resources
- update embedded bootstrap and publish `Common/**` assets
- change commands, permissions, and hosted origin
- let the first Beacon startup migrate local state before reviewing consent
