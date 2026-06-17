import type { StatsLinePoint, StatsObservation, StatsSummary } from './stats-model.js'

export interface CategoryPoint {
  readonly name: string
  readonly servers: number
  readonly players: number
}

const ACTIVE_WINDOW_MS = 45 * 60 * 1000
const BUCKET_MS = 30 * 60 * 1000

function timestamp(value: string): number {
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function bucketStart(value: string): number {
  return Math.floor(timestamp(value) / BUCKET_MS) * BUCKET_MS
}

export class StatsAggregator {
  constructor(
    private readonly observations: readonly StatsObservation[],
    private readonly nowMs: number = Date.now(),
  ) {}

  summary(projectId: string): StatsSummary {
    const latest = this.latestActive(projectId)
    const active = Array.from(latest.values())
    const records = this.records(projectId)
    const lastUpdatedAtUtc = active
      .map((item) => item.observedAtUtc)
      .sort()
      .at(-1) ?? null

    return {
      projectId,
      activeServers: active.length,
      activePlayers: active.reduce((sum, item) => sum + item.playersOnline, 0),
      recordServers: records.recordServers,
      recordPlayers: records.recordPlayers,
      lastUpdatedAtUtc,
    }
  }

  category(projectId: string, field: keyof StatsObservation): CategoryPoint[] {
    const counts = new Map<string, { servers: number; players: number }>()
    for (const observation of this.latestActive(projectId).values()) {
      const raw = observation[field]
      const name = typeof raw === 'string' && raw.length > 0
        ? raw
        : typeof raw === 'number' && Number.isFinite(raw)
          ? String(raw)
          : 'unknown'
      const current = counts.get(name) ?? { servers: 0, players: 0 }
      current.servers += 1
      current.players += observation.playersOnline
      counts.set(name, current)
    }
    return Array.from(counts.entries())
      .map(([name, value]) => ({ name, ...value }))
      .sort((left, right) => right.servers - left.servers || right.players - left.players || left.name.localeCompare(right.name))
  }

  loadedMods(projectId: string): CategoryPoint[] {
    const counts = new Map<string, { servers: number; players: number }>()
    for (const observation of this.latestActive(projectId).values()) {
      for (const mod of observation.loadedMods) {
        const name = `${mod.identifier} ${mod.version}`.trim()
        const current = counts.get(name) ?? { servers: 0, players: 0 }
        current.servers += 1
        current.players += observation.playersOnline
        counts.set(name, current)
      }
    }
    return Array.from(counts.entries())
      .map(([name, value]) => ({ name, ...value }))
      .sort((left, right) => left.name.localeCompare(right.name))
  }

  series(projectId: string, metric: 'servers' | 'players'): StatsLinePoint[] {
    const buckets = this.bucketedLatest(projectId)
    return Array.from(buckets.entries())
      .sort(([left], [right]) => left - right)
      .map(([bucket, bucketServers]) => {
        const values = Array.from(bucketServers.values())
        return {
          timestamp: bucket,
          value: metric === 'servers'
            ? values.length
            : values.reduce((sum, item) => sum + item.playersOnline, 0),
        }
      })
  }

  private latestActive(projectId: string): Map<string, StatsObservation> {
    const cutoff = this.nowMs - ACTIVE_WINDOW_MS
    const latest = new Map<string, StatsObservation>()
    for (const observation of this.observations) {
      if (observation.projectId !== projectId || timestamp(observation.observedAtUtc) < cutoff) {
        continue
      }
      const current = latest.get(observation.serverId)
      if (!current || timestamp(observation.observedAtUtc) >= timestamp(current.observedAtUtc)) {
        latest.set(observation.serverId, observation)
      }
    }
    return latest
  }

  private records(projectId: string): { recordServers: number; recordPlayers: number } {
    const buckets = this.bucketedLatest(projectId)
    let recordServers = 0
    let recordPlayers = 0
    for (const bucketServers of buckets.values()) {
      const values = Array.from(bucketServers.values())
      recordServers = Math.max(recordServers, values.length)
      recordPlayers = Math.max(recordPlayers, values.reduce((sum, item) => sum + item.playersOnline, 0))
    }
    return { recordServers, recordPlayers }
  }

  private bucketedLatest(projectId: string): Map<number, Map<string, StatsObservation>> {
    const buckets = new Map<number, Map<string, StatsObservation>>()
    for (const observation of this.observations) {
      if (observation.projectId !== projectId) {
        continue
      }
      const bucket = bucketStart(observation.observedAtUtc)
      const bucketServers = buckets.get(bucket) ?? new Map<string, StatsObservation>()
      const current = bucketServers.get(observation.serverId)
      if (!current || timestamp(observation.observedAtUtc) >= timestamp(current.observedAtUtc)) {
        bucketServers.set(observation.serverId, observation)
      }
      buckets.set(bucket, bucketServers)
    }
    return buckets
  }
}
