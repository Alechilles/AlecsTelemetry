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
      cpuCores: 8,
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      serverHostingMode: 'local_client',
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
      runtimeVersion: '25+0',
      osVersion: '10.0',
      cpuCores: 8,
      serverHostingMode: 'local_client',
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

  it('ignores custom stats chart samples without writing observations', async () => {
    const append = vi.fn()
    const service = new StatsService({ append, readAll: vi.fn() })

    const result = await service.acceptEvent(event({
      eventName: 'chart_sample',
      entryPoint: 'custom_chart',
      details: { chartId: 'language', value: 'English' },
    }), 'unknown')

    expect(result.accepted).toBe(true)
    expect(result.storedObservation).toBe(false)
    expect(append).not.toHaveBeenCalled()
  })
})
