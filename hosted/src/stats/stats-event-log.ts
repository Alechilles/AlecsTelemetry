import { appendFile, mkdir, readFile } from 'node:fs/promises'
import { dirname } from 'node:path'

import type { StatsObservation } from './stats-model.js'

function isObservation(value: unknown): value is StatsObservation {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Partial<StatsObservation>
  return typeof candidate.projectId === 'string'
    && typeof candidate.eventId === 'string'
    && typeof candidate.eventName === 'string'
    && typeof candidate.serverId === 'string'
    && typeof candidate.sessionId === 'string'
    && typeof candidate.observedAtUtc === 'string'
    && typeof candidate.playersOnline === 'number'
}

function isMissingFile(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'code' in error
    && (error as { readonly code?: unknown }).code === 'ENOENT'
}

export class StatsEventLog {
  constructor(private readonly filePath: string) {}

  async append(observation: StatsObservation): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true })
    await appendFile(this.filePath, `${JSON.stringify(observation)}\n`, 'utf8')
  }

  async readAll(): Promise<StatsObservation[]> {
    let raw: string
    try {
      raw = await readFile(this.filePath, 'utf8')
    } catch (error) {
      if (isMissingFile(error)) {
        return []
      }
      throw error
    }

    const observations: StatsObservation[] = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.trim().length === 0) {
        continue
      }
      try {
        const parsed = JSON.parse(line) as unknown
        if (isObservation(parsed)) {
          observations.push(parsed)
        }
      } catch {
        continue
      }
    }
    return observations
  }
}
