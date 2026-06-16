import { describe, expect, it } from 'vitest'

import { manualReportEnvelopeSchema } from '../src/contract/manual-report-envelope.js'
import { createExampleManualReportEnvelope } from '../src/ingest/manual-report-ingest-service.js'
import type { HostedProjectConfig } from '../src/projects/project-registry.js'

const project: HostedProjectConfig = {
  projectId: 'example-mod',
  displayName: 'Example Mod',
  publicProjectKey: 'pub_example_mod',
  enabled: true,
  rateLimitPerMinute: 60,
  maxPayloadBytes: 262_144,
  duplicateAlertWindowSeconds: 300,
}

describe('manualReportEnvelopeSchema', () => {
  it('accepts a valid issue report envelope', () => {
    const parsed = manualReportEnvelopeSchema.safeParse(createExampleManualReportEnvelope(project))

    expect(parsed.success).toBe(true)
  })

  it('accepts a valid suggestion report envelope', () => {
    const parsed = manualReportEnvelopeSchema.safeParse(createExampleManualReportEnvelope(project, {
      reportKind: 'suggestion',
      reportId: 'suggestion-report-123',
    }))

    expect(parsed.success).toBe(true)
  })

  it('rejects invalid report kinds', () => {
    const parsed = manualReportEnvelopeSchema.safeParse({
      ...createExampleManualReportEnvelope(project),
      reportKind: 'crash',
    })

    expect(parsed.success).toBe(false)
  })
})
