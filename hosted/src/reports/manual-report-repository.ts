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

  listByProject(projectId: string): ManualReportRecord[] {
    return this.records.filter((record) => record.projectId === projectId)
  }

  findByReportId(reportId: string): ManualReportRecord | null {
    return this.records.find((record) => record.reportId === reportId) ?? null
  }
}
