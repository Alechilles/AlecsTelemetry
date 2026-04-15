import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { z } from 'zod'

const discordTargetSchema = z.object({
  channelId: z.string().min(1),
  guildId: z.string().min(1).optional(),
})

export const hostedProjectConfigSchema = z.object({
  projectId: z.string().min(1),
  displayName: z.string().min(1),
  publicProjectKey: z.string().min(1),
  enabled: z.boolean().default(true),
  rateLimitPerMinute: z.number().int().min(1).max(10_000).default(60),
  maxPayloadBytes: z.number().int().min(1024).max(1_048_576).default(262_144),
  duplicateAlertWindowSeconds: z.number().int().min(0).max(86_400).default(300),
  discord: discordTargetSchema.optional(),
})

const hostedProjectRegistrySchema = z.object({
  projects: z.array(hostedProjectConfigSchema),
})

export type HostedProjectConfig = z.infer<typeof hostedProjectConfigSchema>

export class HostedProjectRegistry {
  private readonly byProjectId: Map<string, HostedProjectConfig>
  private readonly byProjectKey: Map<string, HostedProjectConfig>

  private constructor(projects: HostedProjectConfig[]) {
    this.byProjectId = new Map()
    this.byProjectKey = new Map()
    for (const project of projects) {
      this.byProjectId.set(project.projectId.toLowerCase(), project)
      this.byProjectKey.set(project.publicProjectKey, project)
    }
  }

  static async loadFromFile(filePath: string): Promise<HostedProjectRegistry> {
    const absolutePath = resolve(filePath)
    const raw = await readFile(absolutePath, 'utf8')
    const parsed = hostedProjectRegistrySchema.parse(JSON.parse(raw))
    return new HostedProjectRegistry(parsed.projects)
  }

  static fromProjects(projects: HostedProjectConfig[]): HostedProjectRegistry {
    const parsed = hostedProjectRegistrySchema.parse({ projects })
    return new HostedProjectRegistry(parsed.projects)
  }

  findByProjectKey(projectKey: string): HostedProjectConfig | null {
    return this.byProjectKey.get(projectKey) ?? null
  }

  findByProjectId(projectId: string): HostedProjectConfig | null {
    return this.byProjectId.get(projectId.toLowerCase()) ?? null
  }
}
