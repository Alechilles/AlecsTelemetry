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
