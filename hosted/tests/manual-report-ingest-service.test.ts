import pino from 'pino'
import { describe, expect, it } from 'vitest'

import type { CrashAlertRouteResult, HostedCrashAlert, HostedManualReportAlert } from '../src/alerts/alert-router.js'
import type { CrashAlertRouter } from '../src/alerts/alert-router.js'
import { ManualReportIngestService, createExampleManualReportEnvelope } from '../src/ingest/manual-report-ingest-service.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { HostedProjectRegistry, type HostedProjectConfig } from '../src/projects/project-registry.js'
import { ManualReportRepository } from '../src/reports/manual-report-repository.js'

class FakeManualReportAlertRouter implements CrashAlertRouter {
  readonly reports: HostedManualReportAlert[] = []

  async routeCrashAlert(_alert: HostedCrashAlert): Promise<CrashAlertRouteResult> {
    return {
      dispatched: false,
      detail: 'not used',
    }
  }

  async routeManualReportAlert(alert: HostedManualReportAlert): Promise<CrashAlertRouteResult> {
    this.reports.push(alert)
    return {
      dispatched: true,
      detail: 'sent',
    }
  }
}

function createProject(overrides: Partial<HostedProjectConfig> = {}): HostedProjectConfig {
  const project: HostedProjectConfig = {
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
    stats: {
      public: false,
      charts: [],
    },
  }
  return {
    ...project,
    ...overrides,
    discord: overrides.discord ?? project.discord,
    stats: overrides.stats ?? project.stats,
  }
}

function createService(project: HostedProjectConfig): {
  readonly service: ManualReportIngestService
  readonly repository: ManualReportRepository
  readonly router: FakeManualReportAlertRouter
} {
  const repository = new ManualReportRepository()
  const router = new FakeManualReportAlertRouter()
  return {
    service: new ManualReportIngestService(
      HostedProjectRegistry.fromProjects([project]),
      repository,
      router,
      new RequestRateLimiter(),
      pino({ enabled: false }),
    ),
    repository,
    router,
  }
}

describe('ManualReportIngestService', () => {
  it('rejects missing project key', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: null,
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(401)
    expect(result.body.error).toBe('missing_project_key')
  })

  it('rejects unknown project key', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: 'wrong-key',
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(403)
    expect(result.body.error).toBe('unknown_project_key')
  })

  it('rejects disabled projects', async () => {
    const project = createProject({ enabled: false })
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(403)
    expect(result.body.error).toBe('project_disabled')
  })

  it('rejects project id mismatch', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project, { projectId: 'wrong-project-id' }),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(403)
    expect(result.body.error).toBe('project_id_mismatch')
  })

  it('rejects oversized payloads', async () => {
    const project = createProject({ maxPayloadBytes: 1024 })
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 2048,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(413)
    expect(result.body.error).toBe('project_payload_too_large')
  })

  it('rejects invalid report kind payloads', async () => {
    const project = createProject()
    const { service } = createService(project)

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: {
        ...createExampleManualReportEnvelope(project),
        reportKind: 'crash',
      },
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })

    expect(result.status).toBe(400)
    expect(result.body.error).toBe('invalid_payload')
  })

  it('accepts valid issue and suggestion reports', async () => {
    const project = createProject()
    const { service, repository, router } = createService(project)

    const issue = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })
    const suggestion = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project, {
        reportId: 'suggestion-report-123',
        reportKind: 'suggestion',
      }),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:01Z'),
    })

    expect(issue.status).toBe(202)
    expect(issue.body.accepted).toBe(true)
    expect(issue.body.reportKind).toBe('issue')
    expect(issue.body.alertDispatched).toBe(true)
    expect(suggestion.status).toBe(202)
    expect(suggestion.body.reportKind).toBe('suggestion')
    expect(repository.listByProject(project.projectId)).toHaveLength(2)
    expect(router.reports).toHaveLength(2)
  })

  it('rate limits accepted projects', async () => {
    const project = createProject({ rateLimitPerMinute: 1 })
    const { service } = createService(project)

    const first = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:00Z'),
    })
    const second = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleManualReportEnvelope(project, { reportId: 'manual-report-456' }),
      bodyBytes: 100,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-06-16T20:00:10Z'),
    })

    expect(first.status).toBe(202)
    expect(second.status).toBe(429)
    expect(second.body.error).toBe('rate_limited')
  })
})
