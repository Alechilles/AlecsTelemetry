import { z } from 'zod'

const loadedModSchema = z.object({
  identifier: z.string().min(1),
  version: z.string().min(1),
})

const breadcrumbSchema = z.object({
  atUtc: z.string().min(1),
  category: z.string().min(1),
  detail: z.string().min(1),
})

const causeSchema = z.object({
  type: z.string().min(1),
  message: z.string().min(1),
  stack: z.array(z.string()),
})

const throwableSchema = z.object({
  type: z.string().min(1),
  message: z.string().min(1),
  stack: z.array(z.string()),
  causes: z.array(causeSchema),
})

const attributionSchema = z.object({
  identifiedPlugin: z.string().nullable().optional(),
  matchedPluginIdentifier: z.boolean(),
  matchedStackPrefix: z.boolean(),
})

const runtimeSchema = z.object({
  javaVersion: z.string().min(1),
  runtimeVersion: z.string().min(1),
  osName: z.string().min(1),
  osVersion: z.string().min(1),
  osArch: z.string().min(1),
  hytaleBuild: z.string().min(1),
  serverVersion: z.string().min(1),
  loadedMods: z.array(loadedModSchema),
})

export const telemetryCrashEnvelopeSchema = z.object({
  schemaVersion: z.number().int().min(1),
  eventType: z.literal('crash'),
  reportId: z.string().min(1),
  projectId: z.string().min(1),
  projectDisplayName: z.string().min(1),
  source: z.string().min(1),
  fingerprint: z.string().min(1),
  capturedAtUtc: z.string().min(1),
  lastCapturedAtUtc: z.string().min(1),
  occurrenceCount: z.number().int().min(1),
  pluginIdentifier: z.string().min(1),
  pluginVersion: z.string().min(1),
  threadName: z.string().min(1),
  worldName: z.string().nullable().optional(),
  worldRemovalReason: z.string().nullable().optional(),
  worldFailurePluginIdentifier: z.string().nullable().optional(),
  attribution: attributionSchema,
  breadcrumbs: z.array(breadcrumbSchema),
  throwable: throwableSchema,
  runtime: runtimeSchema,
})

export type TelemetryCrashEnvelope = z.infer<typeof telemetryCrashEnvelopeSchema>
