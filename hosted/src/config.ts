import { resolve } from 'node:path'
import { z } from 'zod'

const envSchema = z.object({
  TELEMETRY_HOST: z.string().min(1).default('127.0.0.1'),
  TELEMETRY_PORT: z.coerce.number().int().min(1).max(65535).default(8787),
  TELEMETRY_PROJECTS_FILE: z.string().min(1).default('./config/projects.json'),
  TELEMETRY_STATS_FILE: z.string().min(1).default('./data/stats-observations.jsonl'),
  TELEMETRY_PORTAL_PUBLIC_ORIGIN: z.string().min(1).optional(),
  TELEMETRY_MAX_REQUEST_BODY_BYTES: z.coerce.number().int().min(1024).max(1_048_576).default(262_144),
  DISCORD_BOT_TOKEN: z.string().min(1).optional(),
  LOG_LEVEL: z.string().min(1).default('info'),
})

export interface HostedServiceConfig {
  readonly host: string
  readonly port: number
  readonly projectsFile: string
  readonly statsFile: string
  readonly portalPublicOrigin: string | null
  readonly maxRequestBodyBytes: number
  readonly discordBotToken: string | null
  readonly logLevel: string
}

export function loadHostedServiceConfig(env: NodeJS.ProcessEnv = process.env): HostedServiceConfig {
  const parsed = envSchema.parse(env)
  return {
    host: parsed.TELEMETRY_HOST,
    port: parsed.TELEMETRY_PORT,
    projectsFile: resolve(parsed.TELEMETRY_PROJECTS_FILE),
    statsFile: resolve(parsed.TELEMETRY_STATS_FILE),
    portalPublicOrigin: parsed.TELEMETRY_PORTAL_PUBLIC_ORIGIN ?? null,
    maxRequestBodyBytes: parsed.TELEMETRY_MAX_REQUEST_BODY_BYTES,
    discordBotToken: parsed.DISCORD_BOT_TOKEN ?? null,
    logLevel: parsed.LOG_LEVEL,
  }
}
