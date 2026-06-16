import { describe, expect, it } from 'vitest'

import { formatReportAlert } from '../src/alerts/report-alert-formatter.js'
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
  discord: {
    channelId: '123456789',
  },
}

describe('formatReportAlert', () => {
  it('includes the important manual report fields', () => {
    const content = formatReportAlert({
      project,
      envelope: createExampleManualReportEnvelope(project, {
        attachmentManifests: [
          {
            attachmentId: 'attachment-1',
            kind: 'current_server_log',
            fileName: 'current-server-log.txt',
            contentType: 'text/plain',
            byteCount: 42,
            sha256: 'abc123',
          },
        ],
      }),
    })

    expect(content).toContain('Example Mod')
    expect(content).toContain('player issue report')
    expect(content).toContain('Report ID: `manual-report-123`')
    expect(content).toContain('Title: Config screen does not save')
    expect(content).toContain('Severity: `major`')
    expect(content).toContain('Attachments: `current_server_log`')
    expect(content).toContain('Players online: `12`')
    expect(content.length).toBeLessThanOrEqual(1900)
  })
})
