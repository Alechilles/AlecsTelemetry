import type { ManualReportRepository } from './manual-report-repository.js'

export interface ReportStatusLookupResult {
  readonly found: boolean
  readonly reportId: string
  readonly status: 'received' | 'triaged' | 'resolved' | 'rejected' | null
  readonly resolved: boolean
}

export class ReportStatusService {
  constructor(private readonly repository: ManualReportRepository) {}

  lookup(reportId: string, followUpTokenHash: string): ReportStatusLookupResult {
    const record = this.repository.findByReportId(reportId)
    if (!record || record.envelope.followUpTokenHash !== followUpTokenHash) {
      return {
        found: false,
        reportId,
        status: null,
        resolved: false,
      }
    }
    return {
      found: true,
      reportId,
      status: record.status,
      resolved: record.status === 'resolved',
    }
  }
}
