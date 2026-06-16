import { randomUUID } from 'node:crypto'

import type { ManualReportRecord, ManualReportRepository } from './manual-report-repository.js'

export interface ReportIssueRecord {
  readonly issueId: string
  readonly projectId: string
  readonly title: string
  readonly status: 'open' | 'resolved'
  readonly reportIds: string[]
  readonly githubIssueUrl: string | null
  readonly createdAtUtc: string
  readonly resolvedAtUtc: string | null
}

export interface GithubIssueExportPayload {
  readonly title: string
  readonly body: string
  readonly labels: string[]
  readonly reportIds: string[]
}

export class ReportIssueService {
  private readonly issues = new Map<string, ReportIssueRecord>()

  constructor(private readonly repository: ManualReportRepository) {}

  listReports(projectId: string, reportKind?: 'issue' | 'suggestion'): ManualReportRecord[] {
    return this.repository.listByProject(projectId, reportKind)
  }

  consolidateReports(projectId: string, reportIds: string[], title: string, now = new Date()): ReportIssueRecord {
    const issueId = randomUUID()
    const issue: ReportIssueRecord = {
      issueId,
      projectId,
      title,
      status: 'open',
      reportIds: [...reportIds],
      githubIssueUrl: null,
      createdAtUtc: now.toISOString(),
      resolvedAtUtc: null,
    }
    this.issues.set(issueId, issue)
    this.repository.linkReportsToIssue(reportIds, issueId)
    return issue
  }

  resolveIssue(issueId: string, now = new Date()): ReportIssueRecord | null {
    const issue = this.issues.get(issueId)
    if (!issue) {
      return null
    }
    const resolved: ReportIssueRecord = {
      ...issue,
      status: 'resolved',
      resolvedAtUtc: now.toISOString(),
    }
    this.issues.set(issueId, resolved)
    this.repository.updateIssueStatus(issueId, 'resolved')
    return resolved
  }

  findIssue(issueId: string): ReportIssueRecord | null {
    return this.issues.get(issueId) ?? null
  }

  buildGithubIssuePayload(issueId: string, portalBaseUrl: string): GithubIssueExportPayload | null {
    const issue = this.issues.get(issueId)
    if (!issue) {
      return null
    }
    const reports = issue.reportIds
      .map((reportId) => this.repository.findByReportId(reportId))
      .filter((record): record is ManualReportRecord => record !== null)
    const firstReport = reports[0]
    const environment = firstReport?.envelope.environment ?? {}
    const runtime = firstReport?.envelope.runtime
    const bodyLines = [
      `Portal: ${portalBaseUrl.replace(/\/$/, '')}/issues/${issue.issueId}`,
      '',
      `Reports: ${issue.reportIds.join(', ')}`,
      `Project: ${issue.projectId}`,
      firstReport ? `Plugin: ${firstReport.envelope.pluginIdentifier} @ ${firstReport.envelope.pluginVersion}` : null,
      runtime ? `Hytale build: ${runtime.hytaleBuild}` : null,
      runtime ? `Server version: ${runtime.serverVersion}` : null,
      `Environment: ${JSON.stringify(environment)}`,
      '',
      ...reports.map((report) => `- ${report.reportId}: ${report.title}`),
    ].filter((line): line is string => line !== null)
    return {
      title: issue.title,
      body: bodyLines.join('\n'),
      labels: ['alecs-telemetry', 'player-report'],
      reportIds: [...issue.reportIds],
    }
  }
}
