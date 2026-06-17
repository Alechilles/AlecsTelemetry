import { z } from 'zod'

const nullableStringSchema = z.string().min(1).nullable().optional()
const recordSchema = z.record(z.union([z.string(), z.number(), z.boolean()])).default({})

export const runtimeLoadedModSchema = z.object({
  identifier: z.string().min(1),
  version: z.string().min(1),
})

export const runtimeMetadataSchema = z.object({
  javaVersion: z.string().min(1),
  runtimeVersion: z.string().min(1),
  osName: z.string().min(1),
  osVersion: z.string().min(1),
  osArch: z.string().min(1),
  cpuCores: z.number().int().min(1).default(1),
  hytaleBuild: z.string().min(1),
  serverVersion: z.string().min(1),
  serverHostingMode: z.enum(['local_client', 'dedicated', 'unknown']).default('unknown'),
  loadedMods: z.array(runtimeLoadedModSchema).default([]),
})

export const environmentSnapshotSchema = z.object({
  hytaleBuild: z.string().min(1),
  serverVersion: z.string().min(1),
  modSetHash: z.string().min(1),
}).nullable().optional()

export const telemetryEventEnvelopeSchema = z.object({
  schemaVersion: z.number().int().min(1),
  eventType: z.enum(['error', 'lifecycle', 'performance', 'usage', 'stats']),
  eventName: z.string().min(1),
  eventId: z.string().min(1),
  projectId: z.string().min(1),
  projectDisplayName: z.string().min(1),
  source: z.string().min(1),
  sessionId: z.string().min(1),
  serverId: z.string().min(1),
  fingerprint: nullableStringSchema,
  capturedAtUtc: z.string().min(1),
  pluginIdentifier: z.string().min(1),
  pluginVersion: z.string().min(1),
  worldName: nullableStringSchema,
  severity: z.enum(['info', 'warning', 'error']).default('info'),
  durationMs: z.number().int().min(0).nullable().optional(),
  metricValue: z.number().nullable().optional(),
  subsystem: nullableStringSchema,
  phase: nullableStringSchema,
  operation: nullableStringSchema,
  target: nullableStringSchema,
  featureKey: nullableStringSchema,
  entryPoint: nullableStringSchema,
  runtimeSide: nullableStringSchema,
  entityType: nullableStringSchema,
  itemId: nullableStringSchema,
  blockId: nullableStringSchema,
  biomeId: nullableStringSchema,
  commandName: nullableStringSchema,
  environment: environmentSnapshotSchema,
  attributes: recordSchema,
  details: recordSchema,
  runtime: runtimeMetadataSchema,
})

export type TelemetryEventEnvelope = z.infer<typeof telemetryEventEnvelopeSchema>
