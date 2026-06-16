import type { Logger } from 'pino'
import { ZodError } from 'zod'

import { manualReportEnvelopeSchema, type ManualReportEnvelope } from '../contract/manual-report-envelope.js'
import type { HostedProjectConfig, HostedProjectRegistry } from '../projects/project-registry.js'
import type { ManualReportRepository } from '../reports/manual-report-repository.js'
import type { RequestRateLimiter } from './request-rate-limiter.js'
import type { TelemetryIngestRequest, TelemetryIngestResponse } from './telemetry-ingest-service.js'

function formatValidationIssues(error: ZodError): string[] {
  return error.issues.slice(0, 5).map((issue) => {
    const path = issue.path.length === 0 ? '<root>' : issue.path.join('.')
    return `${path}: ${issue.message}`
  })
}

export class ManualReportIngestService {
  constructor(
    private readonly registry: HostedProjectRegistry,
    private readonly repository: ManualReportRepository,
    private readonly rateLimiter: RequestRateLimiter,
    private readonly logger: Logger,
  ) {}

  async ingest(request: TelemetryIngestRequest): Promise<TelemetryIngestResponse> {
    const projectKey = request.projectKey?.trim() ?? ''
    if (projectKey.length === 0) {
      return {
        status: 401,
        body: {
          accepted: false,
          error: 'missing_project_key',
        },
      }
    }

    const project = this.registry.findByProjectKey(projectKey)
    if (!project) {
      return {
        status: 403,
        body: {
          accepted: false,
          error: 'unknown_project_key',
        },
      }
    }
    if (!project.enabled) {
      return {
        status: 403,
        body: {
          accepted: false,
          error: 'project_disabled',
          projectId: project.projectId,
        },
      }
    }
    if (request.bodyBytes > project.maxPayloadBytes) {
      return {
        status: 413,
        body: {
          accepted: false,
          error: 'project_payload_too_large',
          projectId: project.projectId,
          maxPayloadBytes: project.maxPayloadBytes,
        },
      }
    }

    const parsed = manualReportEnvelopeSchema.safeParse(request.payload)
    if (!parsed.success) {
      return {
        status: 400,
        body: {
          accepted: false,
          error: 'invalid_payload',
          issues: formatValidationIssues(parsed.error),
        },
      }
    }

    const envelope = parsed.data
    if (envelope.projectId !== project.projectId) {
      return {
        status: 403,
        body: {
          accepted: false,
          error: 'project_id_mismatch',
          expectedProjectId: project.projectId,
          providedProjectId: envelope.projectId,
        },
      }
    }

    const nowMs = request.receivedAt.getTime()
    if (!this.rateLimiter.allow(project.projectId, project.rateLimitPerMinute, nowMs)) {
      this.logger.warn({ projectId: project.projectId, remoteAddress: request.remoteAddress }, 'Telemetry project exceeded manual report ingest rate limit.')
      return {
        status: 429,
        body: {
          accepted: false,
          error: 'rate_limited',
          projectId: project.projectId,
        },
      }
    }

    this.repository.save(envelope, request.receivedAt)
    this.logger.info(
      {
        projectId: project.projectId,
        reportId: envelope.reportId,
        reportKind: envelope.reportKind,
        remoteAddress: request.remoteAddress,
      },
      'Accepted telemetry manual report.',
    )

    return {
      status: 202,
      body: {
        accepted: true,
        projectId: project.projectId,
        reportId: envelope.reportId,
        reportKind: envelope.reportKind,
        alertDispatched: false,
        detail: 'Report accepted.',
      },
    }
  }
}

export function createExampleManualReportEnvelope(project: HostedProjectConfig, overrides: Partial<ManualReportEnvelope> = {}): ManualReportEnvelope {
  return {
    schemaVersion: 1,
    eventType: 'manual_report',
    reportId: 'manual-report-123',
    followUpTokenHash: 'follow-up-hash',
    reportKind: 'issue',
    projectId: project.projectId,
    projectDisplayName: project.displayName,
    submittedAtUtc: '2026-06-16T20:00:00Z',
    source: 'player_ui',
    sessionId: 'session-123',
    serverId: 'server-123',
    pluginIdentifier: `Example:${project.displayName}`,
    pluginVersion: '1.0.0',
    title: 'Config screen does not save',
    description: 'Opening the config screen and pressing Save leaves old values in place.',
    contact: {
      provided: true,
      value: 'discord: example',
    },
    formValues: {
      severity: 'major',
    },
    attachmentManifests: [],
    attachments: [],
    context: {
      runtimeMode: 'multiplayer',
      singleplayer: false,
      onlinePlayerCount: 12,
      worldName: 'test-world',
      loadedModsIncluded: true,
      diagnosticsIncluded: true,
    },
    environment: {
      runtimeMode: 'dependency',
    },
    runtime: {
      javaVersion: '25',
      runtimeVersion: '25+0',
      osName: 'Windows 11',
      osVersion: '10.0',
      osArch: 'amd64',
      hytaleBuild: '2026.03.26-89796e57b',
      serverVersion: '2026.03.26-89796e57b',
      loadedMods: [
        {
          identifier: `Example:${project.displayName}`,
          version: '1.0.0',
        },
      ],
    },
    ...overrides,
  }
}
