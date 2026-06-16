import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'

import type { Logger } from 'pino'

import type { ManualReportIngestService } from './ingest/manual-report-ingest-service.js'
import type { TelemetryIngestService } from './ingest/telemetry-ingest-service.js'
import type { ReportStatusService } from './reports/report-status-service.js'

function writeJson(response: ServerResponse, status: number, body: Record<string, unknown>): void {
  const payload = JSON.stringify(body)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  response.end(payload)
}

async function readJsonBody(request: IncomingMessage, maxBytes: number): Promise<{ bodyBytes: number; payload: unknown }> {
  let totalBytes = 0
  const chunks: Buffer[] = []
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
    totalBytes += buffer.length
    if (totalBytes > maxBytes) {
      throw new Error('request_body_too_large')
    }
    chunks.push(buffer)
  }
  const raw = Buffer.concat(chunks).toString('utf8')
  return {
    bodyBytes: totalBytes,
    payload: JSON.parse(raw),
  }
}

export function createHostedServer(options: {
  readonly ingestService: TelemetryIngestService
  readonly manualReportIngestService?: ManualReportIngestService
  readonly reportStatusService?: ReportStatusService
  readonly logger: Logger
  readonly maxRequestBodyBytes: number
}): Server {
  return createServer(async (request, response) => {
    if (request.method === 'GET' && request.url === '/healthz') {
      writeJson(response, 200, { ok: true })
      return
    }

    const isCrashIngest = request.method === 'POST' && request.url === '/ingest/crash'
    const isManualReportIngest = request.method === 'POST' && request.url === '/ingest/report'
    const isReportStatus = request.method === 'POST' && request.url === '/reports/status'
    if (!isCrashIngest && !isManualReportIngest && !isReportStatus) {
      writeJson(response, 404, { error: 'not_found' })
      return
    }

    const contentType = request.headers['content-type']
    if (typeof contentType !== 'string' || !contentType.toLowerCase().includes('application/json')) {
      writeJson(response, 400, { accepted: false, error: 'invalid_content_type' })
      return
    }

    try {
      const { bodyBytes, payload } = await readJsonBody(request, options.maxRequestBodyBytes)
      if (isReportStatus) {
        if (!options.reportStatusService) {
          writeJson(response, 404, { error: 'not_found' })
          return
        }
        if (!payload || typeof payload !== 'object') {
          writeJson(response, 400, { found: false, error: 'invalid_payload' })
          return
        }
        const body = payload as Record<string, unknown>
        if (typeof body.reportId !== 'string' || typeof body.followUpToken !== 'string') {
          writeJson(response, 400, { found: false, error: 'invalid_payload' })
          return
        }
        writeJson(response, 200, options.reportStatusService.lookup(body.reportId, body.followUpToken) as unknown as Record<string, unknown>)
        return
      }
      const projectKeyHeader = request.headers['x-telemetry-project-key']
      const projectKey = Array.isArray(projectKeyHeader) ? projectKeyHeader[0] ?? null : projectKeyHeader ?? null
      const targetService = isManualReportIngest ? options.manualReportIngestService : options.ingestService
      if (!targetService) {
        writeJson(response, 404, { error: 'not_found' })
        return
      }
      const result = await targetService.ingest({
        projectKey,
        payload,
        bodyBytes,
        remoteAddress: request.socket.remoteAddress ?? null,
        receivedAt: new Date(),
      })
      writeJson(response, result.status, result.body)
    } catch (error) {
      if (error instanceof Error && error.message === 'request_body_too_large') {
        writeJson(response, 413, { accepted: false, error: 'request_body_too_large' })
        return
      }
      if (error instanceof SyntaxError) {
        writeJson(response, 400, { accepted: false, error: 'invalid_json' })
        return
      }
      options.logger.error({ error }, 'Unhandled hosted ingest server error.')
      writeJson(response, 500, { accepted: false, error: 'internal_server_error' })
    }
  })
}
