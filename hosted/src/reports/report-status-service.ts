import { createHash } from 'node:crypto'

import type { ManualReportRepository } from './manual-report-repository.js'

export interface ReportStatusLookupResult {
  readonly found: boolean
  readonly reportId: string
  readonly status: 'received' | 'triaged' | 'resolved' | 'rejected' | null
  readonly resolved: boolean
}

export class ReportStatusService {
  constructor(private readonly repository: ManualReportRepository) {}

  lookup(reportId: string, followUpToken: string): ReportStatusLookupResult {
    const record = this.repository.findByReportId(reportId)
    if (!record || record.envelope.followUpTokenHash !== hashFollowUpToken(followUpToken)) {
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

export function hashFollowUpToken(followUpToken: string): string {
  return createHash('sha256').update(followUpToken).digest('hex').slice(0, 24)
}
