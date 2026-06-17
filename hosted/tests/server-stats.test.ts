import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import pino from 'pino'
import { afterEach, describe, expect, it } from 'vitest'

import { NoopCrashAlertRouter } from '../src/alerts/alert-router.js'
import { DuplicateAlertSuppressor } from '../src/ingest/duplicate-alert-suppressor.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { TelemetryIngestService } from '../src/ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry } from '../src/projects/project-registry.js'
import { createHostedServer } from '../src/server.js'
import { StatsApi } from '../src/stats/stats-api.js'
import { StatsEventLog } from '../src/stats/stats-event-log.js'
import { StatsService } from '../src/stats/stats-service.js'

function statsPayload(projectId = 'example-mod'): Record<string, unknown> {
  return {
    schemaVersion: 2,
    eventType: 'stats',
    eventName: 'heartbeat',
    eventId: 'event-1',
    projectId,
    projectDisplayName: 'Example Mod',
    source: 'runtime',
    sessionId: 'session-1',
    serverId: 'server-1',
    capturedAtUtc: '2026-06-16T20:00:00.000Z',
    pluginIdentifier: 'Example:Example Mod',
    pluginVersion: '1.0.0',
    severity: 'info',
    attributes: {},
    details: { playersOnline: 3 },
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
  }
}

describe('hosted stats server routes', () => {
  const servers: Array<import('node:http').Server> = []
  const dirs: string[] = []

  afterEach(async () => {
    await Promise.all(servers.map((server) => new Promise<void>((resolve, reject) => {
      server.close((error) => {
        if (error) {
          reject(error)
          return
        }
        resolve()
      })
    })))
    servers.length = 0
    await Promise.all(dirs.map((dir) => rm(dir, { recursive: true, force: true })))
    dirs.length = 0
  })

  async function startServer(): Promise<{ readonly baseUrl: string; readonly projectKey: string }> {
    const dir = await mkdtemp(join(tmpdir(), 'alecs-telemetry-stats-server-'))
    dirs.push(dir)
    const project = {
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'pub_example_mod',
      rateLimitPerMinute: 60,
      maxPayloadBytes: 262_144,
      duplicateAlertWindowSeconds: 0,
      stats: { public: true, slug: 'example-mod' },
    }
    const registry = HostedProjectRegistry.fromProjects([project])
    const statsService = new StatsService(new StatsEventLog(join(dir, 'stats.jsonl')))
    const ingestService = new TelemetryIngestService(
      registry,
      new NoopCrashAlertRouter(),
      new RequestRateLimiter(),
      new DuplicateAlertSuppressor(),
      pino({ enabled: false }),
      statsService,
    )
    const server = createHostedServer({
      ingestService,
      statsApi: new StatsApi(registry, statsService, Date.parse('2026-06-16T20:15:00.000Z')),
      logger: pino({ enabled: false }),
      maxRequestBodyBytes: 262_144,
    })
    servers.push(server)

    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()))
    const address = server.address()
    if (!address || typeof address === 'string') {
      throw new Error('Expected a bound TCP address.')
    }
    return {
      baseUrl: `http://127.0.0.1:${address.port}`,
      projectKey: project.publicProjectKey,
    }
  }

  it('accepts POST /ingest/event with a valid project key', async () => {
    const server = await startServer()

    const response = await fetch(`${server.baseUrl}/ingest/event`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Telemetry-Project-Key': server.projectKey,
      },
      body: JSON.stringify(statsPayload()),
    })
    const body = await response.json() as Record<string, unknown>

    expect(response.status).toBe(202)
    expect(body.accepted).toBe(true)
    expect(body.storedStatsObservation).toBe(true)
  })

  it('returns public stats summary after ingest', async () => {
    const server = await startServer()

    await fetch(`${server.baseUrl}/ingest/event`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Telemetry-Project-Key': server.projectKey,
      },
      body: JSON.stringify(statsPayload()),
    })
    const response = await fetch(`${server.baseUrl}/api/v1/projects/example-mod/summary`)
    const body = await response.json() as Record<string, unknown>

    expect(response.status).toBe(200)
    expect(body.activeServers).toBe(1)
    expect(body.activePlayers).toBe(3)
  })
})
