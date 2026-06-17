import { describe, expect, it } from 'vitest'

import { HostedProjectRegistry } from '../src/projects/project-registry.js'

describe('HostedProjectRegistry stats config', () => {
  it('normalizes public stats metadata', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
      enabled: true,
      stats: {
        public: true,
        slug: 'example-mod',
        description: 'Example usage stats.',
      },
    }])

    const project = registry.findByProjectId('EXAMPLE-MOD')
    expect(project?.stats.public).toBe(true)
    expect(project?.stats.slug).toBe('example-mod')
  })

  it('defaults stats to private', () => {
    const registry = HostedProjectRegistry.fromProjects([{
      projectId: 'example-mod',
      displayName: 'Example Mod',
      publicProjectKey: 'public-key',
    }])

    const project = registry.findByProjectId('example-mod')
    expect(project?.stats.public).toBe(false)
  })
})
