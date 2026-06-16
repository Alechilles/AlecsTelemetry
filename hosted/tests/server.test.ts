import pino from 'pino'
import { afterEach, describe, expect, it } from 'vitest'

import { NoopCrashAlertRouter } from '../src/alerts/alert-router.js'
import { DuplicateAlertSuppressor } from '../src/ingest/duplicate-alert-suppressor.js'
import { ManualReportIngestService, createExampleManualReportEnvelope } from '../src/ingest/manual-report-ingest-service.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { TelemetryIngestService, createExampleCrashEnvelope } from '../src/ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry } from '../src/projects/project-registry.js'
import { ManualReportRepository } from '../src/reports/manual-report-repository.js'
import { ReportStatusService } from '../src/reports/report-status-service.js'
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

  it('accepts POST /ingest/crash with a valid project key', async () => {
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
      stats: {
        public: false,
        charts: [],
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

    const response = await fetch(`http://127.0.0.1:${address.port}/ingest/crash`, {
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

  it('accepts POST /ingest/report with a valid project key', async () => {
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
      stats: {
        public: false,
        charts: [],
      },
    }
    const registry = HostedProjectRegistry.fromProjects([project])
    const ingestService = new TelemetryIngestService(
      registry,
      new NoopCrashAlertRouter(),
      new RequestRateLimiter(),
      new DuplicateAlertSuppressor(),
      pino({ enabled: false }),
    )
    const manualReportRepository = new ManualReportRepository()
    const manualReportIngestService = new ManualReportIngestService(
      registry,
      manualReportRepository,
      new NoopCrashAlertRouter(),
      new RequestRateLimiter(),
      pino({ enabled: false }),
    )
    const server = createHostedServer({
      ingestService,
      manualReportIngestService,
      reportStatusService: new ReportStatusService(manualReportRepository),
      logger: pino({ enabled: false }),
      maxRequestBodyBytes: 262_144,
    })
    servers.push(server)

    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()))
    const address = server.address()
    if (!address || typeof address === 'string') {
      throw new Error('Expected a bound TCP address.')
    }

    const response = await fetch(`http://127.0.0.1:${address.port}/ingest/report`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Telemetry-Project-Key': project.publicProjectKey,
      },
      body: JSON.stringify(createExampleManualReportEnvelope(project)),
    })
    const payload = await response.json() as Record<string, unknown>

    expect(response.status).toBe(202)
    expect(payload.accepted).toBe(true)
    expect(payload.projectId).toBe(project.projectId)
    expect(payload.reportKind).toBe('issue')

    const statusResponse = await fetch(`http://127.0.0.1:${address.port}/reports/status`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reportId: 'manual-report-123',
        followUpToken: 'follow-up-token',
      }),
    })
    const statusPayload = await statusResponse.json() as Record<string, unknown>

    expect(statusResponse.status).toBe(200)
    expect(statusPayload.found).toBe(true)
    expect(statusPayload.status).toBe('received')
  })
})
