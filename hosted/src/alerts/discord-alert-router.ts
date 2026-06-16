import { REST, Routes } from 'discord.js'
import type { Logger } from 'pino'

import type { CrashAlertRouter, CrashAlertRouteResult, HostedCrashAlert, HostedManualReportAlert } from './alert-router.js'
import { formatCrashAlert } from './crash-alert-formatter.js'
import { formatReportAlert } from './report-alert-formatter.js'

export class DiscordAlertRouter implements CrashAlertRouter {
  private readonly botToken: string | null
  private readonly logger: Logger
  private readonly rest: REST | null

  constructor(botToken: string | null, logger: Logger) {
    this.botToken = botToken
    this.logger = logger
    this.rest = botToken ? new REST({ version: '10' }).setToken(botToken) : null
  }

  async routeCrashAlert(alert: HostedCrashAlert): Promise<CrashAlertRouteResult> {
    return this.routeDiscordMessage(alert.project.projectId, alert.project.discord?.channelId, formatCrashAlert(alert))
  }

  async routeManualReportAlert(alert: HostedManualReportAlert): Promise<CrashAlertRouteResult> {
    return this.routeDiscordMessage(alert.project.projectId, alert.project.discord?.channelId, formatReportAlert(alert))
  }

  private async routeDiscordMessage(projectId: string, channelId: string | undefined, content: string): Promise<CrashAlertRouteResult> {
    if (!channelId) {
      return {
        dispatched: false,
        detail: 'Project has no Discord channel configured.',
      }
    }
    if (!this.botToken || !this.rest) {
      this.logger.warn({ projectId }, 'Discord bot token is not configured; skipping alert dispatch.')
      return {
        dispatched: false,
        detail: 'Discord bot token not configured.',
      }
    }

    await this.rest.post(Routes.channelMessages(channelId), {
      body: {
        content,
      },
    })

    return {
      dispatched: true,
      detail: `Alert sent to Discord channel ${channelId}.`,
    }
  }
}
