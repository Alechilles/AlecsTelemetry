import { z } from 'zod'

const loadedModSchema = z.object({
  identifier: z.string().min(1),
  version: z.string().min(1),
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

const contactSchema = z.object({
  provided: z.boolean(),
  value: z.string().nullable().optional(),
})

const attachmentManifestSchema = z.object({
  attachmentId: z.string().min(1),
  kind: z.string().min(1),
  fileName: z.string().min(1),
  contentType: z.string().min(1),
  byteCount: z.number().int().min(0),
  sha256: z.string().min(1),
})

const attachmentSchema = z.object({
  manifest: attachmentManifestSchema,
  content: z.string(),
})

const contextSchema = z.object({
  runtimeMode: z.string().min(1),
  singleplayer: z.boolean().optional(),
  onlinePlayerCount: z.number().int().min(0),
  worldName: z.string().nullable().optional(),
  loadedModsIncluded: z.boolean(),
  diagnosticsIncluded: z.boolean(),
})

export const manualReportEnvelopeSchema = z.object({
  schemaVersion: z.number().int().min(1),
  eventType: z.literal('manual_report'),
  reportId: z.string().min(1),
  followUpTokenHash: z.string().min(1).optional(),
  reportKind: z.enum(['issue', 'suggestion']),
  projectId: z.string().min(1),
  projectDisplayName: z.string().min(1),
  submittedAtUtc: z.string().min(1),
  source: z.string().min(1),
  sessionId: z.string().min(1),
  serverId: z.string().min(1),
  pluginIdentifier: z.string().min(1),
  pluginVersion: z.string().min(1),
  title: z.string().min(1).max(120),
  description: z.string().min(1).max(4000),
  contact: contactSchema,
  formValues: z.record(z.unknown()),
  attachmentManifests: z.array(attachmentManifestSchema),
  attachments: z.array(attachmentSchema).optional().default([]),
  context: contextSchema,
  environment: z.record(z.unknown()),
  runtime: runtimeSchema,
})

export type ManualReportEnvelope = z.infer<typeof manualReportEnvelopeSchema>
