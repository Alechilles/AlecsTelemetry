# Embedded Mode

Embedded mode lets a modder bundle the telemetry runtime inside their own mod instead
of requiring the standalone `Alec's Telemetry!` dependency.

## When To Use It

Use embedded mode when a modder wants:

- one distributable mod package
- no extra CurseForge dependency for players
- direct ownership of telemetry bootstrap and lifecycle wiring

## Runtime Artifact

Get the embeddable runtime from Alec's Telemetry downloads:

```text
https://telemetry.alecsmods.com/downloads
```

For Maven builds, add the public repository and depend on the runtime artifact:

```xml
<repository>
  <id>alecs-telemetry</id>
  <url>https://telemetry.alecsmods.com/maven/releases</url>
</repository>

<dependency>
  <groupId>com.alechilles</groupId>
  <artifactId>alecstelemetry-runtime</artifactId>
  <version>1.3.0</version>
</dependency>
```

For Gradle Kotlin DSL builds, use the same Maven-format repository:

```kotlin
repositories {
    maven {
        url = uri("https://telemetry.alecsmods.com/maven/releases")
    }
}

dependencies {
    implementation("com.alechilles:alecstelemetry-runtime:1.3.0")
}
```

Package that runtime jar inside your mod. Do not ask server owners to install the
runtime jar separately when you choose embedded mode.

## Publish The Client UI Asset Pack

An embedded host must publish the runtime's `Common/**` resources to Hytale
clients. The final mod package must:

- set `IncludesAssetPack` to `true` in `manifest.json`
- contain every `Common/**` resource from `alecstelemetry-runtime`
- expose the merged resources as the host's development asset-pack source

Shading the runtime classes into the host jar is not sufficient. If the client
cannot load `Common/UI/Custom/TelemetryConsentPage.ui`, `/telemetry consent`
disconnects the client.

This Gradle Groovy DSL example merges the host resources with the runtime UI.
It uses the AzureDoom Hytale Gradle plugin names:

```groovy
def telemetryVersion = '1.3.0'
def telemetryCoordinates =
        "com.alechilles:alecstelemetry-runtime:${telemetryVersion}"
def embeddedAssetPackDirectory =
        layout.buildDirectory.dir('generated/embeddedAssetPack')

configurations {
    telemetryAssetPack {
        canBeConsumed = false
        canBeResolved = true
        transitive = false
    }
}

dependencies {
    implementation telemetryCoordinates
    telemetryAssetPack telemetryCoordinates
}

sourceSets.main.resources.setSrcDirs([embeddedAssetPackDirectory])

hytaleTools {
    includesPack = true
    assetPackSourceDirectory = embeddedAssetPackDirectory
}

tasks.register('prepareEmbeddedAssetPack', Sync) {
    from(layout.projectDirectory.dir('src/main/resources'))
    from({ configurations.telemetryAssetPack.collect { zipTree(it) } }) {
        include 'Common/**'
    }
    into(embeddedAssetPackDirectory)
}

tasks.named('classes') {
    dependsOn(tasks.named('prepareEmbeddedAssetPack'))
}

tasks.named('processResources') {
    dependsOn(tasks.named('prepareEmbeddedAssetPack'))
}

tasks.named('prepareRunServer') {
    dependsOn(tasks.named('prepareEmbeddedAssetPack'))
}
```

If the host already prepares an asset pack, merge the same `Common/**` tree into
that source instead of creating a second pack. A descriptor-only library does
not do this work. The installed standalone or embedded runtime host publishes
the shared UI for descriptor-only projects.

## Descriptor Shape

Embedded mode uses the same `Server/Telemetry/project.json` descriptor as the standalone dependency flow. The descriptor does not need a runtime-mode flag; embedded behavior comes from packaging the runtime and bootstrapping it from the owning mod.

Every installed copy of Alec's Telemetry, standalone or embedded, registers as a
runtime coordinator candidate. The latest compatible runtime version wins.
Non-winning copies become passive clients and forward telemetry operations to
the active coordinator. If standalone is installed but an embedded copy is
newer, standalone can still provide commands and UI while the newer embedded
runtime owns capture, queueing, and upload.

For a mod that owns more than one logical telemetry project, use the anchored
contribution API described in [Embedded Contributions](embedded-contributions.md).
Each contribution has its own descriptor, logical plugin identity, consent state,
destination, and queue even when all of its services share one host-local
provider.

In the 1.1.0 MVP, anchored contributions must use hosted destination mode;
custom destinations remain available to conventional `bootstrap(plugin)` projects.
An established same-ID contribution winner is not automatically hot-replaced or
failed over. Restart the server after retirement when replacement is needed.

## Runtime Precedence

Runtime ownership is selected per server process:

1. Only coordinator candidates with coordinator protocol 3 are eligible to own
   generic contributions. Protocol-2 providers are ineligible for contribution
   ownership; existing conventional bootstrap callers remain source and binary
   compatible.
2. The highest telemetry runtime version wins.
3. If versions match, standalone wins over embedded.
4. If versions and origin match, provider plugin identifier and source path provide a stable tie-breaker.

The active coordinator handles descriptors from all installed enabled mods,
including descriptors with or without the legacy `runtimeMode` field. Passive embedded copies
do not install their own uncaught exception handlers, stats heartbeats, queues,
or upload loops.

Contribution candidates are elected separately per logical project. The highest
valid logical semantic version wins; equal versions prefer a standalone origin,
then use deterministic host/source/descriptor tie-breakers. Registration alone
does not emit telemetry. An active candidate must still pass project consent,
descriptor allowlists, sampling, and destination checks for every operation.
Invalid candidates stay passive and produce bounded diagnostics; they do not stop
the owning mod from loading.

## Minimal Hosted Example

```json
{
  "hosted": {
    "projectKey": "pub_proj_abc123"
  }
}
```

## Bootstrap Shape

The owning mod boots telemetry directly:

```java
import com.alechilles.alecstelemetry.api.TelemetryEventContext;

private EmbeddedTelemetryService telemetry;

@Override
protected void setup() {
    telemetry = EmbeddedTelemetryBootstrap.bootstrap(this);
    try {
        setupInternal();
        telemetry.recordBreadcrumb("lifecycle", "Setup completed.");
        telemetry.recordLifecycleWithContext(
                "plugin_setup",
                0,
                true,
                TelemetryEventContext.lifecycle()
                        .subsystem("plugin")
                        .phase("setup")
                        .runtimeSide("server")
                        .build()
        );
    } catch (Throwable throwable) {
        telemetry.captureSetupFailure(throwable);
        throw throwable;
    }
}

@Override
protected void start() {
    try {
        startInternal();
        telemetry.start();
        telemetry.recordBreadcrumb("lifecycle", "Start completed.");
        telemetry.recordUsageWithContext(
                "settings_opened",
                TelemetryEventContext.usage()
                        .subsystem("settings")
                        .featureKey("settings_page")
                        .entryPoint("/example settings")
                        .runtimeSide("server")
                        .detail("source", "command")
                        .build()
        );
    } catch (Throwable throwable) {
        telemetry.captureStartFailure(throwable);
        throw throwable;
    }
}

@Override
protected void shutdown() {
    if (telemetry != null) {
        telemetry.shutdown();
    }
}
```

The `*WithContext` helpers accept `TelemetryEventContext` so embedded mods can
attach standardized fields such as `subsystem`, `phase`, `featureKey`,
`entryPoint`, `runtimeSide`, `commandName`, and `worldName`. Custom
`detail(key, value)` entries are filtered by the descriptor allowlist before
upload.

## Storage Layout

The elected active coordinator stores runtime settings, project overrides,
server identity, queues, and upload state under one canonical Alec's Telemetry
data root:

```text
<ServerOrSaveRoot>/mods/Alechilles_Alec's Telemetry!/
  Settings/
    runtime.json
    projects/<project-id>.json
    consent-reviewed-projects.json
    server-identity.json
  Telemetry/
    crash-reports/
    events/
    manual-reports/
```

Standalone and embedded runtimes use the same root. Embedded owner data
directories no longer receive mirrored `Telemetry/Settings/projects` override
files.

On startup, the runtime performs a one-time migration from older Alec-created
locations into the canonical root when those files exist:

```text
<ServerOrSaveRoot>/telemetry/Settings/
<ServerOrSaveRoot>/telemetry/Telemetry/
<ServerOrSaveRoot>/Telemetry/Settings/
<ServerOrSaveRoot>/Telemetry/Telemetry/
<ConsumerModDataDir>/Telemetry/Settings/projects/
```

The migration copies files only when the canonical destination is missing. It
removes old Alec-created `Settings` and nested `Telemetry` directories once they
are empty, while leaving unrelated files such as Hytale's lowercase
`telemetry/*.jsonl.gz` session logs in place.

## Important Rule

Pick one packaging strategy per mod: either depend on the standalone runtime or embed and bootstrap the runtime yourself. Runtime election handles coexistence when multiple providers are installed.
