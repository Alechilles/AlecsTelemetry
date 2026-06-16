# Manual Player Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add player-submitted issue and suggestion reports to Alec's Telemetry, including in-game submission, optional diagnostic attachments, server-owner controls, hosted ingest, Discord notifications, portal workflow, resolution tracking, and GitHub issue export.

**Architecture:** Treat manual reports as a first-class telemetry envelope, separate from automated crash reports and generic events. The runtime owns descriptor parsing, local validation, redaction, queueing, retry, and server-owner review; the standalone mod owns Hytale custom UI and public modder APIs; the hosted service owns ingest validation, persistence contracts, alerts, portal workflows, report consolidation, status updates, and external issue creation.

**Tech Stack:** Java 25, Maven, JUnit 5, Gson, Hytale `InteractiveCustomUIPage`, `UICommandBuilder`, `UIEventBuilder`, existing Alec's Telemetry runtime/standalone modules, Node 20, TypeScript, Zod, Vitest, Discord.js, hosted portal service.

---

## Scope Split

This feature spans several subsystems. Implement it in milestones so each milestone produces working software:

1. **Runtime contract:** descriptor schema, report envelope, context snapshots, attachment policy, redaction, queueing, and retry.
2. **In-game submission:** public API, custom report UI, receipt UI, local report history, and server-owner review.
3. **Hosted ingest and alerts:** `/ingest/report`, validation, persistence boundary, Discord report notifications, rate limits, and spam controls.
4. **Portal workflow:** report detail, filtering, issue grouping, resolution state, GitHub issue export, and report status updates.
5. **Docs and examples:** descriptor docs, consumer mod example, server-owner controls, privacy behavior, and operational runbook.

## User Stories Covered

### Player

- As a player, I want to submit an issue report to the mod creator directly in-game.
- As a player, I want to submit a suggestion report separately from an issue report.
- As a player, I want to review what optional information will be included before submitting.
- As a player, I want to optionally include contact information so the mod creator can follow up.
- As a player, I want confirmation that my report was submitted.
- As a player, I want a distinct report ID that I can share with the mod creator afterward.
- As a player, I want reports to queue and retry if submission fails temporarily.
- As a player, I want to be notified when an issue I reported is resolved.

### Modder

- As a modder, I want to receive player-submitted issue reports through Alec's Telemetry.
- As a modder, I want to receive player-submitted suggestion reports through Alec's Telemetry.
- As a modder, I want to include a button in my own UI that opens the Alec's Telemetry report UI for my mod.
- As a modder, I want to define available report fields using simple JSON schema.
- As a modder, I want player report fields to render as real UI controls and arrive in a predictable output schema.
- As a modder, I want reports to include useful runtime, server, mod, and environment context automatically.
- As a modder, I want submitted reports to include the server's installed mod list so I can understand compatibility or interaction issues.
- As a modder, I want to know whether the report came from singleplayer or multiplayer.
- As a modder, I want to know how many players were on the server when the report was submitted.
- As a modder, I want players to optionally attach extra diagnostic information such as current or previous server logs.
- As a modder, I want submitted reports to include a distinct report ID visible to both me and the reporting player.
- As a modder, I want to view submitted reports in the telemetry portal with all included information.
- As a modder, I want to receive a Discord message and notification when a manual report is submitted.
- As a modder, I want issue and suggestion reports to be searchable/filterable separately from automated crash reports.
- As a modder, I want spam protection and rate limits for manual reports.
- As a modder, I want to mark an issue as resolved.
- As a modder, I want to manually consolidate multiple user reports under one issue.
- As a modder, I want to generate a GitHub issue from a submitted report.

### Server Owner

- As a server owner, I want to disable player-submitted reports if I do not want them enabled on my server.
- As a server owner, I want fine-grained control over what player reports can include, such as logs, installed mods, diagnostic data, contact information, and resolution updates.
- As a server owner, I want to control whether large optional attachments, such as logs, are allowed.
- As a server owner, I want size limits and redaction rules so attached logs do not expose sensitive information.
- As a server owner, I want a local log of submitted bug reports so I can detect issues caused by my server configuration.
- As a server owner, I want to enable a manual review process so I can approve player reports before they are sent to mod creators.

## Product Decisions

- Manual reports are not crash reports. Use `eventType: "manual_report"` with `reportKind: "issue"` or `reportKind: "suggestion"`.
- `reportId` is generated client-side before queueing so the player can receive a stable receipt even if upload is delayed.
- The player form always has a standard title, description, optional contact, and optional attachment review section. Modder-defined schema adds project-specific fields below the standard fields.
- Server-owner settings are final authority. A modder can request fields and attachments in `telemetry/project.json`, but the server owner can disable player reports, logs, installed-mod list, diagnostics, contact fields, resolution updates, and manual-review bypass.
- Log attachments require explicit player checkbox consent and server-owner permission.
- Log attachments are clipped, redacted, and size-bounded before local storage or upload.
- Current loaded-mod list is diagnostic metadata, not an attachment. It can be disabled by the server owner.
- Manual review stores reports locally in `Telemetry/manual-reports/<projectId>/review/` until an operator approves or rejects them.
- Rejected reports remain in the local audit log but are never uploaded.
- Resolution updates use a non-secret `reportId` plus a locally stored `followUpToken`. Players can check status in-game from local receipt history; optional contact info can also be used by the modder outside Alec's Telemetry.
- Portal issue grouping is separate from reports: many reports can link to one `issueId`.
- GitHub issue export belongs to the portal workflow and creates one GitHub issue from a portal issue group, not directly from every raw player report.

## Descriptor Contract

Add `reports` to `telemetry/project.json`:

```json
{
  "reports": {
    "enabled": true,
    "issue": {
      "enabled": true,
      "fields": {
        "severity": {
          "type": "enum",
          "label": "Severity",
          "required": true,
          "values": ["minor", "moderate", "major", "blocking"]
        },
        "steps": {
          "type": "text",
          "label": "Steps to reproduce",
          "required": false,
          "maxLength": 2000
        }
      }
    },
    "suggestion": {
      "enabled": true,
      "fields": {
        "category": {
          "type": "enum",
          "label": "Category",
          "required": false,
          "values": ["balance", "content", "quality_of_life", "other"]
        }
      }
    },
    "attachments": {
      "currentServerLog": true,
      "previousServerLog": true,
      "maxBytes": 262144
    },
    "contact": {
      "enabled": true,
      "maxLength": 160
    },
    "resolutionUpdates": {
      "enabled": true
    }
  }
}
```

Supported form field types:

- `string`: single-line text, `maxLength` defaults to `120`.
- `text`: multiline text, `maxLength` defaults to `1000`.
- `boolean`: checkbox.
- `enum`: segmented control or dropdown, requires `values`.
- `number`: numeric input, optional `min` and `max`.

Runtime normalization limits:

- Maximum custom fields per report kind: `20`.
- Maximum field key length: `80`.
- Maximum label length: `80`.
- Maximum enum values per field: `20`.
- Maximum enum value length: `80`.
- Maximum title length: `120`.
- Maximum description length: `4000`.
- Maximum contact length: descriptor value clamped from `1` to `240`.

## Runtime Settings Contract

Extend `Settings/runtime.json` template:

```json
{
  "manualReports": {
    "enabled": true,
    "manualReviewRequired": false,
    "allowContact": true,
    "allowResolutionUpdates": true,
    "allowCurrentServerLog": true,
    "allowPreviousServerLog": true,
    "allowLoadedModList": true,
    "allowDiagnostics": true,
    "maxLogAttachmentBytes": 262144,
    "maxPendingManualReportsPerProject": 200,
    "hostedReportIngestEndpoint": "https://telemetry.alecsmods.com/ingest/report"
  }
}
```

## Manual Report Envelope

```json
{
  "schemaVersion": 1,
  "eventType": "manual_report",
  "reportId": "uuid",
  "followUpTokenHash": "sha256-short-hash",
  "reportKind": "issue",
  "projectId": "example-mod",
  "projectDisplayName": "Example Mod",
  "submittedAtUtc": "2026-06-16T20:00:00Z",
  "source": "player_ui",
  "sessionId": "runtime-session-id",
  "serverId": "stable-server-id",
  "pluginIdentifier": "Example:Example Mod",
  "pluginVersion": "1.0.0",
  "title": "Short summary",
  "description": "Player-entered details",
  "contact": {
    "provided": true,
    "value": "discord: example"
  },
  "formValues": {
    "severity": "major",
    "steps": "Open the pen and click Save."
  },
  "attachmentManifests": [
    {
      "attachmentId": "uuid",
      "kind": "current_server_log",
      "fileName": "current-server-log.txt",
      "contentType": "text/plain",
      "byteCount": 52412,
      "sha256": "hex"
    }
  ],
  "context": {
    "runtimeMode": "multiplayer",
    "onlinePlayerCount": 12,
    "loadedModsIncluded": true,
    "diagnosticsIncluded": true
  },
  "environment": {
    "snapshotKey": "existing-environment-key",
    "runtimeMode": "dependency",
    "modSetHash": "existing-modset-hash"
  },
  "runtime": {
    "javaVersion": "25",
    "runtimeVersion": "25+0",
    "osName": "Windows 11",
    "osVersion": "10.0",
    "osArch": "amd64",
    "hytaleBuild": "build",
    "serverVersion": "server",
    "loadedMods": []
  }
}
```

## File Structure

### Runtime Module

- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportKind.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportFieldType.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportFieldDefinition.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportFormDefinition.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportOptions.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportEnvelope.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportAttachment.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportSubmission.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportContextSnapshot.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportStore.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportLogCollector.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportRedactor.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStore.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeSettings.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`

### Standalone Module

- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportOpenRequest.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportCoordinator.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportViewModel.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportPage.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReceiptPage.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReviewCommand.java`
- Create: `standalone/src/main/resources/Common/UI/Custom/TelemetryReportPage.ui`
- Create: `standalone/src/main/resources/Common/UI/Custom/TelemetryReportReceiptPage.ui`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeDiagnostics.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`

### Hosted Package

- Create: `hosted/src/contract/manual-report-envelope.ts`
- Create: `hosted/src/ingest/manual-report-ingest-service.ts`
- Create: `hosted/src/reports/manual-report-repository.ts`
- Create: `hosted/src/reports/report-issue-service.ts`
- Create: `hosted/src/reports/report-status-service.ts`
- Create: `hosted/src/alerts/report-alert-formatter.ts`
- Modify: `hosted/src/alerts/alert-router.ts`
- Modify: `hosted/src/alerts/discord-alert-router.ts`
- Modify: `hosted/src/projects/project-registry.ts`
- Modify: `hosted/src/server.ts`
- Modify: `hosted/src/index.ts`
- Modify: `hosted/config/projects.example.json`

### Tests

- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportFormDefinitionTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportEnvelopeTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportStoreTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportRedactorTest.java`
- Create: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStoreTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java`
- Modify: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`
- Create: `standalone/src/test/java/com/alechilles/alecstelemetry/reports/TelemetryReportViewModelTest.java`
- Create: `hosted/tests/manual-report-envelope.test.ts`
- Create: `hosted/tests/manual-report-ingest-service.test.ts`
- Create: `hosted/tests/report-alert-formatter.test.ts`
- Create: `hosted/tests/report-issue-service.test.ts`

### Docs and Examples

- Modify: `docs/project-descriptor.md`
- Modify: `docs/hosted-ingest-contract.md`
- Modify: `docs/runtime-overrides.md`
- Create: `docs/manual-player-reports.md`
- Modify: `wiki/Integration-Guides/Project-Descriptor.md`
- Modify: `wiki/Hosted-Operations/Hosted-Ingest-Contract.md`
- Modify: `examples/ExampleConsumerMod/telemetry/project.json`
- Modify: `examples/ExampleConsumerMod/README.md`
- Modify: `examples/EmbeddedConsumerMod/README.md`

## Task 1: Descriptor Schema for Manual Reports

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java`

- [ ] **Step 1: Add failing descriptor tests**

Add tests that parse a descriptor with `reports.issue.fields`, `reports.suggestion.fields`, `reports.attachments`, `reports.contact`, and `reports.resolutionUpdates`.

Expected assertions:

```java
assertTrue(descriptor.reports().enabled());
assertTrue(descriptor.reports().issue().enabled());
assertTrue(descriptor.reports().suggestion().enabled());
assertEquals("severity", descriptor.reports().issue().fields().get(0).key());
assertEquals("enum", descriptor.reports().issue().fields().get(0).type().key());
assertTrue(descriptor.reports().attachments().currentServerLog());
assertEquals(262144, descriptor.reports().attachments().maxBytes());
assertTrue(descriptor.reports().contact().enabled());
assertEquals(160, descriptor.reports().contact().maxLength());
assertTrue(descriptor.reports().resolutionUpdates().enabled());
```

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDescriptorTest test
```

Expected: compile fails because `reports()` does not exist.

- [ ] **Step 2: Implement descriptor records**

Add nested records to `TelemetryProjectDescriptor`:

```java
public record ManualReportOptions(boolean enabled,
                                  @Nonnull ManualReportForm issue,
                                  @Nonnull ManualReportForm suggestion,
                                  @Nonnull ManualReportAttachmentOptions attachments,
                                  @Nonnull ManualReportContactOptions contact,
                                  @Nonnull ManualReportResolutionOptions resolutionUpdates) {
}

public record ManualReportForm(boolean enabled,
                               @Nonnull List<ManualReportField> fields) {
}

public record ManualReportField(@Nonnull String key,
                                @Nonnull String type,
                                @Nonnull String label,
                                boolean required,
                                @Nonnull List<String> values,
                                int maxLength,
                                @Nullable Double min,
                                @Nullable Double max) {
}

public record ManualReportAttachmentOptions(boolean currentServerLog,
                                            boolean previousServerLog,
                                            int maxBytes) {
}

public record ManualReportContactOptions(boolean enabled, int maxLength) {
}

public record ManualReportResolutionOptions(boolean enabled) {
}
```

Normalize unknown field types out of the form. Keep field order stable.

- [ ] **Step 3: Wire the document parser**

Add `reports` to the top-level `Document` class and normalize missing reports to disabled forms:

```java
private ReportsDocument reports;
```

Default behavior:

- `reports.enabled`: `false` when omitted.
- `issue.enabled`: follows `reports.enabled` when omitted.
- `suggestion.enabled`: follows `reports.enabled` when omitted.
- `attachments.maxBytes`: clamp to `0..1048576`, default `262144`.
- `contact.maxLength`: clamp to `1..240`, default `160`.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDescriptorTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java
git commit -m "Feat: parse manual report descriptor schema"
```

## Task 2: Runtime Settings and Paths

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeSettings.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java`

- [ ] **Step 1: Add failing settings/path tests**

Add assertions that default settings expose:

```java
assertTrue(settings.manualReports().enabled());
assertFalse(settings.manualReports().manualReviewRequired());
assertTrue(settings.manualReports().allowCurrentServerLog());
assertTrue(settings.manualReports().allowPreviousServerLog());
assertTrue(settings.manualReports().allowLoadedModList());
assertEquals(262144, settings.manualReports().maxLogAttachmentBytes());
assertEquals("https://telemetry.alecsmods.com/ingest/report", settings.manualReports().hostedReportIngestEndpoint());
```

Add path assertions:

```java
assertTrue(paths.pendingManualReportsDirectory("example-mod").endsWith(Path.of("manual-reports", "example-mod", "pending")));
assertTrue(paths.reviewManualReportsDirectory("example-mod").endsWith(Path.of("manual-reports", "example-mod", "review")));
assertTrue(paths.submittedManualReportsLog().endsWith(Path.of("manual-reports", "submitted-reports.jsonl")));
assertTrue(paths.manualReportReceiptsFile().endsWith(Path.of("manual-reports", "receipts.json")));
```

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: compile fails because manual report settings and paths do not exist.

- [ ] **Step 2: Add runtime settings model**

Add a nested record:

```java
public record ManualReportSettings(boolean enabled,
                                   boolean manualReviewRequired,
                                   boolean allowContact,
                                   boolean allowResolutionUpdates,
                                   boolean allowCurrentServerLog,
                                   boolean allowPreviousServerLog,
                                   boolean allowLoadedModList,
                                   boolean allowDiagnostics,
                                   int maxLogAttachmentBytes,
                                   int maxPendingManualReportsPerProject,
                                   @Nonnull String hostedReportIngestEndpoint) {
}
```

Add `manualReports` to `TelemetryRuntimeSettings` and persist it in the default template.

- [ ] **Step 3: Add data paths**

Add methods to `TelemetryDataPaths`:

```java
@Nonnull
public Path manualReportsRoot() {
    return telemetryRoot.resolve("manual-reports");
}

@Nonnull
public Path pendingManualReportsDirectory(@Nonnull String projectId) {
    return manualReportsRoot().resolve(projectId).resolve("pending");
}

@Nonnull
public Path reviewManualReportsDirectory(@Nonnull String projectId) {
    return manualReportsRoot().resolve(projectId).resolve("review");
}

@Nonnull
public Path submittedManualReportsLog() {
    return manualReportsRoot().resolve("submitted-reports.jsonl");
}

@Nonnull
public Path manualReportReceiptsFile() {
    return manualReportsRoot().resolve("receipts.json");
}
```

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryDataPathsTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeSettings.java runtime/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPaths.java runtime/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryDataPathsTest.java
git commit -m "Feat: add manual report runtime settings"
```

## Task 3: Manual Report Envelope and Form Validation

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportKind.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportSubmission.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportEnvelope.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportAttachment.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportContextSnapshot.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportEnvelopeTest.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportFormDefinitionTest.java`

- [ ] **Step 1: Add failing envelope tests**

Create tests for:

- Generated `reportId` is non-blank UUID.
- `reportKind` normalizes to `issue` or `suggestion`.
- Title truncates at `120`.
- Description truncates at `4000`.
- Contact is omitted when runtime settings disallow contact.
- Unknown custom fields are dropped.
- Required missing custom fields produce a rejected submission result.
- Enum values outside `values` are dropped or rejected when required.

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportEnvelopeTest,ManualReportFormDefinitionTest test
```

Expected: compile fails because report classes do not exist.

- [ ] **Step 2: Add enum and submission model**

Create:

```java
public enum ManualReportKind {
    ISSUE("issue"),
    SUGGESTION("suggestion");

    private final String key;

    ManualReportKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

Create `ManualReportSubmission` with fields:

- `ManualReportKind kind`
- `String title`
- `String description`
- `String contact`
- `Map<String, Object> formValues`
- `boolean includeCurrentServerLog`
- `boolean includePreviousServerLog`
- `boolean includeLoadedModList`
- `boolean includeDiagnostics`
- `boolean allowResolutionUpdates`

- [ ] **Step 3: Add envelope model**

Create `ManualReportEnvelope` with `SCHEMA_VERSION = 1` and `EVENT_TYPE = "manual_report"`.

Methods:

```java
public static CreateResult create(TelemetryProjectRegistration project,
                                  TelemetryRuntimeSettings settings,
                                  ManualReportSubmission submission,
                                  ManualReportContextSnapshot context,
                                  List<ManualReportAttachment.Manifest> attachments,
                                  CrashReportEnvelope.EnvironmentSnapshot environment,
                                  CrashReportEnvelope.RuntimeMetadata runtime)
```

`CreateResult` should be:

```java
public record CreateResult(@Nullable ManualReportEnvelope envelope,
                           @Nonnull List<String> validationErrors) {
    public boolean accepted() {
        return envelope != null && validationErrors.isEmpty();
    }
}
```

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportEnvelopeTest,ManualReportFormDefinitionTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/report runtime/src/test/java/com/alechilles/alecstelemetry/report
git commit -m "Feat: add manual report envelope model"
```

## Task 4: Log Collection and Redaction

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportLogCollector.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportRedactor.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportRedactorTest.java`

- [ ] **Step 1: Add failing redaction tests**

Test that redaction replaces:

- Discord bot token-like strings with `[redacted:token]`.
- Bearer/API key lines with `[redacted:secret]`.
- IPv4 addresses with `[redacted:ip]`.
- Windows user home paths with `[redacted:path]`.
- Email addresses with `[redacted:email]`.

Use sample lines:

```text
Authorization: Bearer abc.def.ghi
Bot token: MTAxMzQ1Njc4OTAxMjM0NTY3.Oabcde.secret
Player email test@example.com
Connecting to 192.168.1.10
C:\Users\Somebody\AppData\Roaming\Hytale
```

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportRedactorTest test
```

Expected: compile fails because redactor does not exist.

- [ ] **Step 2: Implement redactor**

Create `ManualReportRedactor.redact(String input)` with deterministic regex replacements. Keep line order and do not remove unrelated text.

- [ ] **Step 3: Implement collector**

Create `ManualReportLogCollector` with:

```java
public Optional<ManualReportAttachment> collectCurrentServerLog(TelemetryDataPaths paths, int maxBytes)
public Optional<ManualReportAttachment> collectPreviousServerLog(TelemetryDataPaths paths, int maxBytes)
```

Resolve candidate logs from the Hytale root near `runtimeRoot` and the plugin data directory. If no candidate exists, return `Optional.empty()` and keep submission valid.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportRedactorTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportLogCollector.java runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportRedactor.java runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportRedactorTest.java
git commit -m "Feat: redact manual report log attachments"
```

## Task 5: Local Queue, Review, Receipt, and Audit Stores

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportStore.java`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStore.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportStoreTest.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStoreTest.java`

- [ ] **Step 1: Add failing store tests**

Test cases:

- `savePending` writes envelope JSON under `pending`.
- `saveForReview` writes envelope JSON under `review`.
- `approveReviewed` moves report from `review` to `pending`.
- `rejectReviewed` moves report from `review` to `rejected`.
- `appendSubmittedAudit` appends one JSON line containing `reportId`, `projectId`, `reportKind`, `capturedAtUtc`, `reviewState`, and `uploaded`.
- `ManualReportReceiptStore.saveReceipt` persists `reportId`, `projectId`, `reportKind`, `title`, `submittedAtUtc`, `followUpToken`, and `lastKnownStatus`.

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportStoreTest,ManualReportReceiptStoreTest test
```

Expected: compile fails because stores do not exist.

- [ ] **Step 2: Implement store operations**

Use the existing `CrashReportStore` and `TelemetryEventStore` patterns:

- Atomic writes through sibling `.tmp`.
- File names use `<submittedAtUtc-safe>-<reportId>.json`.
- Pending list returns oldest first.
- Delete only after successful upload.

- [ ] **Step 3: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportStoreTest,ManualReportReceiptStoreTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportStore.java runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStore.java runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportStoreTest.java runtime/src/test/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStoreTest.java
git commit -m "Feat: store manual reports locally"
```

## Task 6: Runtime Service Submission and Retry

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java`

- [ ] **Step 1: Add failing runtime tests**

Test:

- Reports disabled globally returns validation error `manual_reports_disabled`.
- Project reports disabled returns validation error `project_reports_disabled`.
- Manual review required stores in `review` and does not upload on flush.
- Manual review disabled stores in `pending` and uploads during flush.
- Failed upload leaves pending report in place.
- Successful upload deletes pending report and appends submitted audit line.

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: compile fails because submission APIs do not exist.

- [ ] **Step 2: Add submission API**

Add to `TelemetryRuntimeService`:

```java
public ManualReportEnvelope.CreateResult submitManualReport(String projectId,
                                                            ManualReportSubmission submission,
                                                            PlayerReportRuntimeContext playerContext)
```

`PlayerReportRuntimeContext` should contain:

- `boolean singleplayer`
- `int onlinePlayerCount`
- `String worldName`
- `List<LoadedModMetadata> loadedMods`

- [ ] **Step 3: Add flush support**

Extend `TelemetryCoreEngine.flushPendingReportsNow` to upload manual reports after crash reports and event reports. Use `project.resolveReportDeliveryTarget(settings)` so report ingest can target `/ingest/report`.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportEnvelopeTest,ManualReportStoreTest,ManualReportReceiptStoreTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: tests pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java standalone/src/test/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeServiceTest.java
git commit -m "Feat: submit and flush manual reports"
```

## Task 7: Public Modder API for Opening Report UI

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportOpenRequest.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryRuntimeApi.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryRuntimeApiImpl.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java`

- [ ] **Step 1: Add failing API compatibility tests**

Assert:

- Existing methods still compile.
- `TelemetryProjectHandle.openReportPage(...)` returns `false` when runtime service is unavailable.
- `TelemetryRuntimeApi.findProject("example").openReportPage(...)` delegates to the coordinator for that project.

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryProjectHandleCompatibilityTest test
```

Expected: compile fails because report open API does not exist.

- [ ] **Step 2: Add open request**

Create:

```java
public record TelemetryReportOpenRequest(@Nonnull String reportKind,
                                         @Nullable String initialTitle,
                                         @Nullable String initialDescription) {
    @Nonnull
    public String normalizedKind() {
        return "suggestion".equalsIgnoreCase(reportKind) ? "suggestion" : "issue";
    }
}
```

- [ ] **Step 3: Add handle API**

Add to `TelemetryProjectHandle`:

```java
boolean openReportPage(@Nonnull Ref<EntityStore> playerEntityRef,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull PlayerRef playerRef,
                       @Nonnull TelemetryReportOpenRequest request);
```

This API is intentionally Hytale-aware because consumer mods open it from their own player UI event handlers.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryProjectHandleCompatibilityTest test
```

Expected: tests pass.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportOpenRequest.java standalone/src/main/java/com/alechilles/alecstelemetry/api standalone/src/main/java/com/alechilles/alecstelemetry/api/internal standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java
git commit -m "Feat: expose manual report UI API"
```

## Task 8: In-Game Report UI and Receipt UI

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportCoordinator.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportViewModel.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportPage.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReceiptPage.java`
- Create: `standalone/src/main/resources/Common/UI/Custom/TelemetryReportPage.ui`
- Create: `standalone/src/main/resources/Common/UI/Custom/TelemetryReportReceiptPage.ui`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/reports/TelemetryReportViewModelTest.java`

- [ ] **Step 1: Add failing view model tests**

Assert the view model:

- Shows report kind selector when both issue and suggestion are enabled.
- Hides suggestion when `reports.suggestion.enabled=false`.
- Includes title, description, optional contact, custom fields, and attachment checkboxes.
- Hides current log checkbox when server settings disallow current logs.
- Hides previous log checkbox when server settings disallow previous logs.
- Hides loaded mod list option when server settings disallow it.
- Includes review warning when manual review is enabled.

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryReportViewModelTest test
```

Expected: compile fails because report view model does not exist.

- [ ] **Step 2: Build UI page**

Follow the existing `TelemetryConsentPage` pattern:

- `TelemetryReportPage extends InteractiveCustomUIPage<TelemetryReportPage.ReportEventData>`
- Event actions: `set_kind`, `set_field`, `toggle_attachment`, `submit`, `cancel`.
- Validation errors render above the submit button.
- Submit calls `TelemetryRuntimeService.submitManualReport`.
- Accepted result opens `TelemetryReportReceiptPage`.

- [ ] **Step 3: Build receipt page**

Receipt page displays:

- `Report ID`
- project display name
- report kind
- status: `Queued`, `Waiting for server owner review`, or `Submitted`
- copyable report ID field if Hytale UI supports copy action; otherwise display ID in full.

- [ ] **Step 4: Compile and commit**

Run:

```powershell
.\mvnw.cmd -pl standalone -DskipTests compile
```

Expected: compile passes.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/reports standalone/src/main/resources/Common/UI/Custom/TelemetryReportPage.ui standalone/src/main/resources/Common/UI/Custom/TelemetryReportReceiptPage.ui standalone/src/test/java/com/alechilles/alecstelemetry/reports/TelemetryReportViewModelTest.java
git commit -m "Feat: add manual report submission UI"
```

## Task 9: Server Owner Review Commands

**Files:**
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReviewCommand.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java`

- [ ] **Step 1: Add command behavior**

Add `/telemetry reports` subcommands:

- `/telemetry reports pending`
- `/telemetry reports approve <reportId>`
- `/telemetry reports reject <reportId>`
- `/telemetry reports submitted`

Permission groups: `OP`, `Admin`, `Operator`.

- [ ] **Step 2: Wire to store**

`pending` lists report ID, project, kind, title, submitted time, and attachment count.

`approve` moves from review to pending and triggers flush.

`reject` moves from review to rejected and appends audit line with `reviewState: "rejected"`.

- [ ] **Step 3: Compile and commit**

Run:

```powershell
.\mvnw.cmd -pl standalone -DskipTests compile
```

Expected: compile passes.

Commit:

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReviewCommand.java standalone/src/main/java/com/alechilles/alecstelemetry/commands/TelemetryCommandRoot.java
git commit -m "Feat: add manual report review commands"
```

## Task 10: Hosted Manual Report Contract and Ingest

**Files:**
- Create: `hosted/src/contract/manual-report-envelope.ts`
- Create: `hosted/src/ingest/manual-report-ingest-service.ts`
- Create: `hosted/src/reports/manual-report-repository.ts`
- Modify: `hosted/src/server.ts`
- Test: `hosted/tests/manual-report-envelope.test.ts`
- Test: `hosted/tests/manual-report-ingest-service.test.ts`

- [ ] **Step 1: Add failing hosted tests**

Test:

- Missing project key returns `401`.
- Unknown project key returns `403`.
- Disabled project returns `403`.
- Project ID mismatch returns `403`.
- Oversized payload returns `413`.
- Invalid report kind returns `400`.
- Valid issue report returns `202`.
- Valid suggestion report returns `202`.
- Rate limit returns `429`.

Run:

```powershell
Push-Location hosted
npm test -- manual-report-ingest-service.test.ts manual-report-envelope.test.ts
Pop-Location
```

Expected: tests fail because contract and service do not exist.

- [ ] **Step 2: Add Zod schema**

Create `manualReportEnvelopeSchema` with:

- `schemaVersion: number`
- `eventType: literal("manual_report")`
- `reportId: string`
- `followUpTokenHash: string.optional()`
- `reportKind: enum(["issue", "suggestion"])`
- `projectId`, `projectDisplayName`, `submittedAtUtc`, `source`
- `pluginIdentifier`, `pluginVersion`
- `title`, `description`
- `contact`
- `formValues`
- `attachmentManifests`
- `context`
- `environment`
- `runtime`

- [ ] **Step 3: Add ingest service**

Mirror `TelemetryIngestService`, but route through `ManualReportRepository` and `ReportAlertRouter`.

Response body:

```json
{
  "accepted": true,
  "projectId": "example-mod",
  "reportId": "uuid",
  "reportKind": "issue",
  "alertDispatched": true,
  "detail": "Report accepted."
}
```

- [ ] **Step 4: Add server route**

Accept `POST /ingest/report` with the same `X-Telemetry-Project-Key` header.

- [ ] **Step 5: Verify and commit**

Run:

```powershell
Push-Location hosted
npm test
npm run check
Pop-Location
```

Expected: tests and typecheck pass.

Commit:

```powershell
git add hosted/src/contract/manual-report-envelope.ts hosted/src/ingest/manual-report-ingest-service.ts hosted/src/reports/manual-report-repository.ts hosted/src/server.ts hosted/tests/manual-report-envelope.test.ts hosted/tests/manual-report-ingest-service.test.ts
git commit -m "Feat: ingest manual player reports"
```

## Task 11: Discord Report Notifications

**Files:**
- Create: `hosted/src/alerts/report-alert-formatter.ts`
- Modify: `hosted/src/alerts/alert-router.ts`
- Modify: `hosted/src/alerts/discord-alert-router.ts`
- Test: `hosted/tests/report-alert-formatter.test.ts`

- [ ] **Step 1: Add failing formatter tests**

Assert formatted issue report contains:

- project display name
- report kind
- report ID
- title
- severity field when present
- attachment manifest count
- portal link when configured

Run:

```powershell
Push-Location hosted
npm test -- report-alert-formatter.test.ts
Pop-Location
```

Expected: test fails because formatter does not exist.

- [ ] **Step 2: Add formatter**

Format under Discord 1900 characters:

```text
**Example Mod** received a player issue report
Report ID: `...`
Project: `example-mod`
Plugin: `Example:Example Mod` @ `1.0.0`
Title: ...
Severity: `major`
Attachments: `current_server_log`
Players online: `12`
Mode: `multiplayer`
View: https://...
```

- [ ] **Step 3: Route alerts**

Add `routeManualReportAlert` alongside `routeCrashAlert`.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
Push-Location hosted
npm test
npm run check
Pop-Location
```

Expected: tests and typecheck pass.

Commit:

```powershell
git add hosted/src/alerts hosted/tests/report-alert-formatter.test.ts
git commit -m "Feat: notify Discord for manual reports"
```

## Task 12: Portal Report Workflow Contract

**Files:**
- Create: `hosted/src/reports/report-issue-service.ts`
- Create: `hosted/src/reports/report-status-service.ts`
- Modify: `hosted/src/reports/manual-report-repository.ts`
- Test: `hosted/tests/report-issue-service.test.ts`

- [ ] **Step 1: Add failing workflow tests**

Test:

- Raw reports can be listed by project and report kind.
- Multiple report IDs can be consolidated under one `issueId`.
- Issue can transition from `open` to `resolved`.
- Resolved issue updates all linked report statuses.
- GitHub export payload contains issue title, body, labels, report IDs, environment summary, and portal URL.

Run:

```powershell
Push-Location hosted
npm test -- report-issue-service.test.ts
Pop-Location
```

Expected: tests fail because workflow services do not exist.

- [ ] **Step 2: Add repository shapes**

Use these records for the hosted reference backend:

```ts
export interface ManualReportRecord {
  reportId: string
  projectId: string
  reportKind: 'issue' | 'suggestion'
  title: string
  receivedAtUtc: string
  envelope: ManualReportEnvelope
  issueId: string | null
  status: 'received' | 'triaged' | 'resolved' | 'rejected'
}

export interface ReportIssueRecord {
  issueId: string
  projectId: string
  title: string
  status: 'open' | 'resolved'
  reportIds: string[]
  githubIssueUrl: string | null
  createdAtUtc: string
  resolvedAtUtc: string | null
}
```

- [ ] **Step 3: Add status service**

Expose report status lookup by `reportId` and `followUpTokenHash`. Do not expose contact info or full report payload through status lookup.

- [ ] **Step 4: Verify and commit**

Run:

```powershell
Push-Location hosted
npm test
npm run check
Pop-Location
```

Expected: tests and typecheck pass.

Commit:

```powershell
git add hosted/src/reports hosted/tests/report-issue-service.test.ts
git commit -m "Feat: model manual report workflow"
```

## Task 13: Player Resolution Updates

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStore.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/reports/TelemetryReportReceiptPage.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Modify: `hosted/src/reports/report-status-service.ts`

- [ ] **Step 1: Add local receipt status behavior**

Receipts have:

- `reportId`
- `followUpToken`
- `projectId`
- `reportKind`
- `title`
- `submittedAtUtc`
- `lastKnownStatus`
- `lastCheckedAtUtc`

- [ ] **Step 2: Add in-game status check**

Add `/telemetry reports mine` to show local receipts and current known status.

If `manualReports.allowResolutionUpdates=false`, show receipts but do not query hosted status.

- [ ] **Step 3: Add hosted status endpoint**

Add `POST /reports/status`:

```json
{
  "reportId": "uuid",
  "followUpToken": "token"
}
```

Response:

```json
{
  "found": true,
  "reportId": "uuid",
  "status": "resolved",
  "resolvedAtUtc": "2026-06-16T20:30:00Z"
}
```

- [ ] **Step 4: Verify and commit**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=ManualReportReceiptStoreTest test
.\mvnw.cmd -pl standalone -DskipTests compile
Push-Location hosted
npm test
npm run check
Pop-Location
```

Expected: tests and compile pass.

Commit:

```powershell
git add runtime/src/main/java/com/alechilles/alecstelemetry/report/ManualReportReceiptStore.java standalone/src/main/java/com/alechilles/alecstelemetry/reports standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java hosted/src/reports/report-status-service.ts hosted/src/server.ts
git commit -m "Feat: track manual report resolution status"
```

## Task 14: Docs and Examples

**Files:**
- Modify: `docs/project-descriptor.md`
- Modify: `docs/hosted-ingest-contract.md`
- Modify: `docs/runtime-overrides.md`
- Create: `docs/manual-player-reports.md`
- Modify: `wiki/Integration-Guides/Project-Descriptor.md`
- Modify: `wiki/Hosted-Operations/Hosted-Ingest-Contract.md`
- Modify: `examples/ExampleConsumerMod/telemetry/project.json`
- Modify: `examples/ExampleConsumerMod/README.md`
- Modify: `examples/EmbeddedConsumerMod/README.md`

- [ ] **Step 1: Document descriptor schema**

Add the JSON example from this plan and explain each field type.

- [ ] **Step 2: Document server-owner controls**

Document:

- disabling manual reports
- requiring manual review
- disabling logs
- disabling loaded mod list
- disabling diagnostics
- disabling contact
- disabling resolution updates
- log redaction and size clipping

- [ ] **Step 3: Document modder API**

Add an example:

```java
TelemetryRuntimeApi api = TelemetryRuntimeLocator.get();
TelemetryProjectHandle project = api == null ? null : api.findProject("example-mod");
if (project != null) {
    project.openReportPage(ref, store, playerRef, new TelemetryReportOpenRequest("issue", null, null));
}
```

- [ ] **Step 4: Verify and commit**

Run:

```powershell
rg -n "manualReports|reports|ManualReport|ingest/report|openReportPage" docs wiki examples README.md
```

Expected: documentation references exist in docs, wiki, and examples.

Commit:

```powershell
git add docs wiki examples
git commit -m "Docs: document manual player reports"
```

## Task 15: Full Verification

**Files:**
- No planned source changes.

- [ ] **Step 1: Run Java focused tests**

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDescriptorTest,TelemetryDataPathsTest,ManualReportEnvelopeTest,ManualReportFormDefinitionTest,ManualReportRedactorTest,ManualReportStoreTest,ManualReportReceiptStoreTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest,TelemetryProjectHandleCompatibilityTest,TelemetryReportViewModelTest test
```

Expected: tests pass.

- [ ] **Step 2: Run hosted tests**

```powershell
Push-Location hosted
npm test
npm run check
Pop-Location
```

Expected: tests and TypeScript check pass.

- [ ] **Step 3: Run full Maven suite**

```powershell
.\mvnw.cmd test
```

Expected: full Maven suite passes.

- [ ] **Step 4: Package standalone mod**

```powershell
.\mvnw.cmd -pl standalone package
```

Expected: standalone jar builds.

- [ ] **Step 5: Runtime smoke test**

Install Alec's Telemetry and ExampleConsumerMod into a local Hytale server. Verify:

- Example mod button opens report UI for `example-mod`.
- Issue and suggestion modes are visually distinct.
- Server-owner-disabled logs are not shown.
- Required custom fields block submission until filled.
- Optional contact can be left blank.
- Current log attachment is redacted and clipped.
- Manual review stores report under `review`.
- `/telemetry reports approve <reportId>` moves report to `pending`.
- Flush uploads report to hosted `/ingest/report`.
- Hosted service sends Discord notification.
- Portal record shows runtime mode, player count, loaded mods when allowed, form fields, contact when provided, and attachment manifests.
- Portal can consolidate two reports under one issue.
- Portal can mark issue resolved.
- Player receipt status shows resolved after status refresh.
- GitHub export creates one issue for the consolidated portal issue.

## Risks and Constraints

- The canonical hosted backend may live outside this repository. Implement the reference hosted package first, then port the same DTOs and validation rules into the production portal service.
- Hytale custom UI text input behavior may limit dynamic form rendering. If arbitrary dynamic fields are too constrained, implement up to 8 visible custom fields in the first UI pass and reject descriptors with more visible fields until a scrolling implementation is verified.
- Log file paths vary by local Hytale layout. Keep log collection best-effort and make missing logs non-fatal.
- Resolution updates require a stored local receipt. Players who delete local data or switch servers can still use the report ID manually with the modder but cannot automatically poll status unless the follow-up token remains available.
- GitHub issue creation needs portal-side GitHub credentials or app installation. Keep it disabled unless configured per hosted project.

## Self-Review

- Spec coverage: every player, modder, and server-owner story maps to a task above.
- Placeholder scan: no task contains undefined future work markers; uncertain runtime dependencies are called out as risks with bounded fallback behavior.
- Type consistency: report kind keys are `issue` and `suggestion`; wire event type is `manual_report`; Java runtime classes use `ManualReport*`; hosted TypeScript classes use `manual-report-*`.
