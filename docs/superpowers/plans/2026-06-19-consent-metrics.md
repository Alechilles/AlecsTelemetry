# Consent Metrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-respecting telemetry for Alec's Telemetry consent choices so the hosted platform can report opt-out rates by project and telemetry category.

**Architecture:** Runtime consent changes will produce a small first-party consent metrics payload only when the Alec's Telemetry self-metrics project is enabled. Hosted ingest will validate and persist those consent observations separately from crash/events/stats so normal telemetry opt-outs are not bypassed by the measurement path.

**Tech Stack:** Java 25 runtime module, JUnit 5, TypeScript hosted service, Zod, Vitest.

---

## File Structure

- Create `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetric.java`: immutable runtime-side consent observation model.
- Create `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporter.java`: compares before/after snapshots and submits consent metrics through a narrow client interface.
- Modify `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`: hook successful consent saves into the reporter after persistence and runtime application.
- Create `standalone/src/main/resources/telemetry/project.json`: self project descriptor for Alec's Telemetry runtime metrics, shown in consent UI.
- Create `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporterTest.java`: unit tests for emitted metric shape and opt-out gating.
- Modify `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`: integration test proving consent changes emit a metric only through the self project.
- Create `hosted/src/contract/consent-metric-envelope.ts`: Zod schema for a dedicated consent metric payload.
- Create `hosted/src/consent/consent-metric-log.ts`: append/read JSONL store for consent observations.
- Create `hosted/src/consent/consent-metric-service.ts`: validation, privacy filtering, and aggregation-ready persistence.
- Modify `hosted/src/server.ts`: route `POST /ingest/consent-metric` to the consent metric service.
- Modify `hosted/src/index.ts`: wire the service and log.
- Create `hosted/tests/consent-metric-service.test.ts`: hosted validation and privacy tests.
- Modify `hosted/tests/server.test.ts`: route-level tests for consent metric ingest.
- Create `docs/consent-metrics.md`: public-facing implementation and privacy notes.

## Consent Policy Decisions

- Consent metrics are controlled by the Alec's Telemetry self project, not by the target mod's disabled category.
- If the self project is disabled, no consent metrics are sent, including the fact that it was disabled.
- Metrics must never include player names, player UUIDs, IP addresses, world names, coordinates, logs, stack traces, chat, raw server ids, or full config files.
- Runtime may include these fields: `schemaVersion`, `eventId`, `capturedAtUtc`, `runtimeVersion`, `projectId`, `projectDisplayName`, `projectVersion`, `category`, `supported`, `enabled`, `previousEnabled`, `changeSource`, `firstReview`.
- Hosted storage should be append-only JSONL initially, matching existing stats event log patterns.

### Task 1: Runtime Consent Metric Model

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetric.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporterTest.java`

- [ ] **Step 1: Write the failing model serialization test**

Add this test class skeleton:

```java
package com.alechilles.alecstelemetry.consent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelemetryConsentMetricReporterTest {

    @Test
    void metricSummaryContainsOnlyConsentFields() {
        TelemetryConsentMetric metric = new TelemetryConsentMetric(
                1,
                "metric-1",
                "2026-06-19T12:00:00Z",
                "0.2.0",
                "alecs-tamework",
                "Alec's Tamework",
                "1.4.0",
                "usage",
                true,
                false,
                true,
                "category_toggle",
                true
        );

        Map<String, Object> summary = metric.toSummary();

        assertEquals("alecs-tamework", summary.get("projectId"));
        assertEquals("usage", summary.get("category"));
        assertEquals(false, summary.get("enabled"));
        assertEquals(true, summary.get("previousEnabled"));
        assertFalse(summary.containsKey("serverId"));
        assertFalse(summary.containsKey("sessionId"));
        assertFalse(summary.containsKey("worldName"));
        assertFalse(summary.containsKey("loadedMods"));
    }
}
```

- [ ] **Step 2: Run the focused failing test**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest test`

Expected: FAIL because `TelemetryConsentMetric` does not exist.

- [ ] **Step 3: Implement the model**

Create `TelemetryConsentMetric.java`:

```java
package com.alechilles.alecstelemetry.consent;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

public record TelemetryConsentMetric(int schemaVersion,
                                     @Nonnull String eventId,
                                     @Nonnull String capturedAtUtc,
                                     @Nonnull String runtimeVersion,
                                     @Nonnull String projectId,
                                     @Nonnull String projectDisplayName,
                                     @Nonnull String projectVersion,
                                     @Nonnull String category,
                                     boolean supported,
                                     boolean enabled,
                                     boolean previousEnabled,
                                     @Nonnull String changeSource,
                                     boolean firstReview) {

    @Nonnull
    public Map<String, Object> toSummary() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", schemaVersion);
        summary.put("eventId", eventId);
        summary.put("capturedAtUtc", capturedAtUtc);
        summary.put("runtimeVersion", runtimeVersion);
        summary.put("projectId", projectId);
        summary.put("projectDisplayName", projectDisplayName);
        summary.put("projectVersion", projectVersion);
        summary.put("category", category);
        summary.put("supported", supported);
        summary.put("enabled", enabled);
        summary.put("previousEnabled", previousEnabled);
        summary.put("changeSource", changeSource);
        summary.put("firstReview", firstReview);
        return summary;
    }
}
```

- [ ] **Step 4: Run the model test**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetric.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporterTest.java
git commit -m "Feat: add consent metric model"
```

### Task 2: Runtime Metric Reporter

**Files:**
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporter.java`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporterTest.java`

- [ ] **Step 1: Add failing reporter tests**

Append these tests to `TelemetryConsentMetricReporterTest`:

```java
    @Test
    void reporterEmitsOneMetricPerSupportedCategoryChangeWhenSelfMetricsAreEnabled() {
        java.util.ArrayList<TelemetryConsentMetric> sent = new java.util.ArrayList<>();
        TelemetryConsentMetricReporter reporter = new TelemetryConsentMetricReporter(
                () -> true,
                sent::add,
                () -> "metric-1",
                () -> "2026-06-19T12:00:00Z",
                () -> "0.2.0"
        );

        TelemetryConsentSnapshot before = new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true);
        TelemetryConsentSnapshot after = new TelemetryConsentSnapshot(true, true, true, true, true, false, true, true);
        TelemetryConsentSnapshot supported = new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true);

        reporter.reportChanges("alecs-tamework", "Alec's Tamework", "1.4.0", supported, before, after, "category_toggle", true);

        assertEquals(1, sent.size());
        assertEquals("usage", sent.getFirst().category());
        assertEquals(false, sent.getFirst().enabled());
        assertEquals(true, sent.getFirst().previousEnabled());
    }

    @Test
    void reporterDoesNotEmitWhenSelfMetricsAreDisabled() {
        java.util.ArrayList<TelemetryConsentMetric> sent = new java.util.ArrayList<>();
        TelemetryConsentMetricReporter reporter = new TelemetryConsentMetricReporter(
                () -> false,
                sent::add,
                () -> "metric-1",
                () -> "2026-06-19T12:00:00Z",
                () -> "0.2.0"
        );

        TelemetryConsentSnapshot before = new TelemetryConsentSnapshot(true, true, true, true, true, true, true, true);
        TelemetryConsentSnapshot after = new TelemetryConsentSnapshot(true, false, true, true, true, true, true, true);

        reporter.reportChanges("alecs-tamework", "Alec's Tamework", "1.4.0", before, before, after, "category_toggle", false);

        assertEquals(0, sent.size());
    }
```

- [ ] **Step 2: Run the focused failing tests**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest test`

Expected: FAIL because `TelemetryConsentMetricReporter` does not exist.

- [ ] **Step 3: Implement the reporter**

Create `TelemetryConsentMetricReporter.java`:

```java
package com.alechilles.alecstelemetry.consent;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TelemetryConsentMetricReporter {
    public interface EnabledSupplier {
        boolean enabled();
    }

    private static final String[] CATEGORIES = {
            "crash", "error", "lifecycle", "performance", "usage", "stats", "breadcrumbs"
    };

    private final EnabledSupplier selfMetricsEnabled;
    private final Consumer<TelemetryConsentMetric> sink;
    private final Supplier<String> eventIdSupplier;
    private final Supplier<String> nowSupplier;
    private final Supplier<String> runtimeVersionSupplier;

    public TelemetryConsentMetricReporter(@Nonnull EnabledSupplier selfMetricsEnabled,
                                          @Nonnull Consumer<TelemetryConsentMetric> sink,
                                          @Nonnull Supplier<String> eventIdSupplier,
                                          @Nonnull Supplier<String> nowSupplier,
                                          @Nonnull Supplier<String> runtimeVersionSupplier) {
        this.selfMetricsEnabled = Objects.requireNonNull(selfMetricsEnabled);
        this.sink = Objects.requireNonNull(sink);
        this.eventIdSupplier = Objects.requireNonNull(eventIdSupplier);
        this.nowSupplier = Objects.requireNonNull(nowSupplier);
        this.runtimeVersionSupplier = Objects.requireNonNull(runtimeVersionSupplier);
    }

    @Nonnull
    public static TelemetryConsentMetricReporter disabled() {
        return new TelemetryConsentMetricReporter(
                () -> false,
                ignored -> { },
                () -> UUID.randomUUID().toString(),
                () -> Instant.now().toString(),
                () -> "unknown"
        );
    }

    public void reportChanges(@Nonnull String projectId,
                              @Nonnull String projectDisplayName,
                              @Nonnull String projectVersion,
                              @Nonnull TelemetryConsentSnapshot supported,
                              @Nonnull TelemetryConsentSnapshot before,
                              @Nonnull TelemetryConsentSnapshot after,
                              @Nonnull String changeSource,
                              boolean firstReview) {
        if (!selfMetricsEnabled.enabled()) {
            return;
        }
        for (String category : CATEGORIES) {
            if (!supported.categoryEnabled(category)) {
                continue;
            }
            boolean previous = before.categoryEnabled(category);
            boolean current = after.categoryEnabled(category);
            if (previous == current) {
                continue;
            }
            sink.accept(new TelemetryConsentMetric(
                    1,
                    eventIdSupplier.get(),
                    nowSupplier.get(),
                    runtimeVersionSupplier.get(),
                    projectId,
                    projectDisplayName,
                    projectVersion,
                    category,
                    true,
                    current,
                    previous,
                    changeSource,
                    firstReview
            ));
        }
    }
}
```

- [ ] **Step 4: Run the reporter tests**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporter.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentMetricReporterTest.java
git commit -m "Feat: report consent metric changes"
```

### Task 3: Runtime Consent Hook and Self Project Descriptor

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Create: `standalone/src/main/resources/telemetry/project.json`
- Modify: `runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java`

- [ ] **Step 1: Add the self project descriptor**

Create `standalone/src/main/resources/telemetry/project.json`:

```json
{
  "schemaVersion": 1,
  "projectId": "alecs-telemetry-runtime",
  "displayName": "Alec's Telemetry",
  "runtimeMode": "standalone",
  "ownerPluginIdentifiers": ["Alechilles:Alec's Telemetry"],
  "packagePrefixes": ["com.alechilles.alecstelemetry"],
  "defaults": {
    "enabled": true,
    "destinationMode": "hosted"
  },
  "usage": {
    "enabled": true,
    "events": ["consent_metric"]
  },
  "hosted": {
    "endpoint": "https://telemetry.alecsmods.com/ingest/crash",
    "eventEndpoint": "https://telemetry.alecsmods.com/ingest/event"
  }
}
```

- [ ] **Step 2: Add failing embedded service test**

Add a test to `EmbeddedTelemetryServiceTest` that creates a service with `consentOnlyDescriptor()`, applies consent from usage enabled to disabled, and asserts exactly one consent metric sink call. If direct sink injection is not available yet, the expected compile failure is the reason to add a package-private constructor parameter in the next step.

Test shape:

```java
    @Test
    void applyingConsentReportsCategoryOptOutThroughSelfMetrics() {
        java.util.ArrayList<TelemetryConsentMetric> metrics = new java.util.ArrayList<>();
        EmbeddedTelemetryService service = serviceWithConsentMetricSink(consentOnlyDescriptor(), metrics::add);

        TelemetryConsentSnapshot after = new TelemetryConsentSnapshot(true, true, true, true, true, false, true, true);

        assertTrue(service.applyConsent("consent-only-mod", after));

        assertEquals(1, metrics.size());
        assertEquals("consent-only-mod", metrics.getFirst().projectId());
        assertEquals("usage", metrics.getFirst().category());
        assertEquals(false, metrics.getFirst().enabled());
    }
```

- [ ] **Step 3: Run the failing embedded test**

Run: `.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest#applyingConsentReportsCategoryOptOutThroughSelfMetrics test`

Expected: FAIL until the service has a consent metric reporter injection point.

- [ ] **Step 4: Wire reporter into successful consent apply**

Modify `EmbeddedTelemetryService` so `applyLocalConsent(...)` captures `before` before saving and reports after successful `applyRuntimeConsent(...)`:

```java
TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics = buildProjectDiagnostics(project);
TelemetryConsentSnapshot before = diagnostics.consentSnapshot();
TelemetryConsentSnapshot normalized = clampToSupported(snapshot, supportedSnapshot(project));
...
boolean applied = applyRuntimeConsent(project.projectId(), normalized);
if (applied) {
    consentMetricReporter.reportChanges(
            project.projectId(),
            project.displayName(),
            project.pluginVersion(),
            supportedSnapshot(project),
            before,
            normalized,
            "consent_ui",
            !project.overridePresent()
    );
}
return applied;
```

Keep the reporter disabled in production until the hosted endpoint client exists. Add constructor/test injection first, then replace the production disabled reporter in Task 5.

- [ ] **Step 5: Run focused runtime tests**

Run: `.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest,TelemetryConsentMetricReporterTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add standalone/src/main/resources/telemetry/project.json runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java runtime/src/test/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryServiceTest.java
git commit -m "Feat: hook consent changes into runtime metrics"
```

### Task 4: Hosted Consent Metric Contract and Service

**Files:**
- Create: `hosted/src/contract/consent-metric-envelope.ts`
- Create: `hosted/src/consent/consent-metric-log.ts`
- Create: `hosted/src/consent/consent-metric-service.ts`
- Create: `hosted/tests/consent-metric-service.test.ts`

- [ ] **Step 1: Write failing hosted service tests**

Create `hosted/tests/consent-metric-service.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'

import { ConsentMetricService } from '../src/consent/consent-metric-service.js'

function metric(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: 1,
    eventId: 'metric-1',
    capturedAtUtc: '2026-06-19T12:00:00.000Z',
    runtimeVersion: '0.2.0',
    projectId: 'alecs-tamework',
    projectDisplayName: "Alec's Tamework",
    projectVersion: '1.4.0',
    category: 'usage',
    supported: true,
    enabled: false,
    previousEnabled: true,
    changeSource: 'consent_ui',
    firstReview: true,
    ...overrides,
  }
}

describe('ConsentMetricService', () => {
  it('stores valid consent metrics', async () => {
    const append = vi.fn()
    const service = new ConsentMetricService({ append, readAll: vi.fn() })

    const result = await service.accept(metric())

    expect(result.accepted).toBe(true)
    expect(append).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 'alecs-tamework',
      category: 'usage',
      enabled: false,
      previousEnabled: true,
    }))
  })

  it('rejects payloads with private runtime identifiers', async () => {
    const append = vi.fn()
    const service = new ConsentMetricService({ append, readAll: vi.fn() })

    const result = await service.accept(metric({ serverId: 'server-1' }))

    expect(result.accepted).toBe(false)
    expect(result.error).toBe('invalid_consent_metric')
    expect(append).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run failing hosted tests**

Run: `cd hosted; npm test -- consent-metric-service.test.ts`

Expected: FAIL because the consent service does not exist.

- [ ] **Step 3: Implement the Zod contract**

Create `hosted/src/contract/consent-metric-envelope.ts`:

```ts
import { z } from 'zod'

export const consentMetricEnvelopeSchema = z.object({
  schemaVersion: z.literal(1),
  eventId: z.string().min(1),
  capturedAtUtc: z.string().min(1),
  runtimeVersion: z.string().min(1),
  projectId: z.string().min(1),
  projectDisplayName: z.string().min(1),
  projectVersion: z.string().min(1),
  category: z.enum(['crash', 'error', 'lifecycle', 'performance', 'usage', 'stats', 'breadcrumbs']),
  supported: z.boolean(),
  enabled: z.boolean(),
  previousEnabled: z.boolean(),
  changeSource: z.enum(['consent_ui', 'first_run_notice', 'command']),
  firstReview: z.boolean(),
}).strict()

export type ConsentMetricEnvelope = z.infer<typeof consentMetricEnvelopeSchema>
```

- [ ] **Step 4: Implement log and service**

Create `hosted/src/consent/consent-metric-log.ts`:

```ts
import { appendFile, mkdir, readFile } from 'node:fs/promises'
import { dirname } from 'node:path'

import type { ConsentMetricEnvelope } from '../contract/consent-metric-envelope.js'

export interface ConsentMetricLog {
  append(metric: ConsentMetricEnvelope): Promise<void>
  readAll(): Promise<ConsentMetricEnvelope[]>
}

export class JsonlConsentMetricLog implements ConsentMetricLog {
  constructor(private readonly filePath: string) {}

  async append(metric: ConsentMetricEnvelope): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true })
    await appendFile(this.filePath, `${JSON.stringify(metric)}\n`, 'utf8')
  }

  async readAll(): Promise<ConsentMetricEnvelope[]> {
    try {
      const raw = await readFile(this.filePath, 'utf8')
      return raw.split('\n')
        .filter((line) => line.trim().length > 0)
        .map((line) => JSON.parse(line) as ConsentMetricEnvelope)
    } catch (error) {
      if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
        return []
      }
      throw error
    }
  }
}
```

Create `hosted/src/consent/consent-metric-service.ts`:

```ts
import { consentMetricEnvelopeSchema, type ConsentMetricEnvelope } from '../contract/consent-metric-envelope.js'
import type { ConsentMetricLog } from './consent-metric-log.js'

export interface ConsentMetricAcceptResult {
  readonly accepted: boolean
  readonly error?: string
}

export class ConsentMetricService {
  constructor(private readonly log: ConsentMetricLog) {}

  async accept(payload: unknown): Promise<ConsentMetricAcceptResult> {
    const parsed = consentMetricEnvelopeSchema.safeParse(payload)
    if (!parsed.success) {
      return { accepted: false, error: 'invalid_consent_metric' }
    }
    await this.log.append(parsed.data)
    return { accepted: true }
  }

  async metrics(): Promise<ConsentMetricEnvelope[]> {
    return this.log.readAll()
  }
}
```

- [ ] **Step 5: Run hosted tests**

Run: `cd hosted; npm test -- consent-metric-service.test.ts`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hosted/src/contract/consent-metric-envelope.ts hosted/src/consent/consent-metric-log.ts hosted/src/consent/consent-metric-service.ts hosted/tests/consent-metric-service.test.ts
git commit -m "Feat: add hosted consent metric service"
```

### Task 5: Hosted Route and Runtime Upload Client

**Files:**
- Modify: `hosted/src/server.ts`
- Modify: `hosted/src/index.ts`
- Modify: `hosted/tests/server.test.ts`
- Create: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/HttpConsentMetricClient.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/HttpConsentMetricClientTest.java`

- [ ] **Step 1: Add failing hosted route test**

Add to `hosted/tests/server.test.ts`:

```ts
it('accepts consent metric payloads on the dedicated route', async () => {
  const consentMetricService = {
    accept: vi.fn().mockResolvedValue({ accepted: true }),
  }
  const server = createTelemetryServer({
    ingestService: fakeIngestService(),
    consentMetricService,
    logger: pino({ enabled: false }),
  })

  const response = await postJson(server, '/ingest/consent-metric', {
    schemaVersion: 1,
    eventId: 'metric-1',
    capturedAtUtc: '2026-06-19T12:00:00.000Z',
    runtimeVersion: '0.2.0',
    projectId: 'alecs-tamework',
    projectDisplayName: "Alec's Tamework",
    projectVersion: '1.4.0',
    category: 'usage',
    supported: true,
    enabled: false,
    previousEnabled: true,
    changeSource: 'consent_ui',
    firstReview: true,
  })

  expect(response.status).toBe(202)
  expect(consentMetricService.accept).toHaveBeenCalledOnce()
})
```

- [ ] **Step 2: Run the failing route test**

Run: `cd hosted; npm test -- server.test.ts`

Expected: FAIL because `createTelemetryServer` does not accept or route `consentMetricService`.

- [ ] **Step 3: Wire hosted route**

Modify `hosted/src/server.ts`:

```ts
export interface TelemetryServerOptions {
  readonly ingestService: TelemetryIngestService
  readonly manualReportIngestService?: ManualReportIngestService
  readonly consentMetricService?: Pick<ConsentMetricService, 'accept'>
  readonly reportStatusService?: ReportStatusService
  readonly statsApi?: StatsApi
  readonly logger: Logger
}
```

Add route handling before generic ingest routing:

```ts
if (request.method === 'POST' && request.url === '/ingest/consent-metric') {
  const body = await readJsonBody(request, options.maxPayloadBytes ?? 262_144)
  const result = await options.consentMetricService?.accept(body)
  if (!result?.accepted) {
    writeJson(response, 400, { accepted: false, error: result?.error ?? 'consent_metrics_unavailable' })
    return
  }
  writeJson(response, 202, { accepted: true })
  return
}
```

Modify `hosted/src/index.ts` to create `JsonlConsentMetricLog` and `ConsentMetricService`, using a path next to the existing stats log.

- [ ] **Step 4: Add runtime HTTP client tests and implementation**

Create `HttpConsentMetricClientTest` following `HttpCrashReportClientTest` patterns. The client posts `TelemetryConsentMetric.toSummary()` to `/ingest/consent-metric` and treats `202` as success.

Minimal production call site:

```java
new TelemetryConsentMetricReporter(
        () -> isUsageRuntimeEnabled(findProject("alecs-telemetry-runtime")),
        metric -> consentMetricClient.upload(metric),
        () -> UUID.randomUUID().toString(),
        () -> Instant.now().toString(),
        () -> runtimeVersion
);
```

If the runtime cannot safely resolve a hosted consent metric endpoint from descriptor fields, add a dedicated runtime setting with default `https://telemetry.alecsmods.com/ingest/consent-metric` and document it in Task 7.

- [ ] **Step 5: Run hosted and runtime focused tests**

Run:

```bash
cd hosted
npm test -- consent-metric-service.test.ts server.test.ts
cd ..
.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest,HttpConsentMetricClientTest,EmbeddedTelemetryServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hosted/src/server.ts hosted/src/index.ts hosted/tests/server.test.ts runtime/src/main/java/com/alechilles/alecstelemetry/consent/HttpConsentMetricClient.java runtime/src/test/java/com/alechilles/alecstelemetry/consent/HttpConsentMetricClientTest.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java
git commit -m "Feat: ingest consent metrics end to end"
```

### Task 6: Consent Metrics Aggregation API

**Files:**
- Modify: `hosted/src/consent/consent-metric-service.ts`
- Create: `hosted/tests/consent-metric-api.test.ts` or extend `hosted/tests/server.test.ts`

- [ ] **Step 1: Write failing aggregation test**

Create a test that appends four metrics and expects opt-out counts by project/category:

```ts
it('summarizes opt-outs by project and category', async () => {
  const rows: any[] = []
  const service = new ConsentMetricService({
    append: async (metric) => { rows.push(metric) },
    readAll: async () => rows,
  })

  await service.accept(metric({ projectId: 'alecs-tamework', category: 'usage', enabled: false, previousEnabled: true }))
  await service.accept(metric({ projectId: 'alecs-tamework', category: 'stats', enabled: true, previousEnabled: false }))
  await service.accept(metric({ projectId: 'alecs-cats', category: 'crash', enabled: false, previousEnabled: true }))

  expect(await service.summary()).toEqual([
    { projectId: 'alecs-cats', category: 'crash', optedOut: 1, optedIn: 0, totalChanges: 1 },
    { projectId: 'alecs-tamework', category: 'stats', optedOut: 0, optedIn: 1, totalChanges: 1 },
    { projectId: 'alecs-tamework', category: 'usage', optedOut: 1, optedIn: 0, totalChanges: 1 },
  ])
})
```

- [ ] **Step 2: Run failing test**

Run: `cd hosted; npm test -- consent-metric-service.test.ts`

Expected: FAIL because `summary()` does not exist.

- [ ] **Step 3: Implement deterministic summary**

Add to `ConsentMetricService`:

```ts
export interface ConsentMetricSummaryRow {
  readonly projectId: string
  readonly category: string
  readonly optedOut: number
  readonly optedIn: number
  readonly totalChanges: number
}

async summary(): Promise<ConsentMetricSummaryRow[]> {
  const buckets = new Map<string, ConsentMetricSummaryRow>()
  for (const metric of await this.log.readAll()) {
    const key = `${metric.projectId}\u0000${metric.category}`
    const current = buckets.get(key) ?? {
      projectId: metric.projectId,
      category: metric.category,
      optedOut: 0,
      optedIn: 0,
      totalChanges: 0,
    }
    buckets.set(key, {
      ...current,
      optedOut: current.optedOut + (metric.enabled ? 0 : 1),
      optedIn: current.optedIn + (metric.enabled ? 1 : 0),
      totalChanges: current.totalChanges + 1,
    })
  }
  return [...buckets.values()].sort((left, right) =>
    left.projectId.localeCompare(right.projectId) || left.category.localeCompare(right.category)
  )
}
```

- [ ] **Step 4: Add route if needed by portal**

If portal work needs a REST endpoint now, add `GET /api/v1/consent-metrics/summary` guarded the same way internal hosted admin routes are guarded. If no admin auth exists in this repo yet, keep this as service-only and let AlecsTelemetryPlatform consume the JSONL path or a separately scoped internal endpoint plan.

- [ ] **Step 5: Run hosted checks**

Run:

```bash
cd hosted
npm test -- consent-metric-service.test.ts
npm run check
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hosted/src/consent/consent-metric-service.ts hosted/tests/consent-metric-service.test.ts
git commit -m "Feat: summarize consent metric opt-outs"
```

### Task 7: Documentation and Privacy Copy

**Files:**
- Create: `docs/consent-metrics.md`
- Modify: `docs/hosted-ingest-contract.md`
- Modify: `wiki/Hosted-Operations/Hosted-Ingest-Contract.md`
- Modify: `wiki/Start-Here/What-Is-Alecs-Telemetry.md`

- [ ] **Step 1: Write consent metrics doc**

Create `docs/consent-metrics.md`:

```markdown
# Consent Metrics

Alec's Telemetry can report anonymous aggregate consent choices for telemetry categories. This is used to understand whether server operators disable crash, error, lifecycle, performance, usage, stats, or breadcrumb telemetry for each participating mod.

Consent metrics are controlled by the Alec's Telemetry project itself. If that project is disabled in `/telemetry consent`, no consent metrics are sent.

Consent metric payloads include only the target project id, target display name, target version, category, previous enabled state, new enabled state, runtime version, timestamp, and whether the change came from the first review flow. They do not include player names, player UUIDs, IP addresses, world names, coordinates, logs, stack traces, chat, raw server ids, session ids, or full loaded mod lists.

The hosted service stores consent observations separately from crash reports, generic events, and public usage stats.
```

- [ ] **Step 2: Update hosted ingest docs**

Add a section to both hosted ingest docs:

```markdown
## Consent Metric Ingest

`POST /ingest/consent-metric` accepts Alec's Telemetry self-metrics for category consent changes. This route is intentionally separate from `/ingest/event` so disabling a mod's event categories does not get bypassed by opt-out measurement.
```

- [ ] **Step 3: Update start-here docs**

Add one concise paragraph to `wiki/Start-Here/What-Is-Alecs-Telemetry.md` explaining that Alec's Telemetry may measure aggregate consent choices only if its own project remains enabled.

- [ ] **Step 4: Run docs grep**

Run: `rg -n "consent metric|consent metrics|/ingest/consent-metric" docs wiki`

Expected: The new route and privacy constraints appear in docs and wiki pages.

- [ ] **Step 5: Commit**

```bash
git add docs/consent-metrics.md docs/hosted-ingest-contract.md wiki/Hosted-Operations/Hosted-Ingest-Contract.md wiki/Start-Here/What-Is-Alecs-Telemetry.md
git commit -m "Docs: document consent metrics privacy model"
```

### Task 8: Full Verification

**Files:**
- No new files.

- [ ] **Step 1: Run runtime focused tests**

Run: `.\mvnw.cmd -pl runtime -Dtest=TelemetryConsentMetricReporterTest,HttpConsentMetricClientTest,EmbeddedTelemetryServiceTest test`

Expected: PASS.

- [ ] **Step 2: Run runtime reactor tests**

Run: `.\mvnw.cmd test`

Expected: PASS.

- [ ] **Step 3: Run hosted checks**

Run:

```bash
cd hosted
npm test
npm run check
npm run build
```

Expected: PASS for all three commands.

- [ ] **Step 4: Verify packaged descriptor**

Run: `.\mvnw.cmd package`

Then inspect the standalone jar:

```bash
jar tf standalone/target/alecstelemetry-standalone-0.2.0.jar | findstr /C:"telemetry/project.json"
```

Expected: `telemetry/project.json` is present in the standalone artifact.

- [ ] **Step 5: Final commit if verification changes files**

If generated files or docs changed during verification, stage only intentional files and commit:

```bash
git status --short
git add <intentional-files-only>
git commit -m "Chore: verify consent metrics packaging"
```

## Self-Review

- Spec coverage: The plan covers a self-controlled consent metrics project, runtime emission at consent-save time, hosted validation/persistence, aggregation by project/category, documentation, and packaging verification.
- Placeholder scan: The only conditional item is the aggregation route in Task 6 because this repo may not own the portal/admin auth surface. The service-level summary remains fully specified and testable.
- Type consistency: Runtime uses `TelemetryConsentMetric` and `TelemetryConsentMetricReporter`; hosted uses `ConsentMetricEnvelope`, `ConsentMetricService`, and `ConsentMetricLog` consistently across tasks.
