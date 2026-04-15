import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

import pino from 'pino'
import { describe, expect, it } from 'vitest'

import type { CrashAlertRouteResult, HostedCrashAlert } from '../src/alerts/alert-router.js'
import type { CrashAlertRouter } from '../src/alerts/alert-router.js'
import { DuplicateAlertSuppressor } from '../src/ingest/duplicate-alert-suppressor.js'
import { RequestRateLimiter } from '../src/ingest/request-rate-limiter.js'
import { TelemetryIngestService, createExampleCrashEnvelope } from '../src/ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry } from '../src/projects/project-registry.js'

class CapturingAlertRouter implements CrashAlertRouter {
  readonly alerts: HostedCrashAlert[] = []

  async routeCrashAlert(alert: HostedCrashAlert): Promise<CrashAlertRouteResult> {
    this.alerts.push(alert)
    return {
      dispatched: true,
      detail: 'captured',
    }
  }
}

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'unknown-project'
}

describe('Example consumer hosted flow', () => {
  it('accepts a crash report derived from the sample consumer mod files', async () => {
    const manifestPath = resolve(process.cwd(), '..', 'examples', 'ExampleConsumerMod', 'manifest.json')
    const descriptorPath = resolve(process.cwd(), '..', 'examples', 'ExampleConsumerMod', 'telemetry', 'project.json')
    const manifest = JSON.parse(await readFile(manifestPath, 'utf8')) as {
      readonly Group: string
      readonly Name: string
      readonly Version: string
    }
    const descriptor = JSON.parse(await readFile(descriptorPath, 'utf8')) as {
      readonly hosted: {
        readonly projectKey: string
      }
    }

    const project = {
      projectId: slugify(manifest.Name),
      displayName: manifest.Name,
      publicProjectKey: descriptor.hosted.projectKey,
      enabled: true,
      rateLimitPerMinute: 60,
      maxPayloadBytes: 262_144,
      duplicateAlertWindowSeconds: 300,
      discord: {
        channelId: '123456789',
        guildId: '987654321',
      },
    }
    const router = new CapturingAlertRouter()
    const service = new TelemetryIngestService(
      HostedProjectRegistry.fromProjects([project]),
      router,
      new RequestRateLimiter(),
      new DuplicateAlertSuppressor(),
      pino({ enabled: false }),
    )

    const result = await service.ingest({
      projectKey: project.publicProjectKey,
      payload: createExampleCrashEnvelope(project, {
        pluginIdentifier: `${manifest.Group}:${manifest.Name}`,
        pluginVersion: manifest.Version,
        throwable: {
          type: 'java.lang.IllegalStateException',
          message: 'Example consumer failed to start',
          stack: ['com.example.consumer.ExampleConsumerMod.start(ExampleConsumerMod.java:27)'],
          causes: [],
        },
      }),
      bodyBytes: 2048,
      remoteAddress: '127.0.0.1',
      receivedAt: new Date('2026-04-14T00:00:00Z'),
    })

    expect(result.status).toBe(202)
    expect(result.body.accepted).toBe(true)
    expect(result.body.projectId).toBe(project.projectId)
    expect(router.alerts).toHaveLength(1)
    expect(router.alerts[0]?.project.displayName).toBe(manifest.Name)
  })
})
