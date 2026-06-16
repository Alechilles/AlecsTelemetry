import { describe, expect, it } from 'vitest'

import { HostedProjectRegistry } from '../src/projects/project-registry.js'

describe('HostedProjectRegistry stats config', () => {
  it('normalizes public stats metadata and custom chart definitions', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
      enabled: true,
      stats: {
        public: true,
        slug: 'example-mod',
        description: 'Example usage stats.',
        charts: [
          {
            chartId: 'language',
            title: 'Language',
            type: 'simple_pie',
            sourceEvent: 'chart_sample',
            valueField: 'language',
            position: 20,
          },
        ],
      },
    }])

    const project = registry.findByProjectId('EXAMPLE-MOD')
    expect(project?.stats.public).toBe(true)
    expect(project?.stats.slug).toBe('example-mod')
    expect(project?.stats.charts[0]?.chartId).toBe('language')
  })

  it('defaults stats to private with no custom charts', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
    }])

    const project = registry.findByProjectId('example-mod')
    expect(project?.stats.public).toBe(false)
    expect(project?.stats.charts).toEqual([])
  })
})
