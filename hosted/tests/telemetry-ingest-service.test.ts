import pino from 'pino'
import { describe, expect, it } from 'vitest'

import type { CrashAlertRouteResult, HostedCrashAlert } from '../src/alerts/alert-router.js'
import type { CrashAlertRouter } from '../src/alerts/alert-router.js'
import { DuplicateAlertSuppressor } from '../src/ingest/duplicate-alert-suppressor.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { TelemetryIngestService, createExampleCrashEnvelope } from '../src/ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry, type HostedProjectConfig } from '../src/projects/project-registry.js'

class FakeCrashAlertRouter implements CrashAlertRouter {
  readonly alerts: HostedCrashAlert[] = []

  async routeCrashAlert(alert: HostedCrashAlert): Promise<CrashAlertRouteResult> {
    this.alerts.push(alert)
    return {
      dispatched: true,
      detail: 'sent',
    }
  }
}

function createProject(overrides: Partial<HostedProjectConfig> = {}): HostedProjectConfig {
  return {
    projectId: 'example-mod',
    displayName: 'Example Mod',
    publicProjectKey: 'pub_example_mod',
    enabled: true,
    rateLimitPerMinute: 2,
    maxPayloadBytes: 262_144,
    duplicateAlertWindowSeconds: 300,
    discord: {
      channelId: '123456789',
      guildId: '987654321',
    },
    ...overrides,
  }
}

function createService(project: HostedProjectConfig, router = new FakeCrashAlertRouter()): {
  readonly service: TelemetryIngestService
  readonly router: FakeCrashAlertRouter
} {
  return {
    service: new TelemetryIngestService(
      HostedProjectRegistry.fromProjects([project]),
      router,
      new RequestRateLimiter(),
      new DuplicateAlertSuppressor(),
      pino({ enabled: false }),
    ),
    router,
  }
}

describe('TelemetryIngestService', () => {
  it('rejects missing project key', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: null,
      payload: createExampleCrashEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })

    expect(result.status).toBe(401)
    expect(result.body.error).toBe('missing_project_key')
  })

  it('rejects project id mismatch for a valid key', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleCrashEnvelope(project, { projectId: 'wrong-project-id' }),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })

    expect(result.status).toBe(403)
    expect(result.body.error).toBe('project_id_mismatch')
  })

  it('accepts a valid crash and dispatches one alert', async () => {
    const project = createProject()
    const { service, router } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleCrashEnvelope(project),
      bodyBytes: 1024,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })

    expect(result.status).toBe(202)
    expect(result.body.accepted).toBe(true)
    expect(result.body.alertDispatched).toBe(true)
    expect(result.body.alertSuppressed).toBe(false)
    expect(router.alerts).toHaveLength(1)
  })

  it('suppresses repeated alerts for the same fingerprint within the configured window', async () => {
    const project = createProject()
    const { service, router } = createService(project)
    const envelope = createExampleCrashEnvelope(project)

    const first = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: envelope,
      bodyBytes: 1024,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })
    const second = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: envelope,
      bodyBytes: 1024,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:01:00Z'),
    })

    expect(first.body.alertSuppressed).toBe(false)
    expect(second.body.alertSuppressed).toBe(true)
    expect(router.alerts).toHaveLength(1)
  })

  it('rate limits accepted projects after the configured request budget', async () => {
    const project = createProject({ rateLimitPerMinute: 1 })
    const { service } = createService(project)
    const envelope = createExampleCrashEnvelope(project)

    const first = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: envelope,
      bodyBytes: 1024,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })
    const second = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: envelope,
      bodyBytes: 1024,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:10Z'),
    })

    expect(first.status).toBe(202)
    expect(second.status).toBe(429)
    expect(second.body.error).toBe('rate_limited')
  })
})
