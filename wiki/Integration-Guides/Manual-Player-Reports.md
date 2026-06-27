---
title: "Manual Player Reports"
order: 11
published: true
draft: false
---

# Manual Player Reports

Parent: [Integration Guides](/mod/alecs-telemetry/integration-guides) | [Home](/mod/alecs-telemetry/home)

Manual player reports let players submit issue or suggestion reports from in game. Reports can include project-defined fields and optional attachments allowed by the server owner.

## Before This Page

Complete [Portal First Setup](/mod/alecs-telemetry/portal-first-setup) once.

## Descriptor Block

Add this block to the shared descriptor:

```json
{
  "telemetry": {
    "reports": {
      "supported": true,
      "issue": {
        "enabled": true,
        "fields": {
          "severity": {
            "type": "enum",
            "label": "Severity",
            "required": true,
            "values": ["minor", "moderate", "major", "blocking"]
          }
        }
      },
      "suggestion": {
        "enabled": true
      }
    }
  }
}
```

Server-owner runtime settings are final authority for contact fields, logs, loaded mod lists, diagnostics, review requirements, and maximum attachment size.

## Open The Report UI From A Plugin

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.tryGet();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-project");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

Players can also use:

```text
/telemetry report
/telemetry report issue
/telemetry report suggestion
/telemetry report <project-id> [issue|suggestion]
```

## Operator Review

If `manualReports.manualReviewRequired` is true in `Settings/runtime.json`, reports are held locally until an operator approves them:

```text
/telemetry reports pending
/telemetry reports approve <report-id>
/telemetry reports reject <report-id>
/telemetry reports submitted
```

Approved reports move to the pending upload queue. Rejected reports stay local and are never uploaded.

## Verify

1. Open the report UI.
2. Submit a test report.
3. If review is required, approve it.
4. Flush the project.
5. Confirm the report appears in the portal.

## Troubleshooting

- No report-enabled projects: check `telemetry.reports.supported`.
- Attachment option missing: server runtime settings may disable it.
- Report not uploaded: it may still be waiting for local review.
- Portal issue missing: check `/telemetry reports submitted` and the shared descriptor's portal project key.
