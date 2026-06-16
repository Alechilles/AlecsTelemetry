import { describe, expect, it } from 'vitest'

import { HostedProjectRegistry } from '../src/projects/project-registry.js'
import { StatsApi } from '../src/stats/stats-api.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

const observations: StatsObservation[] = [{
  projectId: 'example-mod',
  eventId: 'event-1',
  eventName: 'heartbeat',
  serverId: 'server-1',
  sessionId: 'session-1',
  observedAtUtc: '2026-06-16T20:00:00.000Z',
  playersOnline: 5,
  pluginVersion: '1.0.0',
  hytaleBuild: '2026.06.16-test',
  serverVersion: '2026.06.16-test',
  javaVersion: '25',
  osName: 'Windows 11',
  osArch: 'amd64',
  countryCode: 'unknown',
  loadedMods: [],
  custom: {},
}]

describe('StatsApi', () => {
  it('returns public project summaries', async () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'key',
      stats: { public: true, slug: 'example-mod', charts: [] },
    }])
    const api = new StatsApi(registry, { observations: async () => observations }, Date.parse('2026-06-16T20:15:00.000Z'))

    const response = await api.handle('GET', '/api/v1/projects/example-mod/summary')

    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({
      projectId: 'example-mod',
      activeServers: 1,
      activePlayers: 5,
    })
  })

  it('does not expose private projects', async () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'key',
      stats: { public: false, charts: [] },
    }])
    const api = new StatsApi(registry, { observations: async () => observations }, Date.parse('2026-06-16T20:15:00.000Z'))

    const response = await api.handle('GET', '/api/v1/projects/example-mod/summary')

    expect(response.status).toBe(404)
    expect(response.body).toMatchObject({ error: 'not_found' })
  })
})
