import type { TelemetryEventEnvelope } from '../contract/event-envelope.js'
import type { StatsEventLog } from './stats-event-log.js'
import type { StatsObservation } from './stats-model.js'

export interface StatsAcceptResult {
  readonly accepted: true
  readonly storedObservation: boolean
}

function numberDetail(event: TelemetryEventEnvelope, key: string, fallback: number): number {
  const value = event.details[key]
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : fallback
}

function normalizeCountry(countryCode: string | null): string {
  return countryCode && /^[A-Z]{2}$/.test(countryCode) ? countryCode : 'unknown'
}

function customDetails(event: TelemetryEventEnvelope): Record<string, string | number | boolean> {
  const custom: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(event.details)) {
    if (key === 'playersOnline') {
      continue
    }
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      custom[key] = value
    }
  }
  return custom
}

export class StatsService {
  constructor(private readonly log: Pick<StatsEventLog, 'append' | 'readAll'>) {}

  async acceptEvent(event: TelemetryEventEnvelope, countryCode: string | null): Promise<StatsAcceptResult> {
    if (event.eventType !== 'stats' || event.eventName !== 'heartbeat') {
      return { accepted: true, storedObservation: false }
    }

    const observation: StatsObservation = {
      projectId: event.projectId,
      eventId: event.eventId,
      eventName: event.eventName,
      serverId: event.serverId,
      sessionId: event.sessionId,
      observedAtUtc: event.capturedAtUtc,
      playersOnline: numberDetail(event, 'playersOnline', 0),
      pluginVersion: event.pluginVersion,
      hytaleBuild: event.runtime.hytaleBuild,
      serverVersion: event.runtime.serverVersion,
      javaVersion: event.runtime.javaVersion,
      runtimeVersion: event.runtime.runtimeVersion,
      osName: event.runtime.osName,
      osVersion: event.runtime.osVersion,
      osArch: event.runtime.osArch,
      cpuCores: event.runtime.cpuCores,
      serverHostingMode: event.runtime.serverHostingMode,
      countryCode: normalizeCountry(countryCode),
      loadedMods: event.runtime.loadedMods,
      custom: customDetails(event),
    }

    await this.log.append(observation)
    return { accepted: true, storedObservation: true }
  }

  async observations(): Promise<StatsObservation[]> {
    return this.log.readAll()
  }
}
