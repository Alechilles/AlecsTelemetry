import 'dotenv/config'

import pino from 'pino'

import { DiscordAlertRouter } from './alerts/discord-alert-router.js'
import { loadHostedServiceConfig } from './config.js'
import { DuplicateAlertSuppressor } from './ingest/duplicate-alert-suppressor.js'
import { ManualReportIngestService } from './ingest/manual-report-ingest-service.js'
import { RequestRateLimiter } from './ingest/request-rate-limiter.js'
import { TelemetryIngestService } from './ingest/telemetry-ingest-service.js'
import { HostedProjectRegistry } from './projects/project-registry.js'
import { ManualReportRepository } from './reports/manual-report-repository.js'
import { ReportStatusService } from './reports/report-status-service.js'
import { createHostedServer } from './server.js'
import { StatsApi } from './stats/stats-api.js'
import { StatsEventLog } from './stats/stats-event-log.js'
import { StatsService } from './stats/stats-service.js'

async function main(): Promise<void> {
  const config = loadHostedServiceConfig()
  const logger = pino({ level: config.logLevel })
  const registry = await HostedProjectRegistry.loadFromFile(config.projectsFile)
  const router = new DiscordAlertRouter(config.discordBotToken, logger)
  const statsLog = new StatsEventLog(config.statsFile)
  const statsService = new StatsService(statsLog)
  const statsApi = new StatsApi(registry, statsService)
  const ingestService = new TelemetryIngestService(
    registry,
    router,
    new RequestRateLimiter(),
    new DuplicateAlertSuppressor(),
    logger,
    statsService,
  )
  const manualReportRepository = new ManualReportRepository()
  const manualReportIngestService = new ManualReportIngestService(
    registry,
    manualReportRepository,
    router,
    new RequestRateLimiter(),
    logger,
  )
  const server = createHostedServer({
    ingestService,
    statsApi,
    manualReportIngestService,
    reportStatusService: new ReportStatusService(manualReportRepository),
    logger,
    maxRequestBodyBytes: config.maxRequestBodyBytes,
  })

  server.listen(config.port, config.host, () => {
    logger.info(
      {
        host: config.host,
        port: config.port,
        projectsFile: config.projectsFile,
        statsFile: config.statsFile,
      },
      'Alec\'s Telemetry hosted ingest service listening.',
    )
  })
}

void main().catch((error) => {
  // eslint-disable-next-line no-console
  console.error(error)
  process.exitCode = 1
})
