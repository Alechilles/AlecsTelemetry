import type { ManualReportEnvelope } from '../contract/manual-report-envelope.js'

export interface ManualReportRecord {
  readonly reportId: string
  readonly projectId: string
  readonly reportKind: 'issue' | 'suggestion'
  readonly title: string
  readonly receivedAtUtc: string
  readonly envelope: ManualReportEnvelope
  readonly issueId: string | null
  readonly status: 'received' | 'triaged' | 'resolved' | 'rejected'
}

export class ManualReportRepository {
  private readonly records: ManualReportRecord[] = []

  save(envelope: ManualReportEnvelope, receivedAt: Date): ManualReportRecord {
    const record: ManualReportRecord = {
      reportId: envelope.reportId,
      projectId: envelope.projectId,
      reportKind: envelope.reportKind,
      title: envelope.title,
      receivedAtUtc: receivedAt.toISOString(),
      envelope,
      issueId: null,
      status: 'received',
    }
    this.records.push(record)
    return record
  }

  listByProject(projectId: string, reportKind?: 'issue' | 'suggestion'): ManualReportRecord[] {
    return this.records.filter((record) => record.projectId === projectId && (!reportKind || record.reportKind === reportKind))
  }

  findByReportId(reportId: string): ManualReportRecord | null {
    return this.records.find((record) => record.reportId === reportId) ?? null
  }

  linkReportsToIssue(reportIds: string[], issueId: string): ManualReportRecord[] {
    const linked: ManualReportRecord[] = []
    for (let index = 0; index < this.records.length; index += 1) {
      const record = this.records[index]
      if (!record || !reportIds.includes(record.reportId)) {
        continue
      }
      const updated = {
        ...record,
        issueId,
        status: 'triaged' as const,
      }
      this.records[index] = updated
      linked.push(updated)
    }
    return linked
  }

  updateIssueStatus(issueId: string, status: ManualReportRecord['status']): ManualReportRecord[] {
    const updatedRecords: ManualReportRecord[] = []
    for (let index = 0; index < this.records.length; index += 1) {
      const record = this.records[index]
      if (!record || record.issueId !== issueId) {
        continue
      }
      const updated = {
        ...record,
        status,
      }
      this.records[index] = updated
      updatedRecords.push(updated)
    }
    return updatedRecords
  }
}
