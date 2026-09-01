---
title: "Embedded Mode"
order: 4
published: true
draft: false
---

# Embedded Mode

Parent: [Integration Guides](/mod/beacon/integration-guides) | [Home](/mod/beacon/home)

Embedded mode lets a plugin bundle the telemetry runtime inside its own package instead of requiring the standalone Beacon dependency.

## Use This Mode When

- You want one distributable plugin package.
- You have Java lifecycle code.
- You want to capture setup and start failures around your own startup logic.
- You are comfortable validating the packaged runtime.

Asset packs normally use [Standalone Dependency Mode](/mod/beacon/standalone-dependency-mode) because embedded mode requires plugin lifecycle code.

## Before This Page

Complete [Portal First Setup](/mod/beacon/portal-first-setup) once. Embedded mode uses the same portal project, portal project key, `Server/Beacon/project.json`, optional identity overrides, and optional consent icon as standalone mode.

Do not add a descriptor flag to choose embedded mode. New descriptors should omit `runtimeMode`; embedded behavior comes from packaging the runtime and calling `EmbeddedTelemetryBootstrap`.

## Step 1: Add The Runtime Artifact

Open the Beacon downloads page:

```text
https://beacon.modstats.io/downloads
```

For Maven builds, add the public repository:

```xml
<repository>
  <id>beacon</id>
  <url>https://beacon.modstats.io/maven/releases</url>
</repository>
```

Then add the embeddable runtime dependency:

```xml
<dependency>
  <groupId>com.alechilles</groupId>
  <artifactId>beacon-runtime</artifactId>
  <version>2.0.0</version>
</dependency>
```

For Gradle Kotlin DSL builds, use the same Maven-format repository:

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

Package that runtime jar inside your plugin. Server owners should not need to download a separate runtime jar when your plugin uses embedded mode.

## Step 2: Publish The Client UI Asset Pack

An embedded host must set `IncludesAssetPack` to `true` and merge every
`Common/**` resource from `beacon-runtime` into its asset-pack source.
Shading the Java classes is not sufficient. If your build already prepares an
asset pack, add the runtime `Common/**` tree to that source.

Before release, confirm that the final mod contains:

```text
Common/UI/Custom/TelemetryConsentPage.ui
```

If this file is not published to the client, `/beacon consent` disconnects
the client because Hytale cannot find the UI document. See the repository
[`docs/embedded-mode.md`](https://github.com/Alechilles/Beacon/blob/main/docs/embedded-mode.md)
page for a Gradle merge example.

Descriptor-only libraries do not publish this UI. Their installed standalone or
embedded runtime host supplies it.

## Step 3: Bootstrap From Your Plugin

The owning plugin boots telemetry directly:

```java
import com.alechilles.beacon.embedded.EmbeddedTelemetryBootstrap;
import com.alechilles.beacon.embedded.EmbeddedTelemetryService;

private EmbeddedTelemetryService telemetry;

@Override
protected void setup() {
    telemetry = EmbeddedTelemetryBootstrap.bootstrap(this);
    try {
        setupInternal();
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

Wrap your own setup/start code so telemetry can capture failures before rethrowing them.

## Runtime Election

Every installed standalone or embedded copy registers as a runtime coordinator candidate. The active runtime is selected per server process:

1. The highest telemetry runtime version wins.
2. If versions match, standalone wins over embedded.
3. If versions and origin match, provider plugin identifier and source path provide a stable tie-breaker.

The active coordinator handles descriptors from all installed enabled projects. Passive embedded copies do not install their own uncaught exception handlers, stats heartbeats, queues, or upload loops.

## Storage Layout

Standalone and embedded runtimes use the same Beacon data root:

```text
<ServerOrSaveRoot>/mods/Alechilles_Beacon/
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

## Verify Embedded Setup

1. Package your plugin.
2. Confirm the package contains `Common/UI/Custom/TelemetryConsentPage.ui`.
3. Install the plugin.
4. Start a local server.
5. Run `/beacon status` and confirm the active coordinator.
6. Run `/beacon consent` and confirm the consent page opens.
7. Run `/beacon project <project-id>` and confirm your project source path.
8. Run `/beacon test <project-id> embedded-check`.
9. Confirm the test report appears in the portal.

## Important Rule

Pick one primary packaging strategy per plugin:

- depend on standalone Beacon
- embed and bootstrap the runtime yourself

Runtime election handles coexistence when multiple providers are installed, but your project docs and packaging should clearly tell server owners which setup you expect.
