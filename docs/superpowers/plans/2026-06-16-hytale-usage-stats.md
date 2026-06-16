# Hytale Usage Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HStats/bStats-style anonymous Hytale mod usage statistics to Alec's Telemetry, starting with a shippable heartbeat, aggregate stats store, public stats API, embeds, and chart definitions.

**Architecture:** Runtime mods emit bounded `stats` events through the existing `TelemetryEventEnvelope` transport: `eventName: "heartbeat"` for built-in server/player activity and `eventName: "chart_sample"` for custom chart values. The hosted prototype accepts `/ingest/event`, validates stats payloads against project config, appends accepted stats observations to a file-backed event log, and derives current counts, records, time-series charts, and categorical breakdowns from that log. Public stats endpoints remain read-only and separate from crash/error/usage diagnostics.

**Tech Stack:** Java 25 runtime modules, Hytale server plugin API, TypeScript hosted prototype, Node 20, zod, vitest, Maven/JUnit.

---

## Scope

This plan implements the first stats product surface inside this repo:

- anonymous active server count
- anonymous players-online count
- current and record values
- historical line data
- Hytale-specific environment breakdown charts
- bStats-style custom chart definitions: simple pie, advanced pie, drilldown pie, single line, multi line, simple bar, advanced bar
- public read-only REST endpoints
- compact embed JSON endpoint that can power README/CurseForge/Modtale cards
- runtime heartbeat emission for dependency-mode projects
- consent enforcement through the existing project/category controls

This plan does not implement account login, project self-registration UI, project ownership transfer, or a production SQL schema. Those belong in the production backend that `hosted/README.md` says is outside this local/dev prototype. This plan keeps the prototype complete enough to verify contracts, runtime behavior, aggregation semantics, and public API shape before porting the persistence layer to the canonical VPS service.

## Product Rules

- A server is active for a project when that `projectId + serverId` has an accepted heartbeat in the last 45 minutes.
- Player count is the latest `playersOnline` value per active `projectId + serverId`, summed at query time.
- Records are max observed active servers and max observed players in any 30-minute bucket.
- All public stats are aggregate-only. Raw server identifiers never appear in public API responses.
- `remoteAddress` is used only for rate limiting and optional country derivation. It is not stored in observations.
- Country is `unknown` until a backend deployment provides a country resolver.
- Runtime consent must suppress heartbeat and custom chart emission when the separate project stats telemetry category is disabled.
- Diagnostic crash/error/performance/usage pages stay separate from public stats unless a later product decision explicitly exposes aggregate issue counts.

## File Structure

### Hosted Prototype

- Create: `hosted/src/contract/event-envelope.ts`
  - zod schema for `TelemetryEventEnvelope` event uploads.
- Create: `hosted/src/stats/stats-model.ts`
  - shared chart, observation, summary, and API response types.
- Create: `hosted/src/stats/stats-event-log.ts`
  - append-only JSONL persistence for accepted stats observations.
- Create: `hosted/src/stats/stats-aggregator.ts`
  - derives current counts, records, line data, and categorical chart data from observations.
- Create: `hosted/src/stats/stats-service.ts`
  - validates stats events against project chart config and writes observations.
- Create: `hosted/src/stats/stats-api.ts`
  - read-only API route helper for stats endpoints.
- Modify: `hosted/src/projects/project-registry.ts`
  - add public stats metadata and custom chart definitions to hosted project config.
- Modify: `hosted/src/ingest/telemetry-ingest-service.ts`
  - accept event envelopes separately from crash envelopes.
- Modify: `hosted/src/server.ts`
  - route `POST /ingest/event` and read-only `/api/v1/...` stats endpoints.
- Modify: `hosted/src/index.ts`
  - construct file-backed stats services.
- Modify: `hosted/src/config.ts`
  - add `TELEMETRY_STATS_FILE`.
- Test: `hosted/tests/event-envelope.test.ts`
- Test: `hosted/tests/stats-event-log.test.ts`
- Test: `hosted/tests/stats-aggregator.test.ts`
- Test: `hosted/tests/stats-service.test.ts`
- Test: `hosted/tests/stats-api.test.ts`
- Test: `hosted/tests/server-stats.test.ts`

### Runtime

- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounter.java`
  - tracks current online players from `PlayerReadyEvent` and `PlayerDisconnectEvent`.
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsRuntime.java`
  - small adapter interface around `TelemetryRuntimeService` so heartbeat tests do not subclass the final runtime service.
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatService.java`
  - periodically emits first-class `stats` heartbeat events for dependency-mode projects.
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
  - register player counter events and start/stop stats heartbeat service.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCategory.java`
  - add a separate `STATS("stats")` category.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentSnapshot.java`
  - persist and expose the stats category toggle separately from usage.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java`
  - add descriptor-level `stats` defaults, allowlists, and detail rules.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectOverride.java`
  - parse runtime stats overrides.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
  - merge descriptor and override stats settings.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
  - add `recordStatsWithContext` and enforce the stats category gate.
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
  - expose the stats event API and consent mutation.
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java`
  - surface stats as its own consent column.
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java`
  - bind the stats checkbox separately from usage.
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryHandle.java`
  - add default helper methods for custom stats chart events.
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
  - add matching default helper methods for dependency-mode consumers.
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounterTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatServiceTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java`

### Docs

- Modify: `docs/hosted-ingest-contract.md`
- Modify: `docs/project-descriptor.md`
- Create: `docs/usage-stats.md`
- Modify: `README.md`

---

### Task 1: Hosted Event Envelope Contract

**Files:**
- Create: `hosted/src/contract/event-envelope.ts`
- Test: `hosted/tests/event-envelope.test.ts`

- [ ] **Step 1: Write the failing schema tests**

Create `hosted/tests/event-envelope.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { telemetryEventEnvelopeSchema } from '../src/contract/event-envelope.js'

describe('telemetryEventEnvelopeSchema', () => {
  it('accepts a stats heartbeat event', () => {
    const parsed = telemetryEventEnvelopeSchema.parse({
      schemaVersion: 2,
      eventType: 'stats',
      eventName: 'heartbeat',
      eventId: 'event-1',
      projectId: 'example-mod',
      projectDisplayName: 'Example Mod',
      source: 'runtime',
      sessionId: 'session-1',
      serverId: '550e8400-e29b-41d4-a716-446655440000',
      fingerprint: null,
      capturedAtUtc: '2026-06-16T20:00:00.000Z',
      pluginIdentifier: 'example:Example Mod',
      pluginVersion: '1.2.3',
      worldName: null,
      severity: 'info',
      durationMs: null,
      metricValue: null,
      subsystem: null,
      phase: null,
      operation: null,
      target: null,
      featureKey: 'stats',
      entryPoint: 'heartbeat',
      runtimeSide: 'server',
      entityType: null,
      itemId: null,
      blockId: null,
      biomeId: null,
      commandName: null,
      environment: {
        hytaleBuild: '2026.06.16-test',
        serverVersion: '2026.06.16-test',
        modSetHash: 'abc123',
      },
      attributes: {},
      details: {
        playersOnline: 12,
      },
      runtime: {
        javaVersion: '25',
        runtimeVersion: '25+0',
        osName: 'Windows 11',
        osVersion: '10.0',
        osArch: 'amd64',
        hytaleBuild: '2026.06.16-test',
        serverVersion: '2026.06.16-test',
        loadedMods: [
          { identifier: 'example:Example Mod', version: '1.2.3' },
        ],
      },
    })

    expect(parsed.eventType).toBe('stats')
    expect(parsed.details.playersOnline).toBe(12)
  })

  it('rejects unsupported event types', () => {
    const parsed = telemetryEventEnvelopeSchema.safeParse({
      schemaVersion: 2,
      eventType: 'debug',
      eventName: 'heartbeat',
      eventId: 'event-1',
      projectId: 'example-mod',
      projectDisplayName: 'Example Mod',
      source: 'runtime',
      sessionId: 'session-1',
      serverId: 'server-1',
      capturedAtUtc: '2026-06-16T20:00:00.000Z',
      pluginIdentifier: 'example:Example Mod',
      pluginVersion: '1.2.3',
      severity: 'info',
      attributes: {},
      details: {},
      runtime: {
        javaVersion: '25',
        runtimeVersion: '25+0',
        osName: 'Windows 11',
        osVersion: '10.0',
        osArch: 'amd64',
        hytaleBuild: '2026.06.16-test',
        serverVersion: '2026.06.16-test',
        loadedMods: [],
      },
    })

    expect(parsed.success).toBe(false)
  })
})
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd hosted
npm test -- event-envelope.test.ts
```

Expected: TypeScript module resolution fails because `../src/contract/event-envelope.js` does not exist.

- [ ] **Step 3: Add the event envelope schema**

Create `hosted/src/contract/event-envelope.ts`:

```ts
import { z } from 'zod'

const nullableStringSchema = z.string().min(1).nullable().optional()
const recordSchema = z.record(z.union([z.string(), z.number(), z.boolean()])).default({})

export const runtimeLoadedModSchema = z.object({
  identifier: z.string().min(1),
  version: z.string().min(1),
})

export const runtimeMetadataSchema = z.object({
  javaVersion: z.string().min(1),
  runtimeVersion: z.string().min(1),
  osName: z.string().min(1),
  osVersion: z.string().min(1),
  osArch: z.string().min(1),
  hytaleBuild: z.string().min(1),
  serverVersion: z.string().min(1),
  loadedMods: z.array(runtimeLoadedModSchema).default([]),
})

export const environmentSnapshotSchema = z.object({
  hytaleBuild: z.string().min(1),
  serverVersion: z.string().min(1),
  modSetHash: z.string().min(1),
}).nullable().optional()

export const telemetryEventEnvelopeSchema = z.object({
  schemaVersion: z.number().int().min(1),
  eventType: z.enum(['error', 'lifecycle', 'performance', 'usage', 'stats']),
  eventName: z.string().min(1),
  eventId: z.string().min(1),
  projectId: z.string().min(1),
  projectDisplayName: z.string().min(1),
  source: z.string().min(1),
  sessionId: z.string().min(1),
  serverId: z.string().min(1),
  fingerprint: nullableStringSchema,
  capturedAtUtc: z.string().min(1),
  pluginIdentifier: z.string().min(1),
  pluginVersion: z.string().min(1),
  worldName: nullableStringSchema,
  severity: z.enum(['info', 'warning', 'error']).default('info'),
  durationMs: z.number().int().min(0).nullable().optional(),
  metricValue: z.number().nullable().optional(),
  subsystem: nullableStringSchema,
  phase: nullableStringSchema,
  operation: nullableStringSchema,
  target: nullableStringSchema,
  featureKey: nullableStringSchema,
  entryPoint: nullableStringSchema,
  runtimeSide: nullableStringSchema,
  entityType: nullableStringSchema,
  itemId: nullableStringSchema,
  blockId: nullableStringSchema,
  biomeId: nullableStringSchema,
  commandName: nullableStringSchema,
  environment: environmentSnapshotSchema,
  attributes: recordSchema,
  details: recordSchema,
  runtime: runtimeMetadataSchema,
})

export type TelemetryEventEnvelope = z.infer<typeof telemetryEventEnvelopeSchema>
```

- [ ] **Step 4: Run the contract tests**

Run:

```powershell
cd hosted
npm test -- event-envelope.test.ts
```

Expected: `PASS hosted/tests/event-envelope.test.ts`.

- [ ] **Step 5: Commit**

```powershell
git add hosted/src/contract/event-envelope.ts hosted/tests/event-envelope.test.ts
git commit -m "Feat: add hosted event envelope contract"
```

---

### Task 2: Project Stats Chart Configuration

**Files:**
- Modify: `hosted/src/projects/project-registry.ts`
- Test: `hosted/tests/project-registry.test.ts`

- [ ] **Step 1: Write failing project registry tests**

Create `hosted/tests/project-registry.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { HostedProjectRegistry } from '../src/projects/project-registry.js'

describe('HostedProjectRegistry stats config', () => {
  it('normalizes public stats metadata and custom chart definitions', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
      enabled: true,
      stats: {
        public: true,
        slug: 'example-mod',
        description: 'Example usage stats.',
        charts: [
          {
            chartId: 'language',
            title: 'Language',
            type: 'simple_pie',
            sourceEvent: 'chart_sample',
            valueField: 'language',
            position: 20,
          },
        ],
      },
    }])

    const project = registry.findByProjectId('EXAMPLE-MOD')
    expect(project?.stats.public).toBe(true)
    expect(project?.stats.slug).toBe('example-mod')
    expect(project?.stats.charts[0]?.chartId).toBe('language')
  })

  it('defaults stats to private with no custom charts', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
    }])

    const project = registry.findByProjectId('example-mod')
    expect(project?.stats.public).toBe(false)
    expect(project?.stats.charts).toEqual([])
  })
})
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd hosted
npm test -- project-registry.test.ts
```

Expected: compile failure because `stats` is not part of `HostedProjectConfig`.

- [ ] **Step 3: Add chart config schemas**

Modify `hosted/src/projects/project-registry.ts` by adding these schemas above `hostedProjectConfigSchema`:

```ts
const statsChartTypeSchema = z.enum([
  'simple_pie',
  'advanced_pie',
  'drilldown_pie',
  'single_line',
  'multi_line',
  'simple_bar',
  'advanced_bar',
])

const statsChartConfigSchema = z.object({
  chartId: z.string().regex(/^[a-z0-9_:-]{1,80}$/),
  title: z.string().min(1).max(120),
  type: statsChartTypeSchema,
  sourceEvent: z.string().min(1).max(120).default('chart_sample'),
  valueField: z.string().min(1).max(80).optional(),
  seriesField: z.string().min(1).max(80).optional(),
  groupField: z.string().min(1).max(80).optional(),
  subGroupField: z.string().min(1).max(80).optional(),
  countField: z.string().min(1).max(80).optional(),
  position: z.number().int().min(0).max(1000).default(100),
})

const statsProjectConfigSchema = z.object({
  public: z.boolean().default(false),
  slug: z.string().regex(/^[a-z0-9-]{1,120}$/).optional(),
  description: z.string().max(500).optional(),
  charts: z.array(statsChartConfigSchema).default([]),
}).default({})
```

Then add `stats: statsProjectConfigSchema,` to `hostedProjectConfigSchema`.

Export the new types:

```ts
export type StatsChartConfig = z.infer<typeof statsChartConfigSchema>
export type StatsProjectConfig = z.infer<typeof statsProjectConfigSchema>
```

- [ ] **Step 4: Run the project registry tests**

Run:

```powershell
cd hosted
npm test -- project-registry.test.ts
```

Expected: `PASS hosted/tests/project-registry.test.ts`.

- [ ] **Step 5: Commit**

```powershell
git add hosted/src/projects/project-registry.ts hosted/tests/project-registry.test.ts
git commit -m "Feat: add stats chart project config"
```

---

### Task 3: File-Backed Stats Observation Log

**Files:**
- Create: `hosted/src/stats/stats-model.ts`
- Create: `hosted/src/stats/stats-event-log.ts`
- Test: `hosted/tests/stats-event-log.test.ts`

- [ ] **Step 1: Write failing event log tests**

Create `hosted/tests/stats-event-log.test.ts`:

```ts
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

import { afterEach, describe, expect, it } from 'vitest'

import { StatsEventLog } from '../src/stats/stats-event-log.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

describe('StatsEventLog', () => {
  const dirs: string[] = []

  afterEach(async () => {
    await Promise.all(dirs.map((dir) => rm(dir, { recursive: true, force: true })))
  })

  it('appends and reloads observations as jsonl', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'alecs-telemetry-stats-'))
    dirs.push(dir)
    const file = join(dir, 'stats.jsonl')
    const log = new StatsEventLog(file)
    const observation: StatsObservation = {
      projectId: 'example-mod',
      eventId: 'event-1',
      eventName: 'heartbeat',
      serverId: 'server-1',
      sessionId: 'session-1',
      observedAtUtc: '2026-06-16T20:00:00.000Z',
      playersOnline: 3,
      pluginVersion: '1.2.3',
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      javaVersion: '25',
      osName: 'Windows 11',
      osArch: 'amd64',
      countryCode: 'unknown',
      loadedMods: [{ identifier: 'example:Example Mod', version: '1.2.3' }],
      custom: {},
    }

    await log.append(observation)

    expect(await log.readAll()).toEqual([observation])
    expect(await readFile(file, 'utf8')).toContain('"projectId":"example-mod"')
  })

  it('skips malformed lines while preserving valid lines', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'alecs-telemetry-stats-'))
    dirs.push(dir)
    const file = join(dir, 'stats.jsonl')
    const log = new StatsEventLog(file)
    const observation: StatsObservation = {
      projectId: 'example-mod',
      eventId: 'event-1',
      eventName: 'heartbeat',
      serverId: 'server-1',
      sessionId: 'session-1',
      observedAtUtc: '2026-06-16T20:00:00.000Z',
      playersOnline: 0,
      pluginVersion: '1.2.3',
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      javaVersion: '25',
      osName: 'Windows 11',
      osArch: 'amd64',
      countryCode: 'unknown',
      loadedMods: [],
      custom: {},
    }

    await log.append(observation)
    await import('node:fs/promises').then((fs) => fs.appendFile(file, '{bad json}\n', 'utf8'))

    expect(await log.readAll()).toEqual([observation])
  })
})
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd hosted
npm test -- stats-event-log.test.ts
```

Expected: compile failure because the stats files do not exist.

- [ ] **Step 3: Add stats model types**

Create `hosted/src/stats/stats-model.ts`:

```ts
export interface LoadedModSummary {
  readonly identifier: string
  readonly version: string
}

export interface StatsObservation {
  readonly projectId: string
  readonly eventId: string
  readonly eventName: string
  readonly serverId: string
  readonly sessionId: string
  readonly observedAtUtc: string
  readonly playersOnline: number
  readonly pluginVersion: string
  readonly hytaleBuild: string
  readonly serverVersion: string
  readonly javaVersion: string
  readonly osName: string
  readonly osArch: string
  readonly countryCode: string
  readonly loadedMods: readonly LoadedModSummary[]
  readonly custom: Record<string, string | number | boolean>
}

export interface StatsLinePoint {
  readonly timestamp: number
  readonly value: number
}

export interface StatsSummary {
  readonly projectId: string
  readonly activeServers: number
  readonly activePlayers: number
  readonly recordServers: number
  readonly recordPlayers: number
  readonly lastUpdatedAtUtc: string | null
}
```

- [ ] **Step 4: Add the JSONL event log**

Create `hosted/src/stats/stats-event-log.ts`:

```ts
import { mkdir, readFile, appendFile } from 'node:fs/promises'
import { dirname } from 'node:path'

import type { StatsObservation } from './stats-model.js'

function isObservation(value: unknown): value is StatsObservation {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Partial<StatsObservation>
  return typeof candidate.projectId === 'string'
    && typeof candidate.eventId === 'string'
    && typeof candidate.eventName === 'string'
    && typeof candidate.serverId === 'string'
    && typeof candidate.sessionId === 'string'
    && typeof candidate.observedAtUtc === 'string'
    && typeof candidate.playersOnline === 'number'
}

export class StatsEventLog {
  constructor(private readonly filePath: string) {}

  async append(observation: StatsObservation): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true })
    await appendFile(this.filePath, `${JSON.stringify(observation)}\n`, 'utf8')
  }

  async readAll(): Promise<StatsObservation[]> {
    let raw: string
    try {
      raw = await readFile(this.filePath, 'utf8')
    } catch (error) {
      if (error instanceof Error && 'code' in error && error.code === 'ENOENT') {
        return []
      }
      throw error
    }

    const observations: StatsObservation[] = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.trim().length === 0) {
        continue
      }
      try {
        const parsed = JSON.parse(line) as unknown
        if (isObservation(parsed)) {
          observations.push(parsed)
        }
      } catch {
        continue
      }
    }
    return observations
  }
}
```

- [ ] **Step 5: Run the event log tests**

Run:

```powershell
cd hosted
npm test -- stats-event-log.test.ts
```

Expected: `PASS hosted/tests/stats-event-log.test.ts`.

- [ ] **Step 6: Commit**

```powershell
git add hosted/src/stats/stats-model.ts hosted/src/stats/stats-event-log.ts hosted/tests/stats-event-log.test.ts
git commit -m "Feat: add stats observation log"
```

---

### Task 4: Stats Aggregation

**Files:**
- Create: `hosted/src/stats/stats-aggregator.ts`
- Test: `hosted/tests/stats-aggregator.test.ts`

- [ ] **Step 1: Write failing aggregation tests**

Create `hosted/tests/stats-aggregator.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { StatsAggregator } from '../src/stats/stats-aggregator.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

function observation(overrides: Partial<StatsObservation>): StatsObservation {
  return {
    projectId: 'example-mod',
    eventId: `event-${Math.random()}`,
    eventName: 'heartbeat',
    serverId: 'server-1',
    sessionId: 'session-1',
    observedAtUtc: '2026-06-16T20:00:00.000Z',
    playersOnline: 1,
    pluginVersion: '1.0.0',
    hytaleBuild: '2026.06.16-test',
    serverVersion: '2026.06.16-test',
    javaVersion: '25',
    osName: 'Windows 11',
    osArch: 'amd64',
    countryCode: 'unknown',
    loadedMods: [],
    custom: {},
    ...overrides,
  }
}

describe('StatsAggregator', () => {
  it('counts latest active server observations inside the active window', () => {
    const now = Date.parse('2026-06-16T20:30:00.000Z')
    const aggregator = new StatsAggregator([
      observation({ serverId: 'server-1', playersOnline: 2, observedAtUtc: '2026-06-16T20:00:00.000Z' }),
      observation({ serverId: 'server-1', playersOnline: 4, observedAtUtc: '2026-06-16T20:20:00.000Z' }),
      observation({ serverId: 'server-2', playersOnline: 3, observedAtUtc: '2026-06-16T19:00:00.000Z' }),
      observation({ serverId: 'server-3', playersOnline: 5, observedAtUtc: '2026-06-16T20:25:00.000Z' }),
    ], now)

    expect(aggregator.summary('example-mod')).toEqual({
      projectId: 'example-mod',
      activeServers: 2,
      activePlayers: 9,
      recordServers: 2,
      recordPlayers: 9,
      lastUpdatedAtUtc: '2026-06-16T20:25:00.000Z',
    })
  })

  it('builds categorical breakdowns from latest active observations', () => {
    const now = Date.parse('2026-06-16T20:30:00.000Z')
    const aggregator = new StatsAggregator([
      observation({ serverId: 'server-1', pluginVersion: '1.0.0', playersOnline: 2 }),
      observation({ serverId: 'server-2', pluginVersion: '1.1.0', playersOnline: 1 }),
      observation({ serverId: 'server-3', pluginVersion: '1.1.0', playersOnline: 7 }),
    ], now)

    expect(aggregator.category('example-mod', 'pluginVersion')).toEqual([
      { name: '1.1.0', servers: 2, players: 8 },
      { name: '1.0.0', servers: 1, players: 2 },
    ])
  })
})
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd hosted
npm test -- stats-aggregator.test.ts
```

Expected: compile failure because `stats-aggregator.ts` does not exist.

- [ ] **Step 3: Implement aggregation**

Create `hosted/src/stats/stats-aggregator.ts`:

```ts
import type { StatsObservation, StatsSummary } from './stats-model.js'

export interface CategoryPoint {
  readonly name: string
  readonly servers: number
  readonly players: number
}

const ACTIVE_WINDOW_MS = 45 * 60 * 1000
const BUCKET_MS = 30 * 60 * 1000

function timestamp(value: string): number {
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function bucketStart(value: string): number {
  return Math.floor(timestamp(value) / BUCKET_MS) * BUCKET_MS
}

export class StatsAggregator {
  constructor(
    private readonly observations: readonly StatsObservation[],
    private readonly nowMs: number = Date.now(),
  ) {}

  summary(projectId: string): StatsSummary {
    const latest = this.latestActive(projectId)
    const active = Array.from(latest.values())
    const records = this.records(projectId)
    const lastUpdatedAtUtc = active
      .map((item) => item.observedAtUtc)
      .sort()
      .at(-1) ?? null

    return {
      projectId,
      activeServers: active.length,
      activePlayers: active.reduce((sum, item) => sum + item.playersOnline, 0),
      recordServers: records.recordServers,
      recordPlayers: records.recordPlayers,
      lastUpdatedAtUtc,
    }
  }

  category(projectId: string, field: keyof StatsObservation): CategoryPoint[] {
    const counts = new Map<string, { servers: number; players: number }>()
    for (const observation of this.latestActive(projectId).values()) {
      const raw = observation[field]
      const name = typeof raw === 'string' && raw.length > 0 ? raw : 'unknown'
      const current = counts.get(name) ?? { servers: 0, players: 0 }
      current.servers += 1
      current.players += observation.playersOnline
      counts.set(name, current)
    }
    return Array.from(counts.entries())
      .map(([name, value]) => ({ name, ...value }))
      .sort((left, right) => right.servers - left.servers || right.players - left.players || left.name.localeCompare(right.name))
  }

  private latestActive(projectId: string): Map<string, StatsObservation> {
    const cutoff = this.nowMs - ACTIVE_WINDOW_MS
    const latest = new Map<string, StatsObservation>()
    for (const observation of this.observations) {
      if (observation.projectId !== projectId || timestamp(observation.observedAtUtc) < cutoff) {
        continue
      }
      const current = latest.get(observation.serverId)
      if (!current || timestamp(observation.observedAtUtc) >= timestamp(current.observedAtUtc)) {
        latest.set(observation.serverId, observation)
      }
    }
    return latest
  }

  private records(projectId: string): { recordServers: number; recordPlayers: number } {
    const buckets = new Map<number, Map<string, StatsObservation>>()
    for (const observation of this.observations) {
      if (observation.projectId !== projectId) {
        continue
      }
      const bucket = bucketStart(observation.observedAtUtc)
      const bucketServers = buckets.get(bucket) ?? new Map<string, StatsObservation>()
      const current = bucketServers.get(observation.serverId)
      if (!current || timestamp(observation.observedAtUtc) >= timestamp(current.observedAtUtc)) {
        bucketServers.set(observation.serverId, observation)
      }
      buckets.set(bucket, bucketServers)
    }
    let recordServers = 0
    let recordPlayers = 0
    for (const bucketServers of buckets.values()) {
      const values = Array.from(bucketServers.values())
      recordServers = Math.max(recordServers, values.length)
      recordPlayers = Math.max(recordPlayers, values.reduce((sum, item) => sum + item.playersOnline, 0))
    }
    return { recordServers, recordPlayers }
  }
}
```

- [ ] **Step 4: Run the aggregation tests**

Run:

```powershell
cd hosted
npm test -- stats-aggregator.test.ts
```

Expected: `PASS hosted/tests/stats-aggregator.test.ts`.

- [ ] **Step 5: Commit**

```powershell
git add hosted/src/stats/stats-aggregator.ts hosted/tests/stats-aggregator.test.ts
git commit -m "Feat: aggregate public stats"
```

---

### Task 5: Stats Event Ingest Service

**Files:**
- Create: `hosted/src/stats/stats-service.ts`
- Modify: `hosted/src/ingest/telemetry-ingest-service.ts`
- Test: `hosted/tests/stats-service.test.ts`
- Test: `hosted/tests/telemetry-ingest-service.test.ts`

- [ ] **Step 1: Write failing service tests**

Create `hosted/tests/stats-service.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'

import type { TelemetryEventEnvelope } from '../src/contract/event-envelope.js'
import { StatsService } from '../src/stats/stats-service.js'

function event(overrides: Partial<TelemetryEventEnvelope> = {}): TelemetryEventEnvelope {
  return {
    schemaVersion: 2,
    eventType: 'stats',
    eventName: 'heartbeat',
    eventId: 'event-1',
    projectId: 'example-mod',
    projectDisplayName: 'Example Mod',
    source: 'runtime',
    sessionId: 'session-1',
    serverId: 'server-1',
    fingerprint: null,
    capturedAtUtc: '2026-06-16T20:00:00.000Z',
    pluginIdentifier: 'example:Example Mod',
    pluginVersion: '1.2.3',
    worldName: null,
    severity: 'info',
    durationMs: null,
    metricValue: null,
    subsystem: null,
    phase: null,
    operation: null,
    target: null,
    featureKey: 'stats',
    entryPoint: 'heartbeat',
    runtimeSide: 'server',
    entityType: null,
    itemId: null,
    blockId: null,
    biomeId: null,
    commandName: null,
    environment: { hytaleBuild: '2026.06.16-test', serverVersion: '2026.06.16-test', modSetHash: 'abc123' },
    attributes: {},
    details: { playersOnline: 4 },
    runtime: {
      javaVersion: '25',
      runtimeVersion: '25+0',
      osName: 'Windows 11',
      osVersion: '10.0',
      osArch: 'amd64',
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      loadedMods: [{ identifier: 'example:Example Mod', version: '1.2.3' }],
    },
    ...overrides,
  }
}

describe('StatsService', () => {
  it('stores stats heartbeat observations', async () => {
    const append = vi.fn()
    const service = new StatsService({ append, readAll: vi.fn() })

    const result = await service.acceptEvent(event(), 'unknown')

    expect(result.accepted).toBe(true)
    expect(append).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 'example-mod',
      serverId: 'server-1',
      playersOnline: 4,
      pluginVersion: '1.2.3',
      countryCode: 'unknown',
    }))
  })

  it('ignores non-stats events without writing observations', async () => {
    const append = vi.fn()
    const service = new StatsService({ append, readAll: vi.fn() })

    const result = await service.acceptEvent(event({ eventType: 'usage', eventName: 'settings_opened' }), 'unknown')

    expect(result.accepted).toBe(true)
    expect(result.storedObservation).toBe(false)
    expect(append).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
cd hosted
npm test -- stats-service.test.ts
```

Expected: compile failure because `stats-service.ts` does not exist.

- [ ] **Step 3: Implement stats event conversion**

Create `hosted/src/stats/stats-service.ts`:

```ts
import type { TelemetryEventEnvelope } from '../contract/event-envelope.js'
import type { StatsEventLog } from './stats-event-log.js'
import type { StatsObservation } from './stats-model.js'

export interface StatsAcceptResult {
  readonly accepted: true
  readonly storedObservation: boolean
}

function numberDetail(event: TelemetryEventEnvelope, key: string, fallback: number): number {
  const value = event.details[key]
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : fallback
}

function normalizeCountry(countryCode: string | null): string {
  return countryCode && /^[A-Z]{2}$/.test(countryCode) ? countryCode : 'unknown'
}

function customDetails(event: TelemetryEventEnvelope): Record<string, string | number | boolean> {
  const custom: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(event.details)) {
    if (key === 'playersOnline') {
      continue
    }
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      custom[key] = value
    }
  }
  return custom
}

export class StatsService {
  constructor(private readonly log: Pick<StatsEventLog, 'append' | 'readAll'>) {}

  async acceptEvent(event: TelemetryEventEnvelope, countryCode: string | null): Promise<StatsAcceptResult> {
    if (event.eventType !== 'stats' || (event.eventName !== 'heartbeat' && event.eventName !== 'chart_sample')) {
      return { accepted: true, storedObservation: false }
    }

    const observation: StatsObservation = {
      projectId: event.projectId,
      eventId: event.eventId,
      eventName: event.eventName,
      serverId: event.serverId,
      sessionId: event.sessionId,
      observedAtUtc: event.capturedAtUtc,
      playersOnline: numberDetail(event, 'playersOnline', 0),
      pluginVersion: event.pluginVersion,
      hytaleBuild: event.runtime.hytaleBuild,
      serverVersion: event.runtime.serverVersion,
      javaVersion: event.runtime.javaVersion,
      osName: event.runtime.osName,
      osArch: event.runtime.osArch,
      countryCode: normalizeCountry(countryCode),
      loadedMods: event.runtime.loadedMods,
      custom: customDetails(event),
    }

    await this.log.append(observation)
    return { accepted: true, storedObservation: true }
  }

  async observations(): Promise<StatsObservation[]> {
    return this.log.readAll()
  }
}
```

- [ ] **Step 4: Add event ingest to the existing service**

Modify `hosted/src/ingest/telemetry-ingest-service.ts`:

```ts
import { telemetryEventEnvelopeSchema } from '../contract/event-envelope.js'
import type { StatsService } from '../stats/stats-service.js'
```

Change `TelemetryIngestRequest`:

```ts
export type TelemetryIngestKind = 'crash' | 'event'

export interface TelemetryIngestRequest {
  readonly kind: TelemetryIngestKind
  readonly projectKey: string | null
  readonly payload: unknown
  readonly bodyBytes: number
  readonly remoteAddress: string | null
  readonly receivedAt: Date
}
```

Change the constructor:

```ts
  constructor(
    private readonly registry: HostedProjectRegistry,
    private readonly router: CrashAlertRouter,
    private readonly rateLimiter: RequestRateLimiter,
    private readonly suppressor: DuplicateAlertSuppressor,
    private readonly logger: Logger,
    private readonly statsService: StatsService | null = null,
  ) {}
```

After rate limiting and before crash-specific parsing, branch on event kind:

```ts
    if (request.kind === 'event') {
      const parsedEvent = telemetryEventEnvelopeSchema.safeParse(request.payload)
      if (!parsedEvent.success) {
        return {
          status: 400,
          body: {
            accepted: false,
            error: 'invalid_payload',
            issues: formatValidationIssues(parsedEvent.error),
          },
        }
      }
      const event = parsedEvent.data
      if (event.projectId !== project.projectId) {
        return {
          status: 403,
          body: {
            accepted: false,
            error: 'project_id_mismatch',
            expectedProjectId: project.projectId,
            providedProjectId: event.projectId,
          },
        }
      }
      const statsResult = await this.statsService?.acceptEvent(event, 'unknown')
      return {
        status: 202,
        body: {
          accepted: true,
          projectId: project.projectId,
          eventId: event.eventId,
          eventType: event.eventType,
          eventName: event.eventName,
          storedStatsObservation: statsResult?.storedObservation ?? false,
        },
      }
    }
```

Update every existing test call to `service.ingest` with `kind: 'crash'`.

- [ ] **Step 5: Add event ingest test**

Append to `hosted/tests/telemetry-ingest-service.test.ts`:

```ts
  it('accepts event envelopes and stores stats observations', async () => {
    const project = createProject()
    const statsService = {
      acceptEvent: vi.fn().mockResolvedValue({ accepted: true, storedObservation: true }),
    }
    const { service } = createService(project, new FakeCrashAlertRouter(), statsService)

    const result = await service.ingest({
      kind: 'event',
      projectKey: project.publicProjectKey,
      payload: {
        schemaVersion: 2,
        eventType: 'stats',
        eventName: 'heartbeat',
        eventId: 'event-1',
        projectId: project.projectId,
        projectDisplayName: project.displayName,
        source: 'runtime',
        sessionId: 'session-1',
        serverId: 'server-1',
        capturedAtUtc: '2026-06-16T20:00:00.000Z',
        pluginIdentifier: `Example:${project.displayName}`,
        pluginVersion: '1.0.0',
        severity: 'info',
        attributes: {},
        details: { playersOnline: 2 },
        runtime: {
          javaVersion: '25',
          runtimeVersion: '25+0',
          osName: 'Windows 11',
          osVersion: '10.0',
          osArch: 'amd64',
          hytaleBuild: '2026.06.16-test',
          serverVersion: '2026.06.16-test',
          loadedMods: [],
        },
      },
      bodyBytes: 1024,
      remoteAddress: null,
      receivedAt: new Date('2026-06-16T20:00:00.000Z'),
    })

    expect(result.status).toBe(202)
    expect(result.body.storedStatsObservation).toBe(true)
    expect(statsService.acceptEvent).toHaveBeenCalledOnce()
  })
```

Update the local `createService` helper signature to pass the optional `statsService` into `TelemetryIngestService`.

- [ ] **Step 6: Run ingest tests**

Run:

```powershell
cd hosted
npm test -- stats-service.test.ts telemetry-ingest-service.test.ts
```

Expected: both test files pass.

- [ ] **Step 7: Commit**

```powershell
git add hosted/src/stats/stats-service.ts hosted/src/ingest/telemetry-ingest-service.ts hosted/tests/stats-service.test.ts hosted/tests/telemetry-ingest-service.test.ts
git commit -m "Feat: accept stats event ingest"
```

---

### Task 6: Public Stats API and Server Routes

**Files:**
- Create: `hosted/src/stats/stats-api.ts`
- Modify: `hosted/src/server.ts`
- Modify: `hosted/src/index.ts`
- Modify: `hosted/src/config.ts`
- Test: `hosted/tests/stats-api.test.ts`
- Test: `hosted/tests/server-stats.test.ts`

- [ ] **Step 1: Write stats API tests**

Create `hosted/tests/stats-api.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { HostedProjectRegistry } from '../src/projects/project-registry.js'
import { StatsApi } from '../src/stats/stats-api.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

const observations: StatsObservation[] = [{
  projectId: 'example-mod',
  eventId: 'event-1',
  eventName: 'heartbeat',
  serverId: 'server-1',
  sessionId: 'session-1',
  observedAtUtc: '2026-06-16T20:00:00.000Z',
  playersOnline: 5,
  pluginVersion: '1.0.0',
  hytaleBuild: '2026.06.16-test',
  serverVersion: '2026.06.16-test',
  javaVersion: '25',
  osName: 'Windows 11',
  osArch: 'amd64',
  countryCode: 'unknown',
  loadedMods: [],
  custom: {},
}]

describe('StatsApi', () => {
  it('returns public project summaries', async () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'key',
      stats: { public: true, slug: 'example-mod', charts: [] },
    }])
    const api = new StatsApi(registry, { observations: async () => observations }, Date.parse('2026-06-16T20:15:00.000Z'))

    const response = await api.handle('GET', '/api/v1/projects/example-mod/summary')

    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({
      projectId: 'example-mod',
      activeServers: 1,
      activePlayers: 5,
    })
  })

  it('does not expose private projects', async () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'key',
      stats: { public: false, charts: [] },
    }])
    const api = new StatsApi(registry, { observations: async () => observations }, Date.parse('2026-06-16T20:15:00.000Z'))

    const response = await api.handle('GET', '/api/v1/projects/example-mod/summary')

    expect(response.status).toBe(404)
    expect(response.body.error).toBe('not_found')
  })
})
```

- [ ] **Step 2: Implement read-only API helper**

Create `hosted/src/stats/stats-api.ts`:

```ts
import type { HostedProjectRegistry } from '../projects/project-registry.js'
import { StatsAggregator } from './stats-aggregator.js'
import type { StatsService } from './stats-service.js'

export interface StatsApiResponse {
  readonly status: number
  readonly body: Record<string, unknown>
}

function pathParts(url: string): string[] {
  return url.split('?')[0]?.split('/').filter(Boolean) ?? []
}

export class StatsApi {
  constructor(
    private readonly registry: HostedProjectRegistry,
    private readonly statsService: Pick<StatsService, 'observations'>,
    private readonly nowMs: number = Date.now(),
  ) {}

  async handle(method: string, url: string): Promise<StatsApiResponse | null> {
    if (method !== 'GET') {
      return null
    }
    const parts = pathParts(url)
    if (parts[0] !== 'api' || parts[1] !== 'v1' || parts[2] !== 'projects') {
      return null
    }
    const projectId = parts[3]
    if (!projectId) {
      return { status: 200, body: { projects: this.registry.publicProjects().map((project) => ({ projectId: project.projectId, displayName: project.displayName, slug: project.stats.slug ?? project.projectId })) } }
    }
    const project = this.registry.findByProjectId(projectId)
    if (!project || !project.stats.public) {
      return { status: 404, body: { error: 'not_found' } }
    }
    const aggregator = new StatsAggregator(await this.statsService.observations(), this.nowMs)
    if (parts[4] === 'summary') {
      return { status: 200, body: aggregator.summary(project.projectId) }
    }
    if (parts[4] === 'charts' && parts[5] === 'plugin_version') {
      return { status: 200, body: { chartId: 'plugin_version', type: 'simple_pie', data: aggregator.category(project.projectId, 'pluginVersion') } }
    }
    return { status: 200, body: { projectId: project.projectId, displayName: project.displayName, stats: project.stats } }
  }
}
```

Add this method to `HostedProjectRegistry`:

```ts
  publicProjects(): HostedProjectConfig[] {
    return Array.from(this.byProjectId.values()).filter((project) => project.stats.public)
  }
```

- [ ] **Step 3: Route stats API and event ingest in `server.ts`**

Modify `hosted/src/server.ts` options:

```ts
import type { StatsApi } from './stats/stats-api.js'

export function createHostedServer(options: {
  readonly ingestService: TelemetryIngestService
  readonly statsApi?: StatsApi
  readonly logger: Logger
  readonly maxRequestBodyBytes: number
}): Server {
```

Before the POST route check:

```ts
    const statsResponse = await options.statsApi?.handle(request.method ?? 'GET', request.url ?? '/')
    if (statsResponse) {
      writeJson(response, statsResponse.status, statsResponse.body)
      return
    }
```

Replace the hard-coded crash route check:

```ts
    const ingestKind = request.url === '/ingest/crash'
      ? 'crash'
      : request.url === '/ingest/event'
        ? 'event'
        : null

    if (request.method !== 'POST' || ingestKind === null) {
      writeJson(response, 404, { error: 'not_found' })
      return
    }
```

Pass `kind: ingestKind` into `options.ingestService.ingest`.

- [ ] **Step 4: Add config and index wiring**

Modify `hosted/src/config.ts`:

```ts
  TELEMETRY_STATS_FILE: z.string().min(1).default('./data/stats-observations.jsonl'),
```

Add to the returned config object:

```ts
statsFile: resolve(parsed.TELEMETRY_STATS_FILE),
```

Modify `hosted/src/index.ts`:

```ts
import { StatsApi } from './stats/stats-api.js'
import { StatsEventLog } from './stats/stats-event-log.js'
import { StatsService } from './stats/stats-service.js'
```

Construct services:

```ts
  const statsLog = new StatsEventLog(config.statsFile)
  const statsService = new StatsService(statsLog)
  const statsApi = new StatsApi(registry, statsService)
```

Pass `statsService` to `TelemetryIngestService` and `statsApi` to `createHostedServer`.

- [ ] **Step 5: Add server route tests**

Create `hosted/tests/server-stats.test.ts` with one test for `POST /ingest/event` and one for `GET /api/v1/projects/example-mod/summary`. Use the same server bootstrapping pattern as `hosted/tests/server.test.ts`, but pass a real `StatsService` backed by a temporary `StatsEventLog`.

- [ ] **Step 6: Run hosted tests**

Run:

```powershell
cd hosted
npm test
npm run check
```

Expected: all hosted tests pass and TypeScript check passes.

- [ ] **Step 7: Commit**

```powershell
git add hosted/src/server.ts hosted/src/index.ts hosted/src/config.ts hosted/src/stats/stats-api.ts hosted/src/projects/project-registry.ts hosted/tests/stats-api.test.ts hosted/tests/server-stats.test.ts
git commit -m "Feat: expose public stats API"
```

---

### Task 7: Runtime Stats Consent, Player Counter, and Heartbeat

**Files:**
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/api/TelemetryEventContext.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentCategory.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentSnapshot.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptor.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectOverride.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistration.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/core/TelemetryCoreEngine.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/runtime/TelemetryRuntimeService.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModel.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounter.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsRuntime.java`
- Create: `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatService.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/api/TelemetryEventContextTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentViewModelTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounterTest.java`
- Test: `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatServiceTest.java`

- [ ] **Step 1: Verify Hytale event classes before coding**

Run:

```powershell
javap -classpath "C:\Users\22ale\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar" com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent com.hypixel.hytale.server.core.universe.PlayerRef
```

Expected: `PlayerReadyEvent.getPlayerRef()`, `PlayerDisconnectEvent.getPlayerRef()`, and `PlayerRef.getUuid()` are present.

- [ ] **Step 2: Add failing tests for the stats consent model**

Add this test to `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectDescriptorTest.java`:

```java
@Test
void parsesStatsOptionsSeparatelyFromUsageOptions() {
    TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
            """
            {
              "projectId": "stats-mod",
              "displayName": "Stats Mod",
              "stats": {
                "enabled": true,
                "allowedEvents": ["heartbeat", "chart_sample"],
                "details": {
                  "chart_sample": {
                    "allowedFields": {
                      "chartId": { "type": "string", "maxLength": 80 },
                      "value": { "type": "string", "maxLength": 120 }
                    }
                  }
                }
              },
              "usage": {
                "enabled": false,
                "allowedEvents": ["settings_opened"]
              }
            }
            """,
            null
    );

    assertEquals(true, descriptor.stats().enabled());
    assertEquals(List.of("heartbeat", "chart_sample"), descriptor.stats().allowedEvents());
    assertEquals(false, descriptor.usage().enabled());
}
```

Add this test to `runtime/src/test/java/com/alechilles/alecstelemetry/project/TelemetryProjectRegistrationTest.java`:

```java
@Test
void consentSnapshotKeepsStatsSeparateFromUsage() {
    TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
            """
            {
              "projectId": "stats-mod",
              "displayName": "Stats Mod",
              "stats": { "enabled": true, "allowedEvents": ["heartbeat"] },
              "usage": { "enabled": false, "allowedEvents": ["settings_opened"] }
            }
            """,
            null
    );
    TelemetryProjectRegistration registration = new TelemetryProjectRegistration(
            descriptor,
            "Example:Stats Mod",
            "1.0.0",
            null
    );

    assertEquals(true, registration.consentSnapshot().statsEnabled());
    assertEquals(false, registration.consentSnapshot().usageEnabled());
}
```

Add this test to `runtime/src/test/java/com/alechilles/alecstelemetry/api/TelemetryEventContextTest.java`:

```java
@Test
void statsBuilderCreatesStatsContext() {
    TelemetryEventContext context = TelemetryEventContext.stats()
            .detail("playersOnline", 5)
            .build();

    assertEquals(5, context.details().get("playersOnline"));
}
```

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDescriptorTest,TelemetryProjectRegistrationTest,TelemetryEventContextTest test
```

Expected: tests fail because `stats()` descriptor options, `statsEnabled()`, and `TelemetryEventContext.stats()` do not exist.

- [ ] **Step 3: Implement stats consent model and runtime gate**

Implement these concrete changes:

```java
// TelemetryConsentCategory.java
STATS("stats"),
```

```java
// TelemetryConsentSnapshot.java
public record TelemetryConsentSnapshot(boolean projectEnabled,
                                       boolean crashEnabled,
                                       boolean errorEnabled,
                                       boolean lifecycleEnabled,
                                       boolean performanceEnabled,
                                       boolean usageEnabled,
                                       boolean statsEnabled,
                                       boolean breadcrumbsEnabled) {
}
```

```java
// TelemetryEventContext.java
@Nonnull
public static Builder stats() {
    return builder();
}
```

In `TelemetryProjectDescriptor`, add a `StatsOptions` record with the same shape as `UsageOptions`, parse top-level `"stats"`, default it to disabled with no allowed events, and expose `descriptor.stats()`.

```java
public record StatsOptions(boolean enabled,
                           @Nonnull List<String> allowedEvents,
                           @Nonnull Map<String, DetailRules> details) {
    public boolean allows(@Nonnull String eventName) {
        return allowedEvents().contains(eventName);
    }

    @Nonnull
    public Map<String, Object> sanitizeDetails(@Nonnull String eventName,
                                               @Nonnull Map<String, Object> rawDetails) {
        DetailRules rules = details().get(eventName);
        return rules == null ? Map.of() : rules.sanitize(rawDetails);
    }
}
```

In `TelemetryProjectOverride`, add `StatsOverride` matching `UsageOverride`, parse top-level `"stats"`, and include it in `hasAnyValue()`.

In `TelemetryProjectRegistration`, add `stats()` merge logic equivalent to `usage()` and update `consentSnapshot()` to pass `stats().enabled()` into the new snapshot field.

In `TelemetryCoreEngine`, add a `statsEnabledOverrides` map, `setStatsEnabled(String projectId, boolean enabled)`, `isStatsRuntimeEnabled(project)`, `recordStats(...)`, and `recordStatsWithContext(...)`. These methods should mirror the usage event methods but create a `TelemetryEventEnvelope.stats(...)` event and enforce `project.stats().allows(eventName)`.

In `TelemetryEventEnvelope`, add:

```java
public static final String TYPE_STATS = "stats";
```

and a static factory:

```java
@Nonnull
public static TelemetryEventEnvelope stats(@Nonnull String projectId,
                                           @Nonnull String projectDisplayName,
                                           @Nonnull String source,
                                           @Nonnull String sessionId,
                                           @Nonnull String serverId,
                                           @Nonnull String eventName,
                                           @Nonnull String pluginIdentifier,
                                           @Nonnull String pluginVersion,
                                           @Nullable String worldName,
                                           @Nonnull CrashReportEnvelope.EnvironmentSnapshot environment,
                                           @Nonnull Map<String, Object> attributes,
                                           @Nonnull Map<String, Object> details,
                                           @Nonnull TelemetryEventContext context,
                                           @Nonnull CrashReportEnvelope.RuntimeMetadata runtime) {
    return create(TYPE_STATS, eventName, projectId, projectDisplayName, source, sessionId, serverId, pluginIdentifier, pluginVersion, worldName, SEVERITY_INFO, null, null, environment, attributes, details, context, runtime);
}
```

Update `normalizeType` to preserve `TYPE_STATS`.

In `TelemetryRuntimeService`, add:

```java
public void recordStats(@Nonnull String projectId,
                        @Nonnull String eventName,
                        @Nullable String detail) {
    engine.recordStats(projectId, eventName, detail);
}

public void recordStatsWithContext(@Nonnull String projectId,
                                   @Nonnull String eventName,
                                   @Nullable TelemetryEventContext context) {
    engine.recordStatsWithContext(projectId, eventName, context);
}
```

Update consent persistence methods so `TelemetryConsentCategory.STATS` writes and reads `stats.enabled`.

Update `TelemetryConsentViewModel` and `TelemetryConsentPage` to add a separate stats checkbox. Keep `usage` visible as feature usage telemetry and label stats as anonymous public stats.

- [ ] **Step 4: Run stats consent model tests**

Run:

```powershell
.\mvnw.cmd -pl runtime -Dtest=TelemetryProjectDescriptorTest,TelemetryProjectRegistrationTest,TelemetryEventContextTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryConsentViewModelTest test
```

Expected: tests pass.

- [ ] **Step 5: Add player counter tests**

Create `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounterTest.java`:

```java
package com.alechilles.alecstelemetry.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryPlayerCounterTest {
    @Test
    void tracksUniquePlayers() {
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        UUID player = UUID.randomUUID();

        counter.markReady(player);
        counter.markReady(player);

        assertEquals(1, counter.onlinePlayers());
    }

    @Test
    void removesDisconnectedPlayers() {
        TelemetryPlayerCounter counter = new TelemetryPlayerCounter();
        UUID player = UUID.randomUUID();

        counter.markReady(player);
        counter.markDisconnected(player);

        assertEquals(0, counter.onlinePlayers());
    }
}
```

- [ ] **Step 6: Implement player counter**

Create `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryPlayerCounter.java`:

```java
package com.alechilles.alecstelemetry.stats;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TelemetryPlayerCounter {
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            markReady(playerRef.getUuid());
        }
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayer();
        if (playerRef != null) {
            markDisconnected(playerRef.getUuid());
        }
    }

    public void markReady(@Nonnull UUID playerUuid) {
        onlinePlayers.add(playerUuid);
    }

    public void markDisconnected(@Nonnull UUID playerUuid) {
        onlinePlayers.remove(playerUuid);
    }

    public int onlinePlayers() {
        return onlinePlayers.size();
    }
}
```

- [ ] **Step 7: Add heartbeat service tests**

Create `standalone/src/test/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatServiceTest.java`:

```java
package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryStatsHeartbeatServiceTest {
    @Test
    void emitsHeartbeatForEveryConsentVisibleProject() {
        FakeRuntime runtime = new FakeRuntime(List.of(
                registration("example-mod", "Example Mod"),
                registration("second-mod", "Second Mod")
        ));
        TelemetryPlayerCounter players = new TelemetryPlayerCounter();
        players.markReady(java.util.UUID.randomUUID());

        new TelemetryStatsHeartbeatService(runtime, players).emitHeartbeatNow();

        assertEquals(List.of("example-mod", "second-mod"), runtime.projectIds);
        assertEquals(1, runtime.playersOnlineValues.get(0));
        assertEquals(1, runtime.playersOnlineValues.get(1));
    }

    private static TelemetryProjectRegistration registration(String projectId, String displayName) {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "%s",
                  "displayName": "%s",
                  "ownerPluginIdentifiers": ["Example:%s"],
                  "packagePrefixes": ["com.example"],
                  "stats": {
                    "enabled": true,
                    "allowedEvents": ["heartbeat", "chart_sample"]
                  }
                }
                """.formatted(projectId, displayName, displayName),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:" + displayName, "1.0.0", null);
    }

    private static final class FakeRuntime implements TelemetryStatsRuntime {
        private final List<TelemetryProjectRegistration> projects;
        private final java.util.ArrayList<String> projectIds = new java.util.ArrayList<>();
        private final java.util.ArrayList<Integer> playersOnlineValues = new java.util.ArrayList<>();

        private FakeRuntime(List<TelemetryProjectRegistration> projects) {
            this.projects = projects;
        }

        @Override
        public List<TelemetryProjectRegistration> projects() {
            return projects;
        }

        @Override
        public void recordStatsWithContext(String projectId, String eventName, TelemetryEventContext context) {
            projectIds.add(projectId);
            playersOnlineValues.add(((Number) context.details().get("playersOnline")).intValue());
        }
    }
}
```

- [ ] **Step 8: Implement runtime adapter**

Create `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsRuntime.java`:

```java
package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeService;

import javax.annotation.Nonnull;
import java.util.List;

public interface TelemetryStatsRuntime {
    @Nonnull
    List<TelemetryProjectRegistration> projects();

    void recordStatsWithContext(@Nonnull String projectId,
                                @Nonnull String eventName,
                                @Nonnull TelemetryEventContext context);

    @Nonnull
    static TelemetryStatsRuntime from(@Nonnull TelemetryRuntimeService runtimeService) {
        return new TelemetryStatsRuntime() {
            @Override
            public List<TelemetryProjectRegistration> projects() {
                return runtimeService.projects();
            }

            @Override
            public void recordStatsWithContext(@Nonnull String projectId,
                                               @Nonnull String eventName,
                                               @Nonnull TelemetryEventContext context) {
                runtimeService.recordStatsWithContext(projectId, eventName, context);
            }
        };
    }
}
```

- [ ] **Step 9: Implement heartbeat service**

Create `standalone/src/main/java/com/alechilles/alecstelemetry/stats/TelemetryStatsHeartbeatService.java`:

```java
package com.alechilles.alecstelemetry.stats;

import com.alechilles.alecstelemetry.api.TelemetryEventContext;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TelemetryStatsHeartbeatService implements AutoCloseable {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(30);

    private final TelemetryStatsRuntime runtime;
    private final TelemetryPlayerCounter playerCounter;
    private final ScheduledExecutorService executor;

    public TelemetryStatsHeartbeatService(@Nonnull TelemetryStatsRuntime runtime,
                                          @Nonnull TelemetryPlayerCounter playerCounter) {
        this.runtime = runtime;
        this.playerCounter = playerCounter;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AlecsTelemetry-StatsHeartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(this::emitHeartbeatNow, 60, HEARTBEAT_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    public void emitHeartbeatNow() {
        int playersOnline = playerCounter.onlinePlayers();
        for (TelemetryProjectRegistration project : runtime.projects()) {
            runtime.recordStatsWithContext(
                    project.projectId(),
                    "heartbeat",
                    TelemetryEventContext.stats()
                            .featureKey("stats")
                            .entryPoint("heartbeat")
                            .runtimeSide("server")
                            .detail("playersOnline", playersOnline)
                            .build()
            );
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
```

- [ ] **Step 10: Wire standalone plugin events**

Modify `standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java`:

```java
private TelemetryPlayerCounter playerCounter;
private TelemetryStatsHeartbeatService statsHeartbeatService;
```

After runtime service creation:

```java
playerCounter = new TelemetryPlayerCounter();
getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerCounter::onPlayerReady);
getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, playerCounter::onPlayerDisconnect);
statsHeartbeatService = new TelemetryStatsHeartbeatService(TelemetryStatsRuntime.from(runtimeService), playerCounter);
statsHeartbeatService.start();
```

In the plugin shutdown path:

```java
if (statsHeartbeatService != null) {
    statsHeartbeatService.close();
}
```

- [ ] **Step 11: Run runtime tests**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryPlayerCounterTest,TelemetryStatsHeartbeatServiceTest test
.\mvnw.cmd -pl standalone -Dtest=TelemetryRuntimeServiceTest test
```

Expected: tests pass.

- [ ] **Step 12: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/stats standalone/src/main/java/com/alechilles/alecstelemetry/AlecsTelemetry.java standalone/src/test/java/com/alechilles/alecstelemetry/stats
git commit -m "Feat: emit stats heartbeats"
```

---

### Task 8: Custom Chart Runtime Helpers

**Files:**
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java`
- Modify: `standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryHandle.java`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java`
- Modify: `standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java`

- [ ] **Step 1: Add compatibility test calls**

Append calls to `TelemetryProjectHandleCompatibilityTest.compatibilityImplementationsCompile()`:

```java
handle.recordSimpleStatChart("language", "English");
handle.recordNumericStatChart("active_npcs", 12);
handle.recordStatsWithContext("chart_sample", TelemetryEventContext.stats().detail("chartId", "language").detail("value", "English").build());
```

- [ ] **Step 2: Add dependency-mode stats event methods and default chart helpers**

Modify `TelemetryProjectHandle`:

```java
void recordStats(@Nonnull String eventName, @Nullable String detail);

default void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
    recordStats(eventName, context == null ? null : context.detail());
}

default void recordSimpleStatChart(@Nonnull String chartId, @Nonnull String value) {
    recordStatsWithContext(
            "chart_sample",
            TelemetryEventContext.stats()
                    .featureKey("stats")
                    .entryPoint("custom_chart")
                    .runtimeSide("server")
                    .detail("chartId", chartId)
                    .detail("value", value)
                    .build()
    );
}

default void recordNumericStatChart(@Nonnull String chartId, double value) {
    recordStatsWithContext(
            "chart_sample",
            TelemetryEventContext.stats()
                    .featureKey("stats")
                    .entryPoint("custom_chart")
                    .runtimeSide("server")
                    .detail("chartId", chartId)
                    .detail("value", value)
                    .build()
    );
}
```

Modify `TelemetryProjectHandleImpl`:

```java
@Override
public void recordStats(@Nonnull String eventName, @Nullable String detail) {
    runtimeService.recordStats(projectId, eventName, detail);
}

@Override
public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
    runtimeService.recordStatsWithContext(projectId, eventName, context);
}
```

Add this method to the `NoopTelemetryProjectHandle` test implementation:

```java
@Override
public void recordStats(@Nonnull String eventName, @Nullable String detail) {
}
```

- [ ] **Step 3: Add embedded-mode matching helpers**

Add the same `recordStats`, `recordStatsWithContext`, `recordSimpleStatChart`, and `recordNumericStatChart` methods to `EmbeddedTelemetryHandle`.

Modify `EmbeddedTelemetryService`:

```java
public void recordStats(@Nonnull String eventName, @Nullable String detail) {
    if (isEnabled()) {
        engine.recordStats(project.projectId(), eventName, detail);
    }
}

public void recordStatsWithContext(@Nonnull String eventName, @Nullable TelemetryEventContext context) {
    if (isEnabled()) {
        engine.recordStatsWithContext(project.projectId(), eventName, context);
    }
}
```

- [ ] **Step 4: Run compatibility tests**

Run:

```powershell
.\mvnw.cmd -pl standalone -Dtest=TelemetryProjectHandleCompatibilityTest test
.\mvnw.cmd -pl runtime -Dtest=EmbeddedTelemetryServiceTest test
```

Expected: tests pass and existing implementors do not need source changes because the helpers are default interface methods.

- [ ] **Step 5: Commit**

```powershell
git add standalone/src/main/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandle.java standalone/src/main/java/com/alechilles/alecstelemetry/api/internal/TelemetryProjectHandleImpl.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryHandle.java runtime/src/main/java/com/alechilles/alecstelemetry/embedded/EmbeddedTelemetryService.java standalone/src/test/java/com/alechilles/alecstelemetry/api/TelemetryProjectHandleCompatibilityTest.java
git commit -m "Feat: add custom stats chart helpers"
```

---

### Task 9: Documentation and Contracts

**Files:**
- Modify: `docs/hosted-ingest-contract.md`
- Modify: `docs/project-descriptor.md`
- Create: `docs/usage-stats.md`
- Modify: `README.md`

- [ ] **Step 1: Document event ingest**

Update `docs/hosted-ingest-contract.md` endpoint section to include:

```markdown
`/ingest/event` accepts generic event envelopes. Public aggregate stats are represented as
`eventType: "stats"` with either:

- `eventName: "heartbeat"`
- `eventName: "chart_sample"`

`heartbeat` requires `details.playersOnline` as a non-negative integer.
`chart_sample` requires `details.chartId` and a chart-specific `details.value`
or configured field names from hosted project config.
```

- [ ] **Step 2: Document project stats config**

Update `docs/project-descriptor.md` with a separate `stats` category example:

```json
{
  "stats": {
    "enabled": true,
    "allowedEvents": ["heartbeat", "chart_sample"],
    "details": {
      "heartbeat": {
        "allowedFields": {
          "playersOnline": { "type": "number" }
        }
      },
      "chart_sample": {
        "allowedFields": {
          "chartId": { "type": "string", "maxLength": 80 },
          "value": { "type": "string", "maxLength": 120 }
        }
      }
    }
  }
}
```

- [ ] **Step 3: Add usage stats guide**

Create `docs/usage-stats.md`:

```markdown
# Usage Stats

Alec's Telemetry can publish anonymous Hytale mod usage statistics similar to
HStats and bStats.

## Default Stats

The standalone runtime emits a `stats` `heartbeat` event for each
dependency-mode project every 30 minutes. The hosted stats surface derives:

- active servers
- active players
- record servers
- record players
- plugin version breakdown
- Hytale build breakdown
- Java version breakdown
- operating system breakdown
- system architecture breakdown

## Privacy

Public stats never expose raw `serverId`, `sessionId`, IP addresses, player names,
chat, coordinates, world data, or full config files.

## Custom Charts

Mods can emit custom chart values through:

~~~java
TelemetryProjectHandle handle = TelemetryRuntimeLocator.get().project("example-mod");
handle.recordSimpleStatChart("language", "English");
handle.recordNumericStatChart("active_npcs", 12);
~~~

Each custom chart must be declared in hosted project config before the public API
will expose it.
```

- [ ] **Step 4: Update README feature list**

Add one bullet to `README.md` near existing telemetry categories:

```markdown
- Anonymous Hytale usage statistics for active servers, players, environment
  breakdowns, and descriptor-validated custom charts.
```

- [ ] **Step 5: Run doc checks**

Run:

```powershell
rg -n "eventType: \"stats\"|heartbeat|chart_sample|usage statistics" docs README.md
```

Expected: matches appear in the hosted contract, project descriptor, usage stats guide, and README.

- [ ] **Step 6: Commit**

```powershell
git add docs/hosted-ingest-contract.md docs/project-descriptor.md docs/usage-stats.md README.md
git commit -m "Docs: describe Hytale usage stats"
```

---

### Task 10: End-to-End Verification

**Files:**
- No new files required.

- [ ] **Step 1: Run hosted verification**

Run:

```powershell
cd hosted
npm test
npm run check
```

Expected: all hosted tests pass and TypeScript check completes with no errors.

- [ ] **Step 2: Run Java verification**

Run:

```powershell
.\mvnw.cmd -pl runtime,standalone test
```

Expected: runtime and standalone Maven test suites pass.

- [ ] **Step 3: Verify event ingest manually**

Start hosted service:

```powershell
cd hosted
npm run dev
```

In a second terminal, send a heartbeat payload:

```powershell
$payload = @{
  schemaVersion = 2
  eventType = "stats"
  eventName = "heartbeat"
  eventId = "manual-event-1"
  projectId = "example-mod"
  projectDisplayName = "Example Mod"
  source = "manual"
  sessionId = "manual-session"
  serverId = "manual-server"
  capturedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
  pluginIdentifier = "example:Example Mod"
  pluginVersion = "1.0.0"
  severity = "info"
  attributes = @{}
  details = @{ playersOnline = 3 }
  runtime = @{
    javaVersion = "25"
    runtimeVersion = "25+0"
    osName = "Windows 11"
    osVersion = "10.0"
    osArch = "amd64"
    hytaleBuild = "2026.06.16-test"
    serverVersion = "2026.06.16-test"
    loadedMods = @()
  }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:3000/ingest/event" `
  -ContentType "application/json" `
  -Headers @{ "X-Telemetry-Project-Key" = "example-public-key" } `
  -Body $payload
```

Expected response:

```json
{
  "accepted": true,
  "projectId": "example-mod",
  "eventId": "manual-event-1",
  "eventType": "stats",
  "eventName": "heartbeat",
  "storedStatsObservation": true
}
```

- [ ] **Step 4: Verify public summary manually**

Run:

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:3000/api/v1/projects/example-mod/summary"
```

Expected response contains:

```json
{
  "projectId": "example-mod",
  "activeServers": 1,
  "activePlayers": 3
}
```

- [ ] **Step 5: Final commit for verification fixes**

When verification changes were made during this task:

```powershell
git add hosted runtime standalone docs README.md
git commit -m "Fix: stabilize usage stats verification"
```

---

## Follow-On Plans

Create separate plans after this one lands:

1. Production persistence and dashboard management in the canonical VPS backend.
2. Authenticated project registration, owner dashboard, project transfer, and key rotation UI.
3. Rendered public stats pages and image/SVG embed cards.
4. Geo country derivation with retention rules that avoid storing IP addresses.
5. Download/export endpoints and long-range retention compaction.
6. Compatibility analytics that use environment snapshots and loaded-mod co-occurrence.

## Self-Review

- Spec coverage: the plan covers HStats-style registration key usage, active servers, players, embeds, and public stats; it covers bStats-style default charts, custom chart families, public REST API, global privacy boundaries, and server-owner consent. Account UI and production persistence are intentionally split into follow-on plans because they live in the canonical backend rather than this repo's prototype.
- Placeholder scan: no implementation step depends on an unnamed file or unspecified command. Runtime event access and final-service adaptation are grounded in the current local Hytale jar and existing `TelemetryRuntimeService.projects()` API.
- Type consistency: hosted event schemas use `TelemetryEventEnvelope`; stats storage uses `StatsObservation`; runtime heartbeat emits `eventType="stats", eventName="heartbeat"`; custom charts emit `eventType="stats", eventName="chart_sample"`; public aggregation consumes those same names.
