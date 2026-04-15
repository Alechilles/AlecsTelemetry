import type { Logger } from 'pino'
import { ZodError } from 'zod'

import type { CrashAlertRouter } from '../alerts/alert-router.js'
import { telemetryCrashEnvelopeSchema, type TelemetryCrashEnvelope } from '../contract/crash-envelope.js'
import type { HostedProjectConfig } from '../projects/project-registry.js'
import type { HostedProjectRegistry } from '../projects/project-registry.js'
import type { DuplicateAlertSuppressor } from './duplicate-alert-suppressor.js'
import type { RequestRateLimiter } from './request-rate-limiter.js'

export interface TelemetryIngestRequest {
  readonly projectKey: string | null
  readonly payload: unknown
  readonly bodyBytes: number
  readonly remoteAddress: string | null
  readonly receivedAt: Date
}

export interface TelemetryIngestResponse {
  readonly status: number
  readonly body: Record<string, unknown>
}

function formatValidationIssues(error: ZodError): string[] {
  return error.issues.slice(0, 5).map((issue) => {
    const path = issue.path.length === 0 ? '<root>' : issue.path.join('.')
    return `${path}: ${issue.message}`
  })
}

export class TelemetryIngestService {
  constructor(
    private readonly registry: HostedProjectRegistry,
    private readonly router: CrashAlertRouter,
    private readonly rateLimiter: RequestRateLimiter,
    private readonly suppressor: DuplicateAlertSuppressor,
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

    const parsed = telemetryCrashEnvelopeSchema.safeParse(request.payload)
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
      this.logger.warn({ projectId: project.projectId, remoteAddress: request.remoteAddress }, 'Telemetry project exceeded ingest rate limit.')
      return {
        status: 429,
        body: {
          accepted: false,
          error: 'rate_limited',
          projectId: project.projectId,
        },
      }
    }

    const alertSuppressed = !this.suppressor.shouldDispatch(
      project.projectId,
      envelope.fingerprint,
      project.duplicateAlertWindowSeconds,
      nowMs,
    )
    let alertDispatched = false
    let alertDetail = 'Duplicate alert suppressed.'
    if (!alertSuppressed) {
      const routeResult = await this.router.routeCrashAlert({
        project,
        envelope,
      })
      alertDispatched = routeResult.dispatched
      alertDetail = routeResult.detail
    }

    this.logger.info(
      {
        projectId: project.projectId,
        fingerprint: envelope.fingerprint,
        alertDispatched,
        alertSuppressed,
        remoteAddress: request.remoteAddress,
      },
      'Accepted telemetry crash report.',
    )

    return {
      status: 202,
      body: {
        accepted: true,
        projectId: project.projectId,
        fingerprint: envelope.fingerprint,
        alertDispatched,
        alertSuppressed,
        detail: alertDetail,
      },
    }
  }
}

export function createExampleCrashEnvelope(project: HostedProjectConfig, overrides: Partial<TelemetryCrashEnvelope> = {}): TelemetryCrashEnvelope {
  return {
    schemaVersion: 1,
    eventType: 'crash',
    reportId: 'test-report-id',
    projectId: project.projectId,
    projectDisplayName: project.displayName,
    source: 'unit_test',
    fingerprint: 'fingerprint-123',
    capturedAtUtc: '2026-04-14T00:00:00Z',
    lastCapturedAtUtc: '2026-04-14T00:00:00Z',
    occurrenceCount: 1,
    pluginIdentifier: `Example:${project.displayName}`,
    pluginVersion: '1.0.0',
    threadName: 'MainThread',
    worldName: null,
    worldRemovalReason: null,
    worldFailurePluginIdentifier: null,
    attribution: {
      identifiedPlugin: `Example:${project.displayName}`,
      matchedPluginIdentifier: true,
      matchedStackPrefix: true,
    },
    breadcrumbs: [],
    throwable: {
      type: 'java.lang.RuntimeException',
      message: 'Example crash',
      stack: ['com.example.consumer.ExampleConsumerMod.run(ExampleConsumerMod.java:42)'],
      causes: [],
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
