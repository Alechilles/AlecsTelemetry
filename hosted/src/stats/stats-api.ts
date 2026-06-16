import type { HostedProjectRegistry } from '../projects/project-registry.js'
import { StatsAggregator } from './stats-aggregator.js'
import type { StatsService } from './stats-service.js'

export interface StatsApiResponse {
  readonly status: number
  readonly body: object
}

function pathParts(url: string): string[] {
  return url.split('?')[0]?.split('/').filter(Boolean) ?? []
}

export class StatsApi {
  constructor(
    private readonly registry: HostedProjectRegistry,
    private readonly statsService: Pick<StatsService, 'observations'>,
    private readonly nowMs: number = Date.now(),
  ) {}

  async handle(method: string, url: string): Promise<StatsApiResponse> {
    if (method !== 'GET') {
      return { status: 405, body: { error: 'method_not_allowed' } }
    }

    const parts = pathParts(url)
    if (parts[0] !== 'api' || parts[1] !== 'v1' || parts[2] !== 'projects') {
      return { status: 404, body: { error: 'not_found' } }
    }

    const projectId = parts[3]
    if (!projectId) {
      return {
        status: 200,
        body: {
          projects: this.registry.publicProjects().map((project) => ({
            projectId: project.projectId,
            displayName: project.displayName,
            slug: project.stats.slug ?? project.projectId,
          })),
        },
      }
    }

    const project = this.registry.findByProjectId(projectId)
    if (!project || !project.stats.public) {
      return { status: 404, body: { error: 'not_found' } }
    }

    const aggregator = new StatsAggregator(await this.statsService.observations(), this.nowMs)
    if (parts[4] === 'summary') {
      return { status: 200, body: aggregator.summary(project.projectId) }
    }

    if (parts[4] === 'charts' && parts[5] === 'plugin_version') {
      return {
        status: 200,
        body: {
          chartId: 'plugin_version',
          type: 'simple_pie',
          data: aggregator.category(project.projectId, 'pluginVersion'),
        },
      }
    }

    return {
      status: 200,
      body: {
        projectId: project.projectId,
        displayName: project.displayName,
        stats: project.stats,
      },
    }
  }
}
