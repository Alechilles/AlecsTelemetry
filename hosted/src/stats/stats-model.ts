export interface LoadedModSummary {
  readonly identifier: string
  readonly version: string
}

export interface StatsObservation {
  readonly projectId: string
  readonly eventId: string
  readonly eventName: string
  readonly serverId: string
  readonly sessionId: string
  readonly observedAtUtc: string
  readonly playersOnline: number
  readonly pluginVersion: string
  readonly hytaleBuild: string
  readonly serverVersion: string
  readonly javaVersion: string
  readonly runtimeVersion: string
  readonly osName: string
  readonly osVersion: string
  readonly osArch: string
  readonly cpuCores: number
  readonly serverHostingMode: 'local_client' | 'dedicated' | 'unknown'
  readonly countryCode: string
  readonly loadedMods: readonly LoadedModSummary[]
  readonly custom: Record<string, string | number | boolean>
}

export interface StatsLinePoint {
  readonly timestamp: number
  readonly value: number
}

export interface StatsSummary {
  readonly projectId: string
  readonly activeServers: number
  readonly activePlayers: number
  readonly recordServers: number
  readonly recordPlayers: number
  readonly lastUpdatedAtUtc: string | null
}
