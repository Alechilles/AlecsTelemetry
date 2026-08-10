# Passive Descriptor Host Fixture

This is a manual, two-artifact packaging test for descriptor-only embedded
telemetry:

1. `embeddedDescriptorLibraryJar` builds a small Java library containing a
   marker class and one namespaced descriptor.
2. The Hytale host plugin merges that library JAR into its final archive. It
   has no Alec's Telemetry dependency and does not call any Telemetry API.

The final host JAR therefore contains both:

```text
com/alechilles/passivedescriptorlibrary/EmbeddedDescriptorLibrary.class
META-INF/alecs-telemetry/projects/telemetry-demo.json
```

The descriptor is intentionally wired to the publishable `telemetry-demo`
project key on `https://telemetry-dev.alecsmods.com`. It sends only the
standard Stats `heartbeat` event. Do not install the intermediate library JAR;
only install the final host JAR.

## Build and stage

From this directory:

```text
../../../../gradlew build
```

Then copy:

```text
build/libs/Passive Telemetry Descriptor Host Fixture v1.0.0.jar
```

to the server's `mods` directory that already runs an Alec's Telemetry runtime
(standalone or embedded). For the shared local workspace, use
`C:\Users\22ale\AppData\Roaming\Hytale\Modding\run\mods`.

## Expected result

- The host loads and logs the embedded-library marker.
- The Telemetry consent screen shows one `Passive Descriptor Library (Dev
  Fixture)` row with Stats available and no other categories.
- After enabling Stats and waiting for the normal first-heartbeat interval,
  the `telemetry-demo` project receives the aggregate heartbeat through the
  dev endpoint.
