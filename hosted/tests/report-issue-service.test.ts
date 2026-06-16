import { describe, expect, it } from 'vitest'

import { createExampleManualReportEnvelope } from '../src/ingest/manual-report-ingest-service.js'
import type { HostedProjectConfig } from '../src/projects/project-registry.js'
import { ManualReportRepository } from '../src/reports/manual-report-repository.js'
import { ReportIssueService } from '../src/reports/report-issue-service.js'
import { ReportStatusService } from '../src/reports/report-status-service.js'

const project: HostedProjectConfig = {
  projectId: 'example-mod',
  displayName: 'Example Mod',
  publicProjectKey: 'pub_example_mod',
  enabled: true,
  rateLimitPerMinute: 60,
  maxPayloadBytes: 262_144,
  duplicateAlertWindowSeconds: 300,
}

describe('ReportIssueService', () => {
  it('lists, consolidates, resolves, and exports manual report issues', () => {
    const repository = new ManualReportRepository()
    repository.save(createExampleManualReportEnvelope(project, {
      reportId: 'report-1',
      title: 'Config save fails',
      formValues: {
        severity: 'major',
      },
    }), new Date('2026-06-16T20:00:00Z'))
    repository.save(createExampleManualReportEnvelope(project, {
      reportId: 'report-2',
      title: 'Config still fails',
      formValues: {
        severity: 'major',
      },
    }), new Date('2026-06-16T20:01:00Z'))
    repository.save(createExampleManualReportEnvelope(project, {
      reportId: 'suggestion-1',
      reportKind: 'suggestion',
      title: 'Add config presets',
    }), new Date('2026-06-16T20:02:00Z'))

    const service = new ReportIssueService(repository)
    const statusService = new ReportStatusService(repository)

    expect(service.listReports(project.projectId, 'issue')).toHaveLength(2)
    expect(service.listReports(project.projectId, 'suggestion')).toHaveLength(1)

    const issue = service.consolidateReports(
      project.projectId,
      ['report-1', 'report-2'],
      'Config screen does not save',
      new Date('2026-06-16T20:03:00Z'),
    )

    expect(repository.findByReportId('report-1')?.issueId).toBe(issue.issueId)
    expect(repository.findByReportId('report-1')?.status).toBe('triaged')
    expect(repository.findByReportId('report-2')?.issueId).toBe(issue.issueId)

    const resolved = service.resolveIssue(issue.issueId, new Date('2026-06-16T20:04:00Z'))

    expect(resolved?.status).toBe('resolved')
    expect(repository.findByReportId('report-1')?.status).toBe('resolved')
    expect(statusService.lookup('report-1', 'follow-up-token')).toMatchObject({
      found: true,
      status: 'resolved',
      resolved: true,
    })

    const githubPayload = service.buildGithubIssuePayload(issue.issueId, 'https://telemetry.alecsmods.com')

    expect(githubPayload?.title).toBe('Config screen does not save')
    expect(githubPayload?.reportIds).toEqual(['report-1', 'report-2'])
    expect(githubPayload?.labels).toContain('player-report')
    expect(githubPayload?.body).toContain('report-1, report-2')
    expect(githubPayload?.body).toContain('Hytale build:')
    expect(githubPayload?.body).toContain('https://telemetry.alecsmods.com/issues/')
  })
})
