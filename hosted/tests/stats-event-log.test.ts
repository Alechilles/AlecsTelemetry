import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { afterEach, describe, expect, it } from 'vitest'

import { StatsEventLog } from '../src/stats/stats-event-log.js'
import type { StatsObservation } from '../src/stats/stats-model.js'

describe('StatsEventLog', () => {
  const dirs: string[] = []

  afterEach(async () => {
    await Promise.all(dirs.map((dir) => rm(dir, { recursive: true, force: true })))
  })

  it('appends and reloads observations as jsonl', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'alecs-telemetry-stats-'))
    dirs.push(dir)
    const file = join(dir, 'stats.jsonl')
    const log = new StatsEventLog(file)
    const observation: StatsObservation = {
      projectId: 'example-mod',
      eventId: 'event-1',
      eventName: 'heartbeat',
      serverId: 'server-1',
      sessionId: 'session-1',
      observedAtUtc: '2026-06-16T20:00:00.000Z',
      playersOnline: 3,
      pluginVersion: '1.2.3',
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      javaVersion: '25',
      runtimeVersion: '25+0',
      osName: 'Windows 11',
      osVersion: '10.0',
      osArch: 'amd64',
      cpuCores: 8,
      serverHostingMode: 'dedicated',
      countryCode: 'unknown',
      loadedMods: [{ identifier: 'example:Example Mod', version: '1.2.3' }],
      custom: {},
    }

    await log.append(observation)

    expect(await log.readAll()).toEqual([observation])
    expect(await readFile(file, 'utf8')).toContain('"projectId":"example-mod"')
  })

  it('skips malformed lines while preserving valid lines', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'alecs-telemetry-stats-'))
    dirs.push(dir)
    const file = join(dir, 'stats.jsonl')
    const log = new StatsEventLog(file)
    const observation: StatsObservation = {
      projectId: 'example-mod',
      eventId: 'event-1',
      eventName: 'heartbeat',
      serverId: 'server-1',
      sessionId: 'session-1',
      observedAtUtc: '2026-06-16T20:00:00.000Z',
      playersOnline: 0,
      pluginVersion: '1.2.3',
      hytaleBuild: '2026.06.16-test',
      serverVersion: '2026.06.16-test',
      javaVersion: '25',
      runtimeVersion: '25+0',
      osName: 'Windows 11',
      osVersion: '10.0',
      osArch: 'amd64',
      cpuCores: 8,
      serverHostingMode: 'dedicated',
      countryCode: 'unknown',
      loadedMods: [],
      custom: {},
    }

    await log.append(observation)
    await import('node:fs/promises').then((fs) => fs.appendFile(file, '{bad json}\n', 'utf8'))

    expect(await log.readAll()).toEqual([observation])
  })
})
