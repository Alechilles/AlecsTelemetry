import { describe, expect, it } from 'vitest'

import { StatsAggregator } from '../src/stats/stats-aggregator.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

let eventCounter = 0

function observation(overrides: Partial<StatsObservation>): StatsObservation {
  eventCounter += 1
  return {
    projectId: 'example-mod',
    eventId: `event-${eventCounter}`,
    eventName: 'heartbeat',
    serverId: 'server-1',
    sessionId: 'session-1',
    observedAtUtc: '2026-06-16T20:00:00.000Z',
    playersOnline: 1,
    pluginVersion: '1.0.0',
    hytaleBuild: '2026.06.16-test',
    serverVersion: '2026.06.16-test',
    javaVersion: '25',
    osName: 'Windows 11',
    osArch: 'amd64',
    countryCode: 'unknown',
    loadedMods: [],
    custom: {},
    ...overrides,
  }
}

describe('StatsAggregator', () => {
  it('counts latest active server observations inside the active window', () => {
    const now = Date.parse('2026-06-16T20:30:00.000Z')
    const aggregator = new StatsAggregator([
      observation({ serverId: 'server-1', playersOnline: 2, observedAtUtc: '2026-06-16T20:00:00.000Z' }),
      observation({ serverId: 'server-1', playersOnline: 4, observedAtUtc: '2026-06-16T20:20:00.000Z' }),
      observation({ serverId: 'server-2', playersOnline: 3, observedAtUtc: '2026-06-16T19:00:00.000Z' }),
      observation({ serverId: 'server-3', playersOnline: 5, observedAtUtc: '2026-06-16T20:25:00.000Z' }),
    ], now)

    expect(aggregator.summary('example-mod')).toEqual({
      projectId: 'example-mod',
      activeServers: 2,
      activePlayers: 9,
      recordServers: 2,
      recordPlayers: 9,
      lastUpdatedAtUtc: '2026-06-16T20:25:00.000Z',
    })
  })

  it('builds categorical breakdowns from latest active observations', () => {
    const now = Date.parse('2026-06-16T20:30:00.000Z')
    const aggregator = new StatsAggregator([
      observation({ serverId: 'server-1', pluginVersion: '1.0.0', playersOnline: 2 }),
      observation({ serverId: 'server-2', pluginVersion: '1.1.0', playersOnline: 1 }),
      observation({ serverId: 'server-3', pluginVersion: '1.1.0', playersOnline: 7 }),
    ], now)

    expect(aggregator.category('example-mod', 'pluginVersion')).toEqual([
      { name: '1.1.0', servers: 2, players: 8 },
      { name: '1.0.0', servers: 1, players: 2 },
    ])
  })
})
