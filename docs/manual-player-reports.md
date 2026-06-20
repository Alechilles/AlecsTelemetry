# Manual Player Reports

Alec's Telemetry supports player-submitted issue and suggestion reports in addition
to automated crash reports and generic telemetry events.

Consumer mods can open the report page from their own UI:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-consumer-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

Players with the `telemetry.command.telemetry.report` permission can also open the
same UI directly:

```text
/telemetry report
/telemetry report issue
/telemetry report suggestion
/telemetry report <project-id> [issue|suggestion]
```

When the project id is omitted, the command opens a compact project selector
before showing the report form.

The player receives a local receipt with a stable `reportId`. The uploaded envelope
contains `followUpTokenHash`; the raw follow-up token remains local for status
checks.

## Server Controls

`Settings/runtime.json` controls what reports can include:

- `manualReports.enabled`
- `manualReports.manualReviewRequired`
- `manualReports.allowContact`
- `manualReports.allowResolutionUpdates`
- `manualReports.allowCurrentServerLog`
- `manualReports.allowPreviousServerLog`
- `manualReports.allowLoadedModList`
- `manualReports.allowDiagnostics`
- `manualReports.maxLogAttachmentBytes`

Review-required reports are stored under:

```text
Telemetry/manual-reports/<projectId>/review/
```

Operators can use:

```text
/telemetry reports pending
/telemetry reports approve <reportId>
/telemetry reports reject <reportId>
/telemetry reports submitted
```

Approved reports move to the pending upload queue. Rejected reports stay local and
are never uploaded.

## Hosted Behavior

Manual reports post to `POST /ingest/report` with the same
`X-Telemetry-Project-Key` header as crash ingest. The hosted service validates the
envelope, persists the report, sends a Discord notification when configured, and
models issue grouping, resolution status, and GitHub issue export payloads.
