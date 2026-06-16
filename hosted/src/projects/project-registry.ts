import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { z } from 'zod'

const discordTargetSchema = z.object({
  channelId: z.string().min(1),
  guildId: z.string().min(1).optional(),
})

const statsChartTypeSchema = z.enum([
  'simple_pie',
  'advanced_pie',
  'drilldown_pie',
  'single_line',
  'multi_line',
  'simple_bar',
  'advanced_bar',
])

const statsChartConfigSchema = z.object({
  chartId: z.string().regex(/^[a-z0-9_:-]{1,80}$/),
  title: z.string().min(1).max(120),
  type: statsChartTypeSchema,
  sourceEvent: z.string().min(1).max(120).default('chart_sample'),
  valueField: z.string().min(1).max(80).optional(),
  seriesField: z.string().min(1).max(80).optional(),
  groupField: z.string().min(1).max(80).optional(),
  subGroupField: z.string().min(1).max(80).optional(),
  countField: z.string().min(1).max(80).optional(),
  position: z.number().int().min(0).max(1000).default(100),
})

const statsProjectConfigSchema = z.object({
  public: z.boolean().default(false),
  slug: z.string().regex(/^[a-z0-9-]{1,120}$/).optional(),
  description: z.string().max(500).optional(),
  charts: z.array(statsChartConfigSchema).default([]),
}).default({})

export const hostedProjectConfigSchema = z.object({
  projectId: z.string().min(1),
  displayName: z.string().min(1),
  publicProjectKey: z.string().min(1),
  enabled: z.boolean().default(true),
  rateLimitPerMinute: z.number().int().min(1).max(10_000).default(60),
  maxPayloadBytes: z.number().int().min(1024).max(1_048_576).default(262_144),
  duplicateAlertWindowSeconds: z.number().int().min(0).max(86_400).default(300),
  discord: discordTargetSchema.optional(),
  stats: statsProjectConfigSchema,
})

const hostedProjectRegistrySchema = z.object({
  projects: z.array(hostedProjectConfigSchema),
})

export type HostedProjectConfig = z.infer<typeof hostedProjectConfigSchema>
export type HostedProjectInput = z.input<typeof hostedProjectConfigSchema>
export type StatsChartConfig = z.infer<typeof statsChartConfigSchema>
export type StatsProjectConfig = z.infer<typeof statsProjectConfigSchema>

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

  static fromProjects(projects: HostedProjectInput[]): HostedProjectRegistry {
    const parsed = hostedProjectRegistrySchema.parse({ projects })
    return new HostedProjectRegistry(parsed.projects)
  }

  findByProjectKey(projectKey: string): HostedProjectConfig | null {
    return this.byProjectKey.get(projectKey) ?? null
  }

  findByProjectId(projectId: string): HostedProjectConfig | null {
    return this.byProjectId.get(projectId.toLowerCase()) ?? null
  }

  publicProjects(): HostedProjectConfig[] {
    return Array.from(this.byProjectId.values()).filter((project) => project.stats.public)
  }
}
