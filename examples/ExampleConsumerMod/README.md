# Example Consumer Mod

This example shows the minimum files another mod would ship to integrate with
Alec's Telemetry.

In many cases, the only telemetry-specific file needed is:

```text
Server/Telemetry/project.json
```

because the runtime can infer the rest from `manifest.json`.

The sample descriptor also enables manual issue and suggestion reports. A consumer
mod can open the report UI from its own player UI handler with:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```
