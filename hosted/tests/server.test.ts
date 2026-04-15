import pino from 'pino'
import { afterEach, describe, expect, it } from 'vitest'

import { NoopCrashAlertRouter } from '../src/alerts/alert-router.js'
import { DuplicateAlertSuppressor } from '../src/ingest/duplicate-alert-suppressor.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { TelemetryIngestService, createExampleCrashEnvelope } from '../src/ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry } from '../src/projects/project-registry.js'
import { createHostedServer } from '../src/server.js'

describe('hosted server', () => {
  const servers: Array<import('node:http').Server> = []

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
  })

  it('accepts POST /api/v1/ingest/crash with a valid project key', async () => {
    const project = {
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'pub_example_mod',
      enabled: true,
      rateLimitPerMinute: 60,
      maxPayloadBytes: 262_144,
      duplicateAlertWindowSeconds: 0,
      discord: {
        channelId: '123456789',
      },
    }
    const ingestService = new TelemetryIngestService(
      HostedProjectRegistry.fromProjects([project]),
      new NoopCrashAlertRouter(),
      new RequestRateLimiter(),
      new DuplicateAlertSuppressor(),
      pino({ enabled: false }),
    )
    const server = createHostedServer({
      ingestService,
      logger: pino({ enabled: false }),
      maxRequestBodyBytes: 262_144,
    })
    servers.push(server)

    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()))
    const address = server.address()
    if (!address || typeof address === 'string') {
      throw new Error('Expected a bound TCP address.')
    }

    const response = await fetch(`http://127.0.0.1:${address.port}/api/v1/ingest/crash`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Telemetry-Project-Key': project.publicProjectKey,
      },
      body: JSON.stringify(createExampleCrashEnvelope(project)),
    })
    const payload = await response.json() as Record<string, unknown>

    expect(response.status).toBe(202)
    expect(payload.accepted).toBe(true)
    expect(payload.projectId).toBe(project.projectId)
  })
})
